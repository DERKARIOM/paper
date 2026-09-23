package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DocQuadLetterboxTest {

  // ── Existing roundtrip test ──────────────────────────────────────────────

  @Test
  public void roundtrip_forward_inverse_isIdentity() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(1000, 500, 256, 256);

    double[][] pts =
        new double[][] {
          {0.0, 0.0},
          {999.0, 0.0},
          {999.0, 499.0},
          {0.0, 499.0},
          {123.4, 321.0},
        };

    for (double[] p : pts) {
      double[] q = lb.forward(p[0], p[1]);
      double[] r = lb.inverse(q[0], q[1]);
      assertTrue(Math.abs(r[0] - p[0]) < 1e-9);
      assertTrue(Math.abs(r[1] - p[1]) < 1e-9);
    }
  }

  // ── create(srcW, srcH) convenience ────────────────────────────────────────

  @Test
  public void create_twoArg_defaultsDstTo256() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(640, 480);
    assertEquals(256, lb.dstW);
    assertEquals(256, lb.dstH);
  }

  // ── Landscape source (wider than tall) ────────────────────────────────────

  @Test
  public void create_landscape_scaleByWidth() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(1000, 500, 256, 256);
    // scale = min(256/1000, 256/500) = 0.256
    assertEquals(0.256, lb.scale, 1e-9);
    assertEquals(0.0, lb.offsetX, 1e-9);
    // offsetY = (256 - 500*0.256) / 2 = (256 - 128) / 2 = 64
    assertEquals(64.0, lb.offsetY, 1e-9);
  }

  // ── Portrait source (taller than wide) ────────────────────────────────────

  @Test
  public void create_portrait_scaleByHeight() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(500, 1000, 256, 256);
    // scale = min(256/500, 256/1000) = 0.256
    assertEquals(0.256, lb.scale, 1e-9);
    // offsetX = (256 - 500*0.256) / 2 = 64
    assertEquals(64.0, lb.offsetX, 1e-9);
    assertEquals(0.0, lb.offsetY, 1e-9);
  }

  // ── Square source ─────────────────────────────────────────────────────────

  @Test
  public void create_square_noOffset() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(512, 512, 256, 256);
    assertEquals(0.5, lb.scale, 1e-9);
    assertEquals(0.0, lb.offsetX, 1e-9);
    assertEquals(0.0, lb.offsetY, 1e-9);
  }

  // ── Identity (src == dst) ─────────────────────────────────────────────────

  @Test
  public void create_identity_scaleOne() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(256, 256, 256, 256);
    assertEquals(1.0, lb.scale, 1e-9);
    assertEquals(0.0, lb.offsetX, 1e-9);
    assertEquals(0.0, lb.offsetY, 1e-9);
  }

  // ── forward ───────────────────────────────────────────────────────────────

  @Test
  public void forward_origin_returnsOffset() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(500, 1000, 256, 256);
    double[] result = lb.forward(0, 0);
    assertEquals(lb.offsetX, result[0], 1e-9);
    assertEquals(lb.offsetY, result[1], 1e-9);
  }

  @Test
  public void forward_bottomRight_landscape() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(1000, 500, 256, 256);
    double[] result = lb.forward(1000, 500);
    // x = 1000 * 0.256 + 0 = 256
    assertEquals(256.0, result[0], 1e-9);
    // y = 500 * 0.256 + 64 = 192
    assertEquals(192.0, result[1], 1e-9);
  }

  // ── inverse ───────────────────────────────────────────────────────────────

  @Test
  public void inverse_offset_returnsOrigin() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(500, 1000, 256, 256);
    double[] result = lb.inverse(lb.offsetX, lb.offsetY);
    assertEquals(0.0, result[0], 1e-9);
    assertEquals(0.0, result[1], 1e-9);
  }

  @Test
  public void inverse_center_returnsSourceCenter() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(1000, 500, 256, 256);
    double[] result = lb.inverse(128.0, 128.0);
    assertEquals(500.0, result[0], 1e-9);
    assertEquals(250.0, result[1], 1e-9);
  }

  // ── Fields stored correctly ───────────────────────────────────────────────

  @Test
  public void create_fieldsStored() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(640, 480, 256, 256);
    assertEquals(640, lb.srcW);
    assertEquals(480, lb.srcH);
    assertEquals(256, lb.dstW);
    assertEquals(256, lb.dstH);
  }

  // ── Non-square destination ────────────────────────────────────────────────

  @Test
  public void create_nonSquareDst_landscape() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(800, 600, 400, 200);
    // scale = min(400/800, 200/600) = min(0.5, 0.333..) = 0.333..
    double expectedScale = 200.0 / 600.0;
    assertEquals(expectedScale, lb.scale, 1e-9);
    // offsetX = (400 - 800 * scale) / 2
    double expectedOffsetX = (400.0 - 800.0 * expectedScale) / 2.0;
    assertEquals(expectedOffsetX, lb.offsetX, 1e-9);
    assertEquals(0.0, lb.offsetY, 1e-9);
  }

  // ── Roundtrip with portrait ───────────────────────────────────────────────

  @Test
  public void roundtrip_portrait() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(480, 640);
    double[] fwd = lb.forward(240, 320);
    double[] inv = lb.inverse(fwd[0], fwd[1]);
    assertEquals(240.0, inv[0], 1e-9);
    assertEquals(320.0, inv[1], 1e-9);
  }

  // ── Invalid arguments ─────────────────────────────────────────────────────

  @Test(expected = IllegalArgumentException.class)
  public void create_zeroSrcW_throws() {
    DocQuadLetterbox.create(0, 100, 256, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_zeroSrcH_throws() {
    DocQuadLetterbox.create(100, 0, 256, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_negativeSrcW_throws() {
    DocQuadLetterbox.create(-1, 100, 256, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_zeroDstW_throws() {
    DocQuadLetterbox.create(100, 100, 0, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_zeroDstH_throws() {
    DocQuadLetterbox.create(100, 100, 256, 0);
  }

  // ── Very large source ─────────────────────────────────────────────────────

  @Test
  public void create_veryLargeSource_scalesDown() {
    DocQuadLetterbox lb = DocQuadLetterbox.create(10000, 10000, 256, 256);
    assertEquals(0.0256, lb.scale, 1e-9);
    assertEquals(0.0, lb.offsetX, 1e-9);
    assertEquals(0.0, lb.offsetY, 1e-9);
  }
}
