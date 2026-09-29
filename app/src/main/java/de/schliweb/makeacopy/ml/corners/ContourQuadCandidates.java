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

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Classical (OpenCV) document outline hypotheses, used when the network's outline is not
 * convincing (strongly rotated page, white page on a white table, unusual document).
 *
 * <p>Three segmentations produce contours: Otsu threshold (page brighter than the background),
 * inverted Otsu (page darker), each closed/opened to swallow the text, and Canny edges whose
 * thresholds come from the <em>gradient</em> distribution of the frame (85th percentile) — the
 * previous thresholds came from the brightness median, which erased faint edges on bright scenes
 * and let noise through on dark ones. Every sufficiently large contour is reduced to a convex
 * 4-vertex polygon. The candidates are only hypotheses: {@link VerifiedQuadDetector} refines and
 * scores each of them, so a table edge or a screen does not win just because it is large.
 */
final class ContourQuadCandidates {
  private static final int MAX_CONTOURS_PER_MASK = 12;
  private static final int MAX_CANDIDATES = 12;

  private ContourQuadCandidates() {}

  static List<double[][]> find(Bitmap bitmap) {
    List<double[][]> out = new ArrayList<>();
    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat blur = new Mat();
    Mat th = new Mat();
    Mat inv = new Mat();
    Mat work = new Mat();
    Mat edges = new Mat();
    Mat gx = new Mat();
    Mat gy = new Mat();
    Mat mag = new Mat();
    Mat kernel = new Mat();
    Mat small = new Mat();
    Mat k3 = new Mat();
    try {
      Utils.bitmapToMat(bitmap, rgba);
      int w = rgba.cols(), h = rgba.rows();
      double imgArea = (double) w * h;
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
      Imgproc.GaussianBlur(gray, blur, new Size(5, 5), 0);

      int k = Math.max(5, Math.min(w, h) / 40) | 1;
      kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(k, k));
      small = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
      k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));

      Imgproc.threshold(blur, th, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
      Core.bitwise_not(th, inv);
      for (Mat m : new Mat[] {th, inv}) {
        Imgproc.morphologyEx(m, work, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(work, work, Imgproc.MORPH_OPEN, small);
        collect(work, Imgproc.RETR_EXTERNAL, imgArea, out);
      }

      Imgproc.Sobel(blur, gx, CvType.CV_32F, 1, 0, 3);
      Imgproc.Sobel(blur, gy, CvType.CV_32F, 0, 1, 3);
      Core.magnitude(gx, gy, mag);
      double hi = Math.max(20.0, percentile(mag, 0.85));
      Imgproc.Canny(blur, edges, 0.4 * hi, hi, 3, true);
      Imgproc.dilate(edges, edges, k3);
      collect(edges, Imgproc.RETR_LIST, imgArea, out);
    } catch (Throwable t) {
      // best effort: no classical candidates
    } finally {
      Mat[] all = {rgba, gray, blur, th, inv, work, edges, gx, gy, mag, kernel, small, k3};
      for (Mat m : all) m.release();
    }
    return dedupe(out);
  }

  private static void collect(Mat binary, int mode, double imgArea, List<double[][]> out) {
    List<MatOfPoint> contours = new ArrayList<>();
    Mat hierarchy = new Mat();
    Mat copy = binary.clone();
    try {
      Imgproc.findContours(copy, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE);
      contours.sort((a, b) -> Double.compare(Imgproc.contourArea(b), Imgproc.contourArea(a)));
      int n = Math.min(contours.size(), MAX_CONTOURS_PER_MASK);
      for (int i = 0; i < n; i++) {
        double a = Imgproc.contourArea(contours.get(i));
        if (a < 0.04 * imgArea || a > 0.97 * imgArea) continue;
        double[][] q = toQuad(contours.get(i));
        if (q != null && QuadGeometry.isConvex(q)) out.add(q);
      }
    } finally {
      for (MatOfPoint c : contours) c.release();
      hierarchy.release();
      copy.release();
    }
  }

  /** Convex hull reduced to 4 vertices with increasing tolerance, or {@code null}. */
  private static double[][] toQuad(MatOfPoint contour) {
    MatOfInt hullIdx = new MatOfInt();
    MatOfPoint2f hull = null;
    MatOfPoint2f approx = new MatOfPoint2f();
    try {
      Imgproc.convexHull(contour, hullIdx);
      Point[] pts = contour.toArray();
      int[] idx = hullIdx.toArray();
      if (idx.length < 4) return null;
      Point[] hp = new Point[idx.length];
      for (int i = 0; i < idx.length; i++) hp[i] = pts[idx[i]];
      hull = new MatOfPoint2f(hp);
      double per = Imgproc.arcLength(hull, true);
      for (double eps : new double[] {0.01, 0.015, 0.02, 0.03, 0.045, 0.06}) {
        Imgproc.approxPolyDP(hull, approx, eps * per, true);
        long n = approx.total();
        if (n == 4) {
          Point[] a = approx.toArray();
          double[][] q = new double[4][2];
          for (int i = 0; i < 4; i++) {
            q[i][0] = a[i].x;
            q[i][1] = a[i].y;
          }
          return QuadGeometry.orderClockwise(q);
        }
        if (n < 4) return null;
      }
      return null;
    } finally {
      hullIdx.release();
      if (hull != null) hull.release();
      approx.release();
    }
  }

  private static List<double[][]> dedupe(List<double[][]> in) {
    List<double[][]> res = new ArrayList<>();
    for (double[][] q : in) {
      boolean dup = false;
      for (double[][] r : res) {
        if (QuadGeometry.maxCornerDistance(q, r) <= 6) {
          dup = true;
          break;
        }
      }
      if (!dup) res.add(q);
      if (res.size() >= MAX_CANDIDATES) break;
    }
    return res;
  }

  /** Approximate percentile of a CV_32F single-channel Mat via a histogram (no sorting). */
  private static double percentile(Mat m, double p) {
    int n = (int) m.total();
    float[] v = new float[n];
    m.get(0, 0, v);
    final int bins = 1024;
    double max = 0;
    for (float f : v) if (f > max) max = f;
    if (max <= 0) return 0;
    int[] hist = new int[bins];
    double s = (bins - 1) / max;
    for (float f : v) hist[(int) (f * s)]++;
    long target = (long) Math.ceil(p * n);
    long cum = 0;
    for (int i = 0; i < bins; i++) {
      cum += hist[i];
      if (cum >= target) return i / s;
    }
    return max;
  }
}
