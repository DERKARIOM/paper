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

/**
 * Grayscale (luma, 0..255) image with bilinear sampling, used by the edge-based quad refinement.
 * Pure Java so the refinement and scoring can be unit-tested on the JVM.
 */
public final class LumaImage {
  public final int width;
  public final int height;
  private final float[] luma;

  public LumaImage(int width, int height, float[] luma) {
    if (width < 4 || height < 4 || luma == null || luma.length < width * height) {
      throw new IllegalArgumentException("invalid luma image");
    }
    this.width = width;
    this.height = height;
    this.luma = luma;
  }

  /**
   * Converts ARGB pixels to luma (BT.601), reusing {@code reuse} when it is large enough. Returns
   * the luma buffer.
   */
  public static float[] lumaFromArgb(int[] argb, int count, float[] reuse) {
    float[] out = (reuse != null && reuse.length >= count) ? reuse : new float[count];
    for (int i = 0; i < count; i++) {
      int c = argb[i];
      out[i] = 0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF);
    }
    return out;
  }

  /** Bilinear sample; coordinates are clamped to the image. */
  public float sample(double x, double y) {
    if (x < 0) x = 0;
    else if (x > width - 1.001) x = width - 1.001;
    if (y < 0) y = 0;
    else if (y > height - 1.001) y = height - 1.001;
    int x0 = (int) x;
    int y0 = (int) y;
    double fx = x - x0;
    double fy = y - y0;
    int i = y0 * width + x0;
    double top = luma[i] * (1 - fx) + luma[i + 1] * fx;
    double bottom = luma[i + width] * (1 - fx) + luma[i + width + 1] * fx;
    return (float) (top * (1 - fy) + bottom * fy);
  }

  public boolean contains(double x, double y, double margin) {
    return x >= margin && y >= margin && x <= width - 1 - margin && y <= height - 1 - margin;
  }
}
