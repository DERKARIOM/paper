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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.ui.crop.geom.RightAngleSnap;
import de.schliweb.makeacopy.ui.crop.geom.RightAngleSnap.Pt;
import de.schliweb.makeacopy.ui.crop.geom.RightAngleSnap.Result;
import org.junit.Test;

/**
 * JVM unit tests for {@link RightAngleSnap}, mirroring the scenarios listed in
 * {@code docs/fr72_edit_shape_from_export_concept.md} §5.7.
 */
public class RightAngleSnapTest {

  private static final double EPS = 1e-9;

  /**
   * Builds a near-rectangular convex quad with the moving corner at index 1 (top-right). The top
   * edge is (corner 0 → corner 1); we control its slope by setting corner 1's y.
   *
   * <p>Layout (cyclic):
   * <pre>
   *   0 (top-left)    1 (top-right, moving)
   *   3 (bottom-left) 2 (bottom-right)
   * </pre>
   */
  private static Pt[] baseQuad() {
    return new Pt[] {
      Pt.of(100, 100), // 0 top-left (fixed for the top edge)
      Pt.of(900, 100), // 1 top-right — placeholder, callers override via newX/newY
      Pt.of(900, 800), // 2 bottom-right
      Pt.of(100, 800) // 3 bottom-left
    };
  }

  /** Returns the y-offset (relative to corner 0's y) for an edge of given length and angle. */
  private static double dyForAngleDeg(double lengthX, double angleDeg) {
    return Math.tan(Math.toRadians(angleDeg)) * lengthX;
  }

  // ---------------------------------------------------------------------
  // §5.7: Edge with 0.5° tilt → snap to exactly 0°.
  // ---------------------------------------------------------------------
  @Test
  public void edgeWithHalfDegreeTilt_snapsToHorizontal() {
    Pt[] q = baseQuad();
    double newX = 900;
    double newY = 100 + dyForAngleDeg(900 - 100, 0.5); // small tilt downward

    Result r = RightAngleSnap.evaluate(q, /*movingIndex=*/ 1, newX, newY, false, false);

    // The previous edge (0→1) is the top edge; it should engage the snap.
    assertTrue("prev edge should snap", r.prevEdgeSnapped);
    // After snap, the top edge becomes horizontal: y equals corner 0's y (100).
    assertEquals(100.0, r.y, EPS);
    // X is preserved.
    assertEquals(newX, r.x, EPS);
  }

  // ---------------------------------------------------------------------
  // §5.7: Edge with 5° tilt → no snap.
  // ---------------------------------------------------------------------
  @Test
  public void edgeWithFiveDegreeTilt_doesNotSnap() {
    Pt[] q = baseQuad();
    double newX = 900;
    double newY = 100 + dyForAngleDeg(900 - 100, 5.0);

    Result r = RightAngleSnap.evaluate(q, 1, newX, newY, false, false);

    // The top edge (prev) is at 5° → above SNAP_ENTER_DEG (1.5°), so it must not engage. The
    // right edge (next) happens to be perfectly vertical here (Δ=0°) and does engage, but it
    // does not move the y component, so the slanted top edge remains slanted.
    assertFalse("prev edge must not snap at 5°", r.prevEdgeSnapped);
    assertEquals("y must not be flattened by the top-edge snap", newY, r.y, EPS);
  }

  // ---------------------------------------------------------------------
  // §5.7: Hysteresis — engage at 1°, hold at 2.5°, release at 4°.
  // ---------------------------------------------------------------------
  @Test
  public void hysteresis_engagesHoldsAndReleases() {
    Pt[] q = baseQuad();
    double xLen = 900 - 100;

    // Step 1: 1° → engages.
    double y1 = 100 + dyForAngleDeg(xLen, 1.0);
    Result r1 = RightAngleSnap.evaluate(q, 1, 900, y1, false, false);
    assertTrue("must engage at 1°", r1.prevEdgeSnapped);
    assertEquals(100.0, r1.y, EPS);

    // Step 2: 2.5° → still active because exit threshold is 3°.
    double y2 = 100 + dyForAngleDeg(xLen, 2.5);
    Result r2 = RightAngleSnap.evaluate(q, 1, 900, y2, r1.prevEdgeSnapped, r1.nextEdgeSnapped);
    assertTrue("must remain active at 2.5°", r2.prevEdgeSnapped);
    assertEquals(100.0, r2.y, EPS);

    // Step 3: 4° → releases (above SNAP_EXIT_DEG = 3°).
    double y3 = 100 + dyForAngleDeg(xLen, 4.0);
    Result r3 = RightAngleSnap.evaluate(q, 1, 900, y3, r2.prevEdgeSnapped, r2.nextEdgeSnapped);
    assertFalse("must release at 4°", r3.prevEdgeSnapped);
    assertEquals(y3, r3.y, EPS);
  }

  // ---------------------------------------------------------------------
  // §5.7: Convexity protection — snap that would self-intersect must be discarded.
  // ---------------------------------------------------------------------
  @Test
  public void convexityProtection_snapDiscardedWhenItBreaksQuad() {
    // Construct a degenerate setup: corner 1 is being dragged extremely close to corner 3
    // (the diagonally opposite corner). Snapping to corner 0's horizontal axis (y=100) would
    // yield a clearly non-convex / self-intersecting quad with corners arranged as
    // 0(100,100) - 1(snapped to 110,100) - 2(900,800) - 3(100,800).
    Pt[] q =
        new Pt[] {
          Pt.of(100, 100), Pt.of(900, 100), Pt.of(900, 800), Pt.of(100, 800),
        };
    // The drag positions corner 1 essentially on top of corner 0 with a tiny tilt that would
    // engage the snap. After snap it would coincide with (110, 100), which is left of corner 2
    // and produces a non-convex sequence relative to the bottom-right corner. To make the
    // self-intersection effect explicit, we instead simulate a pathological move where the
    // resulting quad becomes non-convex: drag corner 1 below the bottom edge.
    double newX = 110; // far to the left, near corner 0
    double newY = 100 + dyForAngleDeg(newX - 100, 0.5); // 0.5° tilt

    // First sanity-check: the un-snapped position would already form a valid convex quad
    // (corner 1 is just inside the rect). The snap, however, projects corner 1 onto y=100
    // at x=110 — which makes it almost coincide with corner 0's horizontal line very close to
    // it, but the bigger risk is the next test: cross-product sign must remain consistent. To
    // truly trigger self-intersection we move corner 1 across the diagonal: place it BELOW
    // corner 3's y so the cyclic order 0-1-2-3 becomes self-crossing after a horizontal snap.
    Pt[] crossing =
        new Pt[] {
          Pt.of(100, 100), Pt.of(900, 100), Pt.of(900, 800), Pt.of(100, 800),
        };
    double crossX = 50; // left of corner 0 → already odd
    double crossY = 100 + dyForAngleDeg(50 - 100, 0.5); // tiny tilt → would engage snap to y=100

    Result r = RightAngleSnap.evaluate(crossing, 1, crossX, crossY, false, false);
    // The snapped quad would be non-convex (corner 1 left of corner 0 on the same y line).
    // The util must therefore discard the snap and keep the original position with state bits
    // reset.
    assertFalse("snap must be discarded for non-convex result", r.prevEdgeSnapped);
    assertFalse(r.nextEdgeSnapped);
    assertEquals(crossX, r.x, EPS);
    assertEquals(crossY, r.y, EPS);

    // Use the unused 'q'/'newX'/'newY' values to avoid IDE noise.
    assertTrue(q.length == 4);
    assertTrue(newX > 0 && newY > 0);
  }

  // ---------------------------------------------------------------------
  // §5.7: Vertical-axis (90°) symmetric to horizontal — drag of corner 1 along the right edge.
  // ---------------------------------------------------------------------
  @Test
  public void verticalAxis_snapsToVertical() {
    // Move corner 2 (bottom-right). Its prev edge is corner 1 → corner 2, which is the right
    // edge of the quad. We tilt that edge by 0.5° from vertical.
    Pt[] q = baseQuad();
    double yLen = 800 - 100;
    double dx = Math.tan(Math.toRadians(0.5)) * yLen;
    double newX = 900 + dx;
    double newY = 800;

    Result r = RightAngleSnap.evaluate(q, /*movingIndex=*/ 2, newX, newY, false, false);

    assertTrue("prev edge (right) should snap to vertical", r.prevEdgeSnapped);
    // After snap, corner 2's x equals corner 1's x (900), making the right edge vertical.
    assertEquals(900.0, r.x, EPS);
    assertEquals(newY, r.y, EPS);
  }

  // ---------------------------------------------------------------------
  // Determinism — same inputs yield same outputs.
  // ---------------------------------------------------------------------
  @Test
  public void determinism_sameInputsYieldSameOutputs() {
    Pt[] q = baseQuad();
    double newX = 900;
    double newY = 100 + dyForAngleDeg(800, 0.7);

    Result a = RightAngleSnap.evaluate(q, 1, newX, newY, false, false);
    Result b = RightAngleSnap.evaluate(q, 1, newX, newY, false, false);

    assertEquals(a.x, b.x, EPS);
    assertEquals(a.y, b.y, EPS);
    assertEquals(a.prevEdgeSnapped, b.prevEdgeSnapped);
    assertEquals(a.nextEdgeSnapped, b.nextEdgeSnapped);
  }
}
