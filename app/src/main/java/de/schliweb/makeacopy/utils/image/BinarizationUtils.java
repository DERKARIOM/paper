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

import android.graphics.Bitmap;
import android.util.Log;
import java.util.*;
import lombok.experimental.UtilityClass;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

/**
 * Utility class for image binarization operations extracted from OpenCVUtils. Contains black/white
 * conversion, adaptive thresholding (Sauvola, Wolf, NICK), despeckle, border noise removal, and
 * quality scoring for binarized images.
 *
 * <p>This class cannot be instantiated.
 */
@UtilityClass
public final class BinarizationUtils {
  private static final String TAG = "BinarizationUtils";

  /** Configuration options for black-and-white image processing. */
  public static class BwOptions {
    public enum Mode {
      AUTO_ADAPTIVE,
      OTSU_ONLY
    }

    public Mode mode = Mode.AUTO_ADAPTIVE;
    public boolean useClahe = true;
    public boolean removeShadows = true;

    /** Adaptive window (odd). 0 = auto */
    public int blockSize = 0;

    /** Offset for adaptiveThreshold (typ. 5–10) */
    public int C = 5;

    /**
     * Gentle mode for scripts with fine strokes and diacritics (Arabic, Persian, Hebrew). When
     * true, skips aggressive despeckle and morphological closing operations that can destroy small
     * but important character components like dots and thin strokes.
     */
    public boolean gentleMode = false;

    /**
     * Target DPI for the output image. Used to scale despeckle aggressiveness. At lower DPI,
     * despeckle is less aggressive to preserve readability. 0 = auto (assumes 300 DPI as default).
     */
    public int targetDpi = 0;
  }

  /**
   * Robust B/W conversion with shadow handling. Emulator: adaptiveThreshold is disabled (avoid
   * SIGILL). Real devices: gentle adaptive variant (MEAN + higher C).
   */
  public static Bitmap toBw(Bitmap src, BwOptions opt) {
    if (src == null || src.isRecycled()) return null;
    if (opt == null) opt = new BwOptions();

    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat work = null; // Don't create empty Mat - will be assigned below
    Mat bw = new Mat();
    CLAHE clahe = null;

    try {
      Utils.bitmapToMat(src, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

      // --- 1) Shadow correction: division-based normalization (centralized) ---
      if (opt.removeShadows && !OpenCVUtils.isSafeMode()) {
        work = new Mat();
        HighPassUtils.backgroundDivideGray(gray, work, HighPassUtils.KERNEL_FRACTION_BW, 51);
      } else {
        work = gray; // work points to gray, no separate Mat needed
      }

      // --- 2) CLAHE for local contrast enhancement ---
      if (opt.useClahe) {
        clahe = Imgproc.createCLAHE();
        clahe.setClipLimit(2.0);
        clahe.setTilesGridSize(new Size(8, 8));
        clahe.apply(work, work);
      }

      // --- 3) very light smoothing against pepper noise ---
      // Keep this conservative: stronger blur removes faint strokes before thresholding.
      Imgproc.GaussianBlur(work, work, new Size(3, 3), 0);

      boolean ok = false;

      // --- 4) Adaptive threshold: choose a text-preserving local threshold ---
      // Detect low-resolution images (e.g. from autoscan) and use gentler parameters
      int longSide = Math.max(work.width(), work.height());
      boolean lowRes = longSide < 1500;

      if (opt.mode == BwOptions.Mode.AUTO_ADAPTIVE && !OpenCVUtils.isSafeMode()) {
        int bs;
        if (opt.blockSize > 0) {
          bs = (opt.blockSize % 2 == 1) ? opt.blockSize : opt.blockSize + 1;
        } else if (lowRes) {
          // For low-res images use a larger relative block size to avoid
          // over-aggressive binarization that destroys text readability
          bs = Math.max(31, (Math.min(work.width(), work.height()) / 10) | 1);
          if (bs % 2 == 0) bs++;
        } else {
          // Larger block size captures broader context for threshold calculation
          bs = Math.max(51, (Math.min(work.width(), work.height()) / 30) | 1);
          if (bs % 2 == 0) bs++;
        }
        // Keep C low enough to preserve faint gray text. High values whiten weak strokes and make
        // text disappear on photographed documents.
        int cVal;
        if (lowRes) {
          cVal = Math.max(2, Math.min(5, opt.C > 0 ? opt.C : 3));
        } else {
          cVal = Math.max(3, Math.min(7, opt.C > 0 ? opt.C : 4));
        }

        Mat adaptiveMean = new Mat();
        Mat adaptiveGaussian = new Mat();
        Mat otsu = new Mat();
        try {
          Imgproc.adaptiveThreshold(
              work,
              adaptiveMean,
              255,
              Imgproc.ADAPTIVE_THRESH_MEAN_C,
              Imgproc.THRESH_BINARY,
              bs,
              cVal);
          Imgproc.adaptiveThreshold(
              work,
              adaptiveGaussian,
              255,
              Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
              Imgproc.THRESH_BINARY,
              bs,
              cVal);
          Imgproc.threshold(work, otsu, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
          double meanScore = scoreBwQuality(adaptiveMean);
          double gaussianScore = scoreBwQuality(adaptiveGaussian);
          double otsuScore = scoreBwQuality(otsu);
          if (gaussianScore <= meanScore && gaussianScore <= otsuScore) {
            adaptiveGaussian.copyTo(bw);
          } else if (otsuScore <= meanScore) {
            otsu.copyTo(bw);
          } else {
            adaptiveMean.copyTo(bw);
          }
          ok = true;
        } catch (Throwable ignore) {
          ok = false;
        } finally {
          adaptiveMean.release();
          adaptiveGaussian.release();
          otsu.release();
        }
      }

      // --- 5) Fallback / OTSU_ONLY mode ---
      // For low-res OTSU_ONLY (s/w klassisch): use adaptive threshold instead of
      // global Otsu, because Otsu is too aggressive on low-res autoscan images
      // and destroys text readability.
      if (!ok && lowRes && !OpenCVUtils.isSafeMode()) {
        int bs = Math.max(31, (Math.min(work.width(), work.height()) / 10) | 1);
        if (bs % 2 == 0) bs++;
        int cVal = Math.max(2, Math.min(5, opt.C > 0 ? opt.C : 3));
        try {
          Imgproc.adaptiveThreshold(
              work, bw, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, bs, cVal);
          ok = true;
        } catch (Throwable ignore) {
          ok = false;
        }
      }
      if (!ok) {
        Imgproc.threshold(work, bw, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
      }

      // --- 6) conservative cleanup ---
      // Remove only tiny isolated speckles. Avoid morphology/opening/closing here: it is fast, but
      // it also removes punctuation, diacritics and faint text on real camera captures.
      removeTinySpeckles(bw, opt.targetDpi, lowRes || opt.gentleMode);

      Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(bw, out);
      return out;

    } catch (Throwable t) {
      Log.d(TAG, "toBw (robust) failed: " + t.getMessage());
      try {
        Mat tmpGray = new Mat(), tmpBw = new Mat();
        Utils.bitmapToMat(src, rgba);
        Imgproc.cvtColor(rgba, tmpGray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.threshold(tmpGray, tmpBw, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(tmpBw, out);
        tmpGray.release();
        tmpBw.release();
        return out;
      } catch (Throwable t2) {
        Log.d(TAG, "toBw fallback failed: " + t2.getMessage());
        return null;
      }
    } finally {
      OpenCVUtils.release(rgba, bw);
      if (work != gray) OpenCVUtils.release(work);
      OpenCVUtils.release(gray);
      if (clahe != null) {
        try {
          clahe.collectGarbage();
        } catch (Throwable ignore) {
          // Best-effort; failure is non-critical
        }
      }
    }
  }

  /**
   * Converts a given Bitmap image to a black-and-white (grayscale) representation using default
   * options.
   *
   * @param src the source Bitmap to be converted to black-and-white
   * @return a new Bitmap representing the black-and-white version of the source image
   */
  public static Bitmap toBw(Bitmap src) {
    return toBw(src, new BwOptions());
  }

  static void removeTinySpeckles(Mat bw /* CV_8UC1, 0/255 */, int targetDpi, boolean gentle) {
    if (bw == null || bw.empty()) return;
    int effectiveDpi = targetDpi > 0 ? targetDpi : 300;
    float dpiScale = Math.max(0.5f, Math.min(2.0f, effectiveDpi / 300f));
    int minArea = gentle ? 2 : Math.max(3, Math.round(5 * dpiScale * dpiScale));
    int minHeight = gentle ? 1 : Math.max(1, Math.round(2 * dpiScale));
    removeSmallComponents(bw, minArea, minHeight);
  }

  /**
   * Removes small speckles from a binary image using morphological operations. The function
   * processes the input binary image to eliminate noise or small artifacts, leaving the major
   * structures intact.
   *
   * <p>The aggressiveness of despeckle is scaled based on target DPI: - At 300 DPI (reference):
   * uses 3x3 kernel and minArea=15 - At lower DPI (e.g., 72-150): uses smaller kernel (2x2) and
   * lower minArea to preserve readability - At higher DPI (e.g., 600): can use larger kernel and
   * higher minArea
   *
   * @param bw Input binary image of type Mat (CV_8UC1), with pixel values of 0 or 255. It will be
   *     modified in-place to remove speckles.
   * @param targetDpi Target DPI for the output. 0 or negative values default to 300 DPI.
   */
  static void despeckleFast(Mat bw /* CV_8UC1, 0/255 */, int targetDpi) {
    // Reference DPI for scaling calculations
    final int REFERENCE_DPI = 300;
    int effectiveDpi = targetDpi > 0 ? targetDpi : REFERENCE_DPI;

    // Scale factor relative to reference DPI
    float dpiScale = (float) effectiveDpi / REFERENCE_DPI;

    // At low DPI (< 150), skip morphological opening entirely to preserve fine details
    // At medium DPI (150-250), use 2x2 kernel
    // At high DPI (>= 250), use 3x3 kernel
    int kernelSize;
    if (effectiveDpi < 150) {
      kernelSize = 0; // Skip morphological opening
    } else if (effectiveDpi < 250) {
      kernelSize = 2;
    } else {
      kernelSize = 3;
    }

    Mat inv = new Mat();
    Mat kernel = null;
    try {
      if (kernelSize > 0) {
        // Make text and speckles white so the opening operation removes them
        Core.bitwise_not(bw, inv);
        kernel =
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(kernelSize, kernelSize));
        Imgproc.morphologyEx(inv, inv, Imgproc.MORPH_OPEN, kernel);
        Core.bitwise_not(inv, bw);
      }
    } finally {
      inv.release();
      if (kernel != null) kernel.release();
    }

    // Scale minArea based on DPI: at 300 DPI use 15, scale proportionally
    // At low DPI, use smaller minArea to avoid removing small but valid characters
    // Formula: minArea = 15 * (dpi/300)^2, with minimum of 4 pixels
    int minArea = Math.max(4, Math.round(15 * dpiScale * dpiScale));

    // At very low DPI (< 100), skip component removal entirely
    if (effectiveDpi >= 100) {
      removeSmallComponents(bw, minArea);
    }
  }

  /**
   * Removes connected components smaller than the specified minimum area. This helps eliminate
   * small noise artifacts that are too small to be valid characters.
   *
   * @param bw Input binary image of type Mat (CV_8UC1), with pixel values of 0 or 255. Text should
   *     be black (0) on white (255) background.
   * @param minArea Minimum area in pixels for a component to be kept.
   */
  static void removeSmallComponents(Mat bw /* CV_8UC1, 0/255 */, int minArea) {
    if (bw == null || bw.empty() || minArea <= 0) return;

    Mat inv = new Mat();
    Mat labels = new Mat();
    Mat stats = new Mat();
    Mat centroids = new Mat();

    try {
      // Invert so text becomes white (foreground) for connectedComponents
      Core.bitwise_not(bw, inv);

      int numLabels =
          Imgproc.connectedComponentsWithStats(inv, labels, stats, centroids, 8, CvType.CV_32S);

      // Label 0 is background, start from 1
      for (int label = 1; label < numLabels; label++) {
        int area = (int) stats.get(label, Imgproc.CC_STAT_AREA)[0];
        if (area < minArea) {
          // Remove this component by setting its pixels to background (0 in inv, 255 in bw)
          int left = (int) stats.get(label, Imgproc.CC_STAT_LEFT)[0];
          int top = (int) stats.get(label, Imgproc.CC_STAT_TOP)[0];
          int width = (int) stats.get(label, Imgproc.CC_STAT_WIDTH)[0];
          int height = (int) stats.get(label, Imgproc.CC_STAT_HEIGHT)[0];

          // Clear pixels belonging to this label in the bounding box
          for (int y = top; y < top + height && y < bw.rows(); y++) {
            for (int x = left; x < left + width && x < bw.cols(); x++) {
              int[] labelVal = new int[1];
              labels.get(y, x, labelVal);
              if (labelVal[0] == label) {
                bw.put(y, x, 255); // Set to white (background)
              }
            }
          }
        }
      }
    } catch (Throwable ignore) {
      // If connectedComponents fails, skip this optimization
    } finally {
      inv.release();
      labels.release();
      stats.release();
      centroids.release();
    }
  }

  /**
   * Clears noise and small artifacts touching the image borders. This helps remove scanning
   * artifacts, edge noise, and partial characters that often appear at document edges and cause OCR
   * errors.
   *
   * @param bw Input binary image of type Mat (CV_8UC1), with pixel values of 0 or 255. It will be
   *     modified in-place to clear border-touching components.
   */
  static void clearBorderNoise(Mat bw /* CV_8UC1, 0/255 */) {
    if (bw == null || bw.empty()) return;

    int w = bw.cols();
    int h = bw.rows();

    // Define border margin (percentage of image size)
    int marginX = Math.max(8, (int) (w * 0.015)); // 1.5% of width, min 8px
    int marginY = Math.max(8, (int) (h * 0.015)); // 1.5% of height, min 8px

    // Use submat and setTo for efficient border clearing
    // Note: SubMats are views but must still be released to avoid memory leaks
    Mat top = null, bottom = null, left = null, right = null;
    try {
      // Clear top border region
      if (marginY > 0 && marginY < h) {
        top = bw.submat(0, marginY, 0, w);
        top.setTo(new Scalar(255));
      }

      // Clear bottom border region
      if (marginY > 0 && h - marginY > 0) {
        bottom = bw.submat(h - marginY, h, 0, w);
        bottom.setTo(new Scalar(255));
      }

      // Clear left border region
      if (marginX > 0 && marginX < w) {
        left = bw.submat(0, h, 0, marginX);
        left.setTo(new Scalar(255));
      }

      // Clear right border region
      if (marginX > 0 && w - marginX > 0) {
        right = bw.submat(0, h, w - marginX, w);
        right.setTo(new Scalar(255));
      }
    } catch (Throwable ignore) {
      // Fallback: pixel-by-pixel clearing if submat fails
      for (int y = 0; y < marginY && y < h; y++) {
        for (int x = 0; x < w; x++) {
          bw.put(y, x, 255);
        }
      }
      for (int y = h - marginY; y < h; y++) {
        if (y >= 0) {
          for (int x = 0; x < w; x++) {
            bw.put(y, x, 255);
          }
        }
      }
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < marginX && x < w; x++) {
          bw.put(y, x, 255);
        }
      }
      for (int y = 0; y < h; y++) {
        for (int x = w - marginX; x < w; x++) {
          if (x >= 0) {
            bw.put(y, x, 255);
          }
        }
      }
    } finally {
      // Release SubMats to avoid memory leaks
      if (top != null) top.release();
      if (bottom != null) bottom.release();
      if (left != null) left.release();
      if (right != null) right.release();
    }
  }

  /**
   * Scores a binarized image: lower is better. Penalizes excessive white coverage and many tiny
   * blobs. Note: Binary images have text=0 (black) and background=255 (white).
   * connectedComponentsWithStats treats non-zero pixels as foreground, so we must invert first.
   */
  static double scoreBwQuality(Mat bw /* CV_8UC1 0/255, text=0, bg=255 */) {
    Mat inv = new Mat();
    Mat labels = new Mat();
    Mat stats = new Mat();
    Mat centroids = new Mat();
    try {
      int rows = bw.rows(), cols = bw.cols();
      if (rows <= 0 || cols <= 0) return Double.POSITIVE_INFINITY;
      int area = rows * cols;
      int white = Core.countNonZero(bw);
      double whiteFrac = Math.min(1.0, Math.max(0.0, white / (double) area));

      Core.bitwise_not(bw, inv);
      int black = Core.countNonZero(inv);
      double blackFrac = Math.min(1.0, Math.max(0.0, black / (double) area));
      double tooEmptyPenalty = blackFrac < 0.01 ? 1.0 : 0.0;
      double tooDarkPenalty = blackFrac > 0.45 ? 1.0 : 0.0;
      double targetTextCoveragePenalty = Math.abs(blackFrac - 0.12);
      return targetTextCoveragePenalty + tooEmptyPenalty + tooDarkPenalty + whiteFrac * 0.05;
    } catch (Throwable t) {
      return Double.POSITIVE_INFINITY;
    } finally {
      inv.release();
      labels.release();
      stats.release();
      centroids.release();
    }
  }

  /**
   * Removes connected components below given size/height thresholds (keeps punctuation by using
   * tiny limits). Note: Binary images have text=0 (black) and background=255 (white).
   * connectedComponentsWithStats treats non-zero pixels as foreground, so we must invert first.
   */
  static void removeSmallComponents(
      Mat bw /* CV_8UC1 0/255, text=0, bg=255 */, int minArea, int minHeight) {
    Mat inv = new Mat();
    Mat labels = new Mat();
    Mat stats = new Mat();
    Mat centroids = new Mat();
    Mat mask = new Mat();
    try {
      // Invert so text becomes white (foreground) for connectedComponents
      Core.bitwise_not(bw, inv);
      int n = Imgproc.connectedComponentsWithStats(inv, labels, stats, centroids, 8, CvType.CV_32S);
      if (n > 512) {
        return;
      }
      for (int i = 1; i < n; i++) {
        int ai = (int) stats.get(i, Imgproc.CC_STAT_AREA)[0];
        int hi = (int) stats.get(i, Imgproc.CC_STAT_HEIGHT)[0];
        if (ai < minArea || hi < minHeight) {
          Core.compare(labels, new Scalar(i), mask, Core.CMP_EQ);
          bw.setTo(new Scalar(255), mask); // set to background (white)
        }
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    } finally {
      inv.release();
      labels.release();
      stats.release();
      centroids.release();
      mask.release();
    }
  }

  /**
   * Estimates median height of text components to guide scaling; returns -1 if not available. Note:
   * Binary images have text=0 (black) and background=255 (white). connectedComponentsWithStats
   * treats non-zero pixels as foreground, so we must invert first.
   */
  static int estimateMedianComponentHeight(Mat bw /* CV_8UC1 0/255, text=0, bg=255 */) {
    Mat inv = new Mat();
    Mat labels = new Mat();
    Mat stats = new Mat();
    Mat centroids = new Mat();
    try {
      // Invert so text becomes white (foreground) for connectedComponents
      Core.bitwise_not(bw, inv);
      int n = Imgproc.connectedComponentsWithStats(inv, labels, stats, centroids, 8, CvType.CV_32S);
      if (n <= 1) return -1;
      int rows = bw.rows(), cols = bw.cols();
      int imgArea = rows * cols;
      int minArea = Math.max(12, imgArea / 20000);
      int maxArea = Math.max(minArea + 1, imgArea / 5);
      List<Integer> heights = new ArrayList<>();
      for (int i = 1; i < n; i++) {
        int ai = (int) stats.get(i, Imgproc.CC_STAT_AREA)[0];
        int hi = (int) stats.get(i, Imgproc.CC_STAT_HEIGHT)[0];
        int wi = (int) stats.get(i, Imgproc.CC_STAT_WIDTH)[0];
        if (ai < minArea || ai > maxArea) continue;
        if (hi < 3 || hi > rows * 0.6) continue;
        if (wi < 2 || wi > cols * 0.6) continue;
        heights.add(hi);
      }
      if (heights.isEmpty()) return -1;
      Collections.sort(heights);
      return heights.get(heights.size() / 2);
    } catch (Throwable t) {
      return -1;
    } finally {
      inv.release();
      labels.release();
      stats.release();
      centroids.release();
    }
  }

  /**
   * Sauvola local adaptive thresholding.
   *
   * @param src8u grayscale CV_8U
   * @param dst output binary CV_8U (0/255)
   * @param win odd window size for local statistics
   * @param k typically in [0.2, 0.5]
   * @param R dynamic range of standard deviation (typically 128 or 255)
   */
  static void sauvolaThreshold(Mat src8u, Mat dst, int win, double k, double R) {
    if (win % 2 == 0) win++;
    int btype = CvType.CV_32F;
    Mat f = new Mat();
    Mat mean = new Mat();
    Mat sq = new Mat();
    Mat meanSq = new Mat();
    Mat var = new Mat();
    Mat stddev = new Mat();
    Mat thresh = new Mat();
    Mat mask = new Mat();
    try {
      src8u.convertTo(f, btype);
      Imgproc.boxFilter(f, mean, btype, new Size(win, win));
      Core.multiply(f, f, sq);
      Imgproc.boxFilter(sq, meanSq, btype, new Size(win, win));
      // var = E[x^2] - (E[x])^2
      Core.multiply(mean, mean, var);
      Core.subtract(meanSq, var, var);
      Core.max(var, new Scalar(0.0), var);
      Core.sqrt(var, stddev);

      // thresh = mean * (1 + k*((std/R) - 1))
      Mat stdDivR = new Mat();
      Core.divide(stddev, new Scalar(R), stdDivR);
      Mat tmp = new Mat();
      Core.subtract(stdDivR, new Scalar(1.0), tmp);
      Core.multiply(tmp, new Scalar(k), tmp);
      Core.add(tmp, new Scalar(1.0), tmp);
      Core.multiply(mean, tmp, thresh);

      // compare f > thresh -> 255 else 0
      Core.compare(f, thresh, mask, Core.CMP_GT);
      dst.create(src8u.size(), CvType.CV_8U);
      dst.setTo(new Scalar(0));
      dst.setTo(new Scalar(255), mask);
    } finally {
      f.release();
      mean.release();
      sq.release();
      meanSq.release();
      var.release();
      stddev.release();
      thresh.release();
      mask.release();
    }
  }

  /**
   * Wolf local adaptive thresholding. Similar to Sauvola but uses the global maximum standard
   * deviation as R, making it more robust for images with uneven illumination. Formula: T(x,y) =
   * mean * (1 + k * ((stddev / R) - 1)) where R = max(stddev) globally
   *
   * @param src8u grayscale CV_8U
   * @param dst output binary CV_8U (0/255)
   * @param win odd window size for local statistics
   * @param k typically in [0.2, 0.5]
   */
  static void wolfThreshold(Mat src8u, Mat dst, int win, double k) {
    if (win % 2 == 0) win++;
    int btype = CvType.CV_32F;
    Mat f = new Mat();
    Mat mean = new Mat();
    Mat sq = new Mat();
    Mat meanSq = new Mat();
    Mat var = new Mat();
    Mat stddev = new Mat();
    Mat thresh = new Mat();
    Mat mask = new Mat();
    try {
      src8u.convertTo(f, btype);
      Imgproc.boxFilter(f, mean, btype, new Size(win, win));
      Core.multiply(f, f, sq);
      Imgproc.boxFilter(sq, meanSq, btype, new Size(win, win));
      // var = E[x^2] - (E[x])^2
      Core.multiply(mean, mean, var);
      Core.subtract(meanSq, var, var);
      Core.max(var, new Scalar(0.0), var);
      Core.sqrt(var, stddev);

      // Wolf's key difference: R = max(stddev) globally instead of fixed constant
      Core.MinMaxLocResult mmr = Core.minMaxLoc(stddev);
      double wolfR = Math.max(1.0, mmr.maxVal); // avoid division by zero

      Mat stdDivR = new Mat();
      Core.divide(stddev, new Scalar(wolfR), stdDivR);
      Mat tmp = new Mat();
      Core.subtract(stdDivR, new Scalar(1.0), tmp);
      Core.multiply(tmp, new Scalar(k), tmp);
      Core.add(tmp, new Scalar(1.0), tmp);
      Core.multiply(mean, tmp, thresh);

      // compare f > thresh -> 255 else 0
      Core.compare(f, thresh, mask, Core.CMP_GT);
      dst.create(src8u.size(), CvType.CV_8U);
      dst.setTo(new Scalar(0));
      dst.setTo(new Scalar(255), mask);

      stdDivR.release();
      tmp.release();
    } finally {
      f.release();
      mean.release();
      sq.release();
      meanSq.release();
      var.release();
      stddev.release();
      thresh.release();
      mask.release();
    }
  }

  /**
   * NICK (Niblack Improved Contrast K-factor) local adaptive thresholding. An improved version of
   * Niblack that handles low contrast regions better. Formula: T(x,y) = mean + k * sqrt(stddev^2 +
   * mean^2) This avoids the issue of Niblack producing noise in uniform regions.
   *
   * @param src8u grayscale CV_8U
   * @param dst output binary CV_8U (0/255)
   * @param win odd window size for local statistics
   * @param k typically in [-0.2, -0.1] (negative values for dark text on light background)
   */
  static void nickThreshold(Mat src8u, Mat dst, int win, double k) {
    if (win % 2 == 0) win++;
    int btype = CvType.CV_32F;
    Mat f = new Mat();
    Mat mean = new Mat();
    Mat sq = new Mat();
    Mat meanSq = new Mat();
    Mat var = new Mat();
    Mat thresh = new Mat();
    Mat mask = new Mat();
    try {
      src8u.convertTo(f, btype);
      Imgproc.boxFilter(f, mean, btype, new Size(win, win));
      Core.multiply(f, f, sq);
      Imgproc.boxFilter(sq, meanSq, btype, new Size(win, win));
      // var = E[x^2] - (E[x])^2
      Core.multiply(mean, mean, var);
      Core.subtract(meanSq, var, var);
      Core.max(var, new Scalar(0.0), var);

      // NICK formula: T = mean + k * sqrt(var + mean^2)
      // This is equivalent to: T = mean + k * sqrt(stddev^2 + mean^2)
      Mat meanSquared = new Mat();
      Core.multiply(mean, mean, meanSquared);
      Mat sumVarMeanSq = new Mat();
      Core.add(var, meanSquared, sumVarMeanSq);
      Mat sqrtTerm = new Mat();
      Core.sqrt(sumVarMeanSq, sqrtTerm);

      // thresh = mean + k * sqrtTerm
      Mat kTimesRoot = new Mat();
      Core.multiply(sqrtTerm, new Scalar(k), kTimesRoot);
      Core.add(mean, kTimesRoot, thresh);

      // compare f > thresh -> 255 else 0
      Core.compare(f, thresh, mask, Core.CMP_GT);
      dst.create(src8u.size(), CvType.CV_8U);
      dst.setTo(new Scalar(0));
      dst.setTo(new Scalar(255), mask);

      meanSquared.release();
      sumVarMeanSq.release();
      sqrtTerm.release();
      kTimesRoot.release();
    } finally {
      f.release();
      mean.release();
      sq.release();
      meanSq.release();
      var.release();
      thresh.release();
      mask.release();
    }
  }
}
