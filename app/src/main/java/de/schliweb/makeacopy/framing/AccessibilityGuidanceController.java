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

/**
 * Rate-limited, hysteresis-based accessibility guidance controller.
 *
 * <p>This class consumes FramingResult updates (e.g., ~5–6 FPS) and decides when to emit a spoken
 * GuidanceHint. It enforces: - Hysteresis: require N consecutive frames with the same dominant hint
 * before allowing a state change. - Rate limiting: minimum interval between spoken hints. - OK
 * suppression: only speak OK on transition from non-OK, or after a long quiet period.
 *
 * <p>UI/framework integration (e.g., TextToSpeech or accessibility announcements) is handled by the
 * provided Speaker callback; this class is UI-agnostic.
 */
public class AccessibilityGuidanceController {

  public interface Speaker {
    void speak(GuidanceHint hint);
  }

  private final long rateLimitMs;
  private final int minStableFrames;
  private final long okRepeatMs;

  private GuidanceHint lastHint = null;
  private int stableCount = 0;
  private long lastSpeakTs = 0L;
  private GuidanceHint lastSpokenHint = null;

  /**
   * @param rateLimitMs minimum time between spoken hints (e.g., 1200 ms)
   * @param minStableFrames number of consecutive identical hints required to change state (e.g., 2)
   * @param okRepeatMs how often to repeat OK when stable (e.g., 5000 ms)
   */
  public AccessibilityGuidanceController(long rateLimitMs, int minStableFrames, long okRepeatMs) {
    this.rateLimitMs = Math.max(0, rateLimitMs);
    this.minStableFrames = Math.max(1, minStableFrames);
    this.okRepeatMs = Math.max(1000, okRepeatMs);
  }

  public void reset() {
    lastHint = null;
    stableCount = 0;
    lastSpeakTs = 0L;
    lastSpokenHint = null;
  }

  /**
   * Feed a new framing result. If the controller decides to speak, it will call speaker.speak().
   */
  public void onResult(FramingResult result, long nowMs, Speaker speaker) {
    if (result == null || speaker == null) return;

    GuidanceHint current = result.hint != null ? result.hint : GuidanceHint.OK;

    if (lastHint == current) {
      stableCount++;
    } else {
      lastHint = current;
      stableCount = 1;
    }

    // Enforce hysteresis for state changes
    if (stableCount < minStableFrames) return;

    // Enforce rate limit
    if (nowMs - lastSpeakTs < rateLimitMs) return;

    // Suppress spam: only speak OK on transitions or once in a while
    if (current == GuidanceHint.OK) {
      boolean transitioningFromNonOk = lastSpokenHint != GuidanceHint.OK;
      boolean longSinceLastOk = (nowMs - lastSpeakTs) >= okRepeatMs;
      if (!transitioningFromNonOk && !longSinceLastOk) {
        return;
      }
    }

    // Avoid repeating the exact same hint too often beyond rate limit
    if (current == lastSpokenHint && (nowMs - lastSpeakTs) < (rateLimitMs * 2)) {
      return;
    }

    // Speak
    speaker.speak(current);
    lastSpeakTs = nowMs;
    lastSpokenHint = current;
  }
}
