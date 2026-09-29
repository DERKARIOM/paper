/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.ml.corners.QuadGeometry;

/**
 * Optional automatic capture: decides, frame after frame, when the document is reliably detected
 * and held still long enough to take the picture. It only reads the output of the existing corner
 * detection (verified outline, confidence, model mask spill, tracker state, framing guide) and
 * never captures itself: the caller triggers the same capture as the shutter button.
 *
 * <pre>
 * OFF ──enable──► SEARCHING ──eligible frame──► STEADYING ──stable for HOLD_MS──► FIRED
 *                     ▲                              │                               │
 *                     └──── not eligible / moved ────┘            screen shown again ┘
 * </pre>
 *
 * <p>A frame is <b>eligible</b> when all of these hold (thresholds measured on the corner
 * benchmark: 141 correct outlines, 20 scenes with two documents, 20 partly visible pages, 40 scenes
 * without a document — none of the latter three passes):
 *
 * <ul>
 *   <li>the tracker shows the outline as currently detected (not merely held after a loss);
 *   <li>confidence ≥ {@link #MIN_CONFIDENCE};
 *   <li>at most {@link #MAX_MASK_SPILL} of the network's document mask lies outside the outline
 *       (two documents in view spill ~45%, a single page ~0%);
 *   <li>the four corners lie at least {@link #MIN_MARGIN} of the image size inside the image (the
 *       page is not cut by the frame);
 *   <li>the page sits in the framing guide and is large enough ("document detected" hint);
 *   <li>no capture is running.
 * </ul>
 *
 * <p><b>Stable</b>: consecutive eligible frames whose corners move less than {@link #MAX_STEP}
 * between frames and less than {@link #MAX_DRIFT} since the window started, for at least {@link
 * #HOLD_MS} and {@link #MIN_FRAMES} frames. Any non-eligible frame, movement or frame gap restarts
 * the window.
 *
 * <p><b>Re-arming</b> (multi-page): after a capture, the controller stays {@link Phase#FIRED} until
 * the scan screen is shown again; then it waits {@link #ARM_DELAY_MS} and requires either that the
 * previous page left the view ({@link #REMOVED_FRAMES} frames without document after the delay)
 * or that the outline moved by {@link #REARM_MOVE}, so the page just captured is not captured
 * again when the user comes back to add the next one.
 *
 * <p>Pure Java, time injected by the caller: unit-testable. Main thread only.
 */
public final class AutoCaptureController {
  public enum Phase {
    /** Automatic capture disabled (manual mode). */
    OFF,
    /** Waiting for an eligible document. */
    SEARCHING,
    /** Eligible document, waiting for it to stay still. */
    STEADYING,
    /** Capture triggered; locked until the scan screen is shown again. */
    FIRED
  }

  static final double MIN_CONFIDENCE = 0.70;
  static final double MAX_MASK_SPILL = 0.25;
  static final double MIN_MARGIN = 0.0075; // of min(width, height)
  static final double MAX_STEP = 0.012; // of the diagonal, between consecutive frames
  static final double MAX_DRIFT = 0.025; // of the diagonal, since the stable window started
  static final long HOLD_MS = 700;
  static final int MIN_FRAMES = 4;
  static final long MAX_FRAME_GAP_MS = 500;
  static final long ARM_DELAY_MS = 1000;
  static final double REARM_MOVE = 0.10; // of the diagonal
  /** Frames without document needed (after the arm delay) to consider the page removed. */
  static final int REMOVED_FRAMES = 3;

  /** One analysed frame, as seen by the existing detection. */
  public static final class Frame {
    @Nullable final double[][] quad; // verified outline TL, TR, BR, BL (bitmap px) or null
    final double confidence;
    final double maskSpill;
    final boolean trackedDetected;
    final boolean wellPlaced;
    final int width, height;
    final long timeMs;

    public Frame(
        @Nullable double[][] quad,
        double confidence,
        double maskSpill,
        boolean trackedDetected,
        boolean wellPlaced,
        int width,
        int height,
        long timeMs) {
      this.quad = quad;
      this.confidence = confidence;
      this.maskSpill = maskSpill;
      this.trackedDetected = trackedDetected;
      this.wellPlaced = wellPlaced;
      this.width = width;
      this.height = height;
      this.timeMs = timeMs;
    }
  }

  /** Result of one update. */
  public static final class Decision {
    public final Phase phase;

    /** Progress of the stability window, 0..1 (only meaningful while STEADYING). */
    public final float progress;

    /** True exactly once per capture: trigger the (manual) capture now. */
    public final boolean fire;

    Decision(Phase phase, float progress, boolean fire) {
      this.phase = phase;
      this.progress = progress;
      this.fire = fire;
    }
  }

  private boolean enabled;
  private Phase phase = Phase.OFF;
  private long shownAtMs;
  private boolean sawNoDocumentSinceShown;
  private int noDocumentStreak;
  @Nullable private double[][] lastCaptured;
  @Nullable private double[][] lastSeen; // last outline seen, for manual captures
  @Nullable private double[][] windowStart;
  @Nullable private double[][] previous;
  private long windowStartMs;
  private long lastFrameMs = -1;
  private int windowFrames;

  public boolean isEnabled() {
    return enabled;
  }

  public Phase phase() {
    return phase;
  }

  public void setEnabled(boolean on, long nowMs) {
    if (on == enabled) return;
    enabled = on;
    if (on) {
      phase = Phase.SEARCHING;
      shownAtMs = nowMs; // do not fire right after switching it on
      sawNoDocumentSinceShown = false;
      noDocumentStreak = 0;
    } else {
      phase = Phase.OFF;
    }
    clearWindow();
  }

  /** The scan screen is (again) visible: re-arm after a delay (see class doc). */
  public void onScreenShown(long nowMs) {
    shownAtMs = nowMs;
    sawNoDocumentSinceShown = false;
    noDocumentStreak = 0;
    lastFrameMs = -1;
    clearWindow();
    phase = enabled ? Phase.SEARCHING : Phase.OFF;
  }

  /**
   * A capture started (automatic or with the shutter button): lock until the scan screen is shown
   * again, and remember the page so that it is not captured again automatically.
   */
  public void onCaptured(long nowMs) {
    if (phase != Phase.FIRED && lastSeen != null) lastCaptured = QuadGeometry.copy(lastSeen);
    clearWindow();
    if (enabled) phase = Phase.FIRED;
  }

  /** The triggered capture failed: allow a new attempt on the same page. */
  public void onCaptureFailed(long nowMs) {
    lastCaptured = null;
    onScreenShown(nowMs);
  }

  @NonNull
  public Decision update(@NonNull Frame f, boolean captureIdle) {
    if (f.quad != null && f.trackedDetected) lastSeen = QuadGeometry.copy(f.quad);
    if (!enabled) return new Decision(Phase.OFF, 0f, false);
    if (phase == Phase.FIRED) return new Decision(Phase.FIRED, 1f, false);

    double diag = Math.hypot(f.width, f.height);
    boolean gap = lastFrameMs >= 0 && f.timeMs - lastFrameMs > MAX_FRAME_GAP_MS;
    lastFrameMs = f.timeMs;

    // "The page left the view" only counts after the arm delay (the first frames after coming
    // back precede the tracker's confirmation) and over a few frames (not a single glitch).
    if (f.quad == null || !f.trackedDetected) {
      noDocumentStreak++;
      if (f.timeMs - shownAtMs >= ARM_DELAY_MS && noDocumentStreak >= REMOVED_FRAMES) {
        sawNoDocumentSinceShown = true;
      }
    } else {
      noDocumentStreak = 0;
    }
    if (!captureIdle || !isEligible(f)) {
      clearWindow();
      phase = Phase.SEARCHING;
      return new Decision(phase, 0f, false);
    }

    double[][] q = f.quad;
    boolean restart =
        gap
            || windowStart == null
            || QuadGeometry.maxCornerDistance(q, previous) > MAX_STEP * diag
            || QuadGeometry.maxCornerDistance(q, windowStart) > MAX_DRIFT * diag;
    if (restart) {
      windowStart = QuadGeometry.copy(q);
      windowStartMs = f.timeMs;
      windowFrames = 0;
    }
    previous = QuadGeometry.copy(q);
    windowFrames++;
    phase = Phase.STEADYING;

    long held = f.timeMs - windowStartMs;
    float progress = (float) Math.min(1.0, held / (double) HOLD_MS);
    boolean stable = held >= HOLD_MS && windowFrames >= MIN_FRAMES;
    if (stable && isArmed(q, diag, f.timeMs)) {
      phase = Phase.FIRED;
      lastCaptured = QuadGeometry.copy(q);
      clearWindow();
      return new Decision(Phase.FIRED, 1f, true);
    }
    // Stable but not armed yet (just shown, or still the page captured before): keep waiting.
    return new Decision(phase, stable ? 0.99f : progress, false);
  }

  static boolean isEligible(Frame f) {
    if (f.quad == null || !QuadGeometry.isFiniteQuad(f.quad)) return false;
    if (!f.trackedDetected || !f.wellPlaced) return false;
    if (f.confidence < MIN_CONFIDENCE || f.maskSpill > MAX_MASK_SPILL) return false;
    if (!QuadGeometry.isConvex(f.quad)) return false;
    double margin = MIN_MARGIN * Math.min(f.width, f.height);
    for (double[] p : f.quad) {
      if (p[0] < margin || p[1] < margin) return false;
      if (p[0] > f.width - 1 - margin || p[1] > f.height - 1 - margin) return false;
    }
    return true;
  }

  private boolean isArmed(double[][] q, double diag, long nowMs) {
    if (nowMs - shownAtMs < ARM_DELAY_MS) return false;
    if (lastCaptured == null || sawNoDocumentSinceShown) return true;
    return QuadGeometry.maxCornerDistance(q, lastCaptured) > REARM_MOVE * diag;
  }

  private void clearWindow() {
    windowStart = null;
    previous = null;
    windowFrames = 0;
  }
}
