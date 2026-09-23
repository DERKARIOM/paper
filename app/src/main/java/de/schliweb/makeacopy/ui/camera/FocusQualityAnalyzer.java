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

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

/**
 * Computes the raw Laplacian-variance sharpness measure on the small upright analysis bitmap
 * already produced by the live corner-detection pipeline (see {@code
 * docs/focus_quality_indicator_design.md}).
 *
 * <p>Performance notes:
 *
 * <ul>
 *   <li>All Mats are held in {@link ThreadLocal}s and reused across frames — the same pattern the
 *       analysis pipeline already uses for the NV21→RGBA path. No per-frame Mat or Bitmap
 *       allocations occur in the hot path (submat creates only a cheap header).
 *   <li>The input is the existing downscaled detection bitmap (≤ {@code DETECTION_MAX_EDGE} px); no
 *       extra copies of the camera frame are made.
 *   <li>This class must only be called on the single CameraX analyzer thread, and only on frames
 *       that already pass the existing analysis throttle.
 * </ul>
 */
public final class FocusQualityAnalyzer {

  @SuppressWarnings("ThreadLocalUsage") // intentional: reuse Mats on CameraX analyzer thread
  private final ThreadLocal<Mat> rgbaTL = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: reuse Mats on CameraX analyzer thread
  private final ThreadLocal<Mat> grayTL = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: reuse Mats on CameraX analyzer thread
  private final ThreadLocal<Mat> laplacianTL = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: reuse Mats on CameraX analyzer thread
  private final ThreadLocal<MatOfDouble> meanTL = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: reuse Mats on CameraX analyzer thread
  private final ThreadLocal<MatOfDouble> stdDevTL = new ThreadLocal<>();

  private Mat obtain(ThreadLocal<Mat> tl) {
    Mat m = tl.get();
    if (m == null) {
      m = new Mat();
      tl.set(m);
    }
    return m;
  }

  /**
   * Measures the raw Laplacian variance of the given bitmap, optionally restricted to a region of
   * interest (typically the bounding rectangle of the detected document quad).
   *
   * @param bitmap small upright analysis bitmap (ARGB_8888)
   * @param roi optional ROI in bitmap coordinates; clamped to the bitmap bounds. {@code null} or
   *     degenerate rects fall back to the full frame.
   * @return raw Laplacian variance (>= 0), or {@code -1} if the measurement failed
   */
  public double measure(@NonNull Bitmap bitmap, @Nullable Rect roi) {
    try {
      Mat rgba = obtain(rgbaTL);
      Utils.bitmapToMat(bitmap, rgba); // reuses the Mat buffer when dimensions match

      Mat gray = obtain(grayTL);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

      Mat region = gray;
      Rect clamped = clampRoi(roi, gray.cols(), gray.rows());
      boolean useRoi = clamped != null;
      if (useRoi) {
        region = gray.submat(clamped); // header only, no pixel copy
      }

      Mat laplacian = obtain(laplacianTL);
      Imgproc.Laplacian(region, laplacian, CvType.CV_64F);

      MatOfDouble mean = meanTL.get();
      if (mean == null) {
        mean = new MatOfDouble();
        meanTL.set(mean);
      }
      MatOfDouble stdDev = stdDevTL.get();
      if (stdDev == null) {
        stdDev = new MatOfDouble();
        stdDevTL.set(stdDev);
      }
      Core.meanStdDev(laplacian, mean, stdDev);
      double sigma = stdDev.get(0, 0)[0];
      if (useRoi) {
        region.release(); // release the submat header (not the shared gray buffer)
      }
      return sigma * sigma;
    } catch (Throwable t) {
      return -1.0;
    }
  }

  /**
   * Clamps the ROI to the image bounds. Returns {@code null} when the ROI is missing, degenerate,
   * or already covers (almost) the full frame — the caller then measures the full image directly.
   */
  @Nullable
  static Rect clampRoi(@Nullable Rect roi, int width, int height) {
    if (roi == null || width <= 0 || height <= 0) return null;
    int x = Math.max(0, roi.x);
    int y = Math.max(0, roi.y);
    int w = Math.min(roi.width - (x - roi.x), width - x);
    int h = Math.min(roi.height - (y - roi.y), height - y);
    if (w < 16 || h < 16) return null; // too small to give a stable measurement
    if (x == 0 && y == 0 && w == width && h == height) return null; // full frame anyway
    return new Rect(x, y, w, h);
  }
}
