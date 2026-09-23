/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

/**
 * Detects strongly uneven illumination on a downsampled document image.
 *
 * <p>The detector compares the mean luminance of opposing image halves (top vs. bottom and left vs.
 * right). A large half-to-half difference indicates a slow background gradient (typical for phone
 * photos with a real shadow), independently of text content.
 *
 * <p>Rationale for the chosen threshold:
 *
 * <ul>
 *   <li>Pure layout effects (a coloured letterhead band on top of an otherwise white page) can
 *       produce a top/bottom difference of ~20 luminance units <em>without</em> any actual shadow.
 *       The previous threshold of 18 misfired on such pages and pushed them into the
 *       Sauvola/Retinex binary branch, which destroys faint stamps and light prints.
 *   <li>Real shadow gradients on phone photos typically produce ≥ 30 units.
 * </ul>
 *
 * <p>The threshold is therefore raised to 28: high enough to ignore coloured headers, low enough to
 * still catch genuine uneven lighting.
 *
 * <p>This class is a pure function and intentionally has no Android dependencies so that it is
 * fully covered by JVM unit tests.
 */
public final class UnevenLightingPolicy {

  /** Default half-to-half luminance difference required to call a page "uneven". */
  public static final double DEFAULT_HALF_DIFF_THRESHOLD = 28.0;

  private UnevenLightingPolicy() {
    // utility
  }

  /**
   * Computes the maximum half-to-half luminance difference for an ARGB image supplied as a flat
   * {@code int[]} of pixels in row-major order.
   *
   * <p>Luminance follows Rec.709 ({@code 0.2126·R + 0.7152·G + 0.0722·B}). The alpha channel is
   * ignored.
   *
   * @param argb pixel array, length must be {@code w * h}
   * @param w image width, must be ≥ 2
   * @param h image height, must be ≥ 2
   * @return the maximum of |meanTop − meanBottom| and |meanLeft − meanRight|, or {@code 0.0} if the
   *     input is invalid
   */
  public static double maxHalfDiff(int[] argb, int w, int h) {
    if (argb == null || w < 2 || h < 2 || argb.length < w * h) return 0.0;
    double sumTop = 0, sumBottom = 0, sumLeft = 0, sumRight = 0;
    int cTop = 0, cBottom = 0, cLeft = 0, cRight = 0;
    int midY = h / 2;
    int midX = w / 2;
    for (int y = 0; y < h; y++) {
      int rowOff = y * w;
      for (int x = 0; x < w; x++) {
        int p = argb[rowOff + x];
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        double v = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        if (y < midY) {
          sumTop += v;
          cTop++;
        } else {
          sumBottom += v;
          cBottom++;
        }
        if (x < midX) {
          sumLeft += v;
          cLeft++;
        } else {
          sumRight += v;
          cRight++;
        }
      }
    }
    double meanTop = cTop > 0 ? sumTop / cTop : 0.0;
    double meanBottom = cBottom > 0 ? sumBottom / cBottom : 0.0;
    double meanLeft = cLeft > 0 ? sumLeft / cLeft : 0.0;
    double meanRight = cRight > 0 ? sumRight / cRight : 0.0;
    return Math.max(Math.abs(meanTop - meanBottom), Math.abs(meanLeft - meanRight));
  }

  /**
   * Returns {@code true} if the image's half-to-half luminance difference exceeds the default
   * threshold.
   */
  public static boolean isUneven(int[] argb, int w, int h) {
    return isUneven(argb, w, h, DEFAULT_HALF_DIFF_THRESHOLD);
  }

  /** Variant with an explicit threshold, primarily for testing and tuning. */
  public static boolean isUneven(int[] argb, int w, int h, double threshold) {
    return maxHalfDiff(argb, w, h) > threshold;
  }
}
