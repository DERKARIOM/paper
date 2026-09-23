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

import android.content.Context;
import android.graphics.Bitmap;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import org.opencv.core.Point;

/**
 * OpenCV-only Corner-Detection.
 *
 * <p>Kein ML/ORT, kein verstecktes Fusing.
 */
public final class OpenCvCornerDetector implements CornerDetector {

  @Override
  public DetectionResult detect(Bitmap src, Context ctx) {
    if (src == null || ctx == null) return DetectionResult.fail(Source.FALLBACK);
    try {
      Point[] corners = OpenCVUtils.detectDocumentCorners(ctx, src);
      if (corners == null || corners.length != 4) return DetectionResult.fail(Source.FALLBACK);

      double[][] out = new double[4][2];
      for (int i = 0; i < 4; i++) {
        out[i][0] = corners[i].x;
        out[i][1] = corners[i].y;
      }

      Source s =
          isFallbackRectangle(corners, src.getWidth(), src.getHeight())
              ? Source.FALLBACK
              : Source.OPENCV;
      return DetectionResult.success(s, out);
    } catch (Throwable t) {
      return DetectionResult.fail(Source.FALLBACK);
    }
  }

  // Mirror of OpenCVUtils' fallback-rectangle construction (m = max(20, min(w,h)/10)) with
  // tolerance.
  private static boolean isFallbackRectangle(Point[] p, int w, int h) {
    if (p == null || p.length != 4) return false;
    int m = Math.max(20, Math.min(w, h) / 10);
    return close(p[0].x, m)
        && close(p[0].y, m)
        && close(p[1].x, w - m)
        && close(p[1].y, m)
        && close(p[2].x, w - m)
        && close(p[2].y, h - m)
        && close(p[3].x, m)
        && close(p[3].y, h - m);
  }

  private static boolean close(double a, double b) {
    return Math.abs(a - b) <= 2.5;
  }
}
