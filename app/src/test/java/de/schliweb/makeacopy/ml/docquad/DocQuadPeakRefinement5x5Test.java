package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM-Unit-Test for the 5×5 quadratic subpixel peak refinement (`PeakMode.REFINE_5X5_QUADRATIC`).
 *
 * <p>Pure JVM, kein ORT, keine Android-APIs.
 */
public class DocQuadPeakRefinement5x5Test {

  private static float[][][][] makeFlat(float baseLogit) {
    float[][][][] hm = new float[1][4][64][64];
    for (int c = 0; c < 4; c++) {
      for (int y = 0; y < 64; y++) {
        for (int x = 0; x < 64; x++) {
          hm[0][c][y][x] = baseLogit;
        }
      }
    }
    return hm;
  }

  /**
   * Symmetric Gaussian-like peak centered exactly on a pixel center → the parabolic fit must yield
   * dx=dy=0 (no subpixel shift).
   */
  @Test
  public void quadratic5x5_symmetricPeak_yieldsExactPixelCenter() {
    float[][][][] hm = makeFlat(-1000.0f);
    int ix = 32;
    int iy = 32;
    // Symmetric quadratic-shaped logits around (ix,iy).
    for (int dy = -2; dy <= 2; dy++) {
      for (int dx = -2; dx <= 2; dx++) {
        hm[0][0][iy + dy][ix + dx] = 10.0f - (dx * dx + dy * dy);
      }
    }

    double[][] out =
        DocQuadPostprocessor.corners64ToCorners256(
            hm, DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC);

    double x64 = out[0][0] / 4.0;
    double y64 = out[0][1] / 4.0;
    assertEquals(ix + 0.5, x64, 1e-9);
    assertEquals(iy + 0.5, y64, 1e-9);
  }

  /**
   * Asymmetric peak: neighbor on the right is stronger than on the left → subpixel offset must
   * push the refined position to the right (and down for the y-axis case).
   */
  @Test
  public void quadratic5x5_asymmetricPeak_shiftsTowardStrongerNeighbor() {
    float[][][][] hm = makeFlat(-1000.0f);
    int ix = 10;
    int iy = 20;
    // Argmax at (ix,iy), right neighbor stronger than left → expected dx > 0.
    hm[0][0][iy][ix - 1] = 8.0f;
    hm[0][0][iy][ix] = 10.0f;
    hm[0][0][iy][ix + 1] = 9.5f;
    // Bottom neighbor stronger than top → expected dy > 0.
    hm[0][0][iy - 1][ix] = 8.0f;
    hm[0][0][iy + 1][ix] = 9.5f;

    double[][] arg =
        DocQuadPostprocessor.corners64ToCorners256(hm, DocQuadPostprocessor.PeakMode.ARGMAX);
    double[][] ref =
        DocQuadPostprocessor.corners64ToCorners256(
            hm, DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC);

    double x64Arg = arg[0][0] / 4.0;
    double y64Arg = arg[0][1] / 4.0;
    double x64Ref = ref[0][0] / 4.0;
    double y64Ref = ref[0][1] / 4.0;

    assertEquals(ix + 0.5, x64Arg, 0.0);
    assertEquals(iy + 0.5, y64Arg, 0.0);

    // Refined position must shift toward the stronger neighbor and stay within ±0.5 of the
    // argmax pixel center (parabolic offsets are clamped to that range).
    assertTrue("x must shift right of argmax", x64Ref > x64Arg);
    assertTrue("y must shift down from argmax", y64Ref > y64Arg);
    assertTrue(x64Ref <= ix + 1.0);
    assertTrue(y64Ref <= iy + 1.0);
  }

  /**
   * Flat-window degenerate case: parabolic denominator is non-negative for both axes. The
   * implementation must fall back to the 5×5 weighted centroid (or argmax pixel center if even
   * that is degenerate). It must never NaN/Inf and must stay in a 5×5 window.
   */
  @Test
  public void quadratic5x5_degenerateWindow_fallsBackSafely() {
    float[][][][] hm = makeFlat(-1000.0f);
    int ix = 5;
    int iy = 5;
    // A single isolated peak with all neighbors at -1000 → parabolic fit denom is positive
    // (l + r - 2c < 0 means concave; here l = r = -1000 and c = 0, denom = -2000 - 0 = -2000 ⇒
    // actually concave). To force degeneracy we need a flat top: equal value at peak and one
    // neighbor.
    hm[0][0][iy][ix - 1] = 5.0f;
    hm[0][0][iy][ix] = 5.0f; // tie with left neighbor
    hm[0][0][iy][ix + 1] = 5.0f;
    hm[0][0][iy - 1][ix] = 5.0f;
    hm[0][0][iy + 1][ix] = 5.0f;

    double[][] ref =
        DocQuadPostprocessor.corners64ToCorners256(
            hm, DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC);
    double x256 = ref[0][0];
    double y256 = ref[0][1];
    assertTrue("must be finite", Double.isFinite(x256) && Double.isFinite(y256));
    // Stay within the 5×5 window around argmax (=ix,iy due to scan-order tie-breaking).
    double x64 = x256 / 4.0;
    double y64 = y256 / 4.0;
    assertTrue(x64 >= ix - 2.0 - 0.5 && x64 <= ix + 2.0 + 0.5);
    assertTrue(y64 >= iy - 2.0 - 0.5 && y64 <= iy + 2.0 + 0.5);
  }

  /** Determinismus: zweimal mit denselben Inputs exakt gleich. */
  @Test
  public void quadratic5x5_isDeterministic() {
    float[][][][] hm = makeFlat(-1000.0f);
    int ix = 12;
    int iy = 30;
    for (int dy = -2; dy <= 2; dy++) {
      for (int dx = -2; dx <= 2; dx++) {
        hm[0][0][iy + dy][ix + dx] = 10.0f - 0.5f * (dx * dx + dy * dy) - 0.1f * dx;
      }
    }
    double[][] a =
        DocQuadPostprocessor.corners64ToCorners256(
            hm, DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC);
    double[][] b =
        DocQuadPostprocessor.corners64ToCorners256(
            hm, DocQuadPostprocessor.PeakMode.REFINE_5X5_QUADRATIC);
    assertEquals(a[0][0], b[0][0], 0.0);
    assertEquals(a[0][1], b[0][1], 0.0);
  }
}
