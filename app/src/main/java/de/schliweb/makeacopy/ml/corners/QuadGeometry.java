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
 * Geometry helpers for document quads given as {@code double[4][2]} in TL, TR, BR, BL order
 * (clockwise in image coordinates, y pointing down).
 */
public final class QuadGeometry {
  private QuadGeometry() {}

  public static boolean isFiniteQuad(@Nullable double[][] q) {
    if (q == null || q.length != 4) return false;
    for (double[] p : q) {
      if (p == null || p.length < 2 || !Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
        return false;
      }
    }
    return true;
  }

  public static double[][] copy(double[][] q) {
    double[][] out = new double[4][2];
    for (int i = 0; i < 4; i++) {
      out[i][0] = q[i][0];
      out[i][1] = q[i][1];
    }
    return out;
  }

  /**
   * Orders four points clockwise (angle around the centroid) starting with the top-left point
   * (smallest x + y). Works for any rotation: roles follow the geometry, not the detector's corner
   * channels.
   */
  public static double[][] orderClockwise(double[][] pts) {
    double cx = 0, cy = 0;
    for (int i = 0; i < 4; i++) {
      cx += pts[i][0];
      cy += pts[i][1];
    }
    cx /= 4;
    cy /= 4;
    Integer[] idx = {0, 1, 2, 3};
    final double fcx = cx, fcy = cy;
    java.util.Arrays.sort(
        idx,
        (a, b) ->
            Double.compare(
                Math.atan2(pts[a][1] - fcy, pts[a][0] - fcx),
                Math.atan2(pts[b][1] - fcy, pts[b][0] - fcx)));
    int start = 0;
    double best = Double.POSITIVE_INFINITY;
    for (int k = 0; k < 4; k++) {
      double s = pts[idx[k]][0] + pts[idx[k]][1];
      if (s < best) {
        best = s;
        start = k;
      }
    }
    double[][] out = new double[4][2];
    for (int k = 0; k < 4; k++) {
      double[] p = pts[idx[(start + k) % 4]];
      out[k][0] = p[0];
      out[k][1] = p[1];
    }
    return out;
  }

  /** Strict convexity (all turns in the same direction, no collinear vertices). */
  public static boolean isConvex(double[][] q) {
    int sign = 0;
    for (int i = 0; i < 4; i++) {
      double[] a = q[i], b = q[(i + 1) % 4], c = q[(i + 2) % 4];
      double cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]);
      if (!Double.isFinite(cross) || Math.abs(cross) < 1e-6) return false;
      int s = cross > 0 ? 1 : -1;
      if (sign == 0) sign = s;
      else if (s != sign) return false;
    }
    return true;
  }

  public static double area(double[][] q) {
    double a = 0;
    for (int i = 0; i < 4; i++) {
      double[] p = q[i], n = q[(i + 1) % 4];
      a += p[0] * n[1] - n[0] * p[1];
    }
    return Math.abs(a) / 2.0;
  }

  public static double sideLength(double[][] q, int i) {
    double[] a = q[i], b = q[(i + 1) % 4];
    return Math.hypot(b[0] - a[0], b[1] - a[1]);
  }

  /** Interior angle at corner i, in degrees. */
  public static double angleDeg(double[][] q, int i) {
    double[] p = q[(i + 3) % 4], c = q[i], n = q[(i + 1) % 4];
    double ux = p[0] - c[0], uy = p[1] - c[1], vx = n[0] - c[0], vy = n[1] - c[1];
    double den = Math.hypot(ux, uy) * Math.hypot(vx, vy) + 1e-9;
    double cos = Math.max(-1, Math.min(1, (ux * vx + uy * vy) / den));
    return Math.toDegrees(Math.acos(cos));
  }

  public static double maxCornerDistance(double[][] a, double[][] b) {
    double m = 0;
    for (int i = 0; i < 4; i++) {
      m = Math.max(m, Math.hypot(a[i][0] - b[i][0], a[i][1] - b[i][1]));
    }
    return m;
  }

  /**
   * Plausibility of a document outline in [0, 1]: 0 for non-convex quads, corners sharper than 35°
   * or flatter than 145°, tiny areas or strongly unequal opposite sides; otherwise decreasing with
   * the average deviation from right angles (perspective is tolerated).
   */
  public static double plausibility(double[][] q, int imgW, int imgH) {
    if (!isFiniteQuad(q) || !isConvex(q)) return 0;
    double devSum = 0;
    for (int i = 0; i < 4; i++) {
      double a = angleDeg(q, i);
      if (a < 35 || a > 145) return 0;
      devSum += Math.abs(a - 90);
    }
    if (area(q) / ((double) imgW * imgH) < 0.03) return 0;
    double l0 = sideLength(q, 0), l1 = sideLength(q, 1), l2 = sideLength(q, 2);
    double l3 = sideLength(q, 3);
    double r = Math.min(Math.min(l0, l2) / Math.max(l0, l2), Math.min(l1, l3) / Math.max(l1, l3));
    if (!(r >= 0.35)) return 0;
    double angleTerm = clamp01(1.0 - (devSum / 4.0) / 55.0);
    return angleTerm * clamp01(r / 0.7);
  }

  /**
   * Fraction of the outline lying on the image border. Outlines glued to the frame edge are the
   * frame itself, an object cut by the frame, or a page that is only partly visible.
   */
  public static double borderFraction(double[][] q, int w, int h, double tol) {
    int on = 0, total = 0;
    for (int i = 0; i < 4; i++) {
      double[] a = q[i], b = q[(i + 1) % 4];
      for (int k = 0; k < 12; k++) {
        double u = k / 11.0;
        double x = a[0] + (b[0] - a[0]) * u;
        double y = a[1] + (b[1] - a[1]) * u;
        total++;
        if (x < tol || y < tol || x > w - 1 - tol || y > h - 1 - tol) on++;
      }
    }
    return on / (double) total;
  }

  /** Point-in-convex-quad test (clockwise or counter-clockwise quads). */
  public static boolean contains(double[][] q, double x, double y) {
    int sign = 0;
    for (int i = 0; i < 4; i++) {
      double[] a = q[i], b = q[(i + 1) % 4];
      double cross = (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
      int s = cross > 0 ? 1 : (cross < 0 ? -1 : 0);
      if (s == 0) continue;
      if (sign == 0) sign = s;
      else if (s != sign) return false;
    }
    return true;
  }

  static double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }
}
