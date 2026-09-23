/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop;

/**
 * Pure-Java geometry helpers for crop quadrilateral edge dragging and soft-clamping of corner
 * points to image bounds.
 *
 * <p>Coordinate convention: 2D points are passed as parallel {@code float} arrays {@code xs[4],
 * ys[4]} representing the quadrilateral corners in TL, TR, BR, BL order — consistent with {@code
 * TrapezoidSelectionView.corners}.
 */
public final class CropEdgeGeometry {

  /** Hit radius around an edge segment, in pixels. */
  public static final float EDGE_TOUCH_RADIUS_PX_DEFAULT = 24f;

  /** Fractional dead-zone at each end of an edge to keep corner hits safe. */
  public static final float EDGE_END_DEADZONE_DEFAULT = 0.15f;

  /**
   * Tolerance for off-screen corners, expressed as a fraction of the image dimension. With the
   * default of 0.25, a corner may travel up to 25 % of the image width/height past the image bounds
   * in either direction.
   */
  public static final float IMG_OOB_TOL_DEFAULT = 0.25f;

  private CropEdgeGeometry() {
    // utility class
  }

  /** Result of projecting a point P onto the line segment AB. */
  public static final class Projection {
    /** Parametric position along AB: 0 ≙ A, 1 ≙ B (clamped to [0, 1]). */
    public final float t;

    /** Perpendicular distance from P to the segment (in pixels). */
    public final float perpDist;

    public Projection(float t, float perpDist) {
      this.t = t;
      this.perpDist = perpDist;
    }
  }

  /**
   * Project the point {@code (px, py)} onto the segment {@code A=(ax, ay) → B=(bx, by)}. Returns
   * the parametric position (clamped to {@code [0, 1]}) and the perpendicular distance from {@code
   * P} to the segment.
   */
  public static Projection projectOntoSegment(
      float px, float py, float ax, float ay, float bx, float by) {
    float dx = bx - ax;
    float dy = by - ay;
    float lenSq = dx * dx + dy * dy;
    if (lenSq <= 1e-6f) {
      // Degenerate segment → distance from A.
      float ddx = px - ax;
      float ddy = py - ay;
      return new Projection(0f, (float) Math.sqrt(ddx * ddx + ddy * ddy));
    }
    float t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
    float tClamped = Math.max(0f, Math.min(1f, t));
    float qx = ax + tClamped * dx;
    float qy = ay + tClamped * dy;
    float ex = px - qx;
    float ey = py - qy;
    float perp = (float) Math.sqrt(ex * ex + ey * ey);
    return new Projection(tClamped, perp);
  }

  /**
   * Find which quadrilateral edge (if any) is hit by a touch at {@code (x, y)}.
   *
   * @param xs x coordinates of the four corners (TL, TR, BR, BL).
   * @param ys y coordinates of the four corners (TL, TR, BR, BL).
   * @param x touch x in the same coordinate frame as {@code xs/ys}.
   * @param y touch y.
   * @param edgeTouchRadiusPx maximum perpendicular distance to count as a hit.
   * @param endDeadzone fractional dead-zone at each segment end (e.g. 0.15).
   * @return edge index in {@code [0..3]} (0=Top, 1=Right, 2=Bottom, 3=Left) or {@code -1} if no
   *     edge is hit.
   */
  public static int findEdgeHit(
      float[] xs, float[] ys, float x, float y, float edgeTouchRadiusPx, float endDeadzone) {
    if (xs == null || ys == null || xs.length < 4 || ys.length < 4) {
      return -1;
    }
    int best = -1;
    float bestDist = edgeTouchRadiusPx;
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      Projection p = projectOntoSegment(x, y, xs[i], ys[i], xs[j], ys[j]);
      if (p.perpDist <= edgeTouchRadiusPx
          && p.t > endDeadzone
          && p.t < (1f - endDeadzone)
          && p.perpDist < bestDist) {
        bestDist = p.perpDist;
        best = i;
      }
    }
    return best;
  }

  /**
   * Outcome of an attempted edge translation. Either the requested orthogonal delta {@code dxOrth,
   * dyOrth} (computed from the touch point's orthogonal projection onto the original edge's normal)
   * was applied, in which case {@link #applied} is true, or the move would have invalidated the
   * quad, in which case {@link #applied} is false and the original corner positions are returned
   * unchanged.
   */
  public static final class EdgeTranslation {
    public final float[] xs;
    public final float[] ys;
    public final boolean applied;
    public final float dxOrth;
    public final float dyOrth;

    public EdgeTranslation(float[] xs, float[] ys, boolean applied, float dxOrth, float dyOrth) {
      this.xs = xs;
      this.ys = ys;
      this.applied = applied;
      this.dxOrth = dxOrth;
      this.dyOrth = dyOrth;
    }
  }

  /**
   * Translate edge {@code edgeIndex} of the quadrilateral parallel to itself such that the edge's
   * orthogonal offset matches the orthogonal component of the vector from the original edge
   * midpoint {@code (m0x, m0y)} to the current touch position {@code (px, py)}.
   *
   * <p>The translation moves <strong>both</strong> endpoints A and B by the same vector {@code Δ =
   * ((P − M0) · n) · n}, where {@code n} is the unit normal to the original edge. Tangential finger
   * motion has no effect; the edge length and direction are preserved exactly.
   *
   * <p>If the resulting quadrilateral fails {@link #isQuadValid}, the move is rejected and the
   * input corners are returned unchanged.
   *
   * @param xs0 original corner x coordinates (size ≥ 4) — not modified.
   * @param ys0 original corner y coordinates (size ≥ 4) — not modified.
   * @param edgeIndex edge to translate (0..3, see {@link #findEdgeHit}).
   * @param m0x x of the edge's <em>original</em> midpoint (frozen at touch-down).
   * @param m0y y of the edge's original midpoint.
   * @param nx x component of the edge's <em>original</em> unit normal.
   * @param ny y component of the edge's original unit normal.
   * @param px current touch x (after view-matrix inversion).
   * @param py current touch y.
   * @return new corner arrays plus the applied delta; on rejection, {@code applied=false} and the
   *     original arrays are returned.
   */
  public static EdgeTranslation applyEdgeTranslation(
      float[] xs0,
      float[] ys0,
      int edgeIndex,
      float m0x,
      float m0y,
      float nx,
      float ny,
      float px,
      float py) {
    if (xs0 == null || ys0 == null || xs0.length < 4 || ys0.length < 4) {
      throw new IllegalArgumentException("xs/ys must have 4 entries");
    }
    if (edgeIndex < 0 || edgeIndex > 3) {
      throw new IllegalArgumentException("edgeIndex out of range: " + edgeIndex);
    }
    float d = (px - m0x) * nx + (py - m0y) * ny;
    float dx = d * nx;
    float dy = d * ny;

    int a = edgeIndex;
    int b = (edgeIndex + 1) % 4;

    float[] xs = new float[] {xs0[0], xs0[1], xs0[2], xs0[3]};
    float[] ys = new float[] {ys0[0], ys0[1], ys0[2], ys0[3]};
    xs[a] += dx;
    ys[a] += dy;
    xs[b] += dx;
    ys[b] += dy;

    // Reject if the quad becomes degenerate, self-intersecting, or flips its
    // orientation (the latter would correspond to the moving edge crossing the
    // opposite edge — the quad inverts but stays "simple").
    if (!isQuadValid(xs, ys) || signedArea(xs, ys) * signedArea(xs0, ys0) <= 0f) {
      return new EdgeTranslation(
          new float[] {xs0[0], xs0[1], xs0[2], xs0[3]},
          new float[] {ys0[0], ys0[1], ys0[2], ys0[3]},
          false,
          0f,
          0f);
    }
    return new EdgeTranslation(xs, ys, true, dx, dy);
  }

  /** Signed shoelace area of the quad (positive for one winding, negative for the other). */
  static float signedArea(float[] xs, float[] ys) {
    float sum = 0f;
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      sum += xs[i] * ys[j] - xs[j] * ys[i];
    }
    return 0.5f * sum;
  }

  /**
   * Compute the unit normal of edge {@code edgeIndex} pointing <strong>away</strong> from the
   * quadrilateral's interior (i.e. away from the opposite-edge midpoint).
   *
   * @return a 2-element array {@code [nx, ny]} with {@code nx² + ny² ≈ 1}. For a degenerate edge of
   *     zero length, returns {@code [0, 0]}.
   */
  public static float[] outwardUnitNormal(float[] xs, float[] ys, int edgeIndex) {
    int a = edgeIndex;
    int b = (edgeIndex + 1) % 4;
    int c = (edgeIndex + 2) % 4;
    int d = (edgeIndex + 3) % 4;

    float ex = xs[b] - xs[a];
    float ey = ys[b] - ys[a];
    float len = (float) Math.sqrt(ex * ex + ey * ey);
    if (len < 1e-6f) {
      return new float[] {0f, 0f};
    }
    // Two unit normals: rotate (ex, ey) by ±90°.
    float n1x = -ey / len;
    float n1y = ex / len;

    // Choose the one that points away from the opposite-edge midpoint
    // (centroid of corners c and d), i.e. has a positive dot product with
    // (midAB − midCD).
    float midABx = 0.5f * (xs[a] + xs[b]);
    float midABy = 0.5f * (ys[a] + ys[b]);
    float midCDx = 0.5f * (xs[c] + xs[d]);
    float midCDy = 0.5f * (ys[c] + ys[d]);
    float outx = midABx - midCDx;
    float outy = midABy - midCDy;

    float dot = n1x * outx + n1y * outy;
    if (dot >= 0f) {
      return new float[] {n1x, n1y};
    } else {
      return new float[] {-n1x, -n1y};
    }
  }

  /**
   * Check that the quadrilateral with corners {@code (xs[i], ys[i])} (TL, TR, BR, BL) is
   * non-degenerate: positive area, no self-intersection, and a minimum edge length.
   */
  public static boolean isQuadValid(float[] xs, float[] ys) {
    if (xs == null || ys == null || xs.length < 4 || ys.length < 4) {
      return false;
    }
    // Minimum edge length (1 px) — guards against fully collapsed quads.
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      float dx = xs[j] - xs[i];
      float dy = ys[j] - ys[i];
      if (dx * dx + dy * dy < 1f) {
        return false;
      }
    }
    // Signed area via the shoelace formula. A simple (non-self-intersecting)
    // quadrilateral has |area| > 0 and consistent winding; we additionally
    // require all four cross products around the polygon to share the same
    // sign (convex or at worst non-self-intersecting and consistently wound).
    int sign = 0;
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      int k = (i + 2) % 4;
      float v1x = xs[j] - xs[i];
      float v1y = ys[j] - ys[i];
      float v2x = xs[k] - xs[j];
      float v2y = ys[k] - ys[j];
      float cross = v1x * v2y - v1y * v2x;
      int s = (cross > 0f) ? 1 : (cross < 0f) ? -1 : 0;
      if (s == 0) {
        return false;
      }
      if (sign == 0) {
        sign = s;
      } else if (sign != s) {
        return false;
      }
    }
    return true;
  }

  /**
   * Soft-clamp an image-coordinate point to the image bounds expanded by {@code tol · imgW} (resp.
   * {@code tol · imgH}) on every side.
   *
   * <p>Use this in place of a strict view-rect clamp when corners are allowed to wander beyond the
   * visible image (off-screen corners, see §5.2).
   *
   * @param x image-x of the corner (pixels in the original bitmap).
   * @param y image-y of the corner.
   * @param imgW image width.
   * @param imgH image height.
   * @param tol fractional tolerance, e.g. {@link #IMG_OOB_TOL_DEFAULT}.
   * @return a 2-element array {@code [x', y']} clamped to the soft bounds.
   */
  public static float[] clampToImageBoundsSoft(
      float x, float y, float imgW, float imgH, float tol) {
    float minX = -tol * imgW;
    float maxX = imgW + tol * imgW;
    float minY = -tol * imgH;
    float maxY = imgH + tol * imgH;
    float cx = Math.max(minX, Math.min(maxX, x));
    float cy = Math.max(minY, Math.min(maxY, y));
    return new float[] {cx, cy};
  }
}
