package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import org.junit.Test;

/**
 * Tests for Turkish-specific OCR correction logic in {@link OCRPostProcessor}. Covers
 * MOSTLY_LETTERS regex recognition of Turkish characters, ligature corrections on Turkish words,
 * context-aware corrections, and OCR_CONFUSIONS for İ/ı/ğ/ş.
 */
public class OCRPostProcessorTurkishTest {

  // ── MOSTLY_LETTERS: Turkish characters recognized as letter-words ──

  @Test
  public void processText_turkishWord_withDotlessI_ligatureApplied() {
    // "cornışık" contains "rn" → should become "mışık" (ligature rn→m applied)
    // because ı is recognized as a letter by MOSTLY_LETTERS
    String result = OCRPostProcessor.processText("cornışık", "tur");
    assertEquals("comışık", result);
  }

  @Test
  public void processText_turkishWord_withGBreve_ligatureApplied() {
    // "cornğu" contains "rn" → should become "comğu"
    String result = OCRPostProcessor.processText("cornğu", "tur");
    assertEquals("comğu", result);
  }

  @Test
  public void processText_turkishWord_withSCedilla_ligatureApplied() {
    // "cornşe" contains "rn" → should become "comşe"
    String result = OCRPostProcessor.processText("cornşe", "tur");
    assertEquals("comşe", result);
  }

  @Test
  public void processText_turkishWord_withDottedI_ligatureApplied() {
    // "İcornputer" contains "rn" → should become "İcomputer"
    String result = OCRPostProcessor.processText("İcornputer", "tur");
    assertEquals("İcomputer", result);
  }

  @Test
  public void processText_turkishWord_withCapitalGBreve_ligatureApplied() {
    // "Ğcornputer" contains "rn" → should become "Ğcomputer"
    String result = OCRPostProcessor.processText("Ğcornputer", "tur");
    assertEquals("Ğcomputer", result);
  }

  @Test
  public void processText_turkishWord_withCapitalSCedilla_ligatureApplied() {
    // "Şcornputer" contains "rn" → should become "Şcomputer"
    String result = OCRPostProcessor.processText("Şcornputer", "tur");
    assertEquals("Şcomputer", result);
  }

  // ── Context-aware corrections with Turkish characters ──

  @Test
  public void processText_contextAware_digitInTurkishWord() {
    // "h0use" → "hOuse" (0 between letters becomes O)
    // This should also work when Turkish chars are nearby
    String result = OCRPostProcessor.processText("h0use", "tur");
    assertEquals("hOuse", result);
  }

  @Test
  public void processText_contextAware_letterInNumbers_unchanged() {
    // "1O3" → "103" (O between digits becomes 0)
    String result = OCRPostProcessor.processText("1O3", "tur");
    assertEquals("103", result);
  }

  // ── Pure Turkish text: no false corrections ──

  @Test
  public void processText_pureTurkishText_noFalseCorrections() {
    // Common Turkish words should not be falsely corrected
    assertEquals("merhaba", OCRPostProcessor.processText("merhaba", "tur"));
  }

  @Test
  public void processText_turkishWithDotlessI_preserved() {
    assertEquals("sığır", OCRPostProcessor.processText("sığır", "tur"));
  }

  @Test
  public void processText_turkishWithDottedCapitalI_preserved() {
    assertEquals("İstanbul", OCRPostProcessor.processText("İstanbul", "tur"));
  }

  @Test
  public void processText_turkishWithSCedilla_preserved() {
    assertEquals("şehir", OCRPostProcessor.processText("şehir", "tur"));
  }

  @Test
  public void processText_turkishWithGBreve_preserved() {
    assertEquals("dağ", OCRPostProcessor.processText("dağ", "tur"));
  }

  @Test
  public void processText_turkishWithCCedilla_preserved() {
    assertEquals("çay", OCRPostProcessor.processText("çay", "tur"));
  }

  @Test
  public void processText_turkishSentence_preserved() {
    String input = "Türkçe güzel bir dil";
    assertEquals(input, OCRPostProcessor.processText(input, "tur"));
  }

  @Test
  public void processText_turkishWithAllSpecialChars_preserved() {
    // Sentence using all Turkish-specific characters
    String input = "İıĞğŞşÇçÖöÜü";
    assertEquals(input, OCRPostProcessor.processText(input, "tur"));
  }

  // ── Ligature vv→w with Turkish characters ──

  @Test
  public void processText_ligature_vv_to_w_inTurkishContext() {
    String result = OCRPostProcessor.processText("follovving", "tur");
    assertEquals("following", result);
  }

  // ── Ligature cl→d with Turkish characters ──

  @Test
  public void processText_ligature_cl_to_d_inTurkishContext() {
    String result = OCRPostProcessor.processText("aclvance", "tur");
    assertEquals("advance", result);
  }

  // ── Short words: no ligature correction ──

  @Test
  public void processText_shortTurkishWord_noLigatureCorrection() {
    // Words shorter than MIN_WORD_LENGTH_FOR_PATTERN_CORRECTION (3) should not be corrected
    assertEquals("rn", OCRPostProcessor.processText("rn", "tur"));
  }

  // ── Null and empty handling ──

  @Test
  public void processText_null_withTurkishLang_returnsNull() {
    assertNull(OCRPostProcessor.processText(null, "tur"));
  }

  @Test
  public void processText_empty_withTurkishLang_returnsEmpty() {
    assertEquals("", OCRPostProcessor.processText("", "tur"));
  }

  // ── Mixed Turkish and numeric text ──

  @Test
  public void processText_turkishWithNumbers_preserved() {
    assertEquals("İstanbul 34", OCRPostProcessor.processText("İstanbul 34", "tur"));
  }

  @Test
  public void processText_numericString_withTurkishLang_noCorrection() {
    assertEquals("123456", OCRPostProcessor.processText("123456", "tur"));
  }
}
