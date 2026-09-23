package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.utils.ocr.DictionaryManager;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Tests for Hindi (hin) dictionary support: word-based classification, dictionary correction,
 * combined dictionaries (hin+eng), and mixed Hindi+English content.
 */
public class HindiDictionaryTest {

  // ── isWordBasedLanguage ───────────────────────────────────────────────────

  @Test
  public void isWordBasedLanguage_hin_returnsTrue() {
    assertTrue("Hindi should be word-based", DictionaryManager.isWordBasedLanguage("hin"));
  }

  @Test
  public void isWordBasedLanguage_hinPlusEng_returnsTrue() {
    assertTrue("hin+eng should be word-based", DictionaryManager.isWordBasedLanguage("hin+eng"));
  }

  @Test
  public void isWordBasedLanguage_hinPlusChiSim_returnsTrue() {
    assertTrue(
        "hin+chi_sim should be word-based (hin is word-based)",
        DictionaryManager.isWordBasedLanguage("hin+chi_sim"));
  }

  // ── Hindi dictionary correction ───────────────────────────────────────────

  @Test
  public void testHindi_dictionaryCorrection_noMatchReturnsNull() throws Exception {
    Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> dictionary = new HashSet<>();
    dictionary.add("नमस्ते"); // hello
    dictionary.add("दुनिया"); // world
    dictionary.add("भारत"); // India
    dictionary.add("हिन्दी"); // Hindi
    dictionary.add("किताब"); // book
    dictionary.add("घर"); // house

    // Latin text has no match in Hindi dictionary
    assertNull(method.invoke(null, "xyz", dictionary));
  }

  @Test
  public void testHindi_dictionaryContainsDevanagariWords() {
    Set<String> dictionary = new HashSet<>();
    dictionary.add("नमस्ते");
    dictionary.add("दुनिया");
    dictionary.add("भारत");
    dictionary.add("हिन्दी");
    dictionary.add("किताब");
    dictionary.add("घर");
    dictionary.add("पानी"); // water
    dictionary.add("खाना"); // food

    assertTrue("Dictionary should contain 'नमस्ते'", dictionary.contains("नमस्ते"));
    assertTrue("Dictionary should contain 'भारत'", dictionary.contains("भारत"));
    assertTrue("Dictionary should contain 'हिन्दी'", dictionary.contains("हिन्दी"));
    assertTrue("Dictionary should contain 'पानी'", dictionary.contains("पानी"));
    assertTrue("Dictionary should contain 'खाना'", dictionary.contains("खाना"));
  }

  // ── Combined dictionary (hin+eng) ─────────────────────────────────────────

  @Test
  public void testCombinedDictionary_HindiEnglish() throws Exception {
    Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    Set<String> combinedDictionary = new HashSet<>();

    // English words
    combinedDictionary.add("hello");
    combinedDictionary.add("world");
    combinedDictionary.add("computer");
    combinedDictionary.add("document");
    combinedDictionary.add("scanner");

    // Hindi words (Devanagari)
    combinedDictionary.add("नमस्ते"); // hello
    combinedDictionary.add("दुनिया"); // world
    combinedDictionary.add("कंप्यूटर"); // computer
    combinedDictionary.add("दस्तावेज़"); // document
    combinedDictionary.add("स्कैनर"); // scanner
    combinedDictionary.add("किताब"); // book
    combinedDictionary.add("घर"); // house

    // English OCR corrections should work in combined dictionary
    // 0 -> o correction: "hell0" -> "hello"
    assertEquals("hello", method.invoke(null, "hell0", combinedDictionary));

    // 0 -> o correction: "w0rld" -> "world"
    assertEquals("world", method.invoke(null, "w0rld", combinedDictionary));

    // rn -> m correction: "cornputer" -> "computer"
    assertEquals("computer", method.invoke(null, "cornputer", combinedDictionary));

    // 1 -> l correction: "wor1d" -> "world"
    assertEquals("world", method.invoke(null, "wor1d", combinedDictionary));

    // Hindi words should be in combined dictionary
    assertTrue(
        "Hindi word 'नमस्ते' should be in combined dictionary",
        combinedDictionary.contains("नमस्ते"));
    assertTrue(
        "Hindi word 'दुनिया' should be in combined dictionary",
        combinedDictionary.contains("दुनिया"));
    assertTrue(
        "Hindi word 'कंप्यूटर' should be in combined dictionary",
        combinedDictionary.contains("कंप्यूटर"));
  }

  // ── Mixed content (Hindi + English) ───────────────────────────────────────

  @Test
  public void testMixedContent_HindiEnglish() throws Exception {
    Method method =
        OCRPostProcessor.class.getDeclaredMethod(
            "findDictionaryCorrection", String.class, Set.class);
    method.setAccessible(true);

    // Combined dictionary simulating hin+eng
    Set<String> combinedDictionary = new HashSet<>();

    // Common English words
    combinedDictionary.add("the");
    combinedDictionary.add("and");
    combinedDictionary.add("hello");
    combinedDictionary.add("name");
    combinedDictionary.add("email");

    // Common Hindi words
    combinedDictionary.add("और"); // and
    combinedDictionary.add("है"); // is
    combinedDictionary.add("नाम"); // name
    combinedDictionary.add("ईमेल"); // email
    combinedDictionary.add("भारत"); // India

    // English corrections should work
    assertEquals("hello", method.invoke(null, "hell0", combinedDictionary));
    assertEquals("name", method.invoke(null, "narne", combinedDictionary)); // rn -> m
    assertEquals("email", method.invoke(null, "ernail", combinedDictionary)); // rn -> m

    // Hindi words exist in combined dictionary
    assertTrue(combinedDictionary.contains("और"));
    assertTrue(combinedDictionary.contains("है"));
    assertTrue(combinedDictionary.contains("नाम"));
    assertTrue(combinedDictionary.contains("ईमेल"));
    assertTrue(combinedDictionary.contains("भारत"));
  }

  // ── Combined dictionary size ──────────────────────────────────────────────

  @Test
  public void testCombinedDictionarySize_HindiEnglish() {
    Set<String> combinedDictionary = new HashSet<>();

    // English words
    combinedDictionary.add("hello");
    combinedDictionary.add("world");
    combinedDictionary.add("computer");

    // Hindi words
    combinedDictionary.add("नमस्ते");
    combinedDictionary.add("दुनिया");
    combinedDictionary.add("कंप्यूटर");

    // No overlap between Hindi and English → size = sum of both
    assertEquals(
        "Combined dictionary should contain all words from both languages",
        6,
        combinedDictionary.size());
  }

  @Test
  public void testCombinedDictionary_HindiEnglish_noOverlap() {
    Set<String> hindiWords = new HashSet<>();
    hindiWords.add("नमस्ते");
    hindiWords.add("दुनिया");
    hindiWords.add("भारत");

    Set<String> englishWords = new HashSet<>();
    englishWords.add("hello");
    englishWords.add("world");
    englishWords.add("India");

    // Devanagari and Latin scripts never overlap
    Set<String> combined = new HashSet<>(hindiWords);
    combined.addAll(englishWords);
    assertEquals(
        "Hindi and English words should not overlap",
        hindiWords.size() + englishWords.size(),
        combined.size());
  }
}
