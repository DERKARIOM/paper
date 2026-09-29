package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuadGeometryTest {

  @Test
  public void orderClockwiseStartsTopLeftWhateverTheInputOrder() {
    double[][] shuffled = {{400, 500}, {100, 100}, {100, 500}, {400, 100}};
    double[][] q = QuadGeometry.orderClockwise(shuffled);
    assertArrayEquals(new double[] {100, 100}, q[0], 1e-9);
    assertArrayEquals(new double[] {400, 100}, q[1], 1e-9);
    assertArrayEquals(new double[] {400, 500}, q[2], 1e-9);
    assertArrayEquals(new double[] {100, 500}, q[3], 1e-9);
  }

  @Test
  public void plausibilityRejectsDegenerateShapes() {
    int w = 540, h = 720;
    double[][] page = {{100, 100}, {400, 110}, {420, 600}, {90, 590}};
    assertTrue(QuadGeometry.plausibility(page, w, h) > 0.7);
    double[][] sliver = {{100, 100}, {400, 100}, {400, 118}, {100, 118}}; // area < 3%
    assertEquals(0.0, QuadGeometry.plausibility(sliver, w, h), 0);
    double[][] kite = {{100, 300}, {300, 100}, {500, 300}, {300, 320}}; // flat corner
    assertEquals(0.0, QuadGeometry.plausibility(kite, w, h), 0);
    double[][] bowtie = {{100, 100}, {400, 500}, {400, 100}, {100, 500}};
    assertFalse(QuadGeometry.isConvex(bowtie));
  }

  @Test
  public void borderFractionDetectsOutlinesGluedToTheFrame() {
    double[][] frame = {{0, 0}, {539, 0}, {539, 719}, {0, 719}};
    assertTrue(QuadGeometry.borderFraction(frame, 540, 720, 3) > 0.95);
    double[][] inside = {{100, 100}, {400, 100}, {400, 600}, {100, 600}};
    assertEquals(0.0, QuadGeometry.borderFraction(inside, 540, 720, 3), 0);
  }
}
