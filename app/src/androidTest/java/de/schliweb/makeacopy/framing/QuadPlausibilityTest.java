package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import android.graphics.PointF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for QuadPlausibility - the geometric plausibility checks for document
 * detection. Tests cover convexity, self-intersection, minimum area, out-of-bounds, and aspect
 * ratio checks.
 */
@RunWith(AndroidJUnit4.class)
public class QuadPlausibilityTest {

  // Test image dimensions
  private static final int IMG_W = 640;
  private static final int IMG_H = 480;

  // --- Helper methods ---

  private PointF[] makeQuad(
      float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
    return new PointF[] {
      new PointF(x0, y0), // TL
      new PointF(x1, y1), // TR
      new PointF(x2, y2), // BR
      new PointF(x3, y3) // BL
    };
  }

  /**
   * Creates a centered, well-formed quad covering approximately the given percentage of image area.
   */
  private PointF[] makeCenteredQuad(float coveragePercent) {
    float side = (float) Math.sqrt(coveragePercent * IMG_W * IMG_H);
    float halfW = side / 2f;
    float halfH = side / 2f;
    float cx = IMG_W / 2f;
    float cy = IMG_H / 2f;
    return makeQuad(
        cx - halfW,
        cy - halfH, // TL
        cx + halfW,
        cy - halfH, // TR
        cx + halfW,
        cy + halfH, // BR
        cx - halfW,
        cy + halfH // BL
        );
  }

  // --- Convexity tests ---

  @Test
  public void convexQuad_isConvex() {
    PointF[] quad = makeCenteredQuad(0.20f);
    assertTrue("Centered square should be convex", QuadPlausibility.isConvex(quad));
  }

  @Test
  public void concaveQuad_isNotConvex() {
    // Create a concave quad (one corner pushed inward past the diagonal)
    // For a quad TL-TR-BR-BL going clockwise, pushing BR inward (toward TL)
    // creates a concave shape where the turn at BR reverses direction
    PointF[] quad =
        makeQuad(
            100, 100, // TL
            300, 100, // TR
            150, 150, // BR pushed far inward (concave - inside the triangle TL-TR-BL)
            100, 300 // BL
            );
    assertFalse("Concave quad should not be convex", QuadPlausibility.isConvex(quad));
  }

  @Test
  public void nullQuad_isNotConvex() {
    assertFalse("Null quad should not be convex", QuadPlausibility.isConvex(null));
  }

  @Test
  public void wrongSizeQuad_isNotConvex() {
    PointF[] quad = new PointF[] {new PointF(0, 0), new PointF(1, 0), new PointF(1, 1)};
    assertFalse("3-point array should not be convex", QuadPlausibility.isConvex(quad));
  }

  // --- Self-intersection tests ---

  @Test
  public void normalQuad_doesNotSelfIntersect() {
    PointF[] quad = makeCenteredQuad(0.20f);
    assertFalse("Normal quad should not self-intersect", QuadPlausibility.selfIntersects(quad));
  }

  @Test
  public void bowTieQuad_selfIntersects() {
    // Create a bow-tie (self-intersecting) quad
    PointF[] quad =
        makeQuad(
            100, 100, // TL
            300, 300, // TR (crossed)
            300, 100, // BR
            100, 300 // BL (crossed)
            );
    assertTrue("Bow-tie quad should self-intersect", QuadPlausibility.selfIntersects(quad));
  }

  // --- Area tests ---

  @Test
  public void shoelaceArea_calculatesCorrectly() {
    // 100x100 square
    PointF[] quad = makeQuad(0, 0, 100, 0, 100, 100, 0, 100);
    float area = Math.abs(QuadPlausibility.shoelaceArea(quad));
    assertEquals("100x100 square should have area 10000", 10000f, area, 1f);
  }

  @Test
  public void largeQuad_meetsMinArea() {
    PointF[] quad = makeCenteredQuad(0.20f); // 20% coverage
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertTrue("20% coverage should meet min area", result.meetsMinArea);
    assertTrue("Area ratio should be ~0.20", result.areaRatio > 0.15f && result.areaRatio < 0.25f);
  }

  @Test
  public void tinyQuad_failsMinArea() {
    PointF[] quad = makeCenteredQuad(0.02f); // 2% coverage (below 6% threshold)
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertFalse("2% coverage should fail min area", result.meetsMinArea);
  }

  // --- Out-of-bounds tests ---

  @Test
  public void centeredQuad_withinBounds() {
    PointF[] quad = makeCenteredQuad(0.20f);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertTrue("Centered quad should be within bounds", result.withinBounds);
    assertEquals("OOB sum should be 0", 0f, result.oobSum, 0.1f);
  }

  @Test
  public void partiallyOutOfBounds_calculatesOobSum() {
    // Quad with one corner 50px outside left edge
    PointF[] quad =
        makeQuad(
            -50, 100, // TL outside
            200, 100, // TR
            200, 300, // BR
            -50, 300 // BL outside
            );
    float oobSum = QuadPlausibility.computeOobSum(quad, IMG_W, IMG_H, QuadPlausibility.OOB_TOL_PX);
    assertTrue("OOB sum should be positive", oobSum > 0);
  }

  @Test
  public void severelyOutOfBounds_failsCheck() {
    // Quad mostly outside image
    PointF[] quad =
        makeQuad(
            -200, -200,
            -100, -200,
            -100, -100,
            -200, -100);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertFalse("Severely OOB quad should fail bounds check", result.withinBounds);
  }

  // --- Aspect ratio tests ---

  @Test
  public void squareQuad_hasGoodAspect() {
    PointF[] quad = makeCenteredQuad(0.20f);
    float aspect = QuadPlausibility.computeAspectRatio(quad);
    assertTrue("Square should have aspect ~1", aspect >= 1f && aspect < 1.5f);
  }

  @Test
  public void wideQuad_hasHighAspect() {
    // Very wide quad (10:1 ratio)
    PointF[] quad =
        makeQuad(
            100, 200,
            500, 200,
            500, 240,
            100, 240);
    float aspect = QuadPlausibility.computeAspectRatio(quad);
    assertTrue("Wide quad should have high aspect ratio", aspect > 5f);
  }

  @Test
  public void extremeAspect_failsCheck() {
    // Extremely thin quad
    PointF[] quad =
        makeQuad(
            100, 200,
            600, 200,
            600, 210,
            100, 210);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertFalse("Extreme aspect ratio should fail", result.aspectOk);
  }

  // --- Full plausibility tests ---

  @Test
  public void goodDocument_isPlausible() {
    // Well-formed, centered, reasonably sized quad
    PointF[] quad = makeCenteredQuad(0.25f);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);

    assertTrue("Should be convex", result.isConvex);
    assertTrue("Should not self-intersect", result.noSelfIntersection);
    assertTrue("Should meet min area", result.meetsMinArea);
    assertTrue("Should be within bounds", result.withinBounds);
    assertTrue("Should have OK aspect", result.aspectOk);
    assertTrue("Overall should be plausible", result.plausible);
  }

  @Test
  public void nullQuad_notPlausible() {
    QuadPlausibility.Result result = QuadPlausibility.check(null, IMG_W, IMG_H);
    assertFalse("Null quad should not be plausible", result.plausible);
  }

  @Test
  public void invalidImageDimensions_notPlausible() {
    PointF[] quad = makeCenteredQuad(0.20f);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, 0, 0);
    assertFalse("Zero dimensions should not be plausible", result.plausible);
  }

  @Test
  public void quadWithNullPoint_notPlausible() {
    PointF[] quad =
        new PointF[] {new PointF(100, 100), null, new PointF(300, 300), new PointF(100, 300)};
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    assertFalse("Quad with null point should not be plausible", result.plausible);
  }

  @Test
  public void isPlausible_shortcut_matchesFullCheck() {
    PointF[] goodQuad = makeCenteredQuad(0.25f);
    PointF[] badQuad = makeCenteredQuad(0.02f);

    assertEquals(
        "isPlausible should match check().plausible for good quad",
        QuadPlausibility.check(goodQuad, IMG_W, IMG_H).plausible,
        QuadPlausibility.isPlausible(goodQuad, IMG_W, IMG_H));

    assertEquals(
        "isPlausible should match check().plausible for bad quad",
        QuadPlausibility.check(badQuad, IMG_W, IMG_H).plausible,
        QuadPlausibility.isPlausible(badQuad, IMG_W, IMG_H));
  }

  // --- Edge length tests ---

  @Test
  public void minEdgeLength_calculatesCorrectly() {
    PointF[] quad = makeQuad(0, 0, 100, 0, 100, 50, 0, 50);
    float minEdge = QuadPlausibility.minEdgeLength(quad);
    assertEquals("Min edge should be 50 (height)", 50f, minEdge, 0.1f);
  }

  @Test
  public void verySmallEdge_failsMinArea() {
    // Quad with one very short edge (below MIN_EDGE_PX)
    PointF[] quad =
        makeQuad(
            100, 100,
            110, 100, // Only 10px wide at top
            300, 300,
            100, 300);
    QuadPlausibility.Result result = QuadPlausibility.check(quad, IMG_W, IMG_H);
    // This should fail due to small edge even if area might be OK
    assertFalse("Quad with tiny edge should fail", result.meetsMinArea);
  }
}
