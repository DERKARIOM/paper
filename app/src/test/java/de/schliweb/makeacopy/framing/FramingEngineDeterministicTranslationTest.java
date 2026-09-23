package de.schliweb.makeacopy.framing;

import android.graphics.PointF;
import org.junit.Assert;
import org.junit.Test;

/**
 * Deterministic translation-only tests for FramingEngine using the modelHeightOverride to avoid
 * dependency on Android RectF. These tests ensure translation hints are selected when exceeding the
 * SHIFT_OK threshold with zero tilt and neutral scale.
 */
public class FramingEngineDeterministicTranslationTest {

  private static final int W = 1000;
  private static final int H = 800;

  private static FramingEngine.Input makeInput(PointF[] quad) {
    // Use deterministic model height equal to 70% of image height as in engine
    float modelH = 0.70f * H;
    return new FramingEngine.Input(W, H, quad, null, modelH);
  }

  private static PointF pt(float x, float y) {
    PointF p = new PointF();
    p.x = x;
    p.y = y;
    return p;
  }

  private static PointF[] rectQuad(float left, float top, float right, float bottom) {
    // Clockwise TL, TR, BR, BL
    return new PointF[] {pt(left, top), pt(right, top), pt(right, bottom), pt(left, bottom)};
  }

  @Test
  public void moveRight_whenDxBeyondThreshold_andScaleMatches_andNoTilt() {
    FramingEngine engine = new FramingEngine();
    float modelH = 0.70f * H; // 560
    float aspect = (float) W / (float) H; // 1.25
    float w = modelH * aspect; // 700

    // Shift center to the right by 80px → dxNorm ≈ 0.16 (> 0.12 threshold)
    float cx = W * 0.5f + 80f;
    float cy = H * 0.5f;
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - modelH / 2f;
    float bottom = cy + modelH / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, r.hint);
    Assert.assertTrue(r.hasDocument);
    Assert.assertTrue(Math.abs(r.tiltHorizontal) < 1e-3);
    Assert.assertTrue(Math.abs(r.tiltVertical) < 1e-3);
    // Scale should be ~1
    Assert.assertEquals(1.0f, r.scaleRatio, 1e-3f);
  }

  @Test
  public void moveUp_whenDyBeyondThreshold_andScaleMatches_andNoTilt() {
    FramingEngine engine = new FramingEngine();
    float modelH = 0.70f * H; // 560
    float aspect = (float) W / (float) H; // 1.25
    float w = modelH * aspect; // 700

    // Shift center up by 60px → dyNorm ≈ -0.15 (< -0.12 threshold)
    float cx = W * 0.5f;
    float cy = H * 0.5f - 60f;
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - modelH / 2f;
    float bottom = cy + modelH / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    Assert.assertEquals(GuidanceHint.MOVE_UP, r.hint);
    Assert.assertTrue(r.hasDocument);
    Assert.assertTrue(Math.abs(r.tiltHorizontal) < 1e-3);
    Assert.assertTrue(Math.abs(r.tiltVertical) < 1e-3);
    // Scale should be ~1
    Assert.assertEquals(1.0f, r.scaleRatio, 1e-3f);
  }
}
