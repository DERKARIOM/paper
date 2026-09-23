/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.framing;

import android.graphics.PointF;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Utility class for determining whether a detected quad is plausible as a document.
 *
 * <p>This implements "Signal A" from the accessibility concept: hard geometric gates that must pass
 * before any distance hints or READY state can be signaled.
 *
 * <p>A quad is considered plausible if ALL of the following conditions are met:
 *
 * <ul>
 *   <li>Convexity: the quad forms a convex polygon
 *   <li>No self-intersection: edges do not cross each other
 *   <li>Minimum size: quad covers at least MIN_AREA_RATIO of the image
 *   <li>Out-of-bounds limit: corners are not too far outside the image
 *   <li>Aspect sanity: aspect ratio is within reasonable bounds
 * </ul>
 */
@UtilityClass
public class QuadPlausibility {

  // Configuration parameters (from concept spec-sheet section 10.1)

  /** Quad must cover at least 6% of image area */
  public static final float MIN_AREA_RATIO = 0.06f;

  /** Minimum edge length in pixels (for scaled space) */
  public static final float MIN_EDGE_PX = 24f;

  /** Tolerance for out-of-bounds per corner (pixels) */
  public static final float OOB_TOL_PX = 8f;

  /** Maximum sum of out-of-bounds across all corners */
  public static final float OOB_SUM_MAX = 40f;

  /** Maximum aspect ratio (width/height or height/width) */
  public static final float ASPECT_LIKE_MAX = 6.0f;

  /** Result of plausibility check with detailed information for debugging. */
  public static class Result {
    public final boolean plausible;
    public final boolean isConvex;
    public final boolean noSelfIntersection;
    public final boolean meetsMinArea;
    public final boolean withinBounds;
    public final boolean aspectOk;
    public final float areaRatio;
    public final float oobSum;
    public final float aspectRatio;

    public Result(
        boolean plausible,
        boolean isConvex,
        boolean noSelfIntersection,
        boolean meetsMinArea,
        boolean withinBounds,
        boolean aspectOk,
        float areaRatio,
        float oobSum,
        float aspectRatio) {
      this.plausible = plausible;
      this.isConvex = isConvex;
      this.noSelfIntersection = noSelfIntersection;
      this.meetsMinArea = meetsMinArea;
      this.withinBounds = withinBounds;
      this.aspectOk = aspectOk;
      this.areaRatio = areaRatio;
      this.oobSum = oobSum;
      this.aspectRatio = aspectRatio;
    }

    @Override
    public String toString() {
      return "PlausibilityResult{"
          + "plausible="
          + plausible
          + ", isConvex="
          + isConvex
          + ", noSelfIntersection="
          + noSelfIntersection
          + ", meetsMinArea="
          + meetsMinArea
          + ", withinBounds="
          + withinBounds
          + ", aspectOk="
          + aspectOk
          + ", areaRatio="
          + String.format(Locale.ROOT, "%.3f", areaRatio)
          + ", oobSum="
          + String.format(Locale.ROOT, "%.1f", oobSum)
          + ", aspectRatio="
          + String.format(Locale.ROOT, "%.2f", aspectRatio)
          + '}';
    }
  }

  /**
   * Check if a quad is plausible as a document.
   *
   * @param quad 4 points in order TL, TR, BR, BL (upright image space)
   * @param imageWidth width of the image in pixels
   * @param imageHeight height of the image in pixels
   * @return Result with plausibility flag and detailed metrics
   */
  public static Result check(PointF[] quad, int imageWidth, int imageHeight) {
    // Default: not plausible if quad is invalid
    if (quad == null || quad.length != 4 || imageWidth <= 0 || imageHeight <= 0) {
      return new Result(false, false, false, false, false, false, 0f, 0f, 0f);
    }

    // Check for null points
    for (PointF p : quad) {
      if (p == null) {
        return new Result(false, false, false, false, false, false, 0f, 0f, 0f);
      }
    }

    // 1. Convexity check
    boolean isConvex = isConvex(quad);

    // 2. Self-intersection check
    boolean noSelfIntersection = !selfIntersects(quad);

    // 3. Minimum area check
    float imageArea = (float) imageWidth * imageHeight;
    float quadArea = Math.abs(shoelaceArea(quad));
    float areaRatio = quadArea / imageArea;
    boolean meetsMinArea = areaRatio >= MIN_AREA_RATIO;

    // Also check minimum edge length
    float minEdge = minEdgeLength(quad);
    if (minEdge < MIN_EDGE_PX) {
      meetsMinArea = false;
    }

    // 4. Out-of-bounds check
    float oobSum = computeOobSum(quad, imageWidth, imageHeight, OOB_TOL_PX);
    boolean withinBounds = oobSum <= OOB_SUM_MAX;

    // 5. Aspect ratio sanity
    float aspectRatio = computeAspectRatio(quad);
    boolean aspectOk = aspectRatio <= ASPECT_LIKE_MAX;

    // All conditions must be met
    boolean plausible = isConvex && noSelfIntersection && meetsMinArea && withinBounds && aspectOk;

    return new Result(
        plausible,
        isConvex,
        noSelfIntersection,
        meetsMinArea,
        withinBounds,
        aspectOk,
        areaRatio,
        oobSum,
        aspectRatio);
  }

  /** Simple plausibility check returning only boolean. */
  public static boolean isPlausible(PointF[] quad, int imageWidth, int imageHeight) {
    return check(quad, imageWidth, imageHeight).plausible;
  }

  /**
   * Check if a quadrilateral is convex using cross product signs. Points should be in order (either
   * CW or CCW).
   */
  static boolean isConvex(PointF[] quad) {
    if (quad == null || quad.length != 4) return false;

    // Calculate cross products for each consecutive triple of points
    // All cross products should have the same sign for a convex polygon
    Boolean positive = null;
    for (int i = 0; i < 4; i++) {
      PointF a = quad[i];
      PointF b = quad[(i + 1) % 4];
      PointF c = quad[(i + 2) % 4];

      float cross = crossProduct(a, b, c);
      if (Math.abs(cross) < 1e-6f) {
        // Collinear points - still could be valid but degenerate
        continue;
      }

      boolean isPositive = cross > 0;
      if (positive == null) {
        positive = isPositive;
      } else if (positive != isPositive) {
        return false; // Sign changed - not convex
      }
    }
    return true;
  }

  /** Check if any edges of the quad intersect each other (excluding adjacent edges). */
  static boolean selfIntersects(PointF[] quad) {
    if (quad == null || quad.length != 4) return true;

    // Check if edge 0-1 intersects edge 2-3
    if (segmentsIntersect(quad[0], quad[1], quad[2], quad[3])) return true;

    // Check if edge 1-2 intersects edge 3-0
    if (segmentsIntersect(quad[1], quad[2], quad[3], quad[0])) return true;

    return false;
  }

  /** Calculate the area of a polygon using the shoelace formula. */
  static float shoelaceArea(PointF[] quad) {
    if (quad == null || quad.length != 4) return 0f;

    float sum = 0f;
    for (int i = 0; i < 4; i++) {
      PointF current = quad[i];
      PointF next = quad[(i + 1) % 4];
      sum += (current.x * next.y) - (next.x * current.y);
    }
    return sum / 2f;
  }

  /** Calculate the minimum edge length of the quad. */
  static float minEdgeLength(PointF[] quad) {
    if (quad == null || quad.length != 4) return 0f;

    float minLen = Float.MAX_VALUE;
    for (int i = 0; i < 4; i++) {
      PointF a = quad[i];
      PointF b = quad[(i + 1) % 4];
      float len = distance(a, b);
      if (len < minLen) minLen = len;
    }
    return minLen;
  }

  /**
   * Compute the sum of out-of-bounds distances for all corners. A corner is out-of-bounds if it's
   * more than tolerance pixels outside the image.
   */
  static float computeOobSum(PointF[] quad, int imageWidth, int imageHeight, float tolerance) {
    if (quad == null || quad.length != 4) return Float.MAX_VALUE;

    float sum = 0f;
    for (PointF p : quad) {
      // Left boundary
      if (p.x < -tolerance) sum += Math.abs(p.x + tolerance);
      // Right boundary
      if (p.x > imageWidth + tolerance) sum += p.x - imageWidth - tolerance;
      // Top boundary
      if (p.y < -tolerance) sum += Math.abs(p.y + tolerance);
      // Bottom boundary
      if (p.y > imageHeight + tolerance) sum += p.y - imageHeight - tolerance;
    }
    return sum;
  }

  /**
   * Compute the aspect ratio of the quad (max of width/height and height/width). Uses average of
   * opposite edge lengths for width and height estimation.
   */
  static float computeAspectRatio(PointF[] quad) {
    if (quad == null || quad.length != 4) return Float.MAX_VALUE;

    // TL=0, TR=1, BR=2, BL=3
    float top = distance(quad[0], quad[1]);
    float bottom = distance(quad[3], quad[2]);
    float left = distance(quad[0], quad[3]);
    float right = distance(quad[1], quad[2]);

    float avgWidth = (top + bottom) / 2f;
    float avgHeight = (left + right) / 2f;

    if (avgWidth < 1f || avgHeight < 1f) return Float.MAX_VALUE;

    float ratio = avgWidth / avgHeight;
    // Return the larger ratio (either width/height or height/width)
    return ratio > 1f ? ratio : 1f / ratio;
  }

  // --- Helper methods ---

  private static float crossProduct(PointF a, PointF b, PointF c) {
    // Vector AB x Vector BC
    float abx = b.x - a.x;
    float aby = b.y - a.y;
    float bcx = c.x - b.x;
    float bcy = c.y - b.y;
    return abx * bcy - aby * bcx;
  }

  private static float distance(PointF a, PointF b) {
    float dx = b.x - a.x;
    float dy = b.y - a.y;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  /** Check if two line segments intersect (proper intersection, not touching at endpoints). */
  private static boolean segmentsIntersect(PointF p1, PointF p2, PointF p3, PointF p4) {
    float d1 = direction(p3, p4, p1);
    float d2 = direction(p3, p4, p2);
    float d3 = direction(p1, p2, p3);
    float d4 = direction(p1, p2, p4);

    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true;
    }

    // Check for collinear cases
    if (d1 == 0 && onSegment(p3, p4, p1)) return true;
    if (d2 == 0 && onSegment(p3, p4, p2)) return true;
    if (d3 == 0 && onSegment(p1, p2, p3)) return true;
    if (d4 == 0 && onSegment(p1, p2, p4)) return true;

    return false;
  }

  private static float direction(PointF pi, PointF pj, PointF pk) {
    return (pk.x - pi.x) * (pj.y - pi.y) - (pj.x - pi.x) * (pk.y - pi.y);
  }

  private static boolean onSegment(PointF pi, PointF pj, PointF pk) {
    return Math.min(pi.x, pj.x) <= pk.x
        && pk.x <= Math.max(pi.x, pj.x)
        && Math.min(pi.y, pj.y) <= pk.y
        && pk.y <= Math.max(pi.y, pj.y);
  }
}
