/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ml.corners;

/**
 * A utility class for applying the One Euro filter to smooth the positions of four corners (e.g.,
 * top-left, top-right, bottom-right, bottom-left) across frames. This implementation also detects
 * sudden jumps in corner positions and performs resets when necessary.
 *
 * <p>The One Euro filter applies an adaptive low-pass filter where the cutoff frequency dynamically
 * adjusts based on position speed. This helps achieve a balance between smoothness and
 * responsiveness.
 *
 * <p>The filter operates on pixel coordinates and assumes monotonic time progression for
 * timestamps.
 */
public final class OneEuroCornerSmoother {

  private final double minCutoff;
  private final double beta;
  private final double dCutoff;
  private final double resetDistanceFraction;

  // Per-Ecke (4) × per-Achse (2) Zustand.
  private final double[] xPrev = new double[8];
  private final double[] dxPrev = new double[8];
  private boolean initialized = false;
  private long lastTimestampMs = 0L;

  public OneEuroCornerSmoother(
      double minCutoff, double beta, double dCutoff, double resetDistanceFraction) {
    if (!(minCutoff > 0.0)) throw new IllegalArgumentException("minCutoff must be > 0");
    if (!(dCutoff > 0.0)) throw new IllegalArgumentException("dCutoff must be > 0");
    if (!(resetDistanceFraction > 0.0))
      throw new IllegalArgumentException("resetDistanceFraction must be > 0");
    this.minCutoff = minCutoff;
    this.beta = beta;
    this.dCutoff = dCutoff;
    this.resetDistanceFraction = resetDistanceFraction;
  }

  /**
   * Creates a new instance of {@code OneEuroCornerSmoother} with default parameter values.
   *
   * @return An instance of {@code OneEuroCornerSmoother} initialized with default smoothing
   *     parameters.
   */
  public static OneEuroCornerSmoother withDefaults() {
    return new OneEuroCornerSmoother(1.0, 0.05, 1.0, 0.25);
  }

  /**
   * Resets the internal state of the smoother to its initial configuration.
   *
   * <p>This method clears any previously stored state by: - Marking the smoother as uninitialized.
   * - Resetting the last processed timestamp to zero.
   *
   * <p>After calling this method, the smoother requires reinitialization before further processing.
   */
  public void reset() {
    initialized = false;
    lastTimestampMs = 0L;
  }

  /**
   * Applies a smoothing filter on the provided raw corner coordinates.
   *
   * <p>The method takes the raw corner coordinates detected in an image, along with the timestamp
   * and dimensions of the image, and returns the smoothed corner coordinates. It accounts for
   * conditions such as initialization, sudden jumps in corner positions, and invalid timeframes.
   *
   * @param raw A 4x2 array containing the raw corner coordinates, where each sub-array represents a
   *     corner's x and y coordinates. The array must have a fixed structure of 4 rows and 2
   *     columns.
   * @param timestampMs The current timestamp in milliseconds, used to calculate the change in time
   *     between successive calls for smoothing.
   * @param imageW The width of the image in pixels, used to compute thresholds for reset detection.
   * @param imageH The height of the image in pixels, used to compute thresholds for reset
   *     detection.
   * @return A 4x2 array containing the smoothed corner coordinates, where each sub-array represents
   *     a corner's x and y coordinates. The smoothing is based on temporal filtering to reduce
   *     noise and handle discontinuities.
   * @throws IllegalArgumentException if the raw input array is null, does not have exactly 4 rows,
   *     or if any row does not contain exactly 2 elements.
   */
  public double[][] apply(double[][] raw, long timestampMs, int imageW, int imageH) {
    if (raw == null || raw.length != 4) {
      throw new IllegalArgumentException("raw must be 4×2");
    }
    for (int i = 0; i < 4; i++) {
      if (raw[i] == null || raw[i].length != 2) {
        throw new IllegalArgumentException("raw[" + i + "] must be length 2");
      }
    }

    double[][] out = new double[4][2];

    if (!initialized) {
      for (int i = 0; i < 4; i++) {
        xPrev[2 * i] = raw[i][0];
        xPrev[2 * i + 1] = raw[i][1];
        dxPrev[2 * i] = 0.0;
        dxPrev[2 * i + 1] = 0.0;
        out[i][0] = raw[i][0];
        out[i][1] = raw[i][1];
      }
      lastTimestampMs = timestampMs;
      initialized = true;
      return out;
    }

    // Sprung-Detektion: max. Eckenverschiebung gegen letzten geglätteten Zustand.
    double diag = Math.sqrt((double) imageW * imageW + (double) imageH * imageH);
    double resetThreshold = diag * resetDistanceFraction;
    double maxJump = 0.0;
    for (int i = 0; i < 4; i++) {
      double dx = raw[i][0] - xPrev[2 * i];
      double dy = raw[i][1] - xPrev[2 * i + 1];
      double d = Math.sqrt(dx * dx + dy * dy);
      if (d > maxJump) maxJump = d;
    }
    if (maxJump >= resetThreshold) {
      // Hard reset auf Roh-Input.
      for (int i = 0; i < 4; i++) {
        xPrev[2 * i] = raw[i][0];
        xPrev[2 * i + 1] = raw[i][1];
        dxPrev[2 * i] = 0.0;
        dxPrev[2 * i + 1] = 0.0;
        out[i][0] = raw[i][0];
        out[i][1] = raw[i][1];
      }
      lastTimestampMs = timestampMs;
      return out;
    }

    double dtSec = (timestampMs - lastTimestampMs) / 1000.0;
    if (!(dtSec > 0.0) || !Double.isFinite(dtSec)) {
      // Nicht-monotone/identische Zeit: Werte unverändert lassen, Zustand halten.
      for (int i = 0; i < 4; i++) {
        out[i][0] = xPrev[2 * i];
        out[i][1] = xPrev[2 * i + 1];
      }
      return out;
    }
    double rate = 1.0 / dtSec;

    for (int i = 0; i < 4; i++) {
      out[i][0] = filterAxis(2 * i, raw[i][0], rate);
      out[i][1] = filterAxis(2 * i + 1, raw[i][1], rate);
    }
    lastTimestampMs = timestampMs;
    return out;
  }

  private double filterAxis(int axisIdx, double xRaw, double rate) {
    double dxRaw = (xRaw - xPrev[axisIdx]) * rate;
    double aD = alpha(rate, dCutoff);
    double dxHat = aD * dxRaw + (1.0 - aD) * dxPrev[axisIdx];

    double cutoff = minCutoff + beta * Math.abs(dxHat);
    double a = alpha(rate, cutoff);
    double xHat = a * xRaw + (1.0 - a) * xPrev[axisIdx];

    xPrev[axisIdx] = xHat;
    dxPrev[axisIdx] = dxHat;
    return xHat;
  }

  private static double alpha(double rate, double cutoff) {
    double tau = 1.0 / (2.0 * Math.PI * cutoff);
    double te = 1.0 / rate;
    return 1.0 / (1.0 + tau / te);
  }
}
