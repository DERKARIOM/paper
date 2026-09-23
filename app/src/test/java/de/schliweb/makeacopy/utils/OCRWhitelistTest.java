package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.ocr.OCRWhitelist;
import org.junit.Test;

/** Unit tests for {@link OCRWhitelist} to verify multi-language whitelist generation. */
public class OCRWhitelistTest {

  // ==================== Single Language Tests ====================

  @Test
  public void getWhitelistForLanguage_german_returnsGermanCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("deu");
    assertEquals(OCRWhitelist.DE, whitelist);
    assertTrue("Should contain German umlauts", whitelist.contains("ä"));
    assertTrue("Should contain German umlauts", whitelist.contains("ö"));
    assertTrue("Should contain German umlauts", whitelist.contains("ü"));
    assertTrue("Should contain German ß", whitelist.contains("ß"));
  }

  @Test
  public void getWhitelistForLanguage_english_returnsEnglishCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("eng");
    assertEquals(OCRWhitelist.EN, whitelist);
    assertTrue("Should contain basic Latin letters", whitelist.contains("A"));
    assertTrue("Should contain basic Latin letters", whitelist.contains("z"));
  }

  @Test
  public void getWhitelistForLanguage_french_returnsFrenchCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("fra");
    assertEquals(OCRWhitelist.FR, whitelist);
    assertTrue("Should contain French accents", whitelist.contains("é"));
    assertTrue("Should contain French accents", whitelist.contains("è"));
    assertTrue("Should contain French œ", whitelist.contains("œ"));
  }

  @Test
  public void getWhitelistForLanguage_turkish_returnsTurkishCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("tur");
    assertEquals(OCRWhitelist.TR, whitelist);
    assertTrue("Should contain dotted capital I (İ)", whitelist.contains("İ"));
    assertTrue("Should contain dotless small i (ı)", whitelist.contains("ı"));
    assertTrue("Should contain Ğ", whitelist.contains("Ğ"));
    assertTrue("Should contain ğ", whitelist.contains("ğ"));
    assertTrue("Should contain Ş", whitelist.contains("Ş"));
    assertTrue("Should contain ş", whitelist.contains("ş"));
    assertTrue("Should contain Ç", whitelist.contains("Ç"));
    assertTrue("Should contain ç", whitelist.contains("ç"));
    assertTrue("Should contain Ö", whitelist.contains("Ö"));
    assertTrue("Should contain ö", whitelist.contains("ö"));
    assertTrue("Should contain Ü", whitelist.contains("Ü"));
    assertTrue("Should contain ü", whitelist.contains("ü"));
    assertTrue("Should contain basic Latin letters", whitelist.contains("A"));
    assertTrue("Should contain digits", whitelist.contains("0"));
  }

  @Test
  public void getWhitelistForLanguage_russian_returnsCyrillicCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("rus");
    assertEquals(OCRWhitelist.RU, whitelist);
    assertTrue("Should contain Cyrillic А", whitelist.contains("А"));
    assertTrue("Should contain Cyrillic я", whitelist.contains("я"));
    assertTrue("Should contain Cyrillic Ё", whitelist.contains("Ё"));
  }

  @Test
  public void getWhitelistForLanguage_null_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage(null);
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLanguage_unknown_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("xyz");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  // ==================== Multi-Language Spec Tests ====================

  @Test
  public void getWhitelistForLangSpec_singleLanguage_returnsSameAsGetWhitelistForLanguage() {
    String specResult = OCRWhitelist.getWhitelistForLangSpec("deu");
    String singleResult = OCRWhitelist.getWhitelistForLanguage("deu");
    assertEquals(singleResult, specResult);
  }

  @Test
  public void getWhitelistForLangSpec_germanPlusEnglish_containsBothCharacterSets() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("deu+eng");

    // German characters
    assertTrue("Should contain German ä", whitelist.contains("ä"));
    assertTrue("Should contain German ö", whitelist.contains("ö"));
    assertTrue("Should contain German ü", whitelist.contains("ü"));
    assertTrue("Should contain German ß", whitelist.contains("ß"));

    // English/common characters
    assertTrue("Should contain A", whitelist.contains("A"));
    assertTrue("Should contain z", whitelist.contains("z"));
    assertTrue("Should contain digits", whitelist.contains("0"));
    assertTrue("Should contain digits", whitelist.contains("9"));
  }

  @Test
  public void getWhitelistForLangSpec_frenchPlusGerman_containsBothCharacterSets() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("fra+deu");

    // French characters
    assertTrue("Should contain French é", whitelist.contains("é"));
    assertTrue("Should contain French è", whitelist.contains("è"));
    assertTrue("Should contain French œ", whitelist.contains("œ"));

    // German characters
    assertTrue("Should contain German ä", whitelist.contains("ä"));
    assertTrue("Should contain German ß", whitelist.contains("ß"));
  }

  @Test
  public void getWhitelistForLangSpec_russianPlusEnglish_containsBothScripts() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("rus+eng");

    // Cyrillic characters
    assertTrue("Should contain Cyrillic А", whitelist.contains("А"));
    assertTrue("Should contain Cyrillic я", whitelist.contains("я"));

    // Latin characters
    assertTrue("Should contain Latin A", whitelist.contains("A"));
    assertTrue("Should contain Latin z", whitelist.contains("z"));
  }

  @Test
  public void getWhitelistForLangSpec_noDuplicateCharacters() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("deu+eng");

    // Count occurrences of 'A' - should be exactly 1
    long countA = whitelist.chars().filter(c -> c == 'A').count();
    assertEquals("Character 'A' should appear only once", 1, countA);

    // Count occurrences of '0' - should be exactly 1
    long count0 = whitelist.chars().filter(c -> c == '0').count();
    assertEquals("Character '0' should appear only once", 1, count0);
  }

  @Test
  public void getWhitelistForLangSpec_threeLanguages_containsAllCharacterSets() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("deu+fra+spa");

    // German
    assertTrue("Should contain German ß", whitelist.contains("ß"));

    // French
    assertTrue("Should contain French œ", whitelist.contains("œ"));

    // Spanish
    assertTrue("Should contain Spanish ñ", whitelist.contains("ñ"));
  }

  @Test
  public void getWhitelistForLangSpec_null_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec(null);
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLangSpec_empty_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLangSpec_whitespaceOnly_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("   ");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLangSpec_withSpacesAroundPlus_handlesCorrectly() {
    // The implementation should handle "deu + eng" with spaces
    String whitelist = OCRWhitelist.getWhitelistForLangSpec("deu + eng");
    assertTrue("Should contain German ß", whitelist.contains("ß"));
    assertTrue("Should contain Latin A", whitelist.contains("A"));
  }

  // ==================== RTL Language Tests (Arabic/Persian) ====================
  // Note: For RTL languages, the whitelist is typically disabled in OCRHelper,
  // but we test that the whitelist mechanism itself doesn't break

  @Test
  public void getWhitelistForLanguage_arabic_returnsDefault() {
    // Arabic is not in the switch statement, so it returns DEFAULT
    String whitelist = OCRWhitelist.getWhitelistForLanguage("ara");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLanguage_persian_returnsDefault() {
    // Persian/Farsi is not in the switch statement, so it returns DEFAULT
    String whitelist = OCRWhitelist.getWhitelistForLanguage("fas");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  // ==================== CJK Language Tests ====================
  // Note: CJK languages are not in the whitelist (too many characters),
  // OCRHelper handles them specially

  @Test
  public void getWhitelistForLanguage_chineseSimplified_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("chi_sim");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLanguage_chineseTraditional_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("chi_tra");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLanguage_thai_returnsDefault() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("tha");
    assertEquals(OCRWhitelist.DEFAULT, whitelist);
  }

  @Test
  public void getWhitelistForLanguage_hindi_returnsHI() {
    String whitelist = OCRWhitelist.getWhitelistForLanguage("hin");
    assertEquals(OCRWhitelist.HI, whitelist);
  }

  // ==================== All Supported Languages Tests ====================

  @Test
  public void getWhitelistForLanguage_allSupportedLanguages_returnNonEmpty() {
    String[] supportedLangs = {
      "deu", "eng", "spa", "fra", "ita", "por", "nld", "pol", "ces", "slk", "hun", "ron", "dan",
      "nor", "swe", "tur", "rus"
    };

    for (String lang : supportedLangs) {
      String whitelist = OCRWhitelist.getWhitelistForLanguage(lang);
      assertNotNull("Whitelist for " + lang + " should not be null", whitelist);
      assertFalse("Whitelist for " + lang + " should not be empty", whitelist.isEmpty());
      assertTrue("Whitelist for " + lang + " should contain basic digits", whitelist.contains("0"));
    }
  }
}
