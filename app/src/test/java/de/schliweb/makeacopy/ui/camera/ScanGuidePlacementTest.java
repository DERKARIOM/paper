package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertEquals;

import de.schliweb.makeacopy.ui.camera.ScanGuideOverlay.Placement;
import org.junit.Test;

/** Placement rule of the scan framing guide (detected outline vs. fixed frame). */
public class ScanGuidePlacementTest {

  // Frame: 100..500 x 100..666 (A4-like portrait)
  private static final float L = 100f;
  private static final float T = 100f;
  private static final float R = 500f;
  private static final float B = 666f;

  private static Placement eval(float[] xs, float[] ys) {
    return ScanGuideOverlay.evaluate(xs, ys, L, T, R, B);
  }

  @Test
  public void noCornersMeansNothingDetected() {
    assertEquals(Placement.NONE, ScanGuideOverlay.evaluate(null, null, L, T, R, B));
    assertEquals(
        Placement.NONE, ScanGuideOverlay.evaluate(new float[3], new float[3], L, T, R, B));
  }

  @Test
  public void documentFillingTheFrameIsGood() {
    float[] xs = {110, 490, 495, 105};
    float[] ys = {115, 110, 650, 655};
    assertEquals(Placement.GOOD, eval(xs, ys));
  }

  @Test
  public void slightOverflowIsTolerated() {
    // 20 px outside on a 400 px wide frame (5 %) is still fine.
    float[] xs = {80, 520, 520, 80};
    float[] ys = {90, 90, 680, 680};
    assertEquals(Placement.GOOD, eval(xs, ys));
  }

  @Test
  public void documentStickingOutIsOutside() {
    float[] xs = {10, 400, 400, 10};
    float[] ys = {150, 150, 600, 600};
    assertEquals(Placement.OUTSIDE, eval(xs, ys));
  }

  @Test
  public void smallDocumentIsTooFar() {
    // 150 x 200 inside a 400 x 566 frame: ~13 % of the frame area.
    float[] xs = {220, 370, 370, 220};
    float[] ys = {280, 280, 480, 480};
    assertEquals(Placement.TOO_SMALL, eval(xs, ys));
  }
}
