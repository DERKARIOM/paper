/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.framing;

import android.graphics.PointF;

/**
 * Accessibility State Machine for document scanning guidance.
 *
 * <p>Implements the concept from accessibility_mode_docquad_concept.md: - 4 states: NO_DOC,
 * DOC_UNSTABLE, DOC_ALIGNING, READY - Hysteresis for state transitions - Sliding-window stability
 * detection - Event prioritization and rate limiting - Model-independent decisions based on
 * geometry only
 *
 * <p>This class is UI-agnostic; it emits events via the {@link EventListener} callback.
 */
public class A11yStateMachine {

  // --- Configuration parameters (from concept spec-sheet section 10.1) ---

  /** Number of frames in the sliding window for stability */
  public static final int N_WINDOW = 10;

  /** Minimum stable frames before a hint can be spoken */
  public static final int MIN_STABLE_FRAMES_HINT = 2;

  /** Minimum stable frames before READY can be entered */
  public static final int MIN_STABLE_FRAMES_READY = 8;

  /** Frames of plausible doc required before entering DOC_UNSTABLE */
  public static final int K_DOC_ENTER = 2;

  /** Frames of bad conditions tolerated before exiting READY */
  public static final int K_READY_EXIT = 3;

  /** Rate limit between spoken hints (ms) */
  public static final long RATE_LIMIT_MS = 1200;

  /** How often OK can be repeated when stable (ms) */
  public static final long OK_REPEAT_MS = 5000;

  /** Minimum quality (from FramingEngine) required for READY */
  public static final float READY_QGEOM_MIN = 0.80f;

  // --- States ---

  public enum State {
    NO_DOC,
    DOC_UNSTABLE,
    DOC_ALIGNING,
    READY
  }

  // --- Events (prioritized, only one emitted per tick) ---

  public enum Event {
    // Priority 1: System/Flow
    CAMERA_READY,
    LOW_LIGHT,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,

    // Priority 2: READY
    READY_ENTER,

    // Priority 3: Hold still
    HOLD_STILL,

    // Priority 4: Tilt
    TILT_LEFT,
    TILT_RIGHT,
    TILT_FORWARD,
    TILT_BACK,

    // Priority 5: Move
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,

    // Priority 6: Distance (only when plausibleDoc)
    MOVE_CLOSER,
    MOVE_BACK,

    // Priority 6b: Too far (quad detected but too small - can be emitted without plausibleDoc)
    TOO_FAR,

    // Priority 7: Orientation tip (only in NO_DOC)
    ORIENTATION_PORTRAIT_TIP,
    ORIENTATION_LANDSCAPE_TIP,

    // Special: OK/aligned (only on transition or after long quiet)
    OK
  }

  public interface EventListener {
    void onEvent(Event event, State state);
  }

  // --- Internal state ---

  private State currentState = State.NO_DOC;
  private final FrameHistory frameHistory = new FrameHistory(N_WINDOW);

  // Hysteresis counters
  private int plausibleDocCounter = 0;
  private int badFrameCounter = 0;

  // Rate limiting
  private long lastEventTs = 0;
  private Event lastEmittedEvent = null;
  private long lastOkTs = 0;

  // Last hint for stability tracking
  private GuidanceHint lastStableHint = null;
  private int hintStableCount = 0;

  // --- Public API ---

  /** Reset the state machine to initial state. */
  public void reset() {
    currentState = State.NO_DOC;
    frameHistory.clear();
    plausibleDocCounter = 0;
    badFrameCounter = 0;
    lastEventTs = 0;
    lastEmittedEvent = null;
    lastOkTs = 0;
    lastStableHint = null;
    hintStableCount = 0;
  }

  /** Get the current state. */
  public State getState() {
    return currentState;
  }

  /**
   * Threshold for focus distance (diopters) below which object is considered too far. 1.0 diopters
   * = 1 meter distance. Lower values = farther away.
   */
  public static final float TOO_FAR_FOCUS_THRESHOLD_DIOPTERS = 1.0f;

  /**
   * Process a new frame and potentially emit an event. Overload without focus distance - delegates
   * to full version with 0 (unavailable).
   *
   * @param quad detected quad (TL, TR, BR, BL) or null if no detection
   * @param imageWidth image width in pixels
   * @param imageHeight image height in pixels
   * @param framingResult result from FramingEngine (may be null)
   * @param nowMs current timestamp in milliseconds
   * @param listener callback for emitted events (may be null)
   */
  public void onFrame(
      PointF[] quad,
      int imageWidth,
      int imageHeight,
      FramingResult framingResult,
      long nowMs,
      EventListener listener) {
    onFrame(quad, imageWidth, imageHeight, framingResult, 0f, nowMs, listener);
  }

  /**
   * Process a new frame and potentially emit an event.
   *
   * @param quad detected quad (TL, TR, BR, BL) or null if no detection
   * @param imageWidth image width in pixels
   * @param imageHeight image height in pixels
   * @param framingResult result from FramingEngine (may be null)
   * @param focusDistanceDiopters focus distance in diopters (1/meters), 0 if unavailable
   * @param nowMs current timestamp in milliseconds
   * @param listener callback for emitted events (may be null)
   */
  public void onFrame(
      PointF[] quad,
      int imageWidth,
      int imageHeight,
      FramingResult framingResult,
      float focusDistanceDiopters,
      long nowMs,
      EventListener listener) {

    // 1. Compute plausibility (Signal A) - use detailed check for TOO_FAR detection
    QuadPlausibility.Result plausibilityResult =
        QuadPlausibility.check(quad, imageWidth, imageHeight);
    boolean plausibleDoc = plausibilityResult.plausible;

    // Check if quad was detected but is too small (TOO_FAR condition)
    // A quad is "detected but too far" if it has valid geometry but fails min area check
    boolean quadDetectedButTooSmall =
        (quad != null && quad.length == 4)
            && plausibilityResult.isConvex
            && plausibilityResult.noSelfIntersection
            && !plausibilityResult.meetsMinArea
            && plausibilityResult.withinBounds;

    // Model-free distance check: if focus distance is available and indicates far object
    // Focus distance in diopters: higher = closer, lower = farther
    // If focusDistanceDiopters > 0 and < threshold, object is too far
    boolean focusIndicatesTooFar =
        (focusDistanceDiopters > 0 && focusDistanceDiopters < TOO_FAR_FOCUS_THRESHOLD_DIOPTERS);

    // 2. Get framing metrics (Signal B) - already computed by FramingEngine
    float quality = (framingResult != null) ? framingResult.quality : 0f;
    GuidanceHint rawHint = (framingResult != null) ? framingResult.hint : null;

    // Override hint to TOO_FAR if:
    // - quad is detected but too small, OR
    // - focus distance indicates object is too far (model-free detection)
    if ((quadDetectedButTooSmall && !plausibleDoc)
        || (focusIndicatesTooFar && !plausibleDoc && quad == null)) {
      rawHint = GuidanceHint.TOO_FAR;
    }

    // 3. Update frame history for stability (Signal C)
    frameHistory.add(new FrameData(quality, rawHint));

    // 4. Compute stability
    boolean stableForGuidance = frameHistory.isHintStable(MIN_STABLE_FRAMES_HINT);
    boolean stableForReady =
        frameHistory.isHintStable(MIN_STABLE_FRAMES_READY)
            && frameHistory.isQualityStable(MIN_STABLE_FRAMES_READY, READY_QGEOM_MIN);

    // 5. Update hint stability counter
    if (rawHint == lastStableHint) {
      hintStableCount++;
    } else {
      lastStableHint = rawHint;
      hintStableCount = 1;
    }

    // 6. State transitions with hysteresis
    State previousState = currentState;
    updateState(plausibleDoc, stableForGuidance, stableForReady, quality);

    // 7. Determine event to emit (prioritized)
    Event event = determineEvent(previousState, plausibleDoc, rawHint, nowMs);

    // 8. Emit event if allowed by rate limit
    if (event != null && listener != null) {
      if (shouldEmit(event, nowMs)) {
        listener.onEvent(event, currentState);
        lastEventTs = nowMs;
        lastEmittedEvent = event;
        if (event == Event.OK) {
          lastOkTs = nowMs;
        }
      }
    }
  }

  /**
   * Inject a system event (e.g., LOW_LIGHT, FLASHLIGHT_ON). These bypass the normal frame
   * processing but still respect rate limiting.
   */
  public void injectSystemEvent(Event event, long nowMs, EventListener listener) {
    if (event == null || listener == null) return;

    // System events have highest priority
    if (shouldEmit(event, nowMs)) {
      listener.onEvent(event, currentState);
      lastEventTs = nowMs;
      lastEmittedEvent = event;
    }
  }

  // --- State transition logic ---

  private void updateState(
      boolean plausibleDoc, boolean stableForGuidance, boolean stableForReady, float quality) {
    switch (currentState) {
      case NO_DOC:
        if (plausibleDoc) {
          plausibleDocCounter++;
          if (plausibleDocCounter >= K_DOC_ENTER) {
            currentState = State.DOC_UNSTABLE;
            plausibleDocCounter = 0;
          }
        } else {
          plausibleDocCounter = 0;
        }
        break;

      case DOC_UNSTABLE:
        if (!plausibleDoc) {
          badFrameCounter++;
          if (badFrameCounter >= K_DOC_ENTER) {
            currentState = State.NO_DOC;
            badFrameCounter = 0;
          }
        } else {
          badFrameCounter = 0;
          if (stableForGuidance) {
            currentState = State.DOC_ALIGNING;
          }
        }
        break;

      case DOC_ALIGNING:
        if (!plausibleDoc) {
          badFrameCounter++;
          if (badFrameCounter >= K_DOC_ENTER) {
            currentState = State.NO_DOC;
            badFrameCounter = 0;
          }
        } else {
          badFrameCounter = 0;
          if (stableForReady && quality >= READY_QGEOM_MIN) {
            currentState = State.READY;
          } else if (!stableForGuidance) {
            currentState = State.DOC_UNSTABLE;
          }
        }
        break;

      case READY:
        boolean stillGood = plausibleDoc && quality >= READY_QGEOM_MIN;
        if (!stillGood) {
          badFrameCounter++;
          if (badFrameCounter >= K_READY_EXIT) {
            // Determine where to go
            if (!plausibleDoc) {
              currentState = State.NO_DOC;
            } else if (!stableForGuidance) {
              currentState = State.DOC_UNSTABLE;
            } else {
              currentState = State.DOC_ALIGNING;
            }
            badFrameCounter = 0;
          }
        } else {
          badFrameCounter = 0;
        }
        break;
    }
  }

  // --- Event determination with priority ---

  private Event determineEvent(
      State previousState, boolean plausibleDoc, GuidanceHint rawHint, long nowMs) {

    // Priority 2: READY_ENTER (only on state transition)
    if (currentState == State.READY && previousState != State.READY) {
      return Event.READY_ENTER;
    }

    // Priority 3: HOLD_STILL (in DOC_UNSTABLE)
    if (currentState == State.DOC_UNSTABLE) {
      return Event.HOLD_STILL;
    }

    // In NO_DOC state: only orientation tips allowed
    if (currentState == State.NO_DOC) {
      if (rawHint == GuidanceHint.ORIENTATION_PORTRAIT_TIP) {
        return Event.ORIENTATION_PORTRAIT_TIP;
      } else if (rawHint == GuidanceHint.ORIENTATION_LANDSCAPE_TIP) {
        return Event.ORIENTATION_LANDSCAPE_TIP;
      }
      return null; // Stay quiet in NO_DOC
    }

    // In DOC_ALIGNING or READY: emit guidance hints
    if (currentState == State.DOC_ALIGNING || currentState == State.READY) {
      // Only emit if hint is stable
      if (hintStableCount < MIN_STABLE_FRAMES_HINT) {
        return null;
      }

      // Map GuidanceHint to Event with priority
      Event event = mapHintToEvent(rawHint, plausibleDoc);

      // Special handling for OK
      if (event == Event.OK) {
        // Only emit OK on transition to good state or after long quiet
        boolean transitionToGood = (previousState != State.READY && currentState == State.READY);
        boolean longSinceOk = (nowMs - lastOkTs) >= OK_REPEAT_MS;
        if (!transitionToGood && !longSinceOk) {
          return null;
        }
      }

      return event;
    }

    return null;
  }

  private Event mapHintToEvent(GuidanceHint hint, boolean plausibleDoc) {
    if (hint == null) return null;

    switch (hint) {
        // Priority 4: Tilt
      case TILT_LEFT:
        return Event.TILT_LEFT;
      case TILT_RIGHT:
        return Event.TILT_RIGHT;
      case TILT_FORWARD:
        return Event.TILT_FORWARD;
      case TILT_BACK:
        return Event.TILT_BACK;

        // Priority 5: Move
      case MOVE_LEFT:
        return Event.MOVE_LEFT;
      case MOVE_RIGHT:
        return Event.MOVE_RIGHT;
      case MOVE_UP:
        return Event.MOVE_UP;
      case MOVE_DOWN:
        return Event.MOVE_DOWN;

        // Priority 6: Distance (only when plausibleDoc)
      case MOVE_CLOSER:
        return plausibleDoc ? Event.MOVE_CLOSER : null;
      case MOVE_BACK:
        return plausibleDoc ? Event.MOVE_BACK : null;

        // Priority 6b: Too far (can be emitted without plausibleDoc)
      case TOO_FAR:
        return Event.TOO_FAR;

        // Priority 7: Orientation
      case ORIENTATION_PORTRAIT_TIP:
        return Event.ORIENTATION_PORTRAIT_TIP;
      case ORIENTATION_LANDSCAPE_TIP:
        return Event.ORIENTATION_LANDSCAPE_TIP;

        // OK
      case OK:
        return Event.OK;

      default:
        return null;
    }
  }

  private boolean shouldEmit(Event event, long nowMs) {
    // System events always allowed (but still rate-limited)
    boolean isSystemEvent =
        (event == Event.CAMERA_READY
            || event == Event.LOW_LIGHT
            || event == Event.FLASHLIGHT_ON
            || event == Event.FLASHLIGHT_OFF);

    // READY_ENTER always allowed (important feedback)
    if (event == Event.READY_ENTER) {
      return true;
    }

    // Rate limit check
    if (nowMs - lastEventTs < RATE_LIMIT_MS) {
      return isSystemEvent; // System events can bypass rate limit
    }

    // Don't repeat the same event too quickly (except OK which has its own logic)
    if (event == lastEmittedEvent && event != Event.OK) {
      if (nowMs - lastEventTs < RATE_LIMIT_MS * 2) {
        return false;
      }
    }

    return true;
  }

  // --- Frame history for stability tracking ---

  private static class FrameData {
    final float quality;
    final GuidanceHint hint;

    FrameData(float quality, GuidanceHint hint) {
      this.quality = quality;
      this.hint = hint;
    }
  }

  private static class FrameHistory {
    private final FrameData[] buffer;
    private int head = 0;
    private int count = 0;

    FrameHistory(int capacity) {
      buffer = new FrameData[capacity];
    }

    void add(FrameData frame) {
      buffer[head] = frame;
      head = (head + 1) % buffer.length;
      if (count < buffer.length) count++;
    }

    void clear() {
      head = 0;
      count = 0;
      for (int i = 0; i < buffer.length; i++) {
        buffer[i] = null;
      }
    }

    /** Check if the hint has been stable (same value) for at least n frames. */
    boolean isHintStable(int n) {
      if (count < n) return false;

      GuidanceHint reference = null;
      for (int i = 0; i < n; i++) {
        int idx = (head - 1 - i + buffer.length) % buffer.length;
        FrameData frame = buffer[idx];
        if (frame == null) return false;

        if (i == 0) {
          reference = frame.hint;
        } else if (frame.hint != reference) {
          return false;
        }
      }
      return true;
    }

    /** Check if quality has been above threshold for at least n frames. */
    boolean isQualityStable(int n, float threshold) {
      if (count < n) return false;

      for (int i = 0; i < n; i++) {
        int idx = (head - 1 - i + buffer.length) % buffer.length;
        FrameData frame = buffer[idx];
        if (frame == null || frame.quality < threshold) {
          return false;
        }
      }
      return true;
    }
  }

  // --- Debug/Test helpers ---

  /** Get debug info for overlay display. */
  public DebugInfo getDebugInfo() {
    return new DebugInfo(
        currentState,
        lastEmittedEvent,
        lastEventTs,
        plausibleDocCounter,
        badFrameCounter,
        hintStableCount,
        lastStableHint);
  }

  public static class DebugInfo {
    public final State state;
    public final Event lastEvent;
    public final long lastEventTs;
    public final int plausibleDocCounter;
    public final int badFrameCounter;
    public final int hintStableCount;
    public final GuidanceHint lastStableHint;

    DebugInfo(
        State state,
        Event lastEvent,
        long lastEventTs,
        int plausibleDocCounter,
        int badFrameCounter,
        int hintStableCount,
        GuidanceHint lastStableHint) {
      this.state = state;
      this.lastEvent = lastEvent;
      this.lastEventTs = lastEventTs;
      this.plausibleDocCounter = plausibleDocCounter;
      this.badFrameCounter = badFrameCounter;
      this.hintStableCount = hintStableCount;
      this.lastStableHint = lastStableHint;
    }

    @Override
    public String toString() {
      return "A11yDebug{"
          + "state="
          + state
          + ", lastEvent="
          + lastEvent
          + ", hintStable="
          + hintStableCount
          + ", hint="
          + lastStableHint
          + '}';
    }
  }
}
