package de.schliweb.makeacopy.ui.ocr.review;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for RTL (Right-to-Left) text detection and word splitting logic used in
 * OcrReviewFragment.
 *
 * <p>These tests verify that: 1. RTL text (Persian, Arabic) is correctly detected 2. LTR text
 * (English) is correctly detected as non-RTL 3. Word splitting assigns text parts and boxes
 * correctly based on text direction: - LTR: first part to left box, second part to right box - RTL:
 * first part to right box, second part to left box
 */
public class OcrReviewRtlSplitTest {

  /**
   * Checks if a text string contains predominantly RTL (Right-to-Left) characters. This is the same
   * logic used in OcrReviewFragment.isRtlText().
   */
  private boolean isRtlText(String text) {
    if (text == null || text.isEmpty()) return false;

    int rtlCount = 0;
    int ltrCount = 0;

    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      byte directionality = Character.getDirectionality(cp);

      if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
          || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
          || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
          || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE) {
        rtlCount++;
      } else if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT
          || directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
          || directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE) {
        ltrCount++;
      }

      i += Character.charCount(cp);
    }

    return rtlCount > ltrCount;
  }

  /** Result of splitting a word, containing text and box position info. */
  private static class SplitResult {
    String originalWordText; // text in original word (first in doc.words)
    String newWordText; // text in new word (second in doc.words)
    int originalBoxX; // x position of original word's box
    int newBoxX; // x position of new word's box

    SplitResult(String origText, String newText, int origX, int newX) {
      this.originalWordText = origText;
      this.newWordText = newText;
      this.originalBoxX = origX;
      this.newBoxX = newX;
    }
  }

  /**
   * Simulates the word split logic from OcrReviewFragment.splitWordMid(). For LTR: first part to
   * left box, second part to right box For RTL: first part to right box, second part to left box
   */
  private SplitResult splitWordWithBoxes(String text, int boxX, int boxW) {
    if (text == null || text.length() < 2) return null;

    int mid = text.length() / 2;
    int leftSpace = text.lastIndexOf(' ', mid);
    int rightSpace = text.indexOf(' ', mid);
    int splitAt = (leftSpace >= 1) ? leftSpace : (rightSpace > 0 ? rightSpace : mid);
    String a = text.substring(0, splitAt).trim();
    String b = text.substring(splitAt).trim();
    if (a.isEmpty() || b.isEmpty()) return null;

    int lenA = a.codePointCount(0, a.length());
    int lenB = b.codePointCount(0, b.length());
    int totalLen = Math.max(1, lenA + lenB);
    int w1 = Math.max(1, Math.round(boxW * (lenA / (float) totalLen)));
    int w2 = Math.max(1, boxW - w1);

    boolean isRtl = isRtlText(text);

    if (isRtl) {
      // RTL: original word gets RIGHT box with first part (a), new word gets LEFT box with second
      // part (b)
      int origX = boxX + w2; // right side
      int newX = boxX; // left side
      return new SplitResult(a, b, origX, newX);
    } else {
      // LTR: original word gets LEFT box with first part (a), new word gets RIGHT box with second
      // part (b)
      int origX = boxX; // left side
      int newX = boxX + w1; // right side
      return new SplitResult(a, b, origX, newX);
    }
  }

  /** Simple split for text-only tests (returns [firstPart, secondPart]). */
  private String[] splitWord(String text) {
    if (text == null || text.length() < 2) return null;

    int mid = text.length() / 2;
    int leftSpace = text.lastIndexOf(' ', mid);
    int rightSpace = text.indexOf(' ', mid);
    int splitAt = (leftSpace >= 1) ? leftSpace : (rightSpace > 0 ? rightSpace : mid);
    String a = text.substring(0, splitAt).trim();
    String b = text.substring(splitAt).trim();
    if (a.isEmpty() || b.isEmpty()) return null;

    return new String[] {a, b};
  }

  // ==================== RTL Detection Tests ====================

  @Test
  public void testIsRtlText_PersianText() {
    // Persian word "سیگنال" (signal)
    String persianText = "سیگنال";
    assertTrue("Persian text should be detected as RTL", isRtlText(persianText));
  }

  @Test
  public void testIsRtlText_ArabicText() {
    // Arabic word "مرحبا" (hello)
    String arabicText = "مرحبا";
    assertTrue("Arabic text should be detected as RTL", isRtlText(arabicText));
  }

  @Test
  public void testIsRtlText_EnglishText() {
    String englishText = "Hello";
    assertFalse("English text should be detected as LTR", isRtlText(englishText));
  }

  @Test
  public void testIsRtlText_EmptyText() {
    assertFalse("Empty text should return false", isRtlText(""));
  }

  @Test
  public void testIsRtlText_NullText() {
    assertFalse("Null text should return false", isRtlText(null));
  }

  // ==================== Word Split Text Tests ====================

  @Test
  public void testSplitWord_PersianWord_TextParts() {
    // Persian word "سیگنال" splits into two text parts:
    // a = "سیگ" (first 3 chars - beginning of word)
    // b = "نال" (last 3 chars - end of word)
    String persianWord = "سیگنال";
    String[] result = splitWord(persianWord);

    assertEquals("First part should be beginning of Persian word", "سیگ", result[0]);
    assertEquals("Second part should be end of Persian word", "نال", result[1]);
  }

  @Test
  public void testSplitWord_EnglishWord_TextParts() {
    // English word "signal" splits into two text parts:
    // a = "sig" (first 3 chars)
    // b = "nal" (last 3 chars)
    String englishWord = "signal";
    String[] result = splitWord(englishWord);

    assertEquals("First part should be beginning of English word", "sig", result[0]);
    assertEquals("Second part should be end of English word", "nal", result[1]);
  }

  @Test
  public void testSplitWord_ShortWord() {
    // Word with only 1 character should not be split
    String shortWord = "a";
    String[] result = splitWord(shortWord);
    assertNull("Single character word should not be split", result);
  }

  @Test
  public void testSplitWord_TwoCharacterWord() {
    // Word with 2 characters should split into 1+1
    String twoCharWord = "ab";
    String[] result = splitWord(twoCharWord);
    assertEquals("First part should be 'a'", "a", result[0]);
    assertEquals("Second part should be 'b'", "b", result[1]);
  }

  // ==================== Box Position Tests ====================

  @Test
  public void testSplitWord_LTR_BoxPositions() {
    // For LTR text "signal":
    // - Original word gets LEFT box with first part "sig"
    // - New word gets RIGHT box with second part "nal"
    String englishWord = "signal";
    int boxX = 100;
    int boxW = 60;
    SplitResult result = splitWordWithBoxes(englishWord, boxX, boxW);

    assertEquals("Original word text should be first part", "sig", result.originalWordText);
    assertEquals("New word text should be second part", "nal", result.newWordText);
    assertEquals("Original word should be at LEFT (original x)", boxX, result.originalBoxX);
    assertTrue("New word should be to the RIGHT of original", result.newBoxX > result.originalBoxX);
  }

  @Test
  public void testSplitWord_RTL_BoxPositions() {
    // For RTL text "سیگنال":
    // - Original word gets RIGHT box with first part "سیگ" (beginning of word)
    // - New word gets LEFT box with second part "نال" (end of word)
    // This is correct because RTL text reads right-to-left
    String persianWord = "سیگنال";
    int boxX = 100;
    int boxW = 60;
    SplitResult result = splitWordWithBoxes(persianWord, boxX, boxW);

    assertEquals(
        "Original word text should be first part (beginning)", "سیگ", result.originalWordText);
    assertEquals("New word text should be second part (end)", "نال", result.newWordText);
    assertTrue(
        "Original word (beginning) should be to the RIGHT", result.originalBoxX > result.newBoxX);
    assertEquals("New word (end) should be at LEFT (original x)", boxX, result.newBoxX);
  }

  @Test
  public void testSplitWord_RTL_Arabic_BoxPositions() {
    // For RTL Arabic text "مرحبا":
    // - Original word gets RIGHT box with first part "مر"
    // - New word gets LEFT box with second part "حبا"
    String arabicWord = "مرحبا";
    int boxX = 50;
    int boxW = 100;
    SplitResult result = splitWordWithBoxes(arabicWord, boxX, boxW);

    assertEquals("Original word text should be first part", "مر", result.originalWordText);
    assertEquals("New word text should be second part", "حبا", result.newWordText);
    assertTrue(
        "Original word should be to the RIGHT for RTL", result.originalBoxX > result.newBoxX);
    assertEquals("New word should be at LEFT for RTL", boxX, result.newBoxX);
  }

  /**
   * Test case from the issue description: Splitting "سیگنال" should result in: - "سیگ" in the RIGHT
   * box (beginning of word, reads first in RTL) - "نال" in the LEFT box (end of word, reads second
   * in RTL)
   *
   * <p>In Layout/Document view: visually correct RTL display In Text view: concatenating
   * originalWordText + newWordText = "سیگنال"
   */
  @Test
  public void testSplitWord_IssueExample() {
    String persianWord = "سیگنال";
    int boxX = 100;
    int boxW = 60;
    SplitResult result = splitWordWithBoxes(persianWord, boxX, boxW);

    // Text order in doc.words: original first, new second
    // For correct text concatenation: "سیگ" + "نال" = "سیگنال"
    assertEquals(
        "Concatenating texts in doc.words order should give original word",
        persianWord,
        result.originalWordText + result.newWordText);

    // Box positions: for RTL, beginning (سیگ) is on RIGHT, end (نال) is on LEFT
    assertTrue(
        "For RTL, original word (beginning) should be on RIGHT side",
        result.originalBoxX > result.newBoxX);
  }
}
