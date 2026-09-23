package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** M6d: Unit-Tests für QuadScore + Pfadwahl A/B (Corner vs Mask). */
public class DocQuadPathChoiceTest {

  private static float[][][][] makeMaskLogitsAllTrue() {
    float[][][][] m = new float[1][1][64][64];
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        m[0][0][y][x] = 10.0f;
      }
    }
    return m;
  }

  @Test
  public void maskWins_whenCornerQuadSelfIntersects() {
    // A: Bow-tie / self-intersecting quad
    double[][] quadA =
        new double[][] {
          {2.0, 2.0},
          {254.0, 254.0},
          {254.0, 2.0},
          {2.0, 254.0},
        };

    // B: Normaler, großer Rechteck-Quad
    double[][] quadB =
        new double[][] {
          {2.0, 2.0},
          {254.0, 2.0},
          {254.0, 254.0},
          {2.0, 254.0},
        };

    float[][][][] maskLogits = makeMaskLogitsAllTrue();

    DocQuadPostprocessor.PathChoice pc =
        DocQuadPostprocessor.choosePath(quadA, quadB, false, maskLogits);

    assertEquals(DocQuadPostprocessor.ChosenSource.MASK, pc.chosenSource());
    assertQuadEquals(quadB, pc.chosenQuad256());
  }

  @Test
  public void cornersWin_whenMaskQuadIsFallback() {
    double[][] quadA =
        new double[][] {
          {2.0, 2.0},
          {254.0, 2.0},
          {254.0, 254.0},
          {2.0, 254.0},
        };

    double[][] quadB =
        new double[][] {
          {10.0, 10.0},
          {20.0, 10.0},
          {20.0, 20.0},
          {10.0, 20.0},
        };

    DocQuadPostprocessor.PathChoice pc =
        DocQuadPostprocessor.choosePath(quadA, quadB, true, makeMaskLogitsAllTrue());

    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, pc.chosenSource());
    assertQuadEquals(quadA, pc.chosenQuad256());
  }

  @Test
  public void tieBreak_isDeterministic_chooseCorners() {
    double[][] quad =
        new double[][] {
          {2.0, 2.0},
          {254.0, 2.0},
          {254.0, 254.0},
          {2.0, 254.0},
        };

    DocQuadPostprocessor.PathChoice pc =
        DocQuadPostprocessor.choosePath(quad, quad, false, makeMaskLogitsAllTrue());

    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, pc.chosenSource());
    assertQuadEquals(quad, pc.chosenQuad256());
  }

  @Test
  public void cornersWin_whenMaskDisagreesSignificantly() {
    // CORNERS: normal quad
    double[][] quadCorners =
        new double[][] {
          {50.0, 50.0},
          {200.0, 50.0},
          {200.0, 200.0},
          {50.0, 200.0},
        };

    // MASK: significantly different quad (corner distance > 32px)
    double[][] quadMask =
        new double[][] {
          {10.0, 10.0},
          {240.0, 10.0},
          {240.0, 240.0},
          {10.0, 240.0},
        };

    DocQuadPostprocessor.PathChoice pc =
        DocQuadPostprocessor.choosePath(quadCorners, quadMask, false, makeMaskLogitsAllTrue());

    // CORNERS should win due to agreement check (max corner dist > 32px)
    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, pc.chosenSource());
    assertQuadEquals(quadCorners, pc.chosenQuad256());
  }

  @Test
  public void cornersWin_whenMaskNotClearlyBetter() {
    // Both quads are similar (within agreement threshold)
    double[][] quadCorners =
        new double[][] {
          {50.0, 50.0},
          {200.0, 50.0},
          {200.0, 200.0},
          {50.0, 200.0},
        };

    // MASK: slightly different but within 32px agreement threshold
    double[][] quadMask =
        new double[][] {
          {55.0, 55.0},
          {195.0, 55.0},
          {195.0, 195.0},
          {55.0, 195.0},
        };

    DocQuadPostprocessor.PathChoice pc =
        DocQuadPostprocessor.choosePath(quadCorners, quadMask, false, makeMaskLogitsAllTrue());

    // CORNERS should win because MASK is not clearly better (score margin)
    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, pc.chosenSource());
    assertQuadEquals(quadCorners, pc.chosenQuad256());
  }

  private static void assertQuadEquals(double[][] a, double[][] b) {
    assertEquals(4, a.length);
    assertEquals(4, b.length);
    for (int i = 0; i < 4; i++) {
      assertArrayEquals(a[i], b[i], 0.0);
    }
  }
}
