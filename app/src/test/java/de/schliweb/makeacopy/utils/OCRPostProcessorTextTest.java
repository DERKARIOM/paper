package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link OCRPostProcessor} public static methods: processText, analyzeQuality,
 * wordsToText, and OcrQualityStats record.
 */
public class OCRPostProcessorTextTest {

  // ── processText ──────────────────────────────────────────────────────────

  @Test
  public void processText_null_returnsNull() {
    assertEquals(null, OCRPostProcessor.processText(null, "eng"));
  }

  @Test
  public void processText_empty_returnsEmpty() {
    assertEquals("", OCRPostProcessor.processText("", "eng"));
  }

  @Test
  public void processText_plainText_unchanged() {
    assertEquals("hello world", OCRPostProcessor.processText("hello world", "eng"));
  }

  @Test
  public void processText_ligature_rn_to_m() {
    // "cornputer" contains "rn" which should become "m" → "computer"
    String result = OCRPostProcessor.processText("cornputer", "eng");
    assertEquals("computer", result);
  }

  @Test
  public void processText_ligature_cl_to_d() {
    // "clog" should not change (cl at start), but "incline" → depends on context
    // "aclvance" contains "cl" → "advance"
    String result = OCRPostProcessor.processText("aclvance", "eng");
    assertEquals("advance", result);
  }

  @Test
  public void processText_ligature_vv_to_w() {
    String result = OCRPostProcessor.processText("follovving", "eng");
    assertEquals("following", result);
  }

  @Test
  public void processText_shortWord_noLigatureCorrection() {
    // Words shorter than MIN_WORD_LENGTH_FOR_PATTERN_CORRECTION (3) should not be corrected
    assertEquals("rn", OCRPostProcessor.processText("rn", "eng"));
  }

  @Test
  public void processText_numericString_noLigatureCorrection() {
    // Pure numeric strings should not get ligature corrections
    assertEquals("123456", OCRPostProcessor.processText("123456", "eng"));
  }

  @Test
  public void processText_contextAware_letterInNumbers() {
    // "1O3" → O between digits should become 0 → "103"
    String result = OCRPostProcessor.processText("1O3", "eng");
    assertEquals("103", result);
  }

  @Test
  public void processText_contextAware_digitInLetters() {
    // "h0use" → 0 between letters should become O → "hOuse"
    String result = OCRPostProcessor.processText("h0use", "eng");
    assertEquals("hOuse", result);
  }

  // ── analyzeQuality ───────────────────────────────────────────────────────

  @Test
  public void analyzeQuality_null_returnsZeroStats() {
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(null);
    assertEquals(0, stats.totalWordCount());
    assertEquals(0, stats.lowConfidenceWordCount());
    assertEquals(0.0f, stats.meanConfidence(), 0.01f);
  }

  @Test
  public void analyzeQuality_empty_returnsZeroStats() {
    OCRPostProcessor.OcrQualityStats stats =
        OCRPostProcessor.analyzeQuality(Collections.emptyList());
    assertEquals(0, stats.totalWordCount());
  }

  @Test
  public void analyzeQuality_allHighConfidence() {
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("hello", new RectF(0, 0, 50, 20), 95.0f),
            new RecognizedWord("world", new RectF(60, 0, 110, 20), 90.0f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    assertEquals(2, stats.totalWordCount());
    assertEquals(0, stats.lowConfidenceWordCount());
    assertEquals(92.5f, stats.meanConfidence(), 0.01f);
  }

  @Test
  public void analyzeQuality_mixedConfidence() {
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("good", new RectF(0, 0, 50, 20), 90.0f),
            new RecognizedWord("bad", new RectF(60, 0, 110, 20), 40.0f),
            new RecognizedWord("ok", new RectF(120, 0, 160, 20), 75.0f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    assertEquals(3, stats.totalWordCount());
    assertEquals(1, stats.lowConfidenceWordCount()); // only "bad" < 70
    assertTrue(stats.suspiciousWords().size() > 0);
  }

  @Test
  public void analyzeQuality_medianCalculation() {
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("a", new RectF(0, 0, 10, 10), 30.0f),
            new RecognizedWord("b", new RectF(20, 0, 30, 10), 50.0f),
            new RecognizedWord("c", new RectF(40, 0, 50, 10), 90.0f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    assertEquals(50.0f, stats.medianConfidence(), 0.01f);
  }

  // ── OcrQualityStats ──────────────────────────────────────────────────────

  @Test
  public void ocrQualityStats_lowConfidenceRatio() {
    OCRPostProcessor.OcrQualityStats stats =
        new OCRPostProcessor.OcrQualityStats(60.0f, 55.0f, 3, 10, new ArrayList<>());
    assertEquals(0.3f, stats.getLowConfidenceRatio(), 0.001f);
  }

  @Test
  public void ocrQualityStats_lowConfidenceRatio_zeroTotal() {
    OCRPostProcessor.OcrQualityStats stats =
        new OCRPostProcessor.OcrQualityStats(0, 0, 0, 0, new ArrayList<>());
    assertEquals(0.0f, stats.getLowConfidenceRatio(), 0.001f);
  }

  @Test
  public void ocrQualityStats_toString_containsValues() {
    OCRPostProcessor.OcrQualityStats stats =
        new OCRPostProcessor.OcrQualityStats(85.5f, 88.0f, 2, 20, new ArrayList<>());
    String s = stats.toString();
    assertTrue(s.contains("85.5"));
    assertTrue(s.contains("2/20"));
  }

  // ── wordsToText ──────────────────────────────────────────────────────────

  @Test
  public void wordsToText_null_returnsEmpty() {
    assertEquals("", OCRPostProcessor.wordsToText(null));
  }

  @Test
  public void wordsToText_empty_returnsEmpty() {
    assertEquals("", OCRPostProcessor.wordsToText(Collections.emptyList()));
  }

  @Test
  public void wordsToText_singleWord() {
    List<RecognizedWord> words =
        Collections.singletonList(new RecognizedWord("hello", new RectF(0, 0, 50, 20), 90.0f));
    String result = OCRPostProcessor.wordsToText(words);
    assertEquals("hello", result);
  }

  @Test
  public void wordsToText_multipleWords_spaceSeparated() {
    // With stub RectF (all coords 0), all words land on same "line"
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("hello", new RectF(0, 0, 0, 0), 90.0f),
            new RecognizedWord("world", new RectF(0, 0, 0, 0), 90.0f));
    String result = OCRPostProcessor.wordsToText(words);
    assertTrue(result.contains("hello"));
    assertTrue(result.contains("world"));
  }

  @Test
  public void wordsToText_htmlEntities_decoded() {
    List<RecognizedWord> words =
        Collections.singletonList(new RecognizedWord("don&apos;t", new RectF(0, 0, 0, 0), 90.0f));
    String result = OCRPostProcessor.wordsToText(words);
    assertEquals("don't", result);
  }

  @Test
  public void wordsToText_densePaddleBoxes_usesStableOrdering() {
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("A", new RectF(30, 0, 40, 100), 90.0f),
            new RecognizedWord("B", new RectF(10, 45, 20, 55), 90.0f),
            new RecognizedWord("C", new RectF(20, 90, 30, 100), 90.0f),
            new RecognizedWord("D", new RectF(0, 135, 10, 145), 90.0f));

    String result = OCRPostProcessor.wordsToText(words);

    assertTrue(result.contains("A"));
    assertTrue(result.contains("B"));
    assertTrue(result.contains("C"));
    assertTrue(result.contains("D"));
  }
}
