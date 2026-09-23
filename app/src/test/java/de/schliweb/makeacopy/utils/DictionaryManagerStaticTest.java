package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.utils.ocr.DictionaryManager;
import org.junit.Test;

/**
 * Tests for static utility methods of {@link DictionaryManager} that do not require Android
 * Context.
 */
public class DictionaryManagerStaticTest {

  // ── isWordBasedLanguage ───────────────────────────────────────────────────

  @Test
  public void isWordBasedLanguage_null_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage(null));
  }

  @Test
  public void isWordBasedLanguage_empty_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage(""));
  }

  @Test
  public void isWordBasedLanguage_eng_returnsTrue() {
    assertTrue(DictionaryManager.isWordBasedLanguage("eng"));
  }

  @Test
  public void isWordBasedLanguage_deu_returnsTrue() {
    assertTrue(DictionaryManager.isWordBasedLanguage("deu"));
  }

  @Test
  public void isWordBasedLanguage_chiSim_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim"));
  }

  @Test
  public void isWordBasedLanguage_chiTra_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_tra"));
  }

  @Test
  public void isWordBasedLanguage_tha_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("tha"));
  }

  @Test
  public void isWordBasedLanguage_hin_returnsTrue() {
    assertTrue(DictionaryManager.isWordBasedLanguage("hin"));
  }

  @Test
  public void isWordBasedLanguage_multiLang_anyWordBased_returnsTrue() {
    // "fas+eng" → eng is word-based → true
    assertTrue(DictionaryManager.isWordBasedLanguage("fas+eng"));
  }

  @Test
  public void isWordBasedLanguage_multiLang_allNonWordBased_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("chi_sim+chi_tra"));
  }

  @Test
  public void isWordBasedLanguage_multiLang_mixedWithWordBased_returnsTrue() {
    assertTrue(DictionaryManager.isWordBasedLanguage("chi_sim+deu"));
  }

  @Test
  public void isWordBasedLanguage_whitespace_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("   "));
  }

  @Test
  public void isWordBasedLanguage_plusOnly_returnsFalse() {
    assertFalse(DictionaryManager.isWordBasedLanguage("+"));
  }
}
