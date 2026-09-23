/*
 * Copyright (c) 2024-2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM unit tests for {@link CropEdgeGeometry}.
 *
 * <p>Covers: edge hit-test (priority by perpendicular distance, end dead-zone),
 * parallel edge translation (length preserved, tangential motion ignored,
 * invalid result rolled back), outward normal direction, soft clamp.
 *
 * <p>See {@code docs/edge_drag_pan_zoom_concept.md} (§3, §5).
 */
public class CropEdgeGeometryTest {

  private static final float EPS = 1e-3f;

  /** Axis-aligned rectangle 100×60 at origin (TL=(0,0), TR=(100,0), BR=(100,60), BL=(0,60)). */
  private static float[][] rect() {
    return new float[][] {
      {0f, 100f, 100f, 0f}, // xs
      {0f, 0f, 60f, 60f} // ys
    };
  }

  // ---------------------------------------------------------------------------
  // projectOntoSegment
  // ---------------------------------------------------------------------------

  @Test
  public void projectOntoSegment_perpendicularDistanceAndT() {
    // Segment from (0,0) to (100,0), point at (25, 5):
    // expected t = 0.25, perpDist = 5
    CropEdgeGeometry.Projection p =
        CropEdgeGeometry.projectOntoSegment(25f, 5f, 0f, 0f, 100f, 0f);
    assertEquals(0.25f, p.t, EPS);
    assertEquals(5f, p.perpDist, EPS);
  }

  @Test
  public void projectOntoSegment_clampsTOutsideSegment() {
    // Point past the B end of the segment.
    CropEdgeGeometry.Projection p =
        CropEdgeGeometry.projectOntoSegment(150f, 0f, 0f, 0f, 100f, 0f);
    assertEquals(1f, p.t, EPS);
    assertEquals(50f, p.perpDist, EPS);
  }

  @Test
  public void projectOntoSegment_degenerateSegmentReturnsDistanceFromA() {
    CropEdgeGeometry.Projection p = CropEdgeGeometry.projectOntoSegment(3f, 4f, 0f, 0f, 0f, 0f);
    assertEquals(0f, p.t, EPS);
    assertEquals(5f, p.perpDist, EPS);
  }

  // ---------------------------------------------------------------------------
  // findEdgeHit
  // ---------------------------------------------------------------------------

  @Test
  public void findEdgeHit_topEdgeMid() {
    float[][] q = rect();
    // Tap at (50, 4) — 4 px below the top edge, well inside the dead-zone.
    int idx =
        CropEdgeGeometry.findEdgeHit(q[0], q[1], 50f, 4f, /*radius*/ 24f, /*deadzone*/ 0.15f);
    assertEquals("Top edge expected", 0, idx);
  }

  @Test
  public void findEdgeHit_rightEdge() {
    float[][] q = rect();
    int idx = CropEdgeGeometry.findEdgeHit(q[0], q[1], 96f, 30f, 24f, 0.15f);
    assertEquals(1, idx);
  }

  @Test
  public void findEdgeHit_outsideRadiusReturnsMinusOne() {
    float[][] q = rect();
    // (50, 30) is the centroid — far from every edge.
    int idx = CropEdgeGeometry.findEdgeHit(q[0], q[1], 50f, 30f, 24f, 0.15f);
    assertEquals(-1, idx);
  }

  @Test
  public void findEdgeHit_insideDeadzoneReturnsMinusOne() {
    float[][] q = rect();
    // Tap very close to the TL corner (t≈0.05 along top edge) → dead-zone.
    int idx = CropEdgeGeometry.findEdgeHit(q[0], q[1], 5f, 4f, 24f, 0.15f);
    assertEquals(-1, idx);
  }

  @Test
  public void findEdgeHit_picksClosestEdgeOnAmbiguity() {
    float[][] q = rect();
    // Tap at (3, 30) — 3 px from the left edge, 97 px from the right edge.
    int idx = CropEdgeGeometry.findEdgeHit(q[0], q[1], 3f, 30f, 24f, 0.15f);
    assertEquals("Left edge", 3, idx);
  }

  @Test
  public void findEdgeHit_invalidInputReturnsMinusOne() {
    assertEquals(-1, CropEdgeGeometry.findEdgeHit(null, null, 0f, 0f, 24f, 0.15f));
    assertEquals(
        -1,
        CropEdgeGeometry.findEdgeHit(new float[] {0f}, new float[] {0f}, 0f, 0f, 24f, 0.15f));
  }

  // ---------------------------------------------------------------------------
  // outwardUnitNormal
  // ---------------------------------------------------------------------------

  @Test
  public void outwardUnitNormal_topEdgePointsUp() {
    float[][] q = rect();
    float[] n = CropEdgeGeometry.outwardUnitNormal(q[0], q[1], 0);
    // Top edge interior is below, so outward normal must point up (negative y).
    assertEquals(0f, n[0], EPS);
    assertEquals(-1f, n[1], EPS);
  }

  @Test
  public void outwardUnitNormal_bottomEdgePointsDown() {
    float[][] q = rect();
    float[] n = CropEdgeGeometry.outwardUnitNormal(q[0], q[1], 2);
    assertEquals(0f, n[0], EPS);
    assertEquals(1f, n[1], EPS);
  }

  @Test
  public void outwardUnitNormal_leftAndRight() {
    float[][] q = rect();
    float[] left = CropEdgeGeometry.outwardUnitNormal(q[0], q[1], 3);
    float[] right = CropEdgeGeometry.outwardUnitNormal(q[0], q[1], 1);
    assertEquals(-1f, left[0], EPS);
    assertEquals(0f, left[1], EPS);
    assertEquals(1f, right[0], EPS);
    assertEquals(0f, right[1], EPS);
  }

  @Test
  public void outwardUnitNormal_isUnitLength() {
    float[][] q = rect();
    for (int i = 0; i < 4; i++) {
      float[] n = CropEdgeGeometry.outwardUnitNormal(q[0], q[1], i);
      float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1]);
      assertEquals("edge " + i, 1f, len, EPS);
    }
  }

  // ---------------------------------------------------------------------------
  // applyEdgeTranslation
  // ---------------------------------------------------------------------------

  @Test
  public void applyEdgeTranslation_topEdgeMovesParallel() {
    float[][] q = rect();
    float[] xs = q[0];
    float[] ys = q[1];
    float m0x = 0.5f * (xs[0] + xs[1]); // 50
    float m0y = 0.5f * (ys[0] + ys[1]); // 0
    float[] n = CropEdgeGeometry.outwardUnitNormal(xs, ys, 0); // (0, -1)

    // Touch moves to (50, -10) → orthogonal component 10 in the outward direction.
    CropEdgeGeometry.EdgeTranslation res =
        CropEdgeGeometry.applyEdgeTranslation(xs, ys, 0, m0x, m0y, n[0], n[1], 50f, -10f);
    assertTrue(res.applied);
    // Both top corners move up by 10; bottom corners stay.
    assertEquals(-10f, res.ys[0], EPS);
    assertEquals(-10f, res.ys[1], EPS);
    assertEquals(60f, res.ys[2], EPS);
    assertEquals(60f, res.ys[3], EPS);
    // X coordinates unchanged for an axis-aligned edge with vertical normal.
    assertEquals(0f, res.xs[0], EPS);
    assertEquals(100f, res.xs[1], EPS);
    // Edge length preserved.
    float lenBefore = 100f;
    float lenAfter =
        (float)
            Math.sqrt(
                Math.pow(res.xs[1] - res.xs[0], 2) + Math.pow(res.ys[1] - res.ys[0], 2));
    assertEquals(lenBefore, lenAfter, EPS);
  }

  @Test
  public void applyEdgeTranslation_tangentialMotionHasNoEffect() {
    float[][] q = rect();
    float[] xs = q[0];
    float[] ys = q[1];
    float m0x = 50f;
    float m0y = 0f;
    float[] n = CropEdgeGeometry.outwardUnitNormal(xs, ys, 0); // (0, -1)

    // Touch slides along the edge: only x changes, no orthogonal component.
    CropEdgeGeometry.EdgeTranslation res =
        CropEdgeGeometry.applyEdgeTranslation(xs, ys, 0, m0x, m0y, n[0], n[1], 80f, 0f);
    assertTrue(res.applied);
    assertEquals(0f, res.dxOrth, EPS);
    assertEquals(0f, res.dyOrth, EPS);
    assertArrayEquals(xs, res.xs, EPS);
    assertArrayEquals(ys, res.ys, EPS);
  }

  @Test
  public void applyEdgeTranslation_collapseIsRolledBack() {
    float[][] q = rect();
    float[] xs = q[0];
    float[] ys = q[1];
    float[] n = CropEdgeGeometry.outwardUnitNormal(xs, ys, 0); // (0, -1)

    // Pull top edge way past bottom edge → would invert/collapse.
    CropEdgeGeometry.EdgeTranslation res =
        CropEdgeGeometry.applyEdgeTranslation(xs, ys, 0, 50f, 0f, n[0], n[1], 50f, 200f);
    assertFalse(res.applied);
    assertArrayEquals(xs, res.xs, 0f);
    assertArrayEquals(ys, res.ys, 0f);
  }

  @Test(expected = IllegalArgumentException.class)
  public void applyEdgeTranslation_invalidEdgeIndexThrows() {
    float[][] q = rect();
    CropEdgeGeometry.applyEdgeTranslation(q[0], q[1], 7, 0f, 0f, 1f, 0f, 0f, 0f);
  }

  // ---------------------------------------------------------------------------
  // isQuadValid
  // ---------------------------------------------------------------------------

  @Test
  public void isQuadValid_acceptsSimpleRectangle() {
    float[][] q = rect();
    assertTrue(CropEdgeGeometry.isQuadValid(q[0], q[1]));
  }

  @Test
  public void isQuadValid_rejectsCollapsedEdge() {
    float[] xs = {0f, 0f, 100f, 0f};
    float[] ys = {0f, 0f, 60f, 60f};
    assertFalse(CropEdgeGeometry.isQuadValid(xs, ys));
  }

  @Test
  public void isQuadValid_rejectsSelfIntersecting() {
    // Bowtie: TL/BR swapped with TR/BL → crossing diagonals.
    float[] xs = {0f, 100f, 0f, 100f};
    float[] ys = {0f, 60f, 60f, 0f};
    assertFalse(CropEdgeGeometry.isQuadValid(xs, ys));
  }

  // ---------------------------------------------------------------------------
  // clampToImageBoundsSoft
  // ---------------------------------------------------------------------------

  @Test
  public void clampToImageBoundsSoft_insideStaysUnchanged() {
    float[] r =
        CropEdgeGeometry.clampToImageBoundsSoft(
            10f, 10f, 100f, 60f, CropEdgeGeometry.IMG_OOB_TOL_DEFAULT);
    assertEquals(10f, r[0], EPS);
    assertEquals(10f, r[1], EPS);
  }

  @Test
  public void clampToImageBoundsSoft_slightlyOutsideStaysUnchangedWithinTolerance() {
    // tol=0.25 → x can go down to -25, up to 125; y to -15..75.
    float[] r =
        CropEdgeGeometry.clampToImageBoundsSoft(
            -10f, 70f, 100f, 60f, CropEdgeGeometry.IMG_OOB_TOL_DEFAULT);
    assertEquals(-10f, r[0], EPS);
    assertEquals(70f, r[1], EPS);
  }

  @Test
  public void clampToImageBoundsSoft_clampsBeyondTolerance() {
    float[] r =
        CropEdgeGeometry.clampToImageBoundsSoft(-200f, 500f, 100f, 60f, 0.25f);
    assertEquals(-25f, r[0], EPS);
    assertEquals(75f, r[1], EPS);
  }

  @Test
  public void clampToImageBoundsSoft_zeroToleranceMatchesStrictBounds() {
    float[] r = CropEdgeGeometry.clampToImageBoundsSoft(-5f, 70f, 100f, 60f, 0f);
    assertEquals(0f, r[0], EPS);
    assertEquals(60f, r[1], EPS);
  }
}
