package de.schliweb.makeacopy.framing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuadPlausibilityResultTest {

  @Test
  public void constructor_allTrue_plausible() {
    QuadPlausibility.Result r =
        new QuadPlausibility.Result(true, true, true, true, true, true, 0.15f, 5.0f, 1.5f);
    assertTrue(r.plausible);
    assertTrue(r.isConvex);
    assertTrue(r.noSelfIntersection);
    assertTrue(r.meetsMinArea);
    assertTrue(r.withinBounds);
    assertTrue(r.aspectOk);
    assertEquals(0.15f, r.areaRatio, 1e-6f);
    assertEquals(5.0f, r.oobSum, 1e-6f);
    assertEquals(1.5f, r.aspectRatio, 1e-6f);
  }

  @Test
  public void constructor_notPlausible() {
    QuadPlausibility.Result r =
        new QuadPlausibility.Result(false, false, true, true, true, true, 0.01f, 50.0f, 8.0f);
    assertFalse(r.plausible);
    assertFalse(r.isConvex);
  }

  @Test
  public void toString_containsFields() {
    QuadPlausibility.Result r =
        new QuadPlausibility.Result(true, true, true, true, true, true, 0.20f, 3.0f, 1.2f);
    String s = r.toString();
    assertTrue(s.contains("plausible=true"));
    assertTrue(s.contains("isConvex=true"));
    assertTrue(s.contains("areaRatio="));
  }

  @Test
  public void constants_haveExpectedValues() {
    assertEquals(0.06f, QuadPlausibility.MIN_AREA_RATIO, 1e-6f);
    assertEquals(24f, QuadPlausibility.MIN_EDGE_PX, 0);
    assertEquals(8f, QuadPlausibility.OOB_TOL_PX, 1e-6f);
    assertEquals(40f, QuadPlausibility.OOB_SUM_MAX, 0);
    assertEquals(6.0f, QuadPlausibility.ASPECT_LIKE_MAX, 1e-6f);
  }
}
