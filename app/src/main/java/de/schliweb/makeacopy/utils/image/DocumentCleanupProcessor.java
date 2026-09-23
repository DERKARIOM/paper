/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import lombok.experimental.UtilityClass;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

/** Shared, deterministic OpenCV-based document cleanup pipeline. */
@UtilityClass
public final class DocumentCleanupProcessor {
  private static final String TAG = "DocumentCleanupProcessor";

  public static Bitmap apply(Context context, Bitmap input, DocumentCleanupOptions options) {
    if (input == null) return null;
    DocumentCleanupOptions safeOptions =
        options == null ? DocumentCleanupOptions.original() : options;
    if (safeOptions.mode == null || safeOptions.mode == DocumentCleanupMode.ORIGINAL) return input;

    try {
      if (context != null && !OpenCVUtils.isInitialized()) {
        OpenCVUtils.init(context.getApplicationContext());
      }
    } catch (Throwable t) {
      Log.w(TAG, "OpenCV init failed before cleanup", t);
    }

    Bitmap working = maybeDownscale(input, safeOptions.targetLongEdgePx);
    switch (safeOptions.mode) {
      case NATURAL:
        return applyNatural(working, safeOptions);
      case ENHANCED:
        return safeOptions.preserveColor
            ? HighPassUtils.applyHighPassColor(working, true)
            : HighPassUtils.applyHighPassGray(working, true);
      case CLEAN_TEXT:
        if (safeOptions.preserveColor) return applyCleanTextColor(working);
        return OpenCVUtils.prepareForOCR(working, safeOptions.optimizeForOcr);
      case ORIGINAL:
      default:
        return working;
    }
  }

  public static void applyInPlace(Mat bgr, DocumentCleanupOptions options) {
    if (bgr == null || bgr.empty()) return;
    DocumentCleanupOptions safeOptions =
        options == null ? DocumentCleanupOptions.original() : options;
    if (safeOptions.mode == null || safeOptions.mode == DocumentCleanupMode.ORIGINAL) return;

    if (safeOptions.mode == DocumentCleanupMode.NATURAL) {
      applyNaturalInPlace(bgr, safeOptions.strength);
      return;
    }
    if (safeOptions.mode == DocumentCleanupMode.ENHANCED && safeOptions.preserveColor) {
      Mat lab = new Mat();
      try {
        Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);
        HighPassUtils.backgroundDivideLab(lab, lab, HighPassUtils.KERNEL_FRACTION_BW);
        Imgproc.cvtColor(lab, bgr, Imgproc.COLOR_Lab2BGR);
        sharpen(bgr, 1.25);
      } finally {
        lab.release();
      }
    }
  }

  private static Bitmap maybeDownscale(Bitmap input, int targetLongEdgePx) {
    if (targetLongEdgePx <= 0 || input == null) return input;
    int longEdge = Math.max(input.getWidth(), input.getHeight());
    if (longEdge <= targetLongEdgePx) return input;
    double scale = targetLongEdgePx / (double) longEdge;
    int w = Math.max(1, (int) Math.round(input.getWidth() * scale));
    int h = Math.max(1, (int) Math.round(input.getHeight() * scale));
    return Bitmap.createScaledBitmap(input, w, h, true);
  }

  private static Bitmap applyNatural(Bitmap input, DocumentCleanupOptions options) {
    Mat rgba = new Mat();
    Mat bgr = new Mat();
    try {
      Utils.bitmapToMat(input, rgba);
      Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);
      applyNaturalInPlace(bgr, options.strength);
      Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA);
      Bitmap out = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(rgba, out);
      return out;
    } catch (Throwable t) {
      Log.w(TAG, "Natural cleanup failed", t);
      return input;
    } finally {
      rgba.release();
      bgr.release();
    }
  }

  private static Bitmap applyCleanTextColor(Bitmap input) {
    Bitmap luminance = OpenCVUtils.prepareForOCR(input, false);
    if (luminance == null) return input;

    Mat colorRgba = new Mat();
    Mat colorBgr = new Mat();
    Mat lab = new Mat();
    Mat cleanRgba = new Mat();
    Mat cleanGray = new Mat();
    java.util.List<Mat> channels = new java.util.ArrayList<>(3);
    try {
      Utils.bitmapToMat(input, colorRgba);
      Utils.bitmapToMat(luminance, cleanRgba);
      Imgproc.cvtColor(colorRgba, colorBgr, Imgproc.COLOR_RGBA2BGR);
      Imgproc.cvtColor(colorBgr, lab, Imgproc.COLOR_BGR2Lab);
      Imgproc.cvtColor(cleanRgba, cleanGray, Imgproc.COLOR_RGBA2GRAY);
      Core.split(lab, channels);
      cleanGray.copyTo(channels.get(0));
      Core.merge(channels, lab);
      Imgproc.cvtColor(lab, colorBgr, Imgproc.COLOR_Lab2BGR);
      Imgproc.cvtColor(colorBgr, colorRgba, Imgproc.COLOR_BGR2RGBA);
      Bitmap out = Bitmap.createBitmap(colorRgba.cols(), colorRgba.rows(), Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(colorRgba, out);
      return out;
    } catch (Throwable t) {
      Log.w(TAG, "Color-preserving clean text cleanup failed", t);
      return input;
    } finally {
      if (luminance != input) {
        try {
          luminance.recycle();
        } catch (Throwable ignore) {
          // Best-effort; failure is non-critical
        }
      }
      colorRgba.release();
      colorBgr.release();
      lab.release();
      cleanRgba.release();
      cleanGray.release();
      for (Mat channel : channels) channel.release();
    }
  }

  private static void applyNaturalInPlace(Mat bgr, int strength) {
    Mat lab = new Mat();
    java.util.List<Mat> channels = new java.util.ArrayList<>(3);
    try {
      Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);
      Core.split(lab, channels);
      double clip =
          DocumentCleanupOptions.clampStrength(strength) == DocumentCleanupOptions.STRENGTH_LOW
              ? 1.4
              : 1.8;
      CLAHE clahe = Imgproc.createCLAHE(clip, new Size(8, 8));
      clahe.apply(channels.get(0), channels.get(0));
      Core.merge(channels, lab);
      Imgproc.cvtColor(lab, bgr, Imgproc.COLOR_Lab2BGR);
      sharpen(bgr, 1.15);
    } finally {
      lab.release();
      for (Mat channel : channels) channel.release();
    }
  }

  private static void sharpen(Mat mat, double amount) {
    Mat blurred = new Mat();
    try {
      Imgproc.GaussianBlur(mat, blurred, new Size(0, 0), 1.0);
      Core.addWeighted(mat, amount, blurred, 1.0 - amount, 0, mat);
    } finally {
      blurred.release();
    }
  }
}
