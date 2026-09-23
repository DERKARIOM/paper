package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import android.graphics.PointF;
import org.junit.Test;

/**
 * JVM unit tests for {@link QuadPlausibility}. Covers null/edge-case guards, convexity,
 * self-intersection, area, out-of-bounds, and aspect-ratio checks.
 */
public class QuadPlausibilityUnitTest {

  private static final int W = 1000;
  private static final int H = 800;

  private static PointF pt(float x, float y) {
    PointF p = new PointF();
    p.x = x;
    p.y = y;
    return p;
  }

  /** A "nice" rectangle covering ~60% of the image – clearly plausible. */
  private static PointF[] niceQuad() {
    return new PointF[] {pt(100, 100), pt(900, 100), pt(900, 700), pt(100, 700)};
  }

  // ---- null / invalid input guards ----

  @Test
  public void nullQuad_notPlausible() {
    QuadPlausibility.Result r = QuadPlausibility.check(null, W, H);
    assertFalse(r.plausible);
  }

  @Test
  public void emptyQuad_notPlausible() {
    QuadPlausibility.Result r = QuadPlausibility.check(new PointF[0], W, H);
    assertFalse(r.plausible);
  }

  @Test
  public void threePoints_notPlausible() {
    PointF[] tri = {pt(0, 0), pt(100, 0), pt(50, 100)};
    assertFalse(QuadPlausibility.check(tri, W, H).plausible);
  }

  @Test
  public void nullPointInQuad_notPlausible() {
    PointF[] q = {pt(0, 0), null, pt(100, 100), pt(0, 100)};
    assertFalse(QuadPlausibility.check(q, W, H).plausible);
  }

  @Test
  public void zeroImageDimensions_notPlausible() {
    assertFalse(QuadPlausibility.check(niceQuad(), 0, H).plausible);
    assertFalse(QuadPlausibility.check(niceQuad(), W, 0).plausible);
    assertFalse(QuadPlausibility.check(niceQuad(), -1, H).plausible);
  }

  // ---- happy path ----

  @Test
  public void niceRectangle_isPlausible() {
    QuadPlausibility.Result r = QuadPlausibility.check(niceQuad(), W, H);
    assertTrue(r.plausible);
    assertTrue(r.isConvex);
    assertTrue(r.noSelfIntersection);
    assertTrue(r.meetsMinArea);
    assertTrue(r.withinBounds);
    assertTrue(r.aspectOk);
  }

  @Test
  public void isPlausible_delegatesToCheck() {
    assertTrue(QuadPlausibility.isPlausible(niceQuad(), W, H));
    assertFalse(QuadPlausibility.isPlausible(null, W, H));
  }

  // ---- convexity ----

  @Test
  public void convexQuad_isConvex() {
    assertTrue(QuadPlausibility.isConvex(niceQuad()));
  }

  @Test
  public void concaveQuad_isNotConvex() {
    // Dent: TL, TR, center-inward, BL — cross products change sign
    PointF[] q = {pt(100, 100), pt(900, 100), pt(500, 300), pt(100, 700)};
    // Verify it's actually concave by checking cross product sign change
    // If isConvex returns true for this shape, adjust the dent further
    // TL->TR->center: cross > 0, TR->center->BL: cross < 0
    assertFalse(QuadPlausibility.isConvex(q));
  }

  @Test
  public void collinearPoints_stillConvex() {
    PointF[] q = {pt(0, 0), pt(500, 0), pt(1000, 0), pt(500, 500)};
    assertTrue(QuadPlausibility.isConvex(q));
  }

  // ---- self-intersection ----

  @Test
  public void normalQuad_noSelfIntersection() {
    assertFalse(QuadPlausibility.selfIntersects(niceQuad()));
  }

  @Test
  public void bowTieQuad_selfIntersects() {
    // TL, BR, TR, BL — edge 0-1 crosses edge 2-3
    PointF[] q = {pt(100, 100), pt(900, 700), pt(900, 100), pt(100, 700)};
    assertTrue(QuadPlausibility.selfIntersects(q));
  }

  // ---- area ----

  @Test
  public void tinyQuad_failsMinArea() {
    PointF[] q = {pt(400, 400), pt(410, 400), pt(410, 410), pt(400, 410)};
    QuadPlausibility.Result r = QuadPlausibility.check(q, W, H);
    assertFalse(r.meetsMinArea);
    assertFalse(r.plausible);
  }

  @Test
  public void shoelaceArea_correctForUnitSquare() {
    PointF[] q = {pt(0, 0), pt(100, 0), pt(100, 100), pt(0, 100)};
    assertEquals(10000f, Math.abs(QuadPlausibility.shoelaceArea(q)), 0.01f);
  }

  @Test
  public void minEdgeLength_correct() {
    PointF[] q = {pt(0, 0), pt(30, 0), pt(30, 50), pt(0, 50)};
    assertEquals(30f, QuadPlausibility.minEdgeLength(q), 0.01f);
  }

  // ---- out-of-bounds ----

  @Test
  public void quadInsideImage_oobSumZero() {
    float oob = QuadPlausibility.computeOobSum(niceQuad(), W, H, QuadPlausibility.OOB_TOL_PX);
    assertEquals(0f, oob, 0.01f);
  }

  @Test
  public void quadFarOutside_highOobSum() {
    PointF[] q = {pt(-200, -200), pt(1200, -200), pt(1200, 1000), pt(-200, 1000)};
    float oob = QuadPlausibility.computeOobSum(q, W, H, QuadPlausibility.OOB_TOL_PX);
    assertTrue(oob > QuadPlausibility.OOB_SUM_MAX);
  }

  // ---- aspect ratio ----

  @Test
  public void squareQuad_aspectRatioNearOne() {
    PointF[] q = {pt(0, 0), pt(100, 0), pt(100, 100), pt(0, 100)};
    float ar = QuadPlausibility.computeAspectRatio(q);
    assertEquals(1.0f, ar, 0.01f);
  }

  @Test
  public void extremelyNarrowQuad_highAspectRatio() {
    PointF[] q = {pt(0, 0), pt(1000, 0), pt(1000, 10), pt(0, 10)};
    float ar = QuadPlausibility.computeAspectRatio(q);
    assertTrue(ar > QuadPlausibility.ASPECT_LIKE_MAX);
  }

  @Test
  public void degenerateZeroSize_aspectRatioMax() {
    PointF[] q = {pt(0, 0), pt(0, 0), pt(0, 0), pt(0, 0)};
    assertEquals(Float.MAX_VALUE, QuadPlausibility.computeAspectRatio(q), 0f);
  }

  // ---- Result.toString ----

  @Test
  public void resultToString_containsAllFields() {
    QuadPlausibility.Result r = QuadPlausibility.check(niceQuad(), W, H);
    String s = r.toString();
    assertTrue(s.contains("plausible=true"));
    assertTrue(s.contains("isConvex=true"));
    assertTrue(s.contains("areaRatio="));
  }
}
