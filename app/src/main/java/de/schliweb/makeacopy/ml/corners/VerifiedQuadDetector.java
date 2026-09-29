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

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import java.util.List;

/**
 * Document outline detection by <b>hypotheses + verification</b>.
 *
 * <ol>
 *   <li>Hypothesis from the DocQuad network (its corner channels and its document mask).
 *   <li>Each hypothesis is snapped onto the real edges with {@link QuadRefiner} (sub-pixel corners
 *       at the intersection of the fitted sides) and scored with {@link QuadScorer}.
 *   <li>Only if the network's outline is not convincing (score &lt; {@link QuadScorer#CONVINCING}):
 *       classical OpenCV contour hypotheses ({@link ContourQuadCandidates}) are refined and scored
 *       the same way. The best-scoring outline wins; the largest one gets no bonus, so a table, a
 *       screen or the floor does not win because of its size.
 * </ol>
 *
 * <p>Why: on a benchmark of 380 synthetic scenes, the network alone mislabeled one corner in ~25%
 * of the frames (its corner channels are tied to TL/TR/BR/BL roles, which become ambiguous from
 * ~20° of rotation) and drew an outline on 18 of 40 scenes without any document. With verification:
 * 0.3 px mean corner error instead of 3.2 px, 2 of 40 empty scenes (plain cards) outlined.
 *
 * <p>Not thread-safe per instance by design (reused buffers); calls are serialized.
 */
public final class VerifiedQuadDetector implements CornerDetector {
  private static final String TAG = "VerifiedQuad";

  /** First-pass search radius for the refinement, as a fraction of the image diagonal. */
  private static final double SEARCH_RADIUS_FRACTION = 0.016;

  @Nullable private final DocQuadDetector docQuad;
  private final double contourBelow;
  private final int contourMaxEdge;
  private int[] pixels;
  private float[] luma;
  private volatile long lastDurationMs;
  private volatile String lastTimings = "";

  /** Outline, its confidence and where it comes from. */
  public static final class Verified {
    /** TL, TR, BR, BL in bitmap pixels. */
    public final double[][] quad;

    public final double confidence;
    public final Source source;
    public final double[] sideSupport;

    Verified(double[][] quad, double confidence, Source source, double[] sideSupport) {
      this.quad = quad;
      this.confidence = confidence;
      this.source = source;
      this.sideSupport = sideSupport;
    }
  }

  /**
   * One-shot detector (crop screen): contour hypotheses whenever the network outline scores below
   * {@link QuadScorer#CONVINCING}, at full resolution.
   *
   * @param docQuad network detector, or {@code null} to use the classical hypotheses only
   */
  public VerifiedQuadDetector(@Nullable DocQuadDetector docQuad) {
    this(docQuad, QuadScorer.CONVINCING, Integer.MAX_VALUE);
  }

  /**
   * @param contourBelow the (costlier) contour hypotheses are only tried when the network outline
   *     scores below this
   * @param contourMaxEdge contour hypotheses are searched on a copy downscaled to this size
   */
  public VerifiedQuadDetector(
      @Nullable DocQuadDetector docQuad, double contourBelow, int contourMaxEdge) {
    this.docQuad = docQuad;
    this.contourBelow = contourBelow;
    this.contourMaxEdge = contourMaxEdge;
  }

  /** Live settings: contour hypotheses only when the network outline is weak, at half size. */
  public static VerifiedQuadDetector forLive(@Nullable DocQuadDetector docQuad) {
    return new VerifiedQuadDetector(docQuad, 0.6, 360);
  }

  /** Per-step durations of the last call, for diagnostics logs. */
  public String lastTimings() {
    return lastTimings;
  }

  /** Duration of the last {@link #detectVerified} call (for adaptive analysis pacing). */
  public long lastDurationMs() {
    return lastDurationMs;
  }

  @Override
  public DetectionResult detect(Bitmap src, Context ctx) {
    Verified v = detectVerified(src, ctx);
    if (v == null || v.confidence < QuadScorer.KEEP) return DetectionResult.fail(Source.FALLBACK);
    return DetectionResult.successScored(v.source, v.quad, v.confidence);
  }

  /** Best verified outline (any confidence), or {@code null} when there is no hypothesis at all. */
  @Nullable
  public synchronized Verified detectVerified(@Nullable Bitmap src, @Nullable Context ctx) {
    if (src == null || src.isRecycled()) return null;
    long t0 = SystemClock.uptimeMillis();
    try {
      int w = src.getWidth(), h = src.getHeight();
      if (w < 16 || h < 16) return null;
      LumaImage img = luma(src);
      long t1 = SystemClock.uptimeMillis();
      double radius = Math.max(6, SEARCH_RADIUS_FRACTION * Math.hypot(w, h));

      Verified best = null;
      QuadScorer.ModelMask mask = null;
      long tModel = t1;
      if (docQuad != null && ctx != null) {
        DocQuadDetector.Detailed d = docQuad.detectDetailed(src, ctx);
        tModel = SystemClock.uptimeMillis();
        mask = d.mask;
        if (d.result.success && QuadGeometry.isFiniteQuad(d.result.cornersOriginalTLTRBRBL)) {
          best = verify(img, d.result.cornersOriginalTLTRBRBL, radius, mask, Source.DOCQUAD);
        }
      }
      long t2 = SystemClock.uptimeMillis();
      int nCands = 0;
      if ((best == null || best.confidence < contourBelow) && OpenCVUtils.isInitialized()) {
        List<double[][]> cands = contourCandidates(src);
        nCands = cands.size();
        double r2 = Math.max(4, radius / 2);
        for (double[][] q : cands) {
          Verified v = verify(img, q, r2, mask, Source.OPENCV);
          if (v != null && (best == null || v.confidence > best.confidence)) best = v;
        }
      }
      long t3 = SystemClock.uptimeMillis();
      lastTimings =
          "luma="
              + (t1 - t0)
              + " model="
              + (tModel - t1)
              + " (inference="
              + (docQuad != null ? docQuad.lastInferenceMs() : 0)
              + ")"
              + " refine="
              + (t2 - tModel)
              + " contours("
              + nCands
              + ")="
              + (t3 - t2);
      return best;
    } catch (Throwable t) {
      Log.w(TAG, "detection failed: " + t.getMessage());
      return null;
    } finally {
      lastDurationMs = SystemClock.uptimeMillis() - t0;
    }
  }

  @Nullable
  private static Verified verify(
      LumaImage img,
      double[][] quad,
      double radius,
      @Nullable QuadScorer.ModelMask mask,
      Source source) {
    double[][] start = QuadGeometry.orderClockwise(quad);
    if (!QuadGeometry.isConvex(start)) return null;
    QuadRefiner.Result r = QuadRefiner.refine(img, start, radius, 2);
    double conf = QuadScorer.confidence(r, img.width, img.height, mask);
    return new Verified(QuadGeometry.orderClockwise(r.quad), conf, source, r.sideSupport);
  }

  /** Contour hypotheses, searched on a downscaled copy when {@code contourMaxEdge} requires it. */
  private List<double[][]> contourCandidates(Bitmap src) {
    int w = src.getWidth(), h = src.getHeight();
    double s = Math.min(1.0, contourMaxEdge / (double) Math.max(w, h));
    if (s >= 1.0) return ContourQuadCandidates.find(src);
    Bitmap small =
        Bitmap.createScaledBitmap(
            src, Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(h * s)), true);
    try {
      List<double[][]> out = ContourQuadCandidates.find(small);
      for (double[][] q : out) {
        for (double[] p : q) {
          p[0] /= s;
          p[1] /= s;
        }
      }
      return out;
    } finally {
      small.recycle();
    }
  }

  @NonNull
  private LumaImage luma(Bitmap src) {
    int w = src.getWidth(), h = src.getHeight(), n = w * h;
    if (pixels == null || pixels.length < n) pixels = new int[n];
    src.getPixels(pixels, 0, w, 0, 0, w, h);
    luma = LumaImage.lumaFromArgb(pixels, n, luma);
    return new LumaImage(w, h, luma);
  }

  /**
   * Precise refinement of an outline on a high-resolution image (the captured photo), after the
   * capture: the outline found on the small analysis image (±1 px there, i.e. ±5 px on a 12 MP
   * photo) is snapped onto the photo's own edges.
   *
   * @param quadInPhoto TL, TR, BR, BL in {@code photo} pixels
   * @param maxEdge the photo is analysed at most at this size (memory / time bound)
   * @return the refined outline in {@code photo} pixels, or {@code quadInPhoto} when the edges do
   *     not support a change
   */
  @NonNull
  public static double[][] refineOnPhoto(Bitmap photo, double[][] quadInPhoto, int maxEdge) {
    if (photo == null || photo.isRecycled() || !QuadGeometry.isFiniteQuad(quadInPhoto)) {
      return quadInPhoto;
    }
    Bitmap work = photo;
    try {
      int pw = photo.getWidth(), ph = photo.getHeight();
      double s = Math.min(1.0, maxEdge / (double) Math.max(pw, ph));
      if (s < 1.0) {
        work =
            Bitmap.createScaledBitmap(
                photo,
                Math.max(1, (int) Math.round(pw * s)),
                Math.max(1, (int) Math.round(ph * s)),
                true);
      }
      int w = work.getWidth(), h = work.getHeight();
      int[] px = new int[w * h];
      work.getPixels(px, 0, w, 0, 0, w, h);
      LumaImage img = new LumaImage(w, h, LumaImage.lumaFromArgb(px, w * h, null));
      double[][] q = new double[4][2];
      for (int i = 0; i < 4; i++) {
        q[i][0] = quadInPhoto[i][0] * s;
        q[i][1] = quadInPhoto[i][1] * s;
      }
      q = QuadGeometry.orderClockwise(q);
      if (!QuadGeometry.isConvex(q)) return quadInPhoto;
      double radius = Math.max(6, 0.008 * Math.hypot(w, h));
      QuadRefiner.Result r = QuadRefiner.refine(img, q, radius, 3);
      // Only accept the refinement when the sides really lie on edges.
      if (r.minSupport() < 0.5 || QuadGeometry.plausibility(r.quad, w, h) <= 0) {
        return quadInPhoto;
      }
      double[][] out = new double[4][2];
      for (int i = 0; i < 4; i++) {
        out[i][0] = Math.max(0, Math.min(pw, r.quad[i][0] / s));
        out[i][1] = Math.max(0, Math.min(ph, r.quad[i][1] / s));
      }
      return out;
    } catch (Throwable t) {
      Log.w(TAG, "refineOnPhoto failed: " + t.getMessage());
      return quadInPhoto;
    } finally {
      if (work != photo) work.recycle();
    }
  }
}
