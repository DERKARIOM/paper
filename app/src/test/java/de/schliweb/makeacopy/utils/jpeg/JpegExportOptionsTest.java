package de.schliweb.makeacopy.utils.jpeg;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.export.jpeg.JpegExportOptions;
import org.junit.Test;

public class JpegExportOptionsTest {

  @Test
  public void defaultConstructor_setsDefaults() {
    JpegExportOptions opts = new JpegExportOptions();
    assertEquals(85, opts.quality);
    assertEquals(0, opts.longEdgePx);
    assertEquals(JpegExportOptions.Mode.NONE, opts.mode);
    assertFalse(opts.forceGrayscaleJpeg);
    assertEquals(4096, opts.maxLongEdgeGuardPx);
    assertTrue(opts.roundResizeToMultipleOf8);
  }

  @Test
  public void threeArgConstructor_setsValues() {
    JpegExportOptions opts = new JpegExportOptions(90, 2048, JpegExportOptions.Mode.BW_TEXT);
    assertEquals(90, opts.quality);
    assertEquals(2048, opts.longEdgePx);
    assertEquals(JpegExportOptions.Mode.BW_TEXT, opts.mode);
  }

  @Test
  public void threeArgConstructor_clampsQualityHigh() {
    JpegExportOptions opts = new JpegExportOptions(150, 0, JpegExportOptions.Mode.NONE);
    assertEquals(100, opts.quality);
  }

  @Test
  public void threeArgConstructor_clampsQualityLow() {
    JpegExportOptions opts = new JpegExportOptions(-10, 0, JpegExportOptions.Mode.NONE);
    assertEquals(0, opts.quality);
  }

  @Test
  public void threeArgConstructor_negativeLongEdge_becomesZero() {
    JpegExportOptions opts = new JpegExportOptions(85, -500, JpegExportOptions.Mode.NONE);
    assertEquals(0, opts.longEdgePx);
  }

  @Test
  public void threeArgConstructor_nullMode_defaultsToNone() {
    JpegExportOptions opts = new JpegExportOptions(85, 0, null);
    assertEquals(JpegExportOptions.Mode.NONE, opts.mode);
  }

  @Test
  public void sixArgConstructor_setsAllFields() {
    JpegExportOptions opts =
        new JpegExportOptions(50, 1024, JpegExportOptions.Mode.BW_TEXT, true, 8192, false);
    assertEquals(50, opts.quality);
    assertEquals(1024, opts.longEdgePx);
    assertEquals(JpegExportOptions.Mode.BW_TEXT, opts.mode);
    assertTrue(opts.forceGrayscaleJpeg);
    assertEquals(8192, opts.maxLongEdgeGuardPx);
    assertFalse(opts.roundResizeToMultipleOf8);
  }

  @Test
  public void sixArgConstructor_clampsAndNormalizes() {
    JpegExportOptions opts = new JpegExportOptions(200, -1, null, false, -100, true);
    assertEquals(100, opts.quality);
    assertEquals(0, opts.longEdgePx);
    assertEquals(JpegExportOptions.Mode.NONE, opts.mode);
    assertEquals(0, opts.maxLongEdgeGuardPx);
  }

  @Test
  public void modeEnum_allValues() {
    JpegExportOptions.Mode[] modes = JpegExportOptions.Mode.values();
    assertEquals(2, modes.length);
    assertEquals(JpegExportOptions.Mode.NONE, JpegExportOptions.Mode.valueOf("NONE"));
    assertEquals(JpegExportOptions.Mode.BW_TEXT, JpegExportOptions.Mode.valueOf("BW_TEXT"));
  }

  @Test
  public void qualityBoundary_zero() {
    JpegExportOptions opts = new JpegExportOptions(0, 0, JpegExportOptions.Mode.NONE);
    assertEquals(0, opts.quality);
  }

  @Test
  public void qualityBoundary_hundred() {
    JpegExportOptions opts = new JpegExportOptions(100, 0, JpegExportOptions.Mode.NONE);
    assertEquals(100, opts.quality);
  }
}
