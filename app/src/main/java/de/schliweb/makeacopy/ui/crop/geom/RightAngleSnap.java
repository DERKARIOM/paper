/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop.geom;

/**
 * Pure-geometry utility for the "Snap-to-Right-Angle" feature in the crop view.
 *
 * <p>This class is deliberately Android-framework free so that it can be exercised by JVM unit
 * tests. It operates on a quadrilateral defined by 4 corners (in the cyclic order 0-1-2-3) and the
 * two edges adjacent to a moving corner.
 *
 * <p>Specification — see {@code docs/fr72_edit_shape_from_export_concept.md} §5:
 *
 * <ul>
 *   <li>Per adjacent edge of the moving corner, compute the angular deviation to the nearest axis
 *       (0° = horizontal, 90° = vertical).
 *   <li>If the snap is currently inactive and {@code |Δ| ≤ SNAP_ENTER_DEG}, activate it for that
 *       edge and project the moving corner onto the chosen axis through the fixed (other) corner of
 *       the edge so that the edge becomes exactly axis-parallel.
 *   <li>If the snap is active and {@code |Δ| > SNAP_EXIT_DEG}, deactivate it and keep the original
 *       (non-snapped) candidate position (hysteresis).
 *   <li>After a tentative snap the resulting quadrilateral is validated for convexity and absence
 *       of self-intersection. If validation fails, the snap is discarded.
 * </ul>
 */
public final class RightAngleSnap {

  /** Angular tolerance (degrees) below which a near-axis edge engages the snap. */
  public static final float SNAP_ENTER_DEG = 1.5f;

  /** Angular tolerance (degrees) above which an active snap is released. */
  public static final float SNAP_EXIT_DEG = 3.0f;

  private RightAngleSnap() {
    // utility
  }

  /** A 2D point with double precision. Independent of the Android framework. */
  public static final class Pt {
    public final double x;
    public final double y;

    public Pt(double x, double y) {
      this.x = x;
      this.y = y;
    }

    public static Pt of(double x, double y) {
      return new Pt(x, y);
    }
  }

  /**
   * Result of a snap evaluation: the (possibly adjusted) corner position plus the updated state
   * bits for the two adjacent edges (prevEdge = corner-1 → corner, nextEdge = corner → corner+1).
   */
  public static final class Result {
    /** Adjusted x-coordinate of the moving corner (snapped or original). */
    public final double x;

    /** Adjusted y-coordinate of the moving corner (snapped or original). */
    public final double y;

    /** Whether the previous edge (corner-1, corner) is currently snap-active. */
    public final boolean prevEdgeSnapped;

    /** Whether the next edge (corner, corner+1) is currently snap-active. */
    public final boolean nextEdgeSnapped;

    public Result(double x, double y, boolean prevEdgeSnapped, boolean nextEdgeSnapped) {
      this.x = x;
      this.y = y;
      this.prevEdgeSnapped = prevEdgeSnapped;
      this.nextEdgeSnapped = nextEdgeSnapped;
    }
  }

  /**
   * Evaluates the snap for a single corner-drag step.
   *
   * @param corners the current 4 corners in cyclic order (the array is not modified)
   * @param movingIndex index of the corner being moved (0..3)
   * @param newX desired new x of the moving corner (already clamped, in view px)
   * @param newY desired new y of the moving corner
   * @param prevEdgeActive previous snap state for the prev edge (corner-1, corner)
   * @param nextEdgeActive previous snap state for the next edge (corner, corner+1)
   * @return adjusted position + new state bits
   */
  public static Result evaluate(
      Pt[] corners,
      int movingIndex,
      double newX,
      double newY,
      boolean prevEdgeActive,
      boolean nextEdgeActive) {
    if (corners == null || corners.length != 4) {
      return new Result(newX, newY, false, false);
    }
    if (movingIndex < 0 || movingIndex >= 4) {
      return new Result(newX, newY, prevEdgeActive, nextEdgeActive);
    }

    final int prev = (movingIndex + 3) & 3;
    final int next = (movingIndex + 1) & 3;
    final Pt prevFixed = corners[prev];
    final Pt nextFixed = corners[next];

    // Hysteresis decision per edge: returns desired active flag for THIS step.
    EdgeDecision prevDec = decide(prevFixed, newX, newY, prevEdgeActive);
    EdgeDecision nextDec = decide(nextFixed, newX, newY, nextEdgeActive);

    double cx = newX;
    double cy = newY;

    // Apply snap projection for whichever edges decided to be active. The two adjacent edges of a
    // corner usually run on different axes (one horizontal, one vertical); their projections then
    // touch independent coordinates (y vs. x) and can be combined. Only when both edges want to
    // snap to the SAME axis (degenerate corner geometry) we pick the smaller deviation.
    boolean prevHorizontal = isProjectionHorizontal(prevFixed, newX, newY);
    boolean nextHorizontal = isProjectionHorizontal(nextFixed, newX, newY);
    if (prevDec.active && nextDec.active && prevHorizontal == nextHorizontal) {
      double prevDelta = minAxisDeltaDeg(prevFixed, newX, newY);
      double nextDelta = minAxisDeltaDeg(nextFixed, newX, newY);
      if (prevDelta <= nextDelta) {
        nextDec = new EdgeDecision(false, nextDec.deltaDeg);
      } else {
        prevDec = new EdgeDecision(false, prevDec.deltaDeg);
      }
    }
    if (prevDec.active) {
      double[] p = projectOntoNearestAxis(prevFixed, cx, cy);
      cx = p[0];
      cy = p[1];
    }
    if (nextDec.active) {
      double[] p = projectOntoNearestAxis(nextFixed, cx, cy);
      cx = p[0];
      cy = p[1];
    }

    // Convexity / self-intersection guard. If the snap candidate breaks the quad, discard it.
    if (prevDec.active || nextDec.active) {
      Pt[] cand = new Pt[4];
      for (int i = 0; i < 4; i++) cand[i] = corners[i];
      cand[movingIndex] = new Pt(cx, cy);
      if (!isSimpleConvexQuad(cand)) {
        // discard snap, but state bits also reset because the geometry forbids it
        return new Result(newX, newY, false, false);
      }
    }

    return new Result(cx, cy, prevDec.active, nextDec.active);
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private static final class EdgeDecision {
    final boolean active;
    final double deltaDeg;

    EdgeDecision(boolean active, double deltaDeg) {
      this.active = active;
      this.deltaDeg = deltaDeg;
    }
  }

  private static EdgeDecision decide(Pt fixed, double mx, double my, boolean wasActive) {
    double delta = minAxisDeltaDeg(fixed, mx, my);
    if (Double.isNaN(delta)) {
      return new EdgeDecision(false, 90.0); // degenerate edge
    }
    boolean active;
    if (wasActive) {
      // stay active until exit threshold is exceeded
      active = delta <= SNAP_EXIT_DEG;
    } else {
      active = delta <= SNAP_ENTER_DEG;
    }
    return new EdgeDecision(active, delta);
  }

  /**
   * Returns the minimum angular distance (in degrees, in [0, 45]) of the edge from {@code fixed} to
   * {@code (mx, my)} to the nearest axis (horizontal or vertical).
   */
  static double minAxisDeltaDeg(Pt fixed, double mx, double my) {
    double dx = mx - fixed.x;
    double dy = my - fixed.y;
    if (dx == 0.0 && dy == 0.0) {
      return Double.NaN;
    }
    // Angle to horizontal axis, normalised into [0, 90].
    double angHoriz = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
    // Distance to nearest axis (horizontal: 0°, vertical: 90°)
    return Math.min(angHoriz, 90.0 - angHoriz);
  }

  /**
   * Projects {@code (mx, my)} onto the axis through {@code fixed} that is closer to the current
   * edge direction. Snapping to the horizontal axis means equal y; snapping to the vertical axis
   * means equal x.
   */
  static double[] projectOntoNearestAxis(Pt fixed, double mx, double my) {
    if (isProjectionHorizontal(fixed, mx, my)) {
      // edge is more horizontal → flatten dy
      return new double[] {mx, fixed.y};
    } else {
      // edge is more vertical → flatten dx
      return new double[] {fixed.x, my};
    }
  }

  /** Returns true iff snapping the edge {@code fixed → (mx,my)} flattens it onto the x-axis. */
  static boolean isProjectionHorizontal(Pt fixed, double mx, double my) {
    return Math.abs(mx - fixed.x) >= Math.abs(my - fixed.y);
  }

  /**
   * Validates that the 4 points (in cyclic order) form a simple convex quadrilateral, i.e. all
   * cross-products of successive edges have the same sign and there is no self-intersection.
   */
  static boolean isSimpleConvexQuad(Pt[] q) {
    if (q == null || q.length != 4) return false;
    int sign = 0;
    for (int i = 0; i < 4; i++) {
      Pt a = q[i];
      Pt b = q[(i + 1) & 3];
      Pt c = q[(i + 2) & 3];
      double cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
      if (cross == 0.0) {
        // collinear triple → degenerate
        return false;
      }
      int s = cross > 0 ? 1 : -1;
      if (sign == 0) sign = s;
      else if (sign != s) return false;
    }
    // Convex with consistent orientation implies simple (no self-intersection) for a quad.
    return true;
  }
}
