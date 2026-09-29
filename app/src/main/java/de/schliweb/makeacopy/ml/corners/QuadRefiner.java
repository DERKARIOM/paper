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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Snaps an approximate document quad onto the real document edges, with sub-pixel precision.
 *
 * <p>For each side, the intensity profile is read along the side normal at {@link
 * #SAMPLES_PER_SIDE} places; the edge candidates (local maxima of the profile derivative, sub-pixel
 * via a parabola fit, both polarities) are collected, and a RANSAC line fit keeps the straight,
 * consistently oriented edge that most samples agree on — text lines or table grain near the side
 * do not form such a line. Corners are then the intersections of adjacent fitted sides, so they sit
 * exactly on the document boundary instead of a few pixels inside it.
 *
 * <p>The fraction of samples supporting each side ({@link Result#sideSupport}) is real evidence
 * that the outline lies on an edge of the image; the scorer uses it for the confidence.
 *
 * <p>Cost: 4 sides × 24 samples × (2R+3) bilinear reads per iteration, i.e. a few thousand reads —
 * negligible next to the detector itself. Deterministic (fixed RANSAC seed).
 */
public final class QuadRefiner {
  public static final int SAMPLES_PER_SIDE = 24;
  /** Minimum edge strength (gray levels per pixel) regardless of the measured noise. */
  private static final double MIN_EDGE_STRENGTH = 2.5;

  private static final double INLIER_TOL_PX = 1.2;
  private static final int RANSAC_ITERATIONS = 60;
  /** A side needs this fraction of supporting samples before its line may move a corner. */
  private static final double MIN_SUPPORT_TO_MOVE = 0.3;

  private QuadRefiner() {}

  /** Refinement output. */
  public static final class Result {
    /** Refined corners, TL, TR, BR, BL. */
    public final double[][] quad;

    /** Per side (TL→TR, TR→BR, BR→BL, BL→TL): fraction of samples lying on the fitted edge. */
    public final double[] sideSupport;

    Result(double[][] quad, double[] sideSupport) {
      this.quad = quad;
      this.sideSupport = sideSupport;
    }

    public double meanSupport() {
      return (sideSupport[0] + sideSupport[1] + sideSupport[2] + sideSupport[3]) / 4.0;
    }

    public double minSupport() {
      return Math.min(
          Math.min(sideSupport[0], sideSupport[1]), Math.min(sideSupport[2], sideSupport[3]));
    }
  }

  /**
   * Refines {@code quad} (TL, TR, BR, BL) on {@code img}.
   *
   * @param searchRadius how far (px) the true edge may be from the given side in the first pass;
   *     later passes halve it roughly
   * @param iterations number of passes (2 is enough for detector output)
   */
  public static Result refine(LumaImage img, double[][] quad, double searchRadius, int iterations) {
    double[][] q = QuadGeometry.copy(quad);
    double[] support = new double[4];
    double radius = Math.max(3, searchRadius);
    double maxMove = 2.5 * radius;
    for (int it = 0; it < iterations; it++) {
      Line[] lines = new Line[4];
      for (int s = 0; s < 4; s++) {
        lines[s] = fitSide(img, q[s], q[(s + 1) % 4], (int) Math.round(radius));
        support[s] = lines[s] != null ? lines[s].support : 0;
      }
      double[][] next = QuadGeometry.copy(q);
      for (int c = 0; c < 4; c++) {
        // corner c joins side (c-1) (ending at c) and side c (starting at c)
        Line in = lines[(c + 3) % 4];
        Line out = lines[c];
        if (in == null || out == null) continue;
        if (in.support < MIN_SUPPORT_TO_MOVE || out.support < MIN_SUPPORT_TO_MOVE) continue;
        double[] p = intersect(in, out);
        if (p == null) continue;
        if (Math.hypot(p[0] - q[c][0], p[1] - q[c][1]) <= maxMove) next[c] = p;
      }
      q = next;
      radius = Math.max(4, radius * 0.6);
    }
    return new Result(q, support);
  }

  // --------------------------------------------------------------------------------------------

  static final class Line {
    final double cx, cy, dx, dy; // point + unit direction
    final double support;
    final int polarity;
    final double strength; // mean edge strength of the supporting samples

    Line(
        double cx,
        double cy,
        double dx,
        double dy,
        double support,
        int polarity,
        double strength) {
      this.cx = cx;
      this.cy = cy;
      this.dx = dx;
      this.dy = dy;
      this.support = support;
      this.polarity = polarity;
      this.strength = strength;
    }
  }

  private static final class Candidate {
    final double x, y, strength;
    final int sample, polarity;

    Candidate(double x, double y, double strength, int sample, int polarity) {
      this.x = x;
      this.y = y;
      this.strength = strength;
      this.sample = sample;
      this.polarity = polarity;
    }
  }

  static Line fitSide(LumaImage img, double[] a, double[] b, int radius) {
    double dx = b[0] - a[0], dy = b[1] - a[1];
    double len = Math.hypot(dx, dy);
    if (len < 8 || radius < 1) return null;
    double tx = dx / len, ty = dy / len;
    double nx = ty, ny = -tx; // outward normal of a clockwise (y-down) quad
    int n = 2 * radius + 3; // profile positions k = -radius-1 .. radius+1
    float[] prof = new float[n];
    float[] der = new float[n - 2];
    float[] absDer = new float[n - 2];
    List<Candidate> cands = new ArrayList<>(SAMPLES_PER_SIDE * 6);
    for (int s = 0; s < SAMPLES_PER_SIDE; s++) {
      double u = 0.06 + 0.88 * s / (SAMPLES_PER_SIDE - 1.0);
      double px = a[0] + dx * u, py = a[1] + dy * u;
      int inside = 0;
      for (int i = 0; i < n; i++) {
        double k = i - radius - 1;
        double x = px + k * nx, y = py + k * ny;
        if (img.contains(x, y, 1)) inside++;
        // lateral 1-2-1 smoothing along the side reduces noise without blurring across the edge
        prof[i] =
            (img.sample(x - tx, y - ty) + 2 * img.sample(x, y) + img.sample(x + tx, y + ty)) / 4f;
      }
      if (inside < 0.7 * n) continue; // side leaves the image here
      for (int i = 0; i < n - 2; i++) {
        der[i] = (prof[i + 2] - prof[i]) / 2f;
        absDer[i] = Math.abs(der[i]);
      }
      double noise = 1.4826 * median(absDer) + 0.5;
      double thr = Math.max(MIN_EDGE_STRENGTH, 3.0 * noise);
      for (int pol = -1; pol <= 1; pol += 2) {
        // keep the 3 strongest local maxima of pol*der
        Candidate[] top = new Candidate[3];
        for (int i = 1; i < n - 3; i++) {
          double c = pol * der[i], l = pol * der[i - 1], r = pol * der[i + 1];
          if (c < thr || c < l || c <= r) continue;
          double den = l - 2 * c + r;
          double off = den < -1e-9 ? 0.5 * (l - r) / den : 0;
          off = Math.max(-0.5, Math.min(0.5, off));
          double k = (i + 1) - radius - 1 + off; // der[i] is centred on profile index i+1
          Candidate cand = new Candidate(px + k * nx, py + k * ny, c, s, pol);
          insertTop(top, cand);
        }
        for (Candidate t : top) if (t != null) cands.add(t);
      }
    }
    // Up to two straight edges per polarity: the paper edge, and e.g. both borders of a printed
    // frame, a table or a colored band just inside the page.
    List<Line> lines = new ArrayList<>(4);
    for (int pol = -1; pol <= 1; pol += 2) {
      Line first = ransacLine(cands, pol);
      if (first == null) continue;
      lines.add(first);
      List<Candidate> rest = new ArrayList<>(cands.size());
      for (Candidate k : cands) {
        if (Math.abs((k.x - first.cx) * -first.dy + (k.y - first.cy) * first.dx) > 2.0) {
          rest.add(k);
        }
      }
      Line second = ransacLine(rest, pol);
      if (second != null) lines.add(second);
    }
    if (lines.isEmpty()) return null;
    Line best = lines.get(0);
    for (Line l : lines) if (l.support > best.support) best = l;
    // The document boundary is the OUTERMOST straight edge that is well supported and about as
    // contrasted as the best one (printed frames lie inside the page; soft shadow edges outside it
    // are weak and do not qualify).
    double mx = a[0] + dx * 0.5, my = a[1] + dy * 0.5;
    Line chosen = best;
    double chosenOffset = offsetAlongNormal(best, mx, my, nx, ny);
    for (Line l : lines) {
      if (l == best || l.support < 0.6 * best.support || l.strength < 0.35 * best.strength) {
        continue;
      }
      double off = offsetAlongNormal(l, mx, my, nx, ny);
      if (off > chosenOffset) {
        chosen = l;
        chosenOffset = off;
      }
    }
    return chosen;
  }

  /** Signed distance, along the outward normal from (mx, my), to where it crosses the line. */
  private static double offsetAlongNormal(Line l, double mx, double my, double nx, double ny) {
    double den = nx * l.dy - ny * l.dx;
    if (Math.abs(den) < 1e-9) return 0;
    return ((l.cx - mx) * l.dy - (l.cy - my) * l.dx) / den;
  }

  private static void insertTop(Candidate[] top, Candidate c) {
    for (int i = 0; i < top.length; i++) {
      if (top[i] == null || c.strength > top[i].strength) {
        for (int j = top.length - 1; j > i; j--) top[j] = top[j - 1];
        top[i] = c;
        return;
      }
    }
  }

  private static Line ransacLine(List<Candidate> all, int pol) {
    List<Candidate> c = new ArrayList<>();
    for (Candidate k : all) if (k.polarity == pol) c.add(k);
    if (c.size() < 4) return null;
    Random rnd = new Random(7);
    int bestSupport = -1;
    double bestResidual = Double.MAX_VALUE;
    double[] bestLine = null;
    for (int it = 0; it < RANSAC_ITERATIONS; it++) {
      Candidate p = c.get(rnd.nextInt(c.size()));
      Candidate q = c.get(rnd.nextInt(c.size()));
      if (p.sample == q.sample) continue;
      double dx = q.x - p.x, dy = q.y - p.y, d = Math.hypot(dx, dy);
      if (d < 4) continue;
      double nx = -dy / d, ny = dx / d;
      boolean[] seen = new boolean[SAMPLES_PER_SIDE];
      int sup = 0;
      double res = 0;
      for (Candidate k : c) {
        double r = Math.abs((k.x - p.x) * nx + (k.y - p.y) * ny);
        if (r < INLIER_TOL_PX) {
          res += r;
          if (!seen[k.sample]) {
            seen[k.sample] = true;
            sup++;
          }
        }
      }
      if (sup > bestSupport || (sup == bestSupport && res < bestResidual)) {
        bestSupport = sup;
        bestResidual = res;
        bestLine = new double[] {p.x, p.y, dx / d, dy / d};
      }
    }
    if (bestLine == null || bestSupport < 4) return null;
    // Refit twice: one point per sample (the one closest to the current line), total least squares.
    double lx = bestLine[0], ly = bestLine[1], ldx = bestLine[2], ldy = bestLine[3];
    int used = 0;
    double strengthSum = 0;
    for (int pass = 0; pass < 2; pass++) {
      double nx = -ldy, ny = ldx;
      Candidate[] perSample = new Candidate[SAMPLES_PER_SIDE];
      double[] perRes = new double[SAMPLES_PER_SIDE];
      for (Candidate k : c) {
        double r = Math.abs((k.x - lx) * nx + (k.y - ly) * ny);
        if (r >= INLIER_TOL_PX * 1.5) continue;
        if (perSample[k.sample] == null || r < perRes[k.sample]) {
          perSample[k.sample] = k;
          perRes[k.sample] = r;
        }
      }
      double sx = 0, sy = 0;
      used = 0;
      strengthSum = 0;
      for (Candidate k : perSample) {
        if (k == null) continue;
        sx += k.x;
        sy += k.y;
        strengthSum += k.strength;
        used++;
      }
      if (used < 4) return null;
      double mx = sx / used, my = sy / used;
      double sxx = 0, sxy = 0, syy = 0;
      for (Candidate k : perSample) {
        if (k == null) continue;
        double ex = k.x - mx, ey = k.y - my;
        sxx += ex * ex;
        sxy += ex * ey;
        syy += ey * ey;
      }
      // principal direction of the 2x2 covariance
      double theta = 0.5 * Math.atan2(2 * sxy, sxx - syy);
      double ndx = Math.cos(theta), ndy = Math.sin(theta);
      if (ndx * ldx + ndy * ldy < 0) {
        ndx = -ndx;
        ndy = -ndy;
      }
      lx = mx;
      ly = my;
      ldx = ndx;
      ldy = ndy;
    }
    return new Line(
        lx, ly, ldx, ldy, used / (double) SAMPLES_PER_SIDE, pol, strengthSum / Math.max(1, used));
  }

  static double[] intersect(Line a, Line b) {
    // Solve a.c + t * a.d = b.c + s * b.d (Cramer's rule).
    double det = b.dx * a.dy - a.dx * b.dy;
    if (Math.abs(det) < 1e-6) return null;
    double rx = b.cx - a.cx, ry = b.cy - a.cy;
    double t = (b.dx * ry - rx * b.dy) / det;
    return new double[] {a.cx + t * a.dx, a.cy + t * a.dy};
  }

  /** Median of the values (the array is copied, not modified). */
  static double median(float[] v) {
    float[] c = v.clone();
    java.util.Arrays.sort(c);
    int m = c.length / 2;
    return (c.length % 2 == 1) ? c[m] : 0.5 * (c[m - 1] + c[m]);
  }
}
