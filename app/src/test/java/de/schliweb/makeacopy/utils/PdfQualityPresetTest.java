package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import de.schliweb.makeacopy.utils.export.PdfQualityPreset;
import org.junit.Test;

public class PdfQualityPresetTest {

  @Test
  public void enumValues_haveExpectedDefaults() {
    assertEquals(300, PdfQualityPreset.HIGH.targetDpi);
    assertEquals(85, PdfQualityPreset.HIGH.jpegQuality);
    assertFalse(PdfQualityPreset.HIGH.forceGrayscale);

    assertEquals(200, PdfQualityPreset.STANDARD.targetDpi);
    assertEquals(150, PdfQualityPreset.SMALL.targetDpi);
    assertEquals(110, PdfQualityPreset.VERY_SMALL.targetDpi);
  }

  @Test
  public void fromName_exactMatch() {
    assertEquals(
        PdfQualityPreset.HIGH, PdfQualityPreset.fromName("HIGH", PdfQualityPreset.STANDARD));
  }

  @Test
  public void fromName_caseInsensitive() {
    assertEquals(
        PdfQualityPreset.SMALL, PdfQualityPreset.fromName("small", PdfQualityPreset.STANDARD));
    assertEquals(
        PdfQualityPreset.VERY_SMALL,
        PdfQualityPreset.fromName("Very_Small", PdfQualityPreset.STANDARD));
  }

  @Test
  public void fromName_trimmed() {
    assertEquals(
        PdfQualityPreset.HIGH, PdfQualityPreset.fromName("  HIGH  ", PdfQualityPreset.STANDARD));
  }

  @Test
  public void fromName_nullReturnsDefault() {
    assertEquals(
        PdfQualityPreset.STANDARD, PdfQualityPreset.fromName(null, PdfQualityPreset.STANDARD));
  }

  @Test
  public void fromName_invalidReturnsDefault() {
    assertEquals(
        PdfQualityPreset.STANDARD, PdfQualityPreset.fromName("UNKNOWN", PdfQualityPreset.STANDARD));
    assertEquals(PdfQualityPreset.HIGH, PdfQualityPreset.fromName("", PdfQualityPreset.HIGH));
  }

  @Test
  public void allPresetsHavePositiveDpiAndQuality() {
    for (PdfQualityPreset p : PdfQualityPreset.values()) {
      assert p.targetDpi > 0 : "targetDpi must be positive for " + p;
      assert p.jpegQuality > 0 && p.jpegQuality <= 100 : "jpegQuality out of range for " + p;
    }
  }
}
