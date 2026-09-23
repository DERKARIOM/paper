package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import org.junit.Test;

/**
 * Tests for {@link OCRPostProcessor#applyGermanCorrections(String)} and related German-specific OCR
 * correction logic.
 */
public class OCRPostProcessorGermanTest {

  // ── applyGermanCorrections ──

  @Test
  public void applyGermanCorrections_null_returnsNull() {
    assertNull(OCRPostProcessor.applyGermanCorrections(null));
  }

  @Test
  public void applyGermanCorrections_empty_returnsEmpty() {
    assertEquals("", OCRPostProcessor.applyGermanCorrections(""));
  }

  @Test
  public void applyGermanCorrections_noChange_englishText() {
    assertEquals("Hello World", OCRPostProcessor.applyGermanCorrections("Hello World"));
  }

  @Test
  public void applyGermanCorrections_uppercaseB_betweenLowercase_becomesEszett() {
    // "StraBe" → "Straße" (common OCR error: ß misread as uppercase B)
    assertEquals("Straße", OCRPostProcessor.applyGermanCorrections("StraBe"));
  }

  @Test
  public void applyGermanCorrections_lowercaseB_betweenLowercase_unchanged() {
    // "Strabe" has lowercase 'b' — not an OCR error pattern, should stay unchanged
    assertEquals("Strabe", OCRPostProcessor.applyGermanCorrections("Strabe"));
  }

  @Test
  public void applyGermanCorrections_multipleLowercaseB_allCorrected() {
    // "groBe StraBe" → "große Straße"
    assertEquals("große Straße", OCRPostProcessor.applyGermanCorrections("groBe StraBe"));
  }

  @Test
  public void applyGermanCorrections_uppercaseB_atWordStart_unchanged() {
    // "Berlin" should not be changed — B is at start, not between lowercase
    assertEquals("Berlin", OCRPostProcessor.applyGermanCorrections("Berlin"));
  }

  @Test
  public void applyGermanCorrections_uppercaseB_afterUppercase_unchanged() {
    // "ABCdef" — B after uppercase A, not between lowercase
    assertEquals("ABCdef", OCRPostProcessor.applyGermanCorrections("ABCdef"));
  }

  @Test
  public void applyGermanCorrections_uppercaseB_beforeUppercase_unchanged() {
    // "aBCd" — B before uppercase C, not between lowercase
    assertEquals("aBCd", OCRPostProcessor.applyGermanCorrections("aBCd"));
  }

  @Test
  public void applyGermanCorrections_umlautContext_corrected() {
    // "grüBe" → "grüße" (ü is in [a-zäöü] range)
    assertEquals("grüße", OCRPostProcessor.applyGermanCorrections("grüBe"));
  }

  @Test
  public void applyGermanCorrections_digitContext_unchanged() {
    // "1B2" — B between digits, not lowercase letters
    assertEquals("1B2", OCRPostProcessor.applyGermanCorrections("1B2"));
  }

  @Test
  public void applyGermanCorrections_singleChar_unchanged() {
    assertEquals("B", OCRPostProcessor.applyGermanCorrections("B"));
  }

  @Test
  public void applyGermanCorrections_realEszett_unchanged() {
    // Already correct text should not be modified
    assertEquals("Straße", OCRPostProcessor.applyGermanCorrections("Straße"));
  }
}
