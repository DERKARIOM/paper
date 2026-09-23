package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OCRPostProcessorProcessTest {

  private static RecognizedWord word(String text, float confidence) {
    return new RecognizedWord(text, new RectF(), confidence);
  }

  // ── process(words, language) ──────────────────────────────────────────────

  @Test
  public void process_null_returnsNull() {
    assertNull(OCRPostProcessor.process(null, "eng"));
  }

  @Test
  public void process_empty_returnsEmpty() {
    List<RecognizedWord> result = OCRPostProcessor.process(Collections.emptyList(), "eng");
    assertTrue(result.isEmpty());
  }

  @Test
  public void process_highConfidence_unchanged() {
    List<RecognizedWord> words = Arrays.asList(word("Hello", 95), word("World", 92));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(2, result.size());
    assertEquals("Hello", result.get(0).getText());
    assertEquals("World", result.get(1).getText());
  }

  @Test
  public void process_lowConfidence_ligatureCorrection() {
    // "rn" → "m" is a common ligature correction for low confidence
    List<RecognizedWord> words = Collections.singletonList(word("cornputer", 40));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    // Should correct "rn" → "m" → "computer"
    assertEquals("computer", result.get(0).getText());
  }

  @Test
  public void process_preservesListSize() {
    List<RecognizedWord> words = Arrays.asList(word("one", 80), word("two", 80), word("three", 80));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(3, result.size());
  }

  @Test
  public void process_mediumConfidence_appliesLigatureCorrection() {
    // Between LOW and HIGH threshold (70-85): ligature corrections applied
    List<RecognizedWord> words = Collections.singletonList(word("cornputer", 75));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    assertEquals("computer", result.get(0).getText());
  }

  @Test
  public void process_shortWord_noLigatureCorrection() {
    // Words < 3 chars should skip ligature corrections
    List<RecognizedWord> words = Collections.singletonList(word("rn", 40));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    assertEquals("rn", result.get(0).getText());
  }

  @Test
  public void process_numericString_noLetterCorrection() {
    List<RecognizedWord> words = Collections.singletonList(word("12345", 40));
    List<RecognizedWord> result = OCRPostProcessor.process(words, "eng");
    assertEquals(1, result.size());
    assertEquals("12345", result.get(0).getText());
  }
}
