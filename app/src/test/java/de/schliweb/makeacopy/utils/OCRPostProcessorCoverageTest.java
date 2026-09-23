package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Additional JVM unit tests for {@link OCRPostProcessor} covering processText, analyzeQuality,
 * wordsToText, isLineRtl, process, and OcrQualityStats.
 */
public class OCRPostProcessorCoverageTest {

  private static RectF rect(float l, float t, float r, float b) {
    RectF rf = new RectF();
    rf.left = l;
    rf.top = t;
    rf.right = r;
    rf.bottom = b;
    return rf;
  }

  // ---- processText ----

  @Test
  public void processText_null_returnsNull() {
    assertNull(OCRPostProcessor.processText(null, "eng"));
  }

  @Test
  public void processText_empty_returnsEmpty() {
    assertEquals("", OCRPostProcessor.processText("", "eng"));
  }

  @Test
  public void processText_shortText_unchanged() {
    assertEquals("Hi", OCRPostProcessor.processText("Hi", "eng"));
  }

  @Test
  public void processText_appliesLigatureCorrections() {
    // "rn" -> "m" is a common ligature correction
    String result = OCRPostProcessor.processText("cornputer", "eng");
    assertEquals("computer", result);
  }

  @Test
  public void processText_appliesContextAwareCorrections() {
    // In numeric context, 'O' should become '0'
    String result = OCRPostProcessor.processText("1O3", "eng");
    assertEquals("103", result);
  }

  // ---- analyzeQuality ----

  @Test
  public void analyzeQuality_null_returnsZeroStats() {
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(null);
    assertEquals(0, stats.totalWordCount());
    assertEquals(0, stats.lowConfidenceWordCount());
  }

  @Test
  public void analyzeQuality_empty_returnsZeroStats() {
    OCRPostProcessor.OcrQualityStats stats =
        OCRPostProcessor.analyzeQuality(Collections.emptyList());
    assertEquals(0, stats.totalWordCount());
  }

  @Test
  public void analyzeQuality_allHighConfidence() {
    List<RecognizedWord> words = Arrays.asList(word("hello", 95f), word("world", 90f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    assertEquals(2, stats.totalWordCount());
    assertEquals(0, stats.lowConfidenceWordCount());
    assertTrue(stats.meanConfidence() > 90f);
    assertEquals(0f, stats.getLowConfidenceRatio(), 0.01f);
  }

  @Test
  public void analyzeQuality_mixedConfidence() {
    List<RecognizedWord> words =
        Arrays.asList(word("good", 90f), word("bad", 20f), word("ok", 60f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    assertEquals(3, stats.totalWordCount());
    assertEquals(2, stats.lowConfidenceWordCount()); // 20 and 60 < LOW_CONFIDENCE_THRESHOLD (70)
    assertTrue(stats.getLowConfidenceRatio() > 0f);
  }

  @Test
  public void analyzeQuality_toString_containsInfo() {
    List<RecognizedWord> words = Arrays.asList(word("test", 80f));
    OCRPostProcessor.OcrQualityStats stats = OCRPostProcessor.analyzeQuality(words);
    String s = stats.toString();
    assertTrue(s.contains("meanConf="));
    assertTrue(s.contains("medianConf="));
  }

  // ---- process (List<RecognizedWord>) ----

  @Test
  public void process_null_returnsNull() {
    assertNull(OCRPostProcessor.process(null, "eng"));
  }

  @Test
  public void process_empty_returnsEmpty() {
    List<RecognizedWord> empty = Collections.emptyList();
    assertSame(empty, OCRPostProcessor.process(empty, "eng"));
  }

  @Test
  public void process_highConfidenceWord_unchanged() {
    List<RecognizedWord> words = Arrays.asList(word("hello", 95f));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    assertEquals("hello", result.get(0).getText());
  }

  @Test
  public void process_lowConfidenceWord_corrected() {
    // "cornputer" with low confidence should get ligature correction
    List<RecognizedWord> words = Arrays.asList(word("cornputer", 30f));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    assertEquals("computer", result.get(0).getText());
  }

  // ---- wordsToText ----

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
        Arrays.asList(new RecognizedWord("hello", rect(10, 10, 50, 30), 90f));
    String text = OCRPostProcessor.wordsToText(words);
    assertEquals("hello", text.trim());
  }

  @Test
  public void wordsToText_twoWordsOnSameLine() {
    List<RecognizedWord> words =
        Arrays.asList(
            new RecognizedWord("hello", rect(10, 10, 50, 30), 90f),
            new RecognizedWord("world", rect(60, 10, 100, 30), 90f));
    String text = OCRPostProcessor.wordsToText(words);
    assertEquals("hello world", text.trim());
  }

  // wordsToText line-break and paragraph tests require RectF.height() which
  // returns 0 in Android unit-test stubs; these are covered by instrumented tests.

  // ---- Helper ----

  private static RecognizedWord word(String text, float confidence) {
    return new RecognizedWord(text, rect(0, 0, 10, 10), confidence);
  }
}
