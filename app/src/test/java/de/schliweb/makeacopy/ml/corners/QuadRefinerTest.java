package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Edge-based sub-pixel refinement and confidence, on synthetic scenes (pure JVM). */
public class QuadRefinerTest {
  private static final int W = 270, H = 360; // half the live analysis size: fast tests

  @Test
  public void snapsDetectorOutputOntoTheRealCornersWithSubPixelPrecision() {
    for (int k = 0; k < 4; k++) {
      double[][] truth = SyntheticScene.page(W, H, -12 + 8 * k, 0.7, 0.08);
      SyntheticScene s = SyntheticScene.render(W, H, truth, 235, 110, 25, 3, 11 + k);
      double[][] start = SyntheticScene.perturb(truth, 5, 100 + k); // detector-like error
      QuadRefiner.Result r = QuadRefiner.refine(s.image(), start, 8, 2);
      double err = SyntheticScene.maxError(r.quad, truth);
      assertTrue("corner error " + err + " px (rotation " + (-12 + 8 * k) + "°)", err < 0.8);
      assertTrue("all sides supported: min " + r.minSupport(), r.minSupport() > 0.8);
    }
  }

  @Test
  public void worksForStronglyRotatedPages() {
    double[][] truth = SyntheticScene.page(W, H, 38, 0.6, 0.0);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 230, 60, 10, 3, 5);
    QuadRefiner.Result r =
        QuadRefiner.refine(s.image(), SyntheticScene.perturb(truth, 5, 9), 8, 2);
    assertTrue(SyntheticScene.maxError(r.quad, truth) < 0.8);
  }

  @Test
  public void darkPageOnBrightTableHasTheOppositePolarity() {
    double[][] truth = SyntheticScene.page(W, H, 7, 0.7, 0.05);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 70, 215, 0, 3, 3);
    QuadRefiner.Result r =
        QuadRefiner.refine(s.image(), SyntheticScene.perturb(truth, 5, 4), 8, 2);
    assertTrue(SyntheticScene.maxError(r.quad, truth) < 0.8);
  }

  @Test
  public void lowContrastWhitePageOnWhiteTable() {
    double[][] truth = SyntheticScene.page(W, H, 10, 0.7, 0.05);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 252, 236, 0, 2.5, 8);
    QuadRefiner.Result r =
        QuadRefiner.refine(s.image(), SyntheticScene.perturb(truth, 4, 5), 8, 2);
    assertTrue(SyntheticScene.maxError(r.quad, truth) < 1.2);
  }

  @Test
  public void paperEdgeWinsOverAPrintedFrameJustInside() {
    // bordered flyer: a dark printed frame 3% inside the paper edge; the detector's outline sits
    // between both edges
    double[][] truth = SyntheticScene.page(W, H, 6, 0.72, 0.05);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 240, 95, 15, 3, 31, 0.03);
    double[][] start = QuadGeometry.copy(truth);
    double cx = 0, cy = 0;
    for (double[] p : truth) {
      cx += p[0] / 4;
      cy += p[1] / 4;
    }
    for (double[] p : start) { // shrink by 3% toward the center
      p[0] = cx + (p[0] - cx) * 0.97;
      p[1] = cy + (p[1] - cy) * 0.97;
    }
    QuadRefiner.Result r = QuadRefiner.refine(s.image(), start, 8, 2);
    double err = SyntheticScene.maxError(r.quad, truth);
    assertTrue("corner error " + err, err < 1.0);
  }

  @Test
  public void guessedOutlineOnAnEmptyTableHasNoEdgeSupportAndNoConfidence() {
    double[][] guess = SyntheticScene.page(W, H, 5, 0.65, 0.05);
    SyntheticScene s = SyntheticScene.render(W, H, null, 0, 110, 25, 3, 21);
    QuadRefiner.Result r = QuadRefiner.refine(s.image(), guess, 8, 2);
    assertTrue("mean support " + r.meanSupport(), r.meanSupport() < 0.3);
    assertTrue(QuadScorer.confidence(r, W, H, null) < QuadScorer.KEEP);
  }

  @Test
  public void realPageScoresAboveTheDisplayThreshold() {
    double[][] truth = SyntheticScene.page(W, H, 4, 0.7, 0.06);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 235, 110, 25, 3, 13);
    QuadRefiner.Result r =
        QuadRefiner.refine(s.image(), SyntheticScene.perturb(truth, 4, 14), 8, 2);
    double c = QuadScorer.confidence(r, W, H, null);
    assertTrue("confidence " + c, c >= QuadScorer.SHOW);
  }

  @Test
  public void sideOffTheRealEdgeIsNotSupported() {
    double[][] truth = SyntheticScene.page(W, H, 0, 0.7, 0.0);
    SyntheticScene s = SyntheticScene.render(W, H, truth, 235, 110, 0, 3, 17);
    // shift the right side 25 px inside the page: beyond the search radius
    double[][] wrong = QuadGeometry.copy(truth);
    wrong[1][0] -= 25;
    wrong[2][0] -= 25;
    QuadRefiner.Result r = QuadRefiner.refine(s.image(), wrong, 6, 1);
    assertTrue("right side support " + r.sideSupport[1], r.sideSupport[1] < 0.3);
    assertTrue(r.sideSupport[0] > 0.5 && r.sideSupport[3] > 0.8);
  }

  @Test
  public void maskAgreementUsesTheLetterboxMapping() {
    // image 256x256 = network input (scale 1, no offset); mask marks the left half as document
    float[] prob = new float[64 * 64];
    for (int y = 0; y < 64; y++) for (int x = 0; x < 32; x++) prob[y * 64 + x] = 0.9f;
    QuadScorer.ModelMask m = new QuadScorer.ModelMask(prob, 1.0, 0, 0);
    double[][] left = {{0, 0}, {128, 0}, {128, 256}, {0, 256}};
    double[][] right = {{128, 0}, {256, 0}, {256, 256}, {128, 256}};
    assertEquals(1.0, QuadScorer.maskIoU(left, m), 0.03);
    assertEquals(0.0, QuadScorer.maskIoU(right, m), 0.03);
  }
}
