package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests for M6c (Mask→Quad): PCA-Rectangle + deterministic fallback. */
public class DocQuadMaskToQuadTest {

  @Test
  public void axisAlignedRectangleMask_producesReasonableQuad_andCanonicalOrder() {
    float[][][][] maskLogits = new float[1][1][64][64];
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        maskLogits[0][0][y][x] = -10.0f;
      }
    }

    // true rectangle: x=10..30, y=20..40 (inclusive)
    for (int y = 20; y <= 40; y++) {
      for (int x = 10; x <= 30; x++) {
        maskLogits[0][0][y][x] = 10.0f;
      }
    }

    double[][] fallback =
        new double[][] {
          {2.0, 2.0},
          {254.0, 2.0},
          {254.0, 254.0},
          {2.0, 254.0},
        };

    DocQuadPostprocessor.QuadFromMask qm =
        DocQuadPostprocessor.quadFromMask256(maskLogits, fallback);
    assertNotNull(qm);
    assertFalse(qm.usedFallback());
    assertNotNull(qm.quad256());
    assertEquals(4, qm.quad256().length);

    double minX64 = Double.POSITIVE_INFINITY;
    double maxX64 = Double.NEGATIVE_INFINITY;
    double minY64 = Double.POSITIVE_INFINITY;
    double maxY64 = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < 4; i++) {
      assertNotNull(qm.quad256()[i]);
      assertEquals(2, qm.quad256()[i].length);
      double x64 = qm.quad256()[i][0] / 4.0;
      double y64 = qm.quad256()[i][1] / 4.0;
      assertTrue(Double.isFinite(x64));
      assertTrue(Double.isFinite(y64));
      minX64 = Math.min(minX64, x64);
      maxX64 = Math.max(maxX64, x64);
      minY64 = Math.min(minY64, y64);
      maxY64 = Math.max(maxY64, y64);
    }

    // Grobe Bounds (tolerant): Pixelzentren liegen bei 10.5..30.5 bzw. 20.5..40.5.
    assertTrue(minX64 >= 10.0 && minX64 <= 11.0);
    assertTrue(maxX64 >= 30.0 && maxX64 <= 31.0);
    assertTrue(minY64 >= 20.0 && minY64 <= 21.0);
    assertTrue(maxY64 >= 40.0 && maxY64 <= 41.0);

    // Order TL,TR,BR,BL: TL hat kleinste x+y.
    double s0 = qm.quad256()[0][0] + qm.quad256()[0][1];
    for (int i = 1; i < 4; i++) {
      double si = qm.quad256()[i][0] + qm.quad256()[i][1];
      assertTrue(s0 <= si);
    }

    // Area > 0
    double areaAbs = Math.abs(shoelaceArea(qm.quad256()));
    assertTrue(areaAbs > 0.0);
  }

  @Test
  public void emptyMask_usesFallbackExactly() {
    float[][][][] maskLogits = new float[1][1][64][64];
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        maskLogits[0][0][y][x] = -10.0f;
      }
    }

    double[][] fallback =
        new double[][] {
          {2.0, 2.0},
          {254.0, 2.0},
          {254.0, 254.0},
          {2.0, 254.0},
        };

    DocQuadPostprocessor.QuadFromMask qm =
        DocQuadPostprocessor.quadFromMask256(maskLogits, fallback);
    assertNotNull(qm);
    assertTrue(qm.usedFallback());
    assertNotNull(qm.quad256());
    assertEquals(4, qm.quad256().length);

    for (int i = 0; i < 4; i++) {
      assertEquals(fallback[i][0], qm.quad256()[i][0], 0.0);
      assertEquals(fallback[i][1], qm.quad256()[i][1], 0.0);
    }
  }

  private static double shoelaceArea(double[][] pts) {
    double s = 0.0;
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      s += pts[i][0] * pts[j][1] - pts[j][0] * pts[i][1];
    }
    return 0.5 * s;
  }
}
