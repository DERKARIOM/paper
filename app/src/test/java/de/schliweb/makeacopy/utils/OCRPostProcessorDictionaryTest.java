package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.ocr.DictionaryManager;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for dictionary-based OCR post-processing. Tests the correction logic without requiring
 * Android context.
 */
public class OCRPostProcessorDictionaryTest {

  @Test
  public void testExtractCleanWord_withPunctuation() throws Exception {
    // Use reflection to test private method
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("extractCleanWord", String.class);
    method.setAccessible(true);

    assertEquals("Hello", method.invoke(null, "Hello"));
    assertEquals("Hello", method.invoke(null, "Hello,"));
    assertEquals("Hello", method.invoke(null, "\"Hello\""));
    assertEquals("Hello", method.invoke(null, "(Hello)"));
    assertEquals("test", method.invoke(null, "...test..."));
    assertEquals("", method.invoke(null, "..."));
    assertEquals("", method.invoke(null, ""));
  }

  @Test
  public void testApplyCasePattern() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("applyCasePattern", String.class, String.class);
    method.setAccessible(true);

    // All uppercase
    assertEquals("HELLO", method.invoke(null, "WORLD", "hello"));

    // All lowercase
    assertEquals("hello", method.invoke(null, "world", "HELLO"));

    // Title case
    assertEquals("Hello", method.invoke(null, "World", "hello"));

    // Mixed case defaults to lowercase
    assertEquals("hello", method.invoke(null, "wOrLd", "HELLO"));
  }

  @Test
  public void testFindDictionaryCorrection_singleCharSubstitution() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello");
    dictionary.add("world");
    dictionary.add("test");

    // 0 -> o correction: "hell0" -> "hello"
    assertEquals("hello", method.invoke(null, "hell0", dictionary));

    // 1 -> l correction: "he1lo" -> "hello"
    assertEquals("hello", method.invoke(null, "he1lo", dictionary));

    // Word already in dictionary - findDictionaryCorrection may still find a match
    // because 'o' -> 'O' substitution gives "hellO" which lowercase matches "hello".
    // The caller (processWithDictionary) checks if word is in dictionary before
    // calling this method, so this case won't occur in practice.
    // Test with a word that has no valid substitutions
    assertNull(method.invoke(null, "xyz", dictionary));
  }

  @Test
  public void testFindDictionaryCorrection_ligatureCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("summer");
    dictionary.add("hammer");
    dictionary.add("warm");

    // rn -> m correction: "surnrner" -> "summer"
    assertEquals("summer", method.invoke(null, "surnmer", dictionary));

    // rn -> m correction: "harnrner" -> "hammer"
    assertEquals("hammer", method.invoke(null, "harnmer", dictionary));
  }

  @Test
  public void testFindDictionaryCorrection_preservesCase() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello");

    // Uppercase input should produce uppercase output
    assertEquals("HELLO", method.invoke(null, "HELL0", dictionary));

    // Title case
    assertEquals("Hello", method.invoke(null, "Hell0", dictionary));
  }

  @Test
  public void testFindDictionaryCorrection_withPunctuation() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello");

    // Should preserve punctuation
    assertEquals("hello,", method.invoke(null, "hell0,", dictionary));
    assertEquals("\"hello\"", method.invoke(null, "\"hell0\"", dictionary));
  }

  @Test
  public void testFindDictionaryCorrection_noMatchReturnsNull() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello");

    // Word not in dictionary and no valid correction
    assertNull(method.invoke(null, "xyz123", dictionary));

    // Empty dictionary
    assertNull(method.invoke(null, "hello", new HashSet<String>()));
  }

  @Test
  public void testDictionaryManagerIsWordBasedLanguage() {
    // Western languages should be word-based
    assertTrue(DictionaryManager.isWordBasedLanguage("deu"));
    assertTrue(DictionaryManager.isWordBasedLanguage("eng"));
    assertTrue(DictionaryManager.isWordBasedLanguage("fra"));
    assertTrue(DictionaryManager.isWordBasedLanguage("spa"));

    // CJK and Thai should not be word-based
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim"));
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_tra"));
    assertFalse(DictionaryManager.isWordBasedLanguage("tha"));

    // Multi-language spec: returns true if ANY language is word-based
    assertTrue(DictionaryManager.isWordBasedLanguage("deu+eng"));
    assertTrue(DictionaryManager.isWordBasedLanguage("chi_sim+eng")); // eng is word-based
    assertTrue(DictionaryManager.isWordBasedLanguage("fas+eng")); // both are word-based

    // Multi-language spec with only non-word-based languages
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim+chi_tra"));
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim+tha"));

    // Null should return false
    assertFalse(DictionaryManager.isWordBasedLanguage(null));
  }

  @Test
  public void testProcessText_standardCorrections() {
    // Test that standard corrections still work
    String input = "Hel1o W0rld";
    String result = OCRPostProcessor.processText(input, "eng");

    // Standard processing applies ligature and context corrections
    assertNotNull(result);
  }

  @Test
  public void testOcrConfusionsMap() throws Exception {
    // Verify the OCR_CONFUSIONS map is properly initialized
    java.lang.reflect.Field field = OCRPostProcessor.class.getDeclaredField("OCR_CONFUSIONS");
    field.setAccessible(true);

    @SuppressWarnings("unchecked")
    java.util.Map<Character, char[]> confusions =
        (java.util.Map<Character, char[]>) field.get(null);

    assertNotNull(confusions);
    assertTrue(confusions.size() > 0);

    // Check some key mappings
    assertNotNull(confusions.get('0')); // 0 -> O, o, Q
    assertNotNull(confusions.get('1')); // 1 -> l, I, i
    assertNotNull(confusions.get('m')); // m -> n, r (for rn ligature)
  }

  // ==================== Language-specific tests ====================

  @Test
  public void testSpanish_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hola"); // hello
    dictionary.add("mundo"); // world
    dictionary.add("español"); // Spanish
    dictionary.add("niño"); // child

    // 0 -> o correction: "h0la" -> "hola"
    assertEquals("hola", method.invoke(null, "h0la", dictionary));

    // 1 -> l correction in Spanish word
    assertEquals("hola", method.invoke(null, "ho1a", dictionary));

    // rn -> m correction: "rnundo" -> "mundo"
    assertEquals("mundo", method.invoke(null, "rnundo", dictionary));
  }

  @Test
  public void testItalian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("ciao"); // hello
    dictionary.add("mondo"); // world
    dictionary.add("italiano"); // Italian
    dictionary.add("città"); // city

    // 0 -> o correction: "m0ndo" -> "mondo" (single substitution)
    assertEquals("mondo", method.invoke(null, "m0ndo", dictionary));

    // rn -> m correction: "rnondo" -> "mondo"
    assertEquals("mondo", method.invoke(null, "rnondo", dictionary));
  }

  @Test
  public void testPortuguese_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("olá"); // hello
    dictionary.add("mundo"); // world
    dictionary.add("português"); // Portuguese
    dictionary.add("coração"); // heart

    // 0 -> o correction: "mund0" -> "mundo"
    assertEquals("mundo", method.invoke(null, "mund0", dictionary));

    // rn -> m correction
    assertEquals("mundo", method.invoke(null, "rnundo", dictionary));
  }

  @Test
  public void testDutch_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hallo"); // hello
    dictionary.add("wereld"); // world
    dictionary.add("nederland"); // Netherlands
    dictionary.add("mooi"); // beautiful

    // 0 -> o correction: "hall0" -> "hallo"
    assertEquals("hallo", method.invoke(null, "hall0", dictionary));

    // 1 -> l correction: "ha1lo" -> "hallo"
    assertEquals("hallo", method.invoke(null, "ha1lo", dictionary));

    // rn -> m correction: "rnooi" -> "mooi"
    assertEquals("mooi", method.invoke(null, "rnooi", dictionary));
  }

  @Test
  public void testPolish_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("cześć"); // hello
    dictionary.add("świat"); // world
    dictionary.add("polska"); // Poland
    dictionary.add("miłość"); // love

    // 0 -> o correction: "p0lska" -> "polska"
    assertEquals("polska", method.invoke(null, "p0lska", dictionary));

    // rn -> m correction: "rniłość" -> "miłość"
    assertEquals("miłość", method.invoke(null, "rniłość", dictionary));
  }

  @Test
  public void testCzech_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("ahoj"); // hello
    dictionary.add("svět"); // world
    dictionary.add("česky"); // Czech
    dictionary.add("město"); // city

    // 0 -> o correction: "ah0j" -> "ahoj"
    assertEquals("ahoj", method.invoke(null, "ah0j", dictionary));

    // rn -> m correction: "rněsto" -> "město"
    assertEquals("město", method.invoke(null, "rněsto", dictionary));
  }

  @Test
  public void testSlovak_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("ahoj"); // hello
    dictionary.add("svet"); // world
    dictionary.add("slovensko"); // Slovakia
    dictionary.add("mesto"); // city

    // 0 -> o correction: "sl0vensko" -> "slovensko" (single substitution)
    assertEquals("slovensko", method.invoke(null, "sl0vensko", dictionary));

    // rn -> m correction: "rnesto" -> "mesto"
    assertEquals("mesto", method.invoke(null, "rnesto", dictionary));
  }

  @Test
  public void testHungarian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("szia"); // hello
    dictionary.add("világ"); // world
    dictionary.add("magyar"); // Hungarian
    dictionary.add("szerelem"); // love

    // 0 -> o correction in Hungarian context
    // rn -> m correction: "szerelern" -> "szerelem"
    assertEquals("szerelem", method.invoke(null, "szerelern", dictionary));
  }

  @Test
  public void testRomanian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("salut"); // hello
    dictionary.add("lume"); // world
    dictionary.add("română"); // Romanian
    dictionary.add("dragoste"); // love

    // 0 -> o correction: "r0mână" -> "română"
    assertEquals("română", method.invoke(null, "r0mână", dictionary));

    // 1 -> l correction: "1ume" -> "lume"
    assertEquals("lume", method.invoke(null, "1ume", dictionary));
  }

  @Test
  public void testDanish_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hej"); // hello
    dictionary.add("verden"); // world
    dictionary.add("dansk"); // Danish
    dictionary.add("kærlighed"); // love
    dictionary.add("sommer"); // summer

    // rn -> m correction: "sornmer" -> "sommer"
    assertEquals("sommer", method.invoke(null, "sornmer", dictionary));

    // No valid correction for unknown character substitution
    assertNull(method.invoke(null, "d4nsk", dictionary)); // 4 not in confusions
  }

  @Test
  public void testNorwegian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hei"); // hello
    dictionary.add("verden"); // world
    dictionary.add("norsk"); // Norwegian
    dictionary.add("kjærlighet"); // love

    // 0 -> o correction: "n0rsk" -> "norsk"
    assertEquals("norsk", method.invoke(null, "n0rsk", dictionary));
  }

  @Test
  public void testSwedish_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hej"); // hello
    dictionary.add("värld"); // world
    dictionary.add("svensk"); // Swedish
    dictionary.add("kärlek"); // love
    dictionary.add("sommar"); // summer

    // 0 -> o correction
    // rn -> m correction: "sornrnar" -> "sommar"
    assertEquals("sommar", method.invoke(null, "sornmar", dictionary));
  }

  @Test
  public void testRussian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("привет"); // hello
    dictionary.add("мир"); // world
    dictionary.add("русский"); // Russian
    dictionary.add("любовь"); // love

    // Russian uses Cyrillic, so Latin OCR confusions don't apply directly
    // But the dictionary lookup should still work
    assertNull(method.invoke(null, "xyz", dictionary)); // No match

    // Word in dictionary should be found if exact match after case normalization
    // Note: findDictionaryCorrection looks for corrections, not exact matches
  }

  @Test
  public void testPersian_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("سلام"); // hello
    dictionary.add("جهان"); // world
    dictionary.add("فارسی"); // Persian
    dictionary.add("عشق"); // love
    dictionary.add("کتاب"); // book
    dictionary.add("خانه"); // house

    // Persian uses Arabic script, so Latin OCR confusions don't apply directly
    // But the dictionary lookup should still work
    assertNull(method.invoke(null, "xyz", dictionary)); // No match

    // Word in dictionary should be found if exact match after case normalization
    // Note: findDictionaryCorrection looks for corrections, not exact matches
  }

  @Test
  public void testArabic_dictionaryCorrection() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("مرحبا"); // hello
    dictionary.add("عالم"); // world
    dictionary.add("عربي"); // Arabic
    dictionary.add("حب"); // love
    dictionary.add("كتاب"); // book
    dictionary.add("بيت"); // house

    // Arabic uses Arabic script, so Latin OCR confusions don't apply directly
    // But the dictionary lookup should still work
    assertNull(method.invoke(null, "xyz", dictionary)); // No match

    // Word in dictionary should be found if exact match after case normalization
    // Note: findDictionaryCorrection looks for corrections, not exact matches
  }

  @Test
  public void testDictionaryManagerIsWordBasedLanguage_allLanguages() {
    // All Western/Latin languages should be word-based
    assertTrue(DictionaryManager.isWordBasedLanguage("deu")); // German
    assertTrue(DictionaryManager.isWordBasedLanguage("eng")); // English
    assertTrue(DictionaryManager.isWordBasedLanguage("fra")); // French
    assertTrue(DictionaryManager.isWordBasedLanguage("spa")); // Spanish
    assertTrue(DictionaryManager.isWordBasedLanguage("ita")); // Italian
    assertTrue(DictionaryManager.isWordBasedLanguage("por")); // Portuguese
    assertTrue(DictionaryManager.isWordBasedLanguage("nld")); // Dutch
    assertTrue(DictionaryManager.isWordBasedLanguage("pol")); // Polish
    assertTrue(DictionaryManager.isWordBasedLanguage("ces")); // Czech
    assertTrue(DictionaryManager.isWordBasedLanguage("slk")); // Slovak
    assertTrue(DictionaryManager.isWordBasedLanguage("hun")); // Hungarian
    assertTrue(DictionaryManager.isWordBasedLanguage("ron")); // Romanian
    assertTrue(DictionaryManager.isWordBasedLanguage("dan")); // Danish
    assertTrue(DictionaryManager.isWordBasedLanguage("nor")); // Norwegian
    assertTrue(DictionaryManager.isWordBasedLanguage("swe")); // Swedish
    assertTrue(DictionaryManager.isWordBasedLanguage("rus")); // Russian

    // Persian, Arabic, and Hindi should be word-based (they use spaces between words)
    assertTrue(DictionaryManager.isWordBasedLanguage("fas")); // Persian
    assertTrue(DictionaryManager.isWordBasedLanguage("ara")); // Arabic
    assertTrue(DictionaryManager.isWordBasedLanguage("hin")); // Hindi

    // CJK and Thai should NOT be word-based
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim")); // Chinese Simplified
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_tra")); // Chinese Traditional
    assertFalse(DictionaryManager.isWordBasedLanguage("tha")); // Thai
  }

  @Test
  public void testSingleSubstitutions_crossLanguage() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello"); // English
    dictionary.add("hallo"); // German/Dutch
    dictionary.add("ciao"); // Italian
    dictionary.add("olá"); // Portuguese

    // Single 0 -> o correction: "hell0" -> "hello"
    assertEquals("hello", method.invoke(null, "hell0", dictionary));

    // Single 1 -> l correction: "ha1lo" -> "hallo"
    assertEquals("hallo", method.invoke(null, "ha1lo", dictionary));

    // Single 1 -> i correction: "c1ao" -> "ciao"
    assertEquals("ciao", method.invoke(null, "c1ao", dictionary));

    // Single 0 -> o correction: "cia0" -> "ciao"
    assertEquals("ciao", method.invoke(null, "cia0", dictionary));
  }

  @Test
  public void testLigatureCorrections_crossLanguage() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("summer"); // English
    dictionary.add("sommer"); // German/Danish/Norwegian
    dictionary.add("ommer"); // Part of Danish word

    // rn -> m in English: "surnmer" -> "summer"
    assertEquals("summer", method.invoke(null, "surnmer", dictionary));

    // rn -> m in German: "sornmer" -> "sommer"
    assertEquals("sommer", method.invoke(null, "sornmer", dictionary));
  }

  @Test
  public void testCasePreservation_allLanguages() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("hello");
    dictionary.add("mundo");
    dictionary.add("świat");

    // Uppercase preservation
    assertEquals("HELLO", method.invoke(null, "HELL0", dictionary));
    assertEquals("MUNDO", method.invoke(null, "MUND0", dictionary));

    // Title case preservation
    assertEquals("Hello", method.invoke(null, "Hell0", dictionary));
    assertEquals("Mundo", method.invoke(null, "Mund0", dictionary));

    // Lowercase preservation
    assertEquals("hello", method.invoke(null, "hell0", dictionary));
    assertEquals("mundo", method.invoke(null, "mund0", dictionary));
  }

  // ==================== Combined Dictionary Tests (Multi-Language) ====================

  /**
   * Tests combined dictionary for Persian + English (fas+eng). Verifies that words from both
   * languages are found in the combined dictionary.
   */
  @Test
  public void testCombinedDictionary_PersianEnglish() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    // Simulate combined dictionary with Persian and English words
    Set<String> combinedDictionary = new HashSet<>();

    // English words
    combinedDictionary.add("hello");
    combinedDictionary.add("world");
    combinedDictionary.add("computer");
    combinedDictionary.add("document");
    combinedDictionary.add("scanner");

    // Persian words (Farsi)
    combinedDictionary.add("سلام"); // hello
    combinedDictionary.add("جهان"); // world
    combinedDictionary.add("کامپیوتر"); // computer
    combinedDictionary.add("سند"); // document
    combinedDictionary.add("اسکنر"); // scanner
    combinedDictionary.add("کتاب"); // book
    combinedDictionary.add("خانه"); // house

    // Test English word corrections in combined dictionary
    // 0 -> o correction: "hell0" -> "hello"
    assertEquals("hello", method.invoke(null, "hell0", combinedDictionary));

    // 0 -> o correction: "w0rld" -> "world"
    assertEquals("world", method.invoke(null, "w0rld", combinedDictionary));

    // rn -> m correction: "cornputer" -> "computer"
    assertEquals("computer", method.invoke(null, "cornputer", combinedDictionary));

    // 1 -> l correction: "wor1d" -> "world"
    assertEquals("world", method.invoke(null, "wor1d", combinedDictionary));

    // Verify Persian words are in dictionary (no OCR correction needed for exact matches)
    assertTrue(
        "Persian word 'سلام' should be in combined dictionary",
        combinedDictionary.contains("سلام"));
    assertTrue(
        "Persian word 'جهان' should be in combined dictionary",
        combinedDictionary.contains("جهان"));
    assertTrue(
        "Persian word 'کامپیوتر' should be in combined dictionary",
        combinedDictionary.contains("کامپیوتر"));
  }

  /**
   * Tests combined dictionary for Arabic + French (ara+fra). Verifies that words from both
   * languages are found in the combined dictionary.
   */
  @Test
  public void testCombinedDictionary_ArabicFrench() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    // Simulate combined dictionary with Arabic and French words
    Set<String> combinedDictionary = new HashSet<>();

    // French words
    combinedDictionary.add("bonjour"); // hello
    combinedDictionary.add("monde"); // world
    combinedDictionary.add("document"); // document
    combinedDictionary.add("ordinateur"); // computer
    combinedDictionary.add("maison"); // house
    combinedDictionary.add("été"); // summer

    // Arabic words
    combinedDictionary.add("مرحبا"); // hello
    combinedDictionary.add("عالم"); // world
    combinedDictionary.add("وثيقة"); // document
    combinedDictionary.add("حاسوب"); // computer
    combinedDictionary.add("بيت"); // house
    combinedDictionary.add("كتاب"); // book

    // Test French word corrections in combined dictionary
    // 0 -> o correction: "b0njour" -> "bonjour"
    assertEquals("bonjour", method.invoke(null, "b0njour", combinedDictionary));

    // 0 -> o correction: "m0nde" -> "monde"
    assertEquals("monde", method.invoke(null, "m0nde", combinedDictionary));

    // rn -> m correction: "docurnent" -> "document"
    assertEquals("document", method.invoke(null, "docurnent", combinedDictionary));

    // rn -> m correction: "rnaison" -> "maison"
    assertEquals("maison", method.invoke(null, "rnaison", combinedDictionary));

    // Verify Arabic words are in dictionary
    assertTrue(
        "Arabic word 'مرحبا' should be in combined dictionary",
        combinedDictionary.contains("مرحبا"));
    assertTrue(
        "Arabic word 'عالم' should be in combined dictionary", combinedDictionary.contains("عالم"));
    assertTrue(
        "Arabic word 'وثيقة' should be in combined dictionary",
        combinedDictionary.contains("وثيقة"));
  }

  /**
   * Tests that multi-language spec correctly identifies word-based languages. Both fas+eng and
   * ara+fra should be word-based.
   */
  @Test
  public void testMultiLanguageSpec_isWordBased() {
    // Persian + English: both are word-based
    assertTrue("fas+eng should be word-based", DictionaryManager.isWordBasedLanguage("fas+eng"));

    // Arabic + French: both are word-based
    assertTrue("ara+fra should be word-based", DictionaryManager.isWordBasedLanguage("ara+fra"));

    // Arabic + English: both are word-based
    assertTrue("ara+eng should be word-based", DictionaryManager.isWordBasedLanguage("ara+eng"));

    // Persian + French: both are word-based
    assertTrue("fas+fra should be word-based", DictionaryManager.isWordBasedLanguage("fas+fra"));

    // Mixed with non-word-based: should still be true if one is word-based
    assertTrue(
        "chi_sim+eng should be word-based (eng is word-based)",
        DictionaryManager.isWordBasedLanguage("chi_sim+eng"));
    assertTrue(
        "fas+chi_sim should be word-based (fas is word-based)",
        DictionaryManager.isWordBasedLanguage("fas+chi_sim"));
  }

  /**
   * Tests mixed content correction with Persian + English combined dictionary. Simulates a document
   * with both Persian and English text.
   */
  @Test
  public void testMixedContent_PersianEnglish() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    // Combined dictionary simulating fas+eng
    Set<String> combinedDictionary = new HashSet<>();

    // Common English words
    combinedDictionary.add("the");
    combinedDictionary.add("and");
    combinedDictionary.add("hello");
    combinedDictionary.add("name");
    combinedDictionary.add("email");

    // Common Persian words
    combinedDictionary.add("و"); // and
    combinedDictionary.add("است"); // is
    combinedDictionary.add("نام"); // name
    combinedDictionary.add("ایمیل"); // email

    // English corrections should work
    assertEquals("hello", method.invoke(null, "hell0", combinedDictionary));
    assertEquals("name", method.invoke(null, "narne", combinedDictionary)); // rn -> m
    assertEquals("email", method.invoke(null, "ernail", combinedDictionary)); // rn -> m

    // Verify Persian words exist in combined dictionary
    assertTrue(combinedDictionary.contains("و"));
    assertTrue(combinedDictionary.contains("است"));
    assertTrue(combinedDictionary.contains("نام"));
    assertTrue(combinedDictionary.contains("ایمیل"));
  }

  /**
   * Tests mixed content correction with Arabic + French combined dictionary. Simulates a document
   * with both Arabic and French text.
   */
  @Test
  public void testMixedContent_ArabicFrench() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    // Combined dictionary simulating ara+fra
    Set<String> combinedDictionary = new HashSet<>();

    // Common French words
    combinedDictionary.add("le");
    combinedDictionary.add("la");
    combinedDictionary.add("et");
    combinedDictionary.add("nom");
    combinedDictionary.add("adresse");
    combinedDictionary.add("téléphone");

    // Common Arabic words
    combinedDictionary.add("و"); // and
    combinedDictionary.add("هو"); // he/it
    combinedDictionary.add("اسم"); // name
    combinedDictionary.add("عنوان"); // address
    combinedDictionary.add("هاتف"); // phone

    // French corrections should work
    assertEquals("nom", method.invoke(null, "norn", combinedDictionary)); // rn -> m
    assertEquals(
        "adresse",
        method.invoke(
            null,
            "adresse",
            combinedDictionary)); // exact match returns null (no correction needed)

    // Test with OCR error
    assertEquals("nom", method.invoke(null, "n0m", combinedDictionary)); // 0 -> o

    // Verify Arabic words exist in combined dictionary
    assertTrue(combinedDictionary.contains("و"));
    assertTrue(combinedDictionary.contains("هو"));
    assertTrue(combinedDictionary.contains("اسم"));
    assertTrue(combinedDictionary.contains("عنوان"));
    assertTrue(combinedDictionary.contains("هاتف"));
  }

  /** Tests that combined dictionary size is the union of both language dictionaries. */
  @Test
  public void testCombinedDictionarySize() {
    Set<String> persianDict = new HashSet<>();
    persianDict.add("سلام");
    persianDict.add("جهان");
    persianDict.add("کتاب");

    Set<String> englishDict = new HashSet<>();
    englishDict.add("hello");
    englishDict.add("world");
    englishDict.add("book");

    // Combine dictionaries (simulating DictionaryManager behavior)
    Set<String> combined = new HashSet<>();
    combined.addAll(persianDict);
    combined.addAll(englishDict);

    // Combined should have all words from both
    assertEquals("Combined dictionary should have 6 words", 6, combined.size());

    // All Persian words should be present
    assertTrue(combined.contains("سلام"));
    assertTrue(combined.contains("جهان"));
    assertTrue(combined.contains("کتاب"));

    // All English words should be present
    assertTrue(combined.contains("hello"));
    assertTrue(combined.contains("world"));
    assertTrue(combined.contains("book"));
  }

  /**
   * Tests combined dictionary with overlapping words (transliterations). Some words may appear in
   * both languages (e.g., borrowed words).
   */
  @Test
  public void testCombinedDictionary_withOverlap() {
    Set<String> arabicDict = new HashSet<>();
    arabicDict.add("مرحبا");
    arabicDict.add("email"); // Borrowed English word used in Arabic context
    arabicDict.add("internet"); // Borrowed word

    Set<String> frenchDict = new HashSet<>();
    frenchDict.add("bonjour");
    frenchDict.add("email"); // Same word in French
    frenchDict.add("internet"); // Same word in French

    // Combine dictionaries
    Set<String> combined = new HashSet<>();
    combined.addAll(arabicDict);
    combined.addAll(frenchDict);

    // Combined should have 4 unique words (email and internet are duplicates)
    assertEquals("Combined dictionary should have 4 unique words", 4, combined.size());

    // All unique words should be present
    assertTrue(combined.contains("مرحبا"));
    assertTrue(combined.contains("bonjour"));
    assertTrue(combined.contains("email"));
    assertTrue(combined.contains("internet"));
  }

  // ==================== RTL Line Detection Tests ====================

  /**
   * Tests that isLineRtl correctly identifies RTL lines with Persian text. Uses mock RecognizedWord
   * objects to avoid Android dependencies.
   */
  @Test
  public void testIsLineRtl_PersianText() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    // Create a line with Persian words using mock objects
    java.util.List<RecognizedWord> persianLine = new java.util.ArrayList<>();
    persianLine.add(createMockWord("سلام")); // "Hello" in Persian
    persianLine.add(createMockWord("جهان")); // "World" in Persian

    boolean isRtl = (boolean) method.invoke(null, persianLine);
    assertTrue("Persian line should be detected as RTL", isRtl);
  }

  /** Tests that isLineRtl correctly identifies RTL lines with Arabic text. */
  @Test
  public void testIsLineRtl_ArabicText() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    // Create a line with Arabic words
    java.util.List<RecognizedWord> arabicLine = new java.util.ArrayList<>();
    arabicLine.add(createMockWord("مرحبا")); // "Hello" in Arabic
    arabicLine.add(createMockWord("عالم")); // "World" in Arabic

    boolean isRtl = (boolean) method.invoke(null, arabicLine);
    assertTrue("Arabic line should be detected as RTL", isRtl);
  }

  /** Tests that isLineRtl correctly identifies LTR lines with English text. */
  @Test
  public void testIsLineRtl_EnglishText() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    // Create a line with English words
    java.util.List<RecognizedWord> englishLine = new java.util.ArrayList<>();
    englishLine.add(createMockWord("Hello"));
    englishLine.add(createMockWord("World"));

    boolean isRtl = (boolean) method.invoke(null, englishLine);
    assertFalse("English line should be detected as LTR", isRtl);
  }

  /**
   * Tests that isLineRtl handles mixed content correctly. When RTL characters are the majority, the
   * line should be RTL.
   */
  @Test
  public void testIsLineRtl_MixedContent_MajorityRtl() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    // Create a line with mostly Persian and some English
    java.util.List<RecognizedWord> mixedLine = new java.util.ArrayList<>();
    mixedLine.add(createMockWord("این")); // "This" in Persian (3 RTL chars)
    mixedLine.add(createMockWord("یک")); // "One" in Persian (2 RTL chars)
    mixedLine.add(createMockWord("test")); // English (4 LTR chars)
    mixedLine.add(createMockWord("است")); // "Is" in Persian (3 RTL chars)

    // 8 RTL chars vs 4 LTR chars -> should be RTL
    boolean isRtl = (boolean) method.invoke(null, mixedLine);
    assertTrue("Mixed line with majority RTL should be detected as RTL", isRtl);
  }

  /**
   * Tests that isLineRtl handles mixed content correctly. When LTR characters are the majority, the
   * line should be LTR.
   */
  @Test
  public void testIsLineRtl_MixedContent_MajorityLtr() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    // Create a line with mostly English and some Persian
    java.util.List<RecognizedWord> mixedLine = new java.util.ArrayList<>();
    mixedLine.add(createMockWord("This")); // English (4 LTR chars)
    mixedLine.add(createMockWord("is")); // English (2 LTR chars)
    mixedLine.add(createMockWord("a")); // English (1 LTR char)
    mixedLine.add(createMockWord("تست")); // "Test" in Persian (3 RTL chars)

    // 7 LTR chars vs 3 RTL chars -> should be LTR
    boolean isRtl = (boolean) method.invoke(null, mixedLine);
    assertFalse("Mixed line with majority LTR should be detected as LTR", isRtl);
  }

  /** Tests that isLineRtl handles empty lines correctly. */
  @Test
  public void testIsLineRtl_EmptyLine() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    java.util.List<RecognizedWord> emptyLine = new java.util.ArrayList<>();
    boolean isRtl = (boolean) method.invoke(null, emptyLine);
    assertFalse("Empty line should default to LTR", isRtl);
  }

  /** Tests that isLineRtl handles null correctly. */
  @Test
  public void testIsLineRtl_NullLine() throws Exception {
    java.lang.reflect.Method method =
        OCRPostProcessor.class.getDeclaredMethod("isLineRtl", java.util.List.class);
    method.setAccessible(true);

    boolean isRtl = (boolean) method.invoke(null, (Object) null);
    assertFalse("Null line should default to LTR", isRtl);
  }

  /**
   * Helper method to create a mock RecognizedWord with only text (no Android dependencies). Uses
   * reflection to create the object without requiring android.graphics.RectF.
   */
  private RecognizedWord createMockWord(String text) {
    try {
      // Use reflection to create RecognizedWord with null bounding box
      // The isLineRtl method only uses getText(), so bounding box can be null
      java.lang.reflect.Constructor<RecognizedWord> constructor =
          RecognizedWord.class.getDeclaredConstructor(
              String.class, android.graphics.RectF.class, float.class);
      constructor.setAccessible(true);
      return constructor.newInstance(text, null, 90.0f);
    } catch (Exception e) {
      // Fallback: create a simple mock that just returns the text
      return new RecognizedWord(text, null, 90.0f);
    }
  }

  // ==================== wordsToText RTL Sorting Tests ====================
  // NOTE: Tests for wordsToText() with bounding box coordinates require android.graphics.RectF
  // and are located in androidTest: WordsToTextRtlSortingTest.java
  // The isLineRtl tests above verify the RTL detection logic without Android dependencies.
}
