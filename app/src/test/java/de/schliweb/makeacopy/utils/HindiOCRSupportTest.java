package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.layout.DocumentLayoutAnalyzer;
import de.schliweb.makeacopy.utils.layout.ReadingOrderSorter;
import de.schliweb.makeacopy.utils.ocr.DictionaryManager;
import de.schliweb.makeacopy.utils.ocr.OCRUtils;
import de.schliweb.makeacopy.utils.ocr.OCRWhitelist;
import java.util.Arrays;
import org.junit.Test;

/**
 * Comprehensive test suite verifying that Hindi (hin) OCR support is correctly integrated across
 * all relevant subsystems: language mapping, whitelist, dictionary, reading direction, and layout
 * analysis.
 */
public class HindiOCRSupportTest {

  // ==================== Language Registration ====================

  @Test
  public void hindi_isRegisteredInAvailableLanguages() {
    String[] languages = OCRUtils.getLanguages();
    assertTrue("hin must be in available languages", Arrays.asList(languages).contains("hin"));
  }

  @Test
  public void hindi_systemLanguageMapsToTesseract() {
    assertEquals("hin", OCRUtils.mapSystemLanguageToTesseract("hi"));
  }

  @Test
  public void hindi_resolveEffectiveLanguage_returnsSame() {
    assertEquals("hin", OCRUtils.resolveEffectiveLanguage("hin"));
  }

  @Test
  public void hindi_multiLanguageSpec_isPreserved() {
    assertEquals("hin+eng", OCRUtils.resolveEffectiveLanguage("hin+eng"));
  }

  // ==================== Whitelist ====================

  @Test
  public void hindi_whitelist_returnsHI() {
    // Devanagari script has its own dedicated whitelist
    String whitelist = OCRWhitelist.getWhitelistForLanguage("hin");
    assertEquals(OCRWhitelist.HI, whitelist);
  }

  @Test
  public void hindi_whitelist_isNotEmpty() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("hin");
    assertNotNull("Whitelist must not be null", whitelist);
    assertFalse("Whitelist must not be empty", whitelist.isEmpty());
  }

  @Test
  public void hindi_whitelist_containsDevanagariCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("hin");
    // Check key Devanagari characters are present
    assertTrue("Must contain Ka (क)", whitelist.indexOf('\u0915') >= 0);
    assertTrue("Must contain Aa vowel sign (ा)", whitelist.indexOf('\u093E') >= 0);
    assertTrue("Must contain Virama/Halant (्)", whitelist.indexOf('\u094D') >= 0);
    assertTrue("Must contain Devanagari digit 0 (०)", whitelist.indexOf('\u0966') >= 0);
    assertTrue("Must contain Danda (।)", whitelist.indexOf('\u0964') >= 0);
  }

  @Test
  public void hindi_filterByWhitelist_preservesDevanagariText() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("hin");
    String hindiText = "\u0928\u092E\u0938\u094D\u0924\u0947"; // नमस्ते (Namaste)
    String filtered = OCRWhitelist.filterByWhitelist(hindiText, whitelist);
    assertEquals("Hindi text must pass through filter unchanged", hindiText, filtered);
  }

  @Test
  public void hindi_filterByWhitelist_preservesMixedHindiEnglishText() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("hin+eng");
    String mixedText = "Hello \u0928\u092E\u0938\u094D\u0924\u0947 123";
    String filtered = OCRWhitelist.filterByWhitelist(mixedText, whitelist);
    assertEquals(
        "Mixed Hindi+English text must pass through filter unchanged", mixedText, filtered);
  }

  // ==================== Dictionary / Word-based ====================

  @Test
  public void hindi_isWordBasedLanguage() {
    assertTrue("Hindi uses spaces between words", DictionaryManager.isWordBasedLanguage("hin"));
  }

  @Test
  public void hindi_isNotCharacterBased() {
    // Hindi is word-based (like English), not character-based (like Chinese)
    assertTrue(DictionaryManager.isWordBasedLanguage("hin"));
  }

  // ==================== Reading Direction ====================

  @Test
  public void hindi_readingDirection_isLTR() {
    assertEquals(
        "Hindi (Devanagari) is left-to-right",
        ReadingOrderSorter.TextDirection.LTR,
        ReadingOrderSorter.getDirectionForLanguage("hin"));
  }

  @Test
  public void hindi_iso2_readingDirection_isLTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("hi"));
  }

  // ==================== Layout Analyzer ====================

  @Test
  public void layoutAnalyzer_acceptsHindi() {
    DocumentLayoutAnalyzer analyzer = new DocumentLayoutAnalyzer();
    analyzer.setLanguage("hin");
    assertEquals("hin", analyzer.getLanguage());
  }

  // ==================== Edge Cases ====================

  @Test
  public void hindi_upperCase_readingDirection_isLTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("HIN"));
  }

  @Test
  public void hindi_notRTL() {
    // Ensure Hindi is never mistakenly classified as RTL
    assertNotEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("hin"));
  }
}
