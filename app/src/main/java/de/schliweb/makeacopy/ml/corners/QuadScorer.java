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
 * Confidence (0..1) that a refined quad is the document, from three independent kinds of evidence:
 *
 * <ul>
 *   <li><b>edge support</b> — how much of each side lies on a real, straight image edge (from
 *       {@link QuadRefiner}); a guessed or shifted side has little support;
 *   <li><b>model agreement</b> — overlap (IoU) with the document mask predicted by the DocQuad
 *       network, which knows what a page looks like, unlike a table edge or a screen;
 *   <li><b>geometry</b> — {@link QuadGeometry#plausibility}, and a penalty for outlines glued to
 *       the image border.
 * </ul>
 *
 * <p>The combination and the thresholds were calibrated on a synthetic benchmark (19 lighting /
 * surface / document conditions, scenes without any document, partly visible pages): documents
 * correctly outlined score ≥ 0.5 (median ≈ 0.9), empty scenes ≤ 0.45 except plain rectangular
 * cards. It is not a probability and is never shown to the user as a percentage.
 */
public final class QuadScorer {
  /** A new outline is shown from this confidence on. */
  public static final double SHOW = 0.55;

  /** An outline already shown is kept while the confidence stays above this (hysteresis). */
  public static final double KEEP = 0.40;

  /** Above this, the model-based candidate is trusted without trying the contour candidates. */
  public static final double CONVINCING = 0.75;

  private QuadScorer() {}

  /** Mask of the DocQuad network in its 64×64 grid, and the letterbox used for the input. */
  public static final class ModelMask {
    final float[] prob; // 64*64, row-major, probability of "document"
    final double scale, offsetX, offsetY; // image -> 256 input space

    public ModelMask(float[] prob, double scale, double offsetX, double offsetY) {
      this.prob = prob;
      this.scale = scale;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
    }
  }

  public static double confidence(
      QuadRefiner.Result r, int imgW, int imgH, @Nullable ModelMask mask) {
    double geom = QuadGeometry.plausibility(r.quad, imgW, imgH);
    if (geom <= 0) return 0;
    double support = 0.6 * r.meanSupport() + 0.4 * r.minSupport();
    double evidence;
    if (mask != null) {
      evidence = 0.55 * support + 0.45 * maskIoU(r.quad, mask);
    } else {
      evidence = support;
    }
    double border = QuadGeometry.borderFraction(r.quad, imgW, imgH, 3.0);
    return QuadGeometry.clamp01(geom * evidence * (1.0 - 0.8 * border));
  }

  /** IoU between the quad and the model mask (> 0.5), evaluated on the 64×64 grid cell centers. */
  static double maskIoU(double[][] quadImg, ModelMask m) {
    double[][] q64 = new double[4][2];
    for (int i = 0; i < 4; i++) {
      q64[i][0] = (quadImg[i][0] * m.scale + m.offsetX) / 4.0;
      q64[i][1] = (quadImg[i][1] * m.scale + m.offsetY) / 4.0;
    }
    int inter = 0, union = 0;
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        boolean inMask = m.prob[y * 64 + x] > 0.5f;
        boolean inQuad = QuadGeometry.contains(q64, x + 0.5, y + 0.5);
        if (inMask && inQuad) inter++;
        if (inMask || inQuad) union++;
      }
    }
    return union == 0 ? 0 : inter / (double) union;
  }
}
