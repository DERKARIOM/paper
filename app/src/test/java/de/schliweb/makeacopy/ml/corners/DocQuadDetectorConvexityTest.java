/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link DocQuadDetector#isConvexTLTRBRBL(double[][])}.
 *
 * <p>The check guards the live-detection pipeline against "cold start" frames where the model has
 * not yet locked onto the document and returns a degenerate (self-intersecting or wrongly ordered)
 * quad — such as the real-device sample
 * {@code TL=(512.5,47.2) TR=(277.0,370.7) BR=(304.5,334.6) BL=(486.6,5.0)} that triggered this
 * check being introduced.
 */
public class DocQuadDetectorConvexityTest {

  @Test
  public void axisAlignedRectangle_isAccepted() {
    double[][] q = {
        {100, 100}, // TL
        {300, 100}, // TR
        {300, 200}, // BR
        {100, 200}, // BL
    };
    assertTrue(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void slightlyTiltedConvexQuad_isAccepted() {
    double[][] q = {
        {110, 102},
        {305, 95},
        {310, 205},
        {105, 198},
    };
    assertTrue(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void coldStartFrameFromDevice_isRejected() {
    // Verbatim sample from device logs (08:13:31.814).
    double[][] q = {
        {512.5, 47.2},
        {277.0, 370.7},
        {304.5, 334.6},
        {486.6, 5.0},
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void counterClockwiseOrdering_isRejected() {
    // TL/BL/BR/TR (CCW in image coords) — semantically wrong even though convex.
    double[][] q = {
        {100, 100},
        {100, 200},
        {300, 200},
        {300, 100},
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void swappedTLandTR_isRejected() {
    double[][] q = {
        {300, 100}, // "TL" but actually right
        {100, 100}, // "TR" but actually left
        {300, 200}, // BR
        {100, 200}, // BL
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void collinearPoints_areRejected() {
    double[][] q = {
        {100, 100},
        {200, 100},
        {300, 100}, // collinear with TL/TR
        {100, 200},
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void nonConvexConcaveQuad_isRejected() {
    // BR pulled inward to create a concave dent.
    double[][] q = {
        {100, 100},
        {300, 100},
        {200, 150}, // concave corner
        {100, 200},
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }

  @Test
  public void nullOrWrongShape_isRejected() {
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(null));
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(new double[][] {{0, 0}, {1, 0}, {1, 1}}));
    assertFalse(
        DocQuadDetector.isConvexTLTRBRBL(new double[][] {{0, 0}, {1, 0}, null, {0, 1}}));
  }

  @Test
  public void nonFiniteCoordinates_areRejected() {
    double[][] q = {
        {100, 100},
        {Double.NaN, 100},
        {300, 200},
        {100, 200},
    };
    assertFalse(DocQuadDetector.isConvexTLTRBRBL(q));
  }
}
