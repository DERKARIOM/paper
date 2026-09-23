package de.schliweb.makeacopy.ui.ocr.review.model;

import static org.junit.Assert.*;

import android.graphics.RectF;
import de.schliweb.makeacopy.ui.ocr.OCRViewModel;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** JVM unit tests for {@link OcrDocMapper}. */
public class OcrDocMapperTest {

  private static RectF rect(float l, float t, float r, float b) {
    RectF rf = new RectF();
    rf.left = l;
    rf.top = t;
    rf.right = r;
    rf.bottom = b;
    return rf;
  }

  private static OCRViewModel.OcrUiState emptyState() {
    return new OCRViewModel.OcrUiState(
        false, false, "eng", "", new ArrayList<>(), null, null, null, null, null, false);
  }

  @Test
  public void fromState_null_returnsEmptyDoc() {
    OcrDoc doc = OcrDocMapper.fromState(null);
    assertNotNull(doc);
    assertEquals(1, doc.schema);
    assertTrue(doc.words.isEmpty());
  }

  @Test
  public void fromState_emptyState_returnsEmptyDoc() {
    OcrDoc doc = OcrDocMapper.fromState(emptyState());
    assertNotNull(doc);
    assertTrue(doc.words.isEmpty());
  }

  @Test
  public void fromState_withTransform_setsImageSize() {
    OCRViewModel.OcrTransform tx = new OCRViewModel.OcrTransform(100, 200, 300, 400, 3f, 2f, 0, 0);
    OCRViewModel.OcrUiState state = emptyState().withTransform(tx);
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(300, doc.imageSize.w);
    assertEquals(400, doc.imageSize.h);
  }

  @Test
  public void fromState_withWords_mapsCorrectly() {
    RecognizedWord w = new RecognizedWord("hello", rect(10, 20, 60, 40), 85f);
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(1, doc.words.size());
    OcrDoc.Word dw = doc.words.get(0);
    assertEquals(1, dw.id);
    assertEquals("hello", dw.t);
    // Note: bbox values are 0 in JVM tests because RectF copy constructor returns zeros
    // confidence 85 > 1 → normalized to 0.85
    assertEquals(0.85f, dw.c, 0.01f);
    assertFalse(dw.e);
  }

  @Test
  public void fromState_confidenceAlreadyNormalized() {
    RecognizedWord w = new RecognizedWord("x", rect(0, 0, 10, 10), 0.9f);
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(0.9f, doc.words.get(0).c, 0.01f);
  }

  @Test
  public void fromState_negativeConfidence_clampedToZero() {
    RecognizedWord w = new RecognizedWord("x", rect(0, 0, 10, 10), -5f);
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(0f, doc.words.get(0).c, 0.01f);
  }

  @Test
  public void fromState_nullWordInList_skipped() {
    List<RecognizedWord> words = new ArrayList<>();
    words.add(null);
    words.add(new RecognizedWord("ok", rect(0, 0, 10, 10), 90f));
    OCRViewModel.OcrUiState state = emptyState().withWords(words);
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(1, doc.words.size());
    assertEquals("ok", doc.words.get(0).t);
  }

  @Test
  public void fromState_nullText_becomesEmptyString() {
    RecognizedWord w = new RecognizedWord(null, rect(0, 0, 10, 10), 90f);
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals("", doc.words.get(0).t);
  }

  @Test
  public void fromState_multipleWords_incrementingIds() {
    RecognizedWord w1 = new RecognizedWord("a", rect(0, 0, 10, 10), 90f);
    RecognizedWord w2 = new RecognizedWord("b", rect(20, 0, 30, 10), 80f);
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w1, w2));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(2, doc.words.size());
    assertEquals(1, doc.words.get(0).id);
    assertEquals(2, doc.words.get(1).id);
  }

  @Test
  public void fromState_reviewedWords_usedWhenEdited() {
    RecognizedWord ocrWord = new RecognizedWord("ocr", rect(0, 0, 10, 10), 90f);
    RecognizedWord revWord = new RecognizedWord("reviewed", rect(0, 0, 10, 10), 95f);
    OCRViewModel.OcrUiState state =
        emptyState()
            .withWords(Arrays.asList(ocrWord))
            .withReviewedWords(Arrays.asList(revWord))
            .withHasReviewEdits(true);
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals(1, doc.words.size());
    assertEquals("reviewed", doc.words.get(0).t);
  }

  @Test
  public void fromState_wordWithLang_preservesLang() {
    RecognizedWord w = new RecognizedWord("hallo", rect(0, 0, 10, 10), 90f, "deu");
    OCRViewModel.OcrUiState state = emptyState().withWords(Arrays.asList(w));
    OcrDoc doc = OcrDocMapper.fromState(state);
    assertEquals("deu", doc.words.get(0).lang);
  }
}
