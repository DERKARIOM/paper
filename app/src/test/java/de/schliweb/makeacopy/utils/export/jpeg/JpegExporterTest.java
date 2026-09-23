package de.schliweb.makeacopy.utils.export.jpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.utils.image.DocumentCleanupMode;
import de.schliweb.makeacopy.utils.image.DocumentCleanupOptions;
import org.junit.Test;

public class JpegExporterTest {

  @Test
  public void resolveJpegImageOutput_blackWhiteDominatesGrayscale() {
    JpegExportOptions options = new JpegExportOptions();
    options.mode = JpegExportOptions.Mode.BW_TEXT;
    options.forceGrayscaleJpeg = true;

    assertEquals(JpegExporter.JpegImageOutput.BLACK_WHITE, JpegExporter.resolveJpegImageOutput(options));
  }

  @Test
  public void resolveJpegImageOutput_grayscaleWhenForced() {
    JpegExportOptions options = new JpegExportOptions();
    options.forceGrayscaleJpeg = true;

    assertEquals(JpegExporter.JpegImageOutput.GRAYSCALE, JpegExporter.resolveJpegImageOutput(options));
  }

  @Test
  public void resolveJpegImageOutput_colorByDefault() {
    assertEquals(
        JpegExporter.JpegImageOutput.COLOR,
        JpegExporter.resolveJpegImageOutput(new JpegExportOptions()));
  }

  @Test
  public void cleanTextGrayscaleDoesNotUseOcrBinarization() {
    DocumentCleanupOptions options =
        JpegExporter.buildCleanupOptions(DocumentCleanupMode.CLEAN_TEXT, false, false);

    assertEquals(DocumentCleanupMode.CLEAN_TEXT, options.mode);
    assertFalse(options.preserveColor);
    assertFalse(options.optimizeForOcr);
  }

  @Test
  public void cleanTextBlackWhiteDoesNotUseOcrBinarizationBeforeFinalOutput() {
    DocumentCleanupOptions options =
        JpegExporter.buildCleanupOptions(DocumentCleanupMode.CLEAN_TEXT, false, true);

    assertEquals(DocumentCleanupMode.CLEAN_TEXT, options.mode);
    assertFalse(options.preserveColor);
    assertFalse(options.optimizeForOcr);
  }

  @Test
  public void blackWhiteOutputUsesNonColorCleanupOptionsForAllCleanupModes() {
    for (DocumentCleanupMode mode : DocumentCleanupMode.values()) {
      DocumentCleanupOptions options = JpegExporter.buildCleanupOptions(mode, false, true);

      assertEquals(mode, options.mode);
      assertFalse(options.preserveColor);
      assertEquals(mode != DocumentCleanupMode.CLEAN_TEXT, options.optimizeForOcr);
    }
  }
}