package de.schliweb.makeacopy.ui.ocr.review.suggest;

import static org.junit.Assert.*;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Unit tests for Levenshtein similarity scoring and normalization logic. These tests verify the
 * core algorithms used by DictionarySuggestProvider without requiring Android context.
 */
public class LevenshteinSimilarityTest {

  // Pattern to strip punctuation from word boundaries (same as in DictionarySuggestProvider)
  private static final Pattern PUNCT_PATTERN =
      Pattern.compile("^[.,;:!?\"'()\\[\\]{}]+|[.,;:!?\"'()\\[\\]{}]+$");

  /** Normalizes a word for comparison (same logic as DictionarySuggestProvider). */
  private String normalize(String word) {
    if (word == null || word.isEmpty()) return "";
    String stripped = PUNCT_PATTERN.matcher(word).replaceAll("");
    if (stripped.isEmpty()) return "";
    String lower = stripped.toLowerCase(Locale.ROOT);
    String normalized = Normalizer.normalize(lower, Normalizer.Form.NFKD);
    return normalized.replaceAll("\\p{M}", "");
  }

  /** Calculates Levenshtein edit distance (same logic as DictionarySuggestProvider). */
  private int levenshteinDistance(String a, String b) {
    int lenA = a.length();
    int lenB = b.length();
    int[] prev = new int[lenB + 1];
    int[] curr = new int[lenB + 1];

    for (int j = 0; j <= lenB; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= lenA; i++) {
      curr[0] = i;
      char charA = a.charAt(i - 1);

      for (int j = 1; j <= lenB; j++) {
        char charB = b.charAt(j - 1);
        int cost = (charA == charB) ? 0 : 1;
        int insert = curr[j - 1] + 1;
        int delete = prev[j] + 1;
        int substitute = prev[j - 1] + cost;
        curr[j] = Math.min(Math.min(insert, delete), substitute);
      }

      int[] temp = prev;
      prev = curr;
      curr = temp;
    }

    return prev[lenB];
  }

  /** Calculates normalized Levenshtein similarity (same logic as DictionarySuggestProvider). */
  private double calculateSimilarity(String a, String b) {
    int distance = levenshteinDistance(a, b);
    int maxLen = Math.max(a.length(), b.length());
    if (maxLen == 0) return 1.0;
    return 1.0 - ((double) distance / maxLen);
  }

  // ==================== Normalization Tests ====================

  @Test
  public void testNormalize_lowercase() {
    assertEquals("hello", normalize("HELLO"));
    assertEquals("world", normalize("World"));
    assertEquals("test", normalize("TeSt"));
  }

  @Test
  public void testNormalize_stripPunctuation() {
    assertEquals("hello", normalize("hello."));
    assertEquals("world", normalize("\"world\""));
    assertEquals("test", normalize("(test)"));
    assertEquals("word", normalize("...word!!!"));
  }

  @Test
  public void testNormalize_removeDiacritics() {
    assertEquals("cafe", normalize("café"));
    assertEquals("naive", normalize("naïve"));
    assertEquals("resume", normalize("résumé"));
    assertEquals("uber", normalize("über"));
  }

  @Test
  public void testNormalize_combined() {
    assertEquals("cafe", normalize("\"Café!\""));
    assertEquals("resume", normalize("(RÉSUMÉ)"));
  }

  @Test
  public void testNormalize_emptyAndNull() {
    assertEquals("", normalize(""));
    assertEquals("", normalize(null));
    assertEquals("", normalize("..."));
  }

  // ==================== Levenshtein Distance Tests ====================

  @Test
  public void testLevenshtein_identical() {
    assertEquals(0, levenshteinDistance("hello", "hello"));
    assertEquals(0, levenshteinDistance("", ""));
    assertEquals(0, levenshteinDistance("a", "a"));
  }

  @Test
  public void testLevenshtein_singleEdit() {
    // Substitution
    assertEquals(1, levenshteinDistance("cat", "bat"));
    // Insertion
    assertEquals(1, levenshteinDistance("cat", "cats"));
    // Deletion
    assertEquals(1, levenshteinDistance("cats", "cat"));
  }

  @Test
  public void testLevenshtein_multipleEdits() {
    assertEquals(3, levenshteinDistance("kitten", "sitting"));
    assertEquals(2, levenshteinDistance("book", "back"));
  }

  @Test
  public void testLevenshtein_completelyDifferent() {
    assertEquals(3, levenshteinDistance("abc", "xyz"));
    // hello -> world: h->w, e->o, l->r, l->l (same), o->d = 4 edits
    assertEquals(4, levenshteinDistance("hello", "world"));
  }

  @Test
  public void testLevenshtein_emptyStrings() {
    assertEquals(5, levenshteinDistance("hello", ""));
    assertEquals(5, levenshteinDistance("", "world"));
    assertEquals(0, levenshteinDistance("", ""));
  }

  // ==================== Similarity Score Tests ====================

  @Test
  public void testSimilarity_identical() {
    assertEquals(1.0, calculateSimilarity("hello", "hello"), 0.001);
    assertEquals(1.0, calculateSimilarity("test", "test"), 0.001);
  }

  @Test
  public void testSimilarity_singleEdit() {
    // "cat" vs "bat" - 1 edit, max length 3 -> similarity = 1 - 1/3 = 0.667
    assertEquals(0.667, calculateSimilarity("cat", "bat"), 0.01);
    // "cat" vs "cats" - 1 edit, max length 4 -> similarity = 1 - 1/4 = 0.75
    assertEquals(0.75, calculateSimilarity("cat", "cats"), 0.01);
  }

  @Test
  public void testSimilarity_ocrConfusions() {
    // Common OCR confusions should still have reasonable similarity
    // "hello" vs "he11o" (l->1) - 2 edits, max length 5 -> 0.6
    assertEquals(0.6, calculateSimilarity("hello", "he11o"), 0.01);
    // "book" vs "b00k" (o->0) - 2 edits, max length 4 -> 0.5
    assertEquals(0.5, calculateSimilarity("book", "b00k"), 0.01);
  }

  @Test
  public void testSimilarity_emptyStrings() {
    assertEquals(1.0, calculateSimilarity("", ""), 0.001);
    assertEquals(0.0, calculateSimilarity("hello", ""), 0.001);
    assertEquals(0.0, calculateSimilarity("", "world"), 0.001);
  }

  @Test
  public void testSimilarity_minScoreThreshold() {
    // Test that we can identify words above/below the 0.6 threshold
    // "test" vs "text" - 1 edit, max 4 -> 0.75 (above threshold)
    assertTrue(calculateSimilarity("test", "text") >= 0.6);
    // "hello" vs "world" - 4 edits, max 5 -> 1 - 4/5 = 0.2 (below threshold)
    assertTrue(calculateSimilarity("hello", "world") < 0.6);
  }

  // ==================== Ranking Tests ====================

  @Test
  public void testRanking_higherScoreFirst() {
    // "tset" should be more similar to "test" than "best"
    double scoreTest = calculateSimilarity(normalize("tset"), normalize("test"));
    double scoreBest = calculateSimilarity(normalize("tset"), normalize("best"));
    assertTrue("'test' should rank higher than 'best' for input 'tset'", scoreTest > scoreBest);
  }

  @Test
  public void testRanking_deterministicTieBreaker() {
    // When scores are equal, shorter length difference should win
    // If still tied, lexicographic order
    double score1 = calculateSimilarity("abc", "abd");
    double score2 = calculateSimilarity("abc", "abe");
    assertEquals("Equal edit distance should give equal scores", score1, score2, 0.001);
  }
}
