package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link DocQuadPostprocessor} — pure-math methods that require no Android
 * dependencies.
 */
public class DocQuadPostprocessorTest {

  // ── Helper: create a valid [1][1][64][64] mask filled with a constant logit ──
  private static float[][][][] makeMask(float logit) {
    float[][][][] m = new float[1][1][64][64];
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        m[0][0][y][x] = logit;
      }
    }
    return m;
  }

  // ── Helper: create a valid [1][4][64][64] corner heatmap with one hot peak per channel ──
  private static float[][][][] makeCornerHeatmaps(int[][] peaks) {
    float[][][][] hm = new float[1][4][64][64];
    for (int c = 0; c < 4; c++) {
      hm[0][c][peaks[c][1]][peaks[c][0]] = 10.0f; // strong peak
    }
    return hm;
  }

  // ── Helper: a "nice" quad in 256-space (roughly centered document) ──
  private static double[][] niceQuad256() {
    return new double[][] {{30, 30}, {220, 30}, {220, 220}, {30, 220}};
  }

  // ────────────────────────────────────────────────────────────────────────────
  // computeMaskStats
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void computeMaskStats_allNegative() {
    float[][][][] mask = makeMask(-10.0f); // sigmoid(-10) ≈ 0
    DocQuadPostprocessor.MaskStats stats = DocQuadPostprocessor.computeMaskStats(mask);
    assertEquals(0, stats.maskProbGt05Count());
    assertTrue(stats.maskProbMean() < 0.01);
  }

  @Test
  public void computeMaskStats_allPositive() {
    float[][][][] mask = makeMask(10.0f); // sigmoid(10) ≈ 1
    DocQuadPostprocessor.MaskStats stats = DocQuadPostprocessor.computeMaskStats(mask);
    assertEquals(64 * 64, stats.maskProbGt05Count());
    assertTrue(stats.maskProbMean() > 0.99);
  }

  @Test
  public void computeMaskStats_zeroLogit() {
    float[][][][] mask = makeMask(0.0f); // sigmoid(0) = 0.5 exactly → NOT > 0.5
    DocQuadPostprocessor.MaskStats stats = DocQuadPostprocessor.computeMaskStats(mask);
    assertEquals(0, stats.maskProbGt05Count());
    assertEquals(0.5, stats.maskProbMean(), 0.001);
  }

  @Test(expected = IllegalArgumentException.class)
  public void computeMaskStats_nullThrows() {
    DocQuadPostprocessor.computeMaskStats(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void computeMaskStats_wrongShapeThrows() {
    DocQuadPostprocessor.computeMaskStats(new float[1][1][32][32]);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // argmaxCorners64ToCorners256
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void argmaxCorners_singlePeakPerChannel() {
    // Peaks at (10,5), (50,5), (50,55), (10,55) in 64-space
    int[][] peaks = {{10, 5}, {50, 5}, {50, 55}, {10, 55}};
    float[][][][] hm = makeCornerHeatmaps(peaks);

    double[][] corners = DocQuadPostprocessor.argmaxCorners64ToCorners256(hm);

    assertEquals(4, corners.length);
    // Expected: (ix+0.5)*4.0
    assertEquals((10 + 0.5) * 4.0, corners[0][0], 1e-9);
    assertEquals((5 + 0.5) * 4.0, corners[0][1], 1e-9);
    assertEquals((50 + 0.5) * 4.0, corners[1][0], 1e-9);
    assertEquals((55 + 0.5) * 4.0, corners[3][1], 1e-9);
  }

  @Test
  public void argmaxCorners_tieBreakFirstInScanOrder() {
    // Two equal peaks at (0,0) and (63,63) — first in scan order wins
    float[][][][] hm = new float[1][4][64][64];
    hm[0][0][0][0] = 5.0f;
    hm[0][0][63][63] = 5.0f;
    // Fill other channels with a single peak
    for (int c = 1; c < 4; c++) {
      hm[0][c][32][32] = 10.0f;
    }

    double[][] corners = DocQuadPostprocessor.argmaxCorners64ToCorners256(hm);
    // Channel 0: (0,0) wins because strict '>' means first stays
    assertEquals(0.5 * 4.0, corners[0][0], 1e-9);
    assertEquals(0.5 * 4.0, corners[0][1], 1e-9);
  }

  @Test(expected = IllegalArgumentException.class)
  public void argmaxCorners_nullThrows() {
    DocQuadPostprocessor.argmaxCorners64ToCorners256(null);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // corners64ToCorners256 dispatch
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void corners64ToCorners256_argmaxMode() {
    int[][] peaks = {{10, 5}, {50, 5}, {50, 55}, {10, 55}};
    float[][][][] hm = makeCornerHeatmaps(peaks);

    double[][] result =
        DocQuadPostprocessor.corners64ToCorners256(hm, DocQuadPostprocessor.PeakMode.ARGMAX);
    // Should match argmax result
    double[][] expected = DocQuadPostprocessor.argmaxCorners64ToCorners256(hm);
    for (int i = 0; i < 4; i++) {
      assertArrayEquals(expected[i], result[i], 1e-9);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void corners64ToCorners256_nullModeThrows() {
    int[][] peaks = {{10, 5}, {50, 5}, {50, 55}, {10, 55}};
    DocQuadPostprocessor.corners64ToCorners256(makeCornerHeatmaps(peaks), null);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // refineCorners64ToCorners256_3x3
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void refineCorners_singlePeakMatchesArgmax() {
    // With a single isolated peak, refinement should be very close to argmax
    int[][] peaks = {{32, 32}, {32, 32}, {32, 32}, {32, 32}};
    float[][][][] hm = makeCornerHeatmaps(peaks);

    double[][] argmax = DocQuadPostprocessor.argmaxCorners64ToCorners256(hm);
    double[][] refined = DocQuadPostprocessor.refineCorners64ToCorners256_3x3(hm);

    for (int c = 0; c < 4; c++) {
      // With only one non-zero pixel, refinement centroid = that pixel
      assertEquals(argmax[c][0], refined[c][0], 0.1);
      assertEquals(argmax[c][1], refined[c][1], 0.1);
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // mapCorners256ToOriginal
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void mapCorners256ToOriginal_identityLetterbox() {
    // 256x256 image → no padding, scale=1
    DocQuadLetterbox lb = DocQuadLetterbox.create(256, 256);
    double[][] quad = niceQuad256();
    double[][] orig = DocQuadPostprocessor.mapCorners256ToOriginal(quad, lb);

    assertEquals(4, orig.length);
    for (int i = 0; i < 4; i++) {
      assertEquals(quad[i][0], orig[i][0], 0.5);
      assertEquals(quad[i][1], orig[i][1], 0.5);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void mapCorners256ToOriginal_nullQuadThrows() {
    DocQuadPostprocessor.mapCorners256ToOriginal(null, DocQuadLetterbox.create(256, 256));
  }

  @Test(expected = IllegalArgumentException.class)
  public void mapCorners256ToOriginal_nullLetterboxThrows() {
    DocQuadPostprocessor.mapCorners256ToOriginal(niceQuad256(), null);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // quadFromMask256
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void quadFromMask256_emptyMaskReturnsFallback() {
    float[][][][] mask = makeMask(-10.0f); // all below 0.5
    double[][] fallback = niceQuad256();

    DocQuadPostprocessor.QuadFromMask result = DocQuadPostprocessor.quadFromMask256(mask, fallback);

    assertTrue(result.usedFallback());
    assertArrayEquals(fallback[0], result.quad256()[0], 1e-9);
  }

  @Test
  public void quadFromMask256_fullMaskProducesQuad() {
    float[][][][] mask = makeMask(10.0f); // all above 0.5
    double[][] fallback = niceQuad256();

    DocQuadPostprocessor.QuadFromMask result = DocQuadPostprocessor.quadFromMask256(mask, fallback);

    // Should NOT use fallback since mask is full
    // Result quad should cover roughly the full 256-space
    assertNotNull(result.quad256());
    assertEquals(4, result.quad256().length);
  }

  @Test(expected = IllegalArgumentException.class)
  public void quadFromMask256_nullFallbackThrows() {
    DocQuadPostprocessor.quadFromMask256(makeMask(0.0f), null);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // choosePath
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void choosePath_fallbackMaskReturnsCornersSource() {
    double[][] quad = niceQuad256();
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(quad, quad, true, mask);

    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, choice.chosenSource());
    assertEquals(Double.POSITIVE_INFINITY, choice.penaltyMask(), 0.0);
  }

  @Test
  public void choosePath_identicalQuadsPreferCorners() {
    double[][] quad = niceQuad256();
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(quad, quad, false, mask);

    // When quads are identical, CORNERS is default
    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, choice.chosenSource());
  }

  @Test
  public void choosePath_cornersHardPenalty_maskValid_prefersMask() {
    // CORNERS quad with severe geometric issues (self-intersecting bowtie)
    double[][] badCorners = {
      {10, 10}, {240, 240}, {240, 10}, {10, 240} // bowtie → self-intersects
    };
    double[][] goodMask = niceQuad256();
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(badCorners, goodMask, false, mask);

    assertEquals(DocQuadPostprocessor.ChosenSource.MASK, choice.chosenSource());
  }

  @Test
  public void choosePath_maskHardPenalty_prefersCorners() {
    // MASK quad with severe geometric issues (self-intersecting bowtie)
    double[][] goodCorners = niceQuad256();
    double[][] badMask = {
      {10, 10}, {240, 240}, {240, 10}, {10, 240} // bowtie → self-intersects
    };
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(goodCorners, badMask, false, mask);

    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, choice.chosenSource());
  }

  @Test
  public void choosePath_highDisagreement_prefersCorners() {
    // MASK quad far from CORNERS (>32px max corner distance)
    double[][] corners = niceQuad256();
    double[][] farMask = {{10 + 50, 10 + 50}, {240 + 50, 10 + 50}, {240 + 50, 240}, {10 + 50, 240}};
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(corners, farMask, false, mask);

    assertEquals(DocQuadPostprocessor.ChosenSource.CORNERS, choice.chosenSource());
  }

  @Test
  public void choosePath_bothPenaltiesFinite() {
    double[][] quad = niceQuad256();
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.PathChoice choice =
        DocQuadPostprocessor.choosePath(quad, quad, false, mask);

    assertTrue(Double.isFinite(choice.penaltyCorners()));
    assertTrue(Double.isFinite(choice.penaltyMask()));
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Full postprocess pipeline
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  public void postprocess_smokeTest() {
    // Peaks roughly at TL, TR, BR, BL in 64-space
    int[][] peaks = {{8, 8}, {55, 8}, {55, 55}, {8, 55}};
    float[][][][] hm = makeCornerHeatmaps(peaks);
    float[][][][] mask = makeMask(10.0f);

    DocQuadPostprocessor.Result result = DocQuadPostprocessor.postprocess(hm, mask, null);

    assertNotNull(result);
    assertNotNull(result.corners256());
    assertEquals(4, result.corners256().length);
    assertNotNull(result.chosenQuad256());
    assertNotNull(result.chosenSource());
  }

  @Test
  public void postprocess_withLetterbox() {
    int[][] peaks = {{8, 8}, {55, 8}, {55, 55}, {8, 55}};
    float[][][][] hm = makeCornerHeatmaps(peaks);
    float[][][][] mask = makeMask(10.0f);
    DocQuadLetterbox lb = DocQuadLetterbox.create(1920, 1080);

    DocQuadPostprocessor.Result result = DocQuadPostprocessor.postprocess(hm, mask, lb);

    assertNotNull(result);
    assertNotNull(result.cornersOriginal());
    assertEquals(4, result.cornersOriginal().length);
  }
}
