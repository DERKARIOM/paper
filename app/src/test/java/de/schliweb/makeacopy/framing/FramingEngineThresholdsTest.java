package de.schliweb.makeacopy.framing;

import android.graphics.PointF;
import org.junit.Assert;
import org.junit.Test;

/**
 * Threshold-oriented tests for FramingEngine covering translation, scale, and tilt hint selection
 * using synthetic, upright quads. These tests assume the simple heuristic thresholds embedded in
 * FramingEngine.
 */
public class FramingEngineThresholdsTest {

  private static final int W = 1000;
  private static final int H = 800;

  private static FramingEngine.Input makeInput(PointF[] quad) {
    // Provide a deterministic model height override for stable expectations
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
    // Clockwise: TL, TR, BR, BL
    return new PointF[] {pt(left, top), pt(right, top), pt(right, bottom), pt(left, bottom)};
  }

  @Test
  public void testMoveRightWhenDxBeyondThreshold() {
    FramingEngine engine = new FramingEngine();

    // Model height ~0.70*H = 560. Use same height to avoid scale-based hints.
    float modelH = 0.70f * H; // 560
    float aspect = (float) W / (float) H; // use image aspect
    float w = modelH * aspect; // 560 * 1.25 = 700

    float cx = W * 0.5f + 80f; // shift right by 80px (>|0.06*W|=60)
    float cy = H * 0.5f;
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - modelH / 2f;
    float bottom = cy + modelH / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, r.hint);
    Assert.assertTrue(r.hasDocument);
  }

  @Test
  public void testMoveUpWhenDyBeyondThreshold() {
    FramingEngine engine = new FramingEngine();

    float modelH = 0.70f * H;
    float aspect = (float) W / (float) H;
    float w = modelH * aspect;

    float cx = W * 0.5f;
    float cy = H * 0.5f - 60f; // move up by 60px (>|0.06*H|=48)
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - modelH / 2f;
    float bottom = cy + modelH / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    Assert.assertEquals(GuidanceHint.MOVE_UP, r.hint);
    Assert.assertTrue(r.hasDocument);
  }

  @Test
  public void testMoveBackWhenScaleTooLarge() {
    FramingEngine engine = new FramingEngine();

    // Create a tall rect so that qBounds.height > 1.2 * modelH (modelH=560 -> threshold 672)
    float h = 750f;
    float aspect = (float) W / (float) H;
    float w = h * aspect;

    float cx = W * 0.5f;
    float cy = H * 0.5f;
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - h / 2f;
    float bottom = cy + h / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    Assert.assertEquals(GuidanceHint.MOVE_BACK, r.hint);
    Assert.assertTrue(r.hasDocument);
  }

  @Test
  public void testMoveCloserWhenScaleTooSmall() {
    FramingEngine engine = new FramingEngine();

    // Height smaller than 0.85*modelH (<=476)
    float h = 400f;
    float aspect = (float) W / (float) H;
    float w = h * aspect;

    float cx = W * 0.5f;
    float cy = H * 0.5f;
    float left = cx - w / 2f;
    float right = cx + w / 2f;
    float top = cy - h / 2f;
    float bottom = cy + h / 2f;

    FramingResult r = engine.evaluate(makeInput(rectQuad(left, top, right, bottom)));
    // Tilt is ~0 here; translation ~0; should pick MOVE_CLOSER
    Assert.assertEquals(GuidanceHint.MOVE_CLOSER, r.hint);
    Assert.assertTrue(r.hasDocument);
  }

  @Test
  public void testTiltLeftWhenLeftEdgeLonger() {
    // Construct trapezoid where left edge is significantly longer than right edge.
    // TL(300,200), TR(700,220), BR(680,520), BL(300,600)
    PointF[] q = new PointF[] {pt(300, 200), pt(700, 220), pt(680, 520), pt(300, 600)};

    FramingEngine engine = new FramingEngine();
    FramingResult r = engine.evaluate(makeInput(q));
    Assert.assertEquals(GuidanceHint.TILT_LEFT, r.hint);
    Assert.assertTrue(r.tiltHorizontal > 0.10f);
    Assert.assertTrue(r.hasDocument);
  }

  @Test
  public void testTiltForwardWhenTopEdgeLonger() {
    // Top edge much longer than bottom edge → TILT_FORWARD according to engine logic
    // TL(300,200), TR(700,200)  -> top length 400
    // BR(520,600), BL(480,600)  -> bottom length 40
    PointF[] q = new PointF[] {pt(300, 200), pt(700, 200), pt(520, 600), pt(480, 600)};

    FramingEngine engine = new FramingEngine();
    FramingResult r = engine.evaluate(makeInput(q));
    Assert.assertEquals(GuidanceHint.TILT_FORWARD, r.hint);
    Assert.assertTrue(r.tiltVertical > 0.10f);
    Assert.assertTrue(r.hasDocument);
  }
}
