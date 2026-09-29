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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.BuildConfig;
import de.schliweb.makeacopy.ml.docquad.DocQuadLetterbox;
import de.schliweb.makeacopy.ml.docquad.DocQuadOrtRunner;
import de.schliweb.makeacopy.ml.docquad.DocQuadPostprocessor;

/** Produktiver Adapter für DocQuadNet-256. */
public final class DocQuadDetector implements CornerDetector {

  private static final String TAG = "DocQuadDetector";

  // Rate-limited logging window in ms when FEATURE_FRAMING_LOGGING is on.
  private static final long LOG_INTERVAL_MS = 1000L;
  private long lastLogMs = 0L;

  // Release-Asset (F-Droid kompatibel, kein Download)
  public static final String DEFAULT_MODEL_ASSET_PATH = "docquad/docquadnet256_trained_opset17.ort";

  private final DocQuadOrtRunner runner;

  /** Creates a detector with the given ORT runner. */
  public DocQuadDetector(DocQuadOrtRunner runner) {
    Log.d(TAG, "DocQuadDetector created");
    this.runner = runner;
  }

  /** Detection result plus the network's document mask (used by the confidence scoring). */
  public static final class Detailed {
    public final DetectionResult result;

    /** Document mask predicted by the network, or {@code null} when inference failed. */
    @Nullable public final QuadScorer.ModelMask mask;

    Detailed(DetectionResult result, @Nullable QuadScorer.ModelMask mask) {
      this.result = result;
      this.mask = mask;
    }
  }

  @Override
  public DetectionResult detect(Bitmap src, Context ctx) {
    return detectDetailed(src, ctx).result;
  }

  // Reused across calls: the live analysis runs this several times per second on one thread, so
  // a 256x256 bitmap, a 64k int array and a 196k float array are no longer allocated per frame.
  private Bitmap reuse256;
  private volatile long lastInferenceMs;

  /** Duration of the last network inference alone (diagnostics). */
  public long lastInferenceMs() {
    return lastInferenceMs;
  }

  private int[] reusePixels;
  private float[] reuseInput;

  /** Like {@link #detect}, and also returns the document mask predicted by the network. */
  public synchronized Detailed detectDetailed(Bitmap src, Context ctx) {
    if (src == null || ctx == null) return new Detailed(DetectionResult.fail(Source.DOCQUAD), null);
    QuadScorer.ModelMask mask = null;
    try {
      int srcW = src.getWidth();
      int srcH = src.getHeight();
      if (srcW <= 0 || srcH <= 0) return new Detailed(DetectionResult.fail(Source.DOCQUAD), null);

      DocQuadLetterbox lb =
          DocQuadLetterbox.create(srcW, srcH, DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H);
      reuse256 = renderLetterbox256(src, lb, reuse256);
      if (reusePixels == null) {
        reusePixels = new int[DocQuadOrtRunner.IN_W * DocQuadOrtRunner.IN_H];
        reuseInput = new float[3 * reusePixels.length];
      }
      float[] input = bitmapToNchwFloat01(reuse256, reusePixels, reuseInput);

      long tRun = SystemClock.uptimeMillis();
      DocQuadOrtRunner.Outputs outputs = runner.run(input);
      lastInferenceMs = SystemClock.uptimeMillis() - tRun;
      mask = toModelMask(outputs, lb);

      DocQuadPostprocessor.PeakMode peakMode = DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC;

      DocQuadPostprocessor.Result r = DocQuadPostprocessor.postprocess(outputs, lb, peakMode);
      if (r == null || r.chosenQuadOriginal() == null || r.chosenQuadOriginal().length != 4)
        return new Detailed(DetectionResult.fail(Source.DOCQUAD), mask);

      if (BuildConfig.FEATURE_FRAMING_LOGGING) {
        long now = SystemClock.uptimeMillis();
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
          lastLogMs = now;
          double[][] q = r.chosenQuadOriginal();
          Log.d(
              TAG,
              String.format(
                  java.util.Locale.US,
                  "detect peakMode=%s src=%dx%d chosen=%s TL=(%.1f,%.1f) TR=(%.1f,%.1f)"
                      + " BR=(%.1f,%.1f) BL=(%.1f,%.1f)",
                  peakMode,
                  srcW,
                  srcH,
                  String.valueOf(r.chosenSource()),
                  q[0][0],
                  q[0][1],
                  q[1][0],
                  q[1][1],
                  q[2][0],
                  q[2][1],
                  q[3][0],
                  q[3][1]));
        }
      }

      /*
           if (r.suspiciousForProduct()) {
             Log.i(TAG, "Detection flagged as suspicious: " + r.suspiciousReason());
             return DetectionResult.fail(Source.DOCQUAD);
           }
      */

      if (!isValidQuad(r.chosenQuadOriginal(), srcW, srcH))
        return new Detailed(DetectionResult.fail(Source.DOCQUAD), mask);

      return new Detailed(
          DetectionResult.successDebug(
              Source.DOCQUAD,
              r.chosenQuadOriginal(),
              String.valueOf(r.chosenSource()),
              r.penaltyMask(),
              r.penaltyCorners()),
          mask);
    } catch (Throwable t) {
      return new Detailed(DetectionResult.fail(Source.DOCQUAD), mask);
    }
  }

  /** Releases the reusable input bitmap (native memory). */
  public synchronized void release() {
    if (reuse256 != null && !reuse256.isRecycled()) reuse256.recycle();
    reuse256 = null;
    reusePixels = null;
    reuseInput = null;
  }

  private static QuadScorer.ModelMask toModelMask(
      DocQuadOrtRunner.Outputs outputs, DocQuadLetterbox lb) {
    float[][] logits = outputs.maskLogits()[0][0];
    float[] prob = new float[64 * 64];
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        prob[y * 64 + x] = (float) (1.0 / (1.0 + Math.exp(-logits[y][x])));
      }
    }
    return new QuadScorer.ModelMask(prob, lb.scale, lb.offsetX, lb.offsetY);
  }

  /** Preprocess exakt wie Training: RGB, 0..1, NCHW float32. */
  private static float[] bitmapToNchwFloat01(Bitmap bmp, int[] px, float[] out) {
    int w = bmp.getWidth();
    int h = bmp.getHeight();
    if (w != DocQuadOrtRunner.IN_W || h != DocQuadOrtRunner.IN_H) {
      throw new IllegalArgumentException("bitmap must be 256x256");
    }
    int hw = h * w;
    bmp.getPixels(px, 0, w, 0, 0, w, h);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int c = px[y * w + x];
        float r = ((c >> 16) & 0xFF) / 255.0f;
        float g = ((c >> 8) & 0xFF) / 255.0f;
        float b = (c & 0xFF) / 255.0f;
        int idx = y * w + x;
        out[idx] = r;
        out[hw + idx] = g;
        out[2 * hw + idx] = b;
      }
    }
    return out;
  }

  // Neutral mid-gray padding reduces hard contrast at letterbox borders compared to pure black,
  // which empirically reduces spurious heatmap peaks at the image edge for documents close to
  // the frame border. Value 0xFF808080 is RGB(128,128,128) — the photometric mid-point in
  // [0..1] float space the model consumes.
  private static final int LETTERBOX_PAD_COLOR = 0xFF808080;

  private static Bitmap renderLetterbox256(Bitmap src, DocQuadLetterbox lb, Bitmap reuse) {
    Bitmap out =
        (reuse != null && !reuse.isRecycled())
            ? reuse
            : Bitmap.createBitmap(
                DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    canvas.drawColor(LETTERBOX_PAD_COLOR);

    float left = (float) lb.offsetX;
    float top = (float) lb.offsetY;
    float right = (float) (lb.offsetX + (double) lb.srcW * lb.scale);
    float bottom = (float) (lb.offsetY + (double) lb.srcH * lb.scale);
    RectF dst = new RectF(left, top, right, bottom);

    // Enable bilinear filtering and dithering during the strong downscale (often >10×) to
    // reduce aliasing-induced false edges that can confuse the corner heatmaps.
    android.graphics.Paint paint = new android.graphics.Paint();
    paint.setFilterBitmap(true);
    paint.setDither(true);
    paint.setAntiAlias(true);
    canvas.drawBitmap(src, null, dst, paint);
    return out;
  }

  private static boolean isFinite(double v) {
    return !Double.isNaN(v) && !Double.isInfinite(v);
  }

  private static boolean isValidQuad(double[][] c, int w, int h) {
    if (c == null || c.length != 4) return false;
    for (int i = 0; i < 4; i++) {
      if (c[i] == null || c[i].length != 2) return false;
      double x = c[i][0];
      double y = c[i][1];
      if (!isFinite(x) || !isFinite(y)) return false;
      // Plausibility: leicht außerhalb tolerieren, aber nicht komplett wild
      if (x < -w * 0.25 || x > w * 1.25) return false;
      if (y < -h * 0.25 || y > h * 1.25) return false;
    }
    // Convexity + TL/TR/BR/BL orientation check: rejects flipped/self-intersecting quads
    // such as the "cold start" frame where the model returns a degenerate ordering before
    // it has locked onto the document.
    return isConvexTLTRBRBL(c);
  }

  /**
   * Returns {@code true} iff the four corners form a strictly convex, non-self-intersecting
   * quadrilateral traversed in TL→TR→BR→BL order (i.e. clockwise in image coordinates where y grows
   * downward).
   *
   * <p>Implementation: all four signed cross products of consecutive edge vectors must share the
   * same (positive) sign and be non-zero. A positive sign in image coordinates (y grows downward)
   * corresponds to a clockwise winding, which is what TL/TR/BR/BL implies. Zero or mixed signs mean
   * the polygon is degenerate (collinear) or self-intersecting (e.g. swapped corners).
   *
   * <p>Visible for testing.
   */
  static boolean isConvexTLTRBRBL(double[][] c) {
    if (c == null || c.length != 4) return false;
    double prevSign = 0.0;
    for (int i = 0; i < 4; i++) {
      double[] a = c[i];
      double[] b = c[(i + 1) % 4];
      double[] d = c[(i + 2) % 4];
      if (a == null || b == null || d == null) return false;
      double abx = b[0] - a[0];
      double aby = b[1] - a[1];
      double bdx = d[0] - b[0];
      double bdy = d[1] - b[1];
      double cross = abx * bdy - aby * bdx;
      if (!isFinite(cross) || cross == 0.0) return false;
      double sign = Math.signum(cross);
      if (i == 0) {
        // Require clockwise winding (positive cross in image coordinates with y-down).
        if (sign < 0.0) return false;
        prevSign = sign;
      } else if (sign != prevSign) {
        return false;
      }
    }
    return true;
  }
}
