package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link DocQuadScore} — pure geometry utilities for quad scoring. All methods use
 * double[][] (no Android dependencies).
 */
public class DocQuadScoreTest {

  // Helper: unit square TL(0,0) TR(1,0) BR(1,1) BL(0,1)
  private static double[][] unitSquare() {
    return new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
  }

  // Helper: rectangle TL(0,0) TR(100,0) BR(100,50) BL(0,50)
  private static double[][] rect100x50() {
    return new double[][] {{0, 0}, {100, 0}, {100, 50}, {0, 50}};
  }

  // ==================== areaAbs ====================

  @Test
  public void areaAbs_unitSquare() {
    assertEquals(1.0, DocQuadScore.areaAbs(unitSquare()), 1e-9);
  }

  @Test
  public void areaAbs_rectangle() {
    assertEquals(5000.0, DocQuadScore.areaAbs(rect100x50()), 1e-9);
  }

  @Test
  public void areaAbs_ccwOrder_sameResult() {
    // BL, BR, TR, TL (counter-clockwise)
    double[][] ccw = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    assertEquals(1.0, DocQuadScore.areaAbs(ccw), 1e-9);
  }

  @Test(expected = IllegalArgumentException.class)
  public void areaAbs_null_throws() {
    DocQuadScore.areaAbs(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void areaAbs_wrongLength_throws() {
    DocQuadScore.areaAbs(new double[][] {{0, 0}, {1, 0}, {1, 1}});
  }

  // ==================== perimeter ====================

  @Test
  public void perimeter_unitSquare() {
    assertEquals(4.0, DocQuadScore.perimeter(unitSquare()), 1e-9);
  }

  @Test
  public void perimeter_rectangle() {
    assertEquals(300.0, DocQuadScore.perimeter(rect100x50()), 1e-9);
  }

  // ==================== edgeLengthMin / edgeLengthMax ====================

  @Test
  public void edgeLengthMin_unitSquare() {
    assertEquals(1.0, DocQuadScore.edgeLengthMin(unitSquare()), 1e-9);
  }

  @Test
  public void edgeLengthMax_unitSquare() {
    assertEquals(1.0, DocQuadScore.edgeLengthMax(unitSquare()), 1e-9);
  }

  @Test
  public void edgeLengthMin_rectangle() {
    assertEquals(50.0, DocQuadScore.edgeLengthMin(rect100x50()), 1e-9);
  }

  @Test
  public void edgeLengthMax_rectangle() {
    assertEquals(100.0, DocQuadScore.edgeLengthMax(rect100x50()), 1e-9);
  }

  // ==================== aspectLike ====================

  @Test
  public void aspectLike_square_isOne() {
    assertEquals(1.0, DocQuadScore.aspectLike(unitSquare()), 1e-9);
  }

  @Test
  public void aspectLike_rectangle_isTwo() {
    assertEquals(2.0, DocQuadScore.aspectLike(rect100x50()), 1e-9);
  }

  // ==================== isConvex ====================

  @Test
  public void isConvex_square_true() {
    assertTrue(DocQuadScore.isConvex(unitSquare()));
  }

  @Test
  public void isConvex_concave_false() {
    // TL(0,0) TR(2,0) BR(1,0.5) BL(0,2) — BR is inside, making it concave
    double[][] concave = {{0, 0}, {2, 0}, {1, 0.5}, {0, 2}};
    assertFalse(DocQuadScore.isConvex(concave));
  }

  @Test
  public void isConvex_collinear_false() {
    // All points on a line → degenerate, returns false
    double[][] line = {{0, 0}, {1, 0}, {2, 0}, {3, 0}};
    assertFalse(DocQuadScore.isConvex(line));
  }

  // ==================== selfIntersects ====================

  @Test
  public void selfIntersects_square_false() {
    assertFalse(DocQuadScore.selfIntersects(unitSquare()));
  }

  @Test
  public void selfIntersects_bowTie_true() {
    // Bow-tie: TL(0,0) TR(1,1) BR(1,0) BL(0,1) — edges cross
    double[][] bowTie = {{0, 0}, {1, 1}, {1, 0}, {0, 1}};
    assertTrue(DocQuadScore.selfIntersects(bowTie));
  }

  // ==================== oobSum ====================

  @Test
  public void oobSum_insideImage_zero() {
    // Quad fully inside 256x256 image
    double[][] quad = {{10, 10}, {200, 10}, {200, 200}, {10, 200}};
    assertEquals(0.0, DocQuadScore.oobSum(quad, 256, 256, 0), 1e-9);
  }

  @Test
  public void oobSum_outsideLeft_positive() {
    // One corner at x=-5
    double[][] quad = {{-5, 10}, {100, 10}, {100, 100}, {0, 100}};
    double oob = DocQuadScore.oobSum(quad, 256, 256, 0);
    assertTrue(oob > 0);
  }

  @Test
  public void oobSum_withinTolerance_zero() {
    // Corner at x=-3, tolerance=5 → within tolerance
    double[][] quad = {{-3, 10}, {100, 10}, {100, 100}, {0, 100}};
    assertEquals(0.0, DocQuadScore.oobSum(quad, 256, 256, 5), 1e-9);
  }

  @Test(expected = IllegalArgumentException.class)
  public void oobSum_zeroWidth_throws() {
    DocQuadScore.oobSum(unitSquare(), 0, 256, 0);
  }

  // ==================== oobMax ====================

  @Test
  public void oobMax_insideImage_zero() {
    double[][] quad = {{10, 10}, {200, 10}, {200, 200}, {10, 200}};
    assertEquals(0.0, DocQuadScore.oobMax(quad, 256, 256, 0), 1e-9);
  }

  @Test
  public void oobMax_oneCornerOutside() {
    double[][] quad = {{-10, 10}, {100, 10}, {100, 100}, {0, 100}};
    double oobMax = DocQuadScore.oobMax(quad, 256, 256, 0);
    assertEquals(10.0, oobMax, 1e-9);
  }

  // ==================== requireQuad validation ====================

  @Test(expected = IllegalArgumentException.class)
  public void requireQuad_nullInnerArray_throws() {
    DocQuadScore.areaAbs(new double[][] {{0, 0}, null, {1, 1}, {0, 1}});
  }

  @Test(expected = IllegalArgumentException.class)
  public void requireQuad_wrongInnerLength_throws() {
    DocQuadScore.areaAbs(new double[][] {{0, 0}, {1}, {1, 1}, {0, 1}});
  }
}
