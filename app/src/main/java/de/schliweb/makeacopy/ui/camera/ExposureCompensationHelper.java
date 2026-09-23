/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Pure utility methods for exposure compensation index ↔ EV float conversion and clamping.
 * Extracted for testability without Android dependencies.
 */
@UtilityClass
public class ExposureCompensationHelper {

  /** Maximum EV magnitude allowed for document scanning (±2.0 EV). */
  public static final float MAX_EV = 2.0f;

  /** Clamps the given index to the device's supported range [lower, upper]. */
  public static int clampIndex(int index, int lower, int upper) {
    return Math.max(lower, Math.min(upper, index));
  }

  /**
   * Converts an exposure compensation index to an EV float value.
   *
   * @param index the integer index (e.g. -6 to +6)
   * @param step the step size as a float (e.g. 1/3 ≈ 0.333, 1/2 = 0.5)
   * @return the EV value (e.g. -2.0, +1.5)
   */
  public static float indexToEv(int index, float step) {
    return index * step;
  }

  /** Formats an EV value for display, e.g. "+1.0", "-0.5", "+0.0". */
  public static String formatEv(float ev) {
    return (ev >= 0 ? "+" : "") + String.format(Locale.US, "%.1f", ev);
  }

  /**
   * Converts a SeekBar progress value (0-based) to the actual exposure index.
   *
   * @param progress the SeekBar progress (0 .. upper-lower)
   * @param lower the lower bound of the exposure range
   * @return the exposure compensation index
   */
  public static int progressToIndex(int progress, int lower) {
    return progress + lower;
  }

  /**
   * Converts an exposure index to a SeekBar progress value.
   *
   * @param index the exposure compensation index
   * @param lower the lower bound of the exposure range
   * @return the SeekBar progress (0-based)
   */
  public static int indexToProgress(int index, int lower) {
    return index - lower;
  }

  /**
   * Restricts the device's lower range bound to {@code -MAX_EV / step} to avoid extreme
   * under-exposure that degrades OCR quality.
   */
  public static int clampRangeLower(int deviceLower, float step) {
    if (step <= 0f) return deviceLower;
    int minIndex = Math.round(-MAX_EV / step);
    return Math.max(deviceLower, minIndex);
  }

  /**
   * Restricts the device's upper range bound to {@code +MAX_EV / step} to avoid extreme
   * over-exposure that degrades OCR quality.
   */
  public static int clampRangeUpper(int deviceUpper, float step) {
    if (step <= 0f) return deviceUpper;
    int maxIndex = Math.round(MAX_EV / step);
    return Math.min(deviceUpper, maxIndex);
  }
}
