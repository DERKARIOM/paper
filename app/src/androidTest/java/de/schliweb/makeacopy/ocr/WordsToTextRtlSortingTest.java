package de.schliweb.makeacopy.ocr;

import static org.junit.Assert.*;

import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for wordsToText() RTL sorting functionality. These tests verify that the
 * BiDi/RTL fix correctly sorts words based on their bounding box coordinates and detected script
 * direction.
 *
 * <p>Background: The original implementation always sorted words left-to-right (LTR), which caused
 * Persian and Arabic text to appear in wrong order. The fix detects RTL scripts and sorts those
 * lines right-to-left.
 *
 * <p>These tests require android.graphics.RectF and must run as instrumented tests.
 */
@RunWith(AndroidJUnit4.class)
@SuppressWarnings("StringSplitter")
public class WordsToTextRtlSortingTest {

  /** Helper method to create a RecognizedWord with text and bounding box coordinates. */
  private RecognizedWord createWordWithBounds(
      String text, float left, float top, float right, float bottom) {
    RectF bounds = new RectF(left, top, right, bottom);
    return new RecognizedWord(text, bounds, 90.0f);
  }


  /**
   * Tests that wordsToText correctly sorts Persian words from right to left. Simulates OCR output
   * where Persian words have bounding boxes positioned RTL.
   *
   * <p>This test verifies the fix for the BiDi/RTL issue where Persian text was being output in
   * wrong order because words were sorted LTR instead of RTL.
   */
  @Test
  public void testWordsToText_PersianRtlSorting() {
    // Simulate Persian sentence: "این سند شامل متن فارسی است"
    // (This document contains Persian text)
    // Words are positioned right-to-left as they would appear in OCR output
    List<RecognizedWord> words = new ArrayList<>();

    // Persian words positioned from right to left (as OCR would detect them)
    // Word positions: rightmost word first in visual order
    // Inputs use logical codepoint order (RecognizedWord convention).
    words.add(createWordWithBounds("این", 500, 10, 550, 30)); // "This" - rightmost
    words.add(createWordWithBounds("سند", 400, 10, 480, 30)); // "document"
    words.add(createWordWithBounds("شامل", 300, 10, 380, 30)); // "contains"
    words.add(createWordWithBounds("متن", 200, 10, 280, 30)); // "text"
    words.add(createWordWithBounds("فارسی", 100, 10, 180, 30)); // "Persian"
    words.add(createWordWithBounds("است", 10, 10, 80, 30)); // "is" - leftmost

    String result = OCRPostProcessor.wordsToText(words);

    System.out.println("[DEBUG_LOG] Persian RTL result: " + result);

    // For RTL text, words should be sorted right-to-left
    // Expected output: "این سند شامل متن فارسی است"
    assertTrue("Persian text should start with 'این' (rightmost word)", result.startsWith("این"));
    assertTrue("Persian text should end with 'است' (leftmost word)", result.trim().endsWith("است"));

    // Verify word order is correct (right to left)
    String[] outputWords = Pattern.compile("\\s+").split(result.trim());
    assertEquals("Should have 6 words", 6, outputWords.length);
    assertEquals("First word should be 'این'", "این", outputWords[0]);
    assertEquals("Last word should be 'است'", "است", outputWords[5]);
  }

  /**
   * Tests that wordsToText correctly sorts English words from left to right. Verifies that LTR
   * sorting still works correctly after RTL fix.
   */
  @Test
  public void testWordsToText_EnglishLtrSorting() {
    // Simulate English sentence: "This is a test document"
    // Words are positioned left-to-right as they would appear in OCR output
    List<RecognizedWord> words = new ArrayList<>();

    // English words positioned from left to right
    words.add(createWordWithBounds("This", 10, 10, 50, 30)); // leftmost
    words.add(createWordWithBounds("is", 60, 10, 80, 30));
    words.add(createWordWithBounds("a", 90, 10, 100, 30));
    words.add(createWordWithBounds("test", 110, 10, 150, 30));
    words.add(createWordWithBounds("document", 160, 10, 250, 30)); // rightmost

    String result = OCRPostProcessor.wordsToText(words);

    System.out.println("[DEBUG_LOG] English LTR result: " + result);

    // For LTR text, words should be sorted left-to-right
    // Expected output: "This is a test document"
    assertTrue("English text should start with 'This' (leftmost word)", result.startsWith("This"));
    assertTrue(
        "English text should end with 'document' (rightmost word)",
        result.trim().endsWith("document"));

    // Verify word order is correct (left to right)
    String[] outputWords = Pattern.compile("\\s+").split(result.trim());
    assertEquals("Should have 5 words", 5, outputWords.length);
    assertEquals("First word should be 'This'", "This", outputWords[0]);
    assertEquals("Last word should be 'document'", "document", outputWords[4]);
  }

  /**
   * Tests that wordsToText handles mixed Persian+English content correctly. Simulates a document
   * with both RTL and LTR text on separate lines.
   */
  @Test
  public void testWordsToText_MixedPersianEnglish_SeparateLines() {
    List<RecognizedWord> words = new ArrayList<>();

    // Line 1: English text (LTR) - y position 10-30
    words.add(createWordWithBounds("Hello", 10, 10, 60, 30));
    words.add(createWordWithBounds("World", 70, 10, 130, 30));

    // Line 2: Persian text (RTL) - y position 50-70
    words.add(createWordWithBounds("سلام", 100, 50, 150, 70)); // "Hello" - rightmost
    words.add(createWordWithBounds("جهان", 30, 50, 80, 70)); // "World" - leftmost

    String result = OCRPostProcessor.wordsToText(words);
    String[] lines = Pattern.compile("\n").split(result);

    System.out.println("[DEBUG_LOG] Mixed content result: " + result);

    // Should have 2 lines
    assertEquals("Should have 2 lines", 2, lines.length);

    // Line 1 should be English (LTR order)
    assertTrue("First line should start with 'Hello'", lines[0].startsWith("Hello"));

    // Line 2 should be Persian (RTL order: سلام جهان)
    assertTrue(
        "Second line should start with 'سلام' (rightmost Persian word)",
        lines[1].trim().startsWith("سلام"));
  }

  /**
   * Tests that wordsToText handles Arabic text correctly (RTL). Similar to Persian but with Arabic
   * script.
   */
  @Test
  public void testWordsToText_ArabicRtlSorting() {
    // Simulate Arabic sentence: "مرحبا بالعالم"
    // (Hello World)
    List<RecognizedWord> words = new ArrayList<>();

    // Arabic words positioned from right to left
    words.add(createWordWithBounds("مرحبا", 100, 10, 180, 30)); // "Hello" - rightmost
    words.add(createWordWithBounds("بالعالم", 10, 10, 90, 30)); // "World" - leftmost

    String result = OCRPostProcessor.wordsToText(words);

    System.out.println("[DEBUG_LOG] Arabic RTL result: " + result);

    // For RTL text, words should be sorted right-to-left
    assertTrue(
        "Arabic text should start with 'مرحبا' (rightmost word)", result.startsWith("مرحبا"));
    assertTrue(
        "Arabic text should end with 'بالعالم' (leftmost word)", result.trim().endsWith("بالعالم"));
  }

  /**
   * Tests wordsToText with a realistic multi-line Persian document. Verifies that each line is
   * correctly sorted RTL.
   */
  @Test
  public void testWordsToText_MultiLinePersianDocument() {
    List<RecognizedWord> words = new ArrayList<>();

    // Inputs use logical codepoint order (RecognizedWord convention).
    // Line 1: "این سند" (This document) - y: 10-30
    words.add(createWordWithBounds("این", 150, 10, 200, 30)); // rightmost
    words.add(createWordWithBounds("سند", 80, 10, 140, 30)); // leftmost

    // Line 2: "متن فارسی" (Persian text) - y: 50-70
    words.add(createWordWithBounds("متن", 150, 50, 200, 70)); // rightmost
    words.add(createWordWithBounds("فارسی", 60, 50, 140, 70)); // leftmost

    // Line 3: "است" (is) - y: 90-110
    words.add(createWordWithBounds("است", 150, 90, 200, 110));

    String result = OCRPostProcessor.wordsToText(words);
    String[] lines = Pattern.compile("\n").split(result);

    System.out.println("[DEBUG_LOG] Multi-line Persian result: " + result);

    // Should have 3 lines
    assertTrue("Should have at least 3 lines", lines.length >= 3);

    // Each line should be in correct RTL order
    assertTrue("Line 1 should start with 'این'", lines[0].trim().startsWith("این"));
    assertTrue("Line 2 should start with 'متن'", lines[1].trim().startsWith("متن"));
    assertTrue("Line 3 should contain 'است'", lines[2].trim().contains("است"));
  }

  /**
   * Tests that wordsToText handles Hebrew text correctly (RTL). Hebrew is another RTL script that
   * should be sorted right-to-left.
   */
  @Test
  public void testWordsToText_HebrewRtlSorting() {
    // Simulate Hebrew sentence: "שלום עולם"
    // (Hello World)
    List<RecognizedWord> words = new ArrayList<>();

    // Hebrew words positioned from right to left
    words.add(createWordWithBounds("שלום", 100, 10, 180, 30)); // "Hello" - rightmost
    words.add(createWordWithBounds("עולם", 10, 10, 90, 30)); // "World" - leftmost

    String result = OCRPostProcessor.wordsToText(words);

    System.out.println("[DEBUG_LOG] Hebrew RTL result: " + result);

    // For RTL text, words should be sorted right-to-left
    assertTrue("Hebrew text should start with 'שלום' (rightmost word)", result.startsWith("שלום"));
    assertTrue(
        "Hebrew text should end with 'עולם' (leftmost word)", result.trim().endsWith("עולם"));
  }

  /** Tests edge case: single word line should work regardless of direction. */
  @Test
  public void testWordsToText_SingleWordLines() {
    List<RecognizedWord> words = new ArrayList<>();

    // Single English word
    words.add(createWordWithBounds("Hello", 10, 10, 60, 30));

    // Single Persian word on next line (visual codepoint order)
    words.add(createWordWithBounds("سلام", 10, 50, 60, 70));

    String result = OCRPostProcessor.wordsToText(words);
    String[] lines = Pattern.compile("\n").split(result);

    System.out.println("[DEBUG_LOG] Single word lines result: " + result);

    assertEquals("Should have 2 lines", 2, lines.length);
    assertEquals("First line should be 'Hello'", "Hello", lines[0].trim());
    assertEquals("Second line should be 'سلام'", "سلام", lines[1].trim());
  }

  /**
   * Tests that numbers in RTL context are handled correctly. Numbers should maintain their LTR
   * order even in RTL text.
   */
  @Test
  public void testWordsToText_NumbersInRtlContext() {
    List<RecognizedWord> words = new ArrayList<>();

    // Persian text with numbers: "قیمت 1000 تومان"
    // (Price 1000 Toman) — Persian words in visual codepoint order, digits unchanged.
    words.add(createWordWithBounds("قیمت", 200, 10, 280, 30)); // "Price" - rightmost
    words.add(createWordWithBounds("1000", 120, 10, 180, 30)); // number
    words.add(createWordWithBounds("تومان", 10, 10, 100, 30)); // "Toman" - leftmost

    String result = OCRPostProcessor.wordsToText(words);

    System.out.println("[DEBUG_LOG] Numbers in RTL result: " + result);

    // The line should be detected as RTL due to majority Persian characters
    // Words should be sorted right-to-left
    assertTrue("Should start with 'قیمت'", result.startsWith("قیمت"));
    assertTrue("Should contain '1000'", result.contains("1000"));
    assertTrue("Should end with 'تومان'", result.trim().endsWith("تومان"));
  }
}
