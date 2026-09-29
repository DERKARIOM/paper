/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ml.corners;

import androidx.annotation.Nullable;

/**
 * Temporal stabilization of the live document outline (one call per analysed frame, ~5–8 Hz).
 *
 * <ul>
 *   <li><b>Show</b>: a new outline appears after two consistent frames, or at once when the
 *       confidence is high; an isolated false detection never appears.
 *   <li><b>Stabilize</b> (per corner, no lag on real motion): movements below a dead zone (0.35%
 *       of the diagonal ≈ 3 px on the analysis image) only drift slowly — no visible tremble;
 *       larger movements are followed with a gain that grows with the movement and reaches 1
 *       (immediate) from 2% of the diagonal, so moving the phone is followed without delay.
 *   <li><b>Outliers</b>: a jump larger than 8% of the diagonal (another object, a mislabeled
 *       corner) is ignored unless the next frame confirms it; then the outline snaps to the new position.
 *   <li><b>Loss</b>: when the document is momentarily not found (motion blur, glare), the last
 *       reliable outline is kept for {@link #HOLD_MS} ({@link State#HOLDING}), then dropped. The
 *       overlay fades it out, so the frame does not blink.
 *   <li><b>Hysteresis</b>: a new outline needs {@link QuadScorer#SHOW}; a tracked outline is kept
 *       down to {@link QuadScorer#KEEP}.
 * </ul>
 *
 * Pure Java, time injected by the caller: unit-testable and deterministic.
 */
public final class CornerTracker {
  public enum State {
    /** Nothing shown. */
    NONE,
    /** Outline confirmed by the current frame. */
    DETECTED,
    /** Current frame failed; last reliable outline kept for a short time. */
    HOLDING
  }

  public static final long HOLD_MS = 600;
  static final double DEAD_ZONE = 0.0035; // fraction of the diagonal
  static final double FULL_GAIN_MOTION = 0.02; // fraction of the diagonal
  static final double JUMP = 0.08; // fraction of the diagonal
  static final double INSTANT_SHOW_CONFIDENCE = 0.8;
  static final double DRIFT_GAIN = 0.15;
  static final double MIN_GAIN = 0.35;

  private State state = State.NONE;
  @Nullable private double[][] tracked;
  @Nullable private double[][] pending; // candidate awaiting confirmation (new or jump)
  private long lastGoodMs;

  /** Output of one update. */
  public static final class Output {
    public final State state;

    /** Outline to display (TL, TR, BR, BL, same coordinates as the input), or {@code null}. */
    @Nullable public final double[][] quad;

    Output(State state, @Nullable double[][] quad) {
      this.state = state;
      this.quad = quad;
    }
  }

  public void reset() {
    state = State.NONE;
    tracked = null;
    pending = null;
    lastGoodMs = 0;
  }

  public State state() {
    return state;
  }

  /**
   * Feeds the detection of one analysed frame and returns the outline to display.
   *
   * @param quad detected outline (TL, TR, BR, BL) or {@code null}
   * @param confidence {@link QuadScorer} confidence of {@code quad}
   * @param w width of the coordinate space (for the relative thresholds)
   * @param h height of the coordinate space
   */
  public Output update(@Nullable double[][] quad, double confidence, long nowMs, int w, int h) {
    double diag = Math.hypot(w, h);
    boolean tracking = tracked != null;
    double needed = tracking ? QuadScorer.KEEP : QuadScorer.SHOW;
    boolean valid = quad != null && QuadGeometry.isFiniteQuad(quad) && confidence >= needed;

    if (valid) {
      if (!tracking) {
        boolean consistent =
            pending != null && QuadGeometry.maxCornerDistance(pending, quad) <= JUMP * diag;
        boolean confirmed = confidence >= INSTANT_SHOW_CONFIDENCE || consistent;
        if (confirmed) {
          tracked = QuadGeometry.copy(quad);
          pending = null;
          lastGoodMs = nowMs;
          state = State.DETECTED;
        } else {
          pending = QuadGeometry.copy(quad);
        }
      } else if (QuadGeometry.maxCornerDistance(quad, tracked) > JUMP * diag) {
        // Large jump: follow only when the next frame confirms it.
        if (pending != null && QuadGeometry.maxCornerDistance(pending, quad) <= JUMP * diag) {
          tracked = QuadGeometry.copy(quad);
          pending = null;
          lastGoodMs = nowMs;
          state = State.DETECTED;
        } else {
          pending = QuadGeometry.copy(quad);
          holdOrDrop(nowMs);
        }
      } else {
        pending = null;
        smoothInto(tracked, quad, diag);
        lastGoodMs = nowMs;
        state = State.DETECTED;
      }
    } else {
      pending = null;
      holdOrDrop(nowMs);
    }
    return new Output(state, tracked != null ? QuadGeometry.copy(tracked) : null);
  }

  private void holdOrDrop(long nowMs) {
    if (tracked != null && nowMs - lastGoodMs <= HOLD_MS) {
      state = State.HOLDING;
    } else {
      tracked = null;
      state = State.NONE;
    }
  }

  /** Adaptive per-corner filter: dead zone for jitter, full gain for real movements. */
  static void smoothInto(double[][] cur, double[][] target, double diag) {
    double dead = DEAD_ZONE * diag;
    double full = FULL_GAIN_MOTION * diag;
    for (int i = 0; i < 4; i++) {
      double dx = target[i][0] - cur[i][0];
      double dy = target[i][1] - cur[i][1];
      double d = Math.hypot(dx, dy);
      double gain;
      if (d <= dead) {
        gain = DRIFT_GAIN;
      } else if (d >= full) {
        gain = 1.0;
      } else {
        gain = MIN_GAIN + (1.0 - MIN_GAIN) * (d - dead) / (full - dead);
      }
      cur[i][0] += gain * dx;
      cur[i][1] += gain * dy;
    }
  }
}
