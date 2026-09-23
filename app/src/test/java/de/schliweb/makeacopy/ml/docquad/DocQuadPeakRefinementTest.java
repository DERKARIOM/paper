package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM-Unit-Test für M6b Peak-Refinement (Subpixel) ohne ORT. */
public class DocQuadPeakRefinementTest {

  @Test
  public void refine3x3_shiftsCentroidTowardNeighbors_andIsDeterministic() {
    float[][][][] hm = new float[1][4][64][64];
    for (int c = 0; c < 4; c++) {
      for (int y = 0; y < 64; y++) {
        for (int x = 0; x < 64; x++) {
          hm[0][c][y][x] = -1000.0f;
        }
      }
    }

    // Channel 0: Argmax at (10,20), but neighborhood pulls slightly to the right/down.
    int ix = 10;
    int iy = 20;
    hm[0][0][iy][ix] = 10.0f;
    hm[0][0][iy][ix + 1] = 9.0f;
    hm[0][0][iy + 1][ix] = 9.0f;
    hm[0][0][iy + 1][ix + 1] = 8.0f;

    double[][] arg =
        DocQuadPostprocessor.corners64ToCorners256(hm, DocQuadPostprocessor.PeakMode.ARGMAX);
    double[][] ref1 =
        DocQuadPostprocessor.corners64ToCorners256(hm, DocQuadPostprocessor.PeakMode.REFINE_3X3);
    double[][] ref2 =
        DocQuadPostprocessor.corners64ToCorners256(hm, DocQuadPostprocessor.PeakMode.REFINE_3X3);

    // Argmax pixel center in 64-space.
    double x64Arg = arg[0][0] / 4.0;
    double y64Arg = arg[0][1] / 4.0;
    assertEquals(ix + 0.5, x64Arg, 0.0);
    assertEquals(iy + 0.5, y64Arg, 0.0);

    double x64Ref = ref1[0][0] / 4.0;
    double y64Ref = ref1[0][1] / 4.0;

    assertTrue(x64Ref > x64Arg);
    assertTrue(y64Ref > y64Arg);

    // Refined centroid stays within the (clipped) 3x3 window in 64-space.
    assertTrue(x64Ref >= ix - 0.5);
    assertTrue(x64Ref <= ix + 1.5);
    assertTrue(y64Ref >= iy - 0.5);
    assertTrue(y64Ref <= iy + 1.5);

    // Determinismus: zweimal auf denselben Inputs exakt gleich.
    assertEquals(ref1[0][0], ref2[0][0], 0.0);
    assertEquals(ref1[0][1], ref2[0][1], 0.0);
  }
}
