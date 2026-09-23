package de.schliweb.makeacopy.ui.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link OCRViewModel.OcrUiState} immutable record and its {@code with*} methods.
 */
public class OcrUiStateTest {

  private static OCRViewModel.OcrUiState defaultState() {
    return new OCRViewModel.OcrUiState(
        false, false, "eng", null, new ArrayList<>(), null, null, null, null, null, false);
  }

  @Test
  public void defaultState_hasExpectedDefaults() {
    OCRViewModel.OcrUiState s = defaultState();
    assertFalse(s.processing());
    assertFalse(s.imageProcessed());
    assertEquals("eng", s.language());
    assertNull(s.ocrText());
    assertTrue(s.words().isEmpty());
    assertNull(s.durationMs());
    assertNull(s.meanConfidence());
    assertNull(s.transform());
    assertNull(s.reviewedText());
    assertNull(s.reviewedWords());
    assertFalse(s.hasReviewEdits());
  }

  @Test
  public void withProcessing_changesOnlyProcessing() {
    OCRViewModel.OcrUiState s = defaultState().withProcessing(true);
    assertTrue(s.processing());
    assertFalse(s.imageProcessed());
    assertEquals("eng", s.language());
  }

  @Test
  public void withImageProcessed_changesOnlyImageProcessed() {
    OCRViewModel.OcrUiState s = defaultState().withImageProcessed(true);
    assertFalse(s.processing());
    assertTrue(s.imageProcessed());
  }

  @Test
  public void withText_changesOnlyOcrText() {
    OCRViewModel.OcrUiState s = defaultState().withText("Hello World");
    assertEquals("Hello World", s.ocrText());
    assertFalse(s.processing());
  }

  @Test
  public void withWords_changesOnlyWords() {
    List<RecognizedWord> words = new ArrayList<>();
    OCRViewModel.OcrUiState s = defaultState().withWords(words);
    assertEquals(words, s.words());
    assertNull(s.ocrText());
  }

  @Test
  public void chaining_withMethods() {
    OCRViewModel.OcrUiState s =
        defaultState().withProcessing(true).withText("test").withImageProcessed(true);
    assertTrue(s.processing());
    assertEquals("test", s.ocrText());
    assertTrue(s.imageProcessed());
  }
}
