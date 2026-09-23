package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.WordsJson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * JVM unit tests for {@link WordsJson} — parse and serialize logic.
 *
 * <p>Note: Android stub RectF fields default to 0 in JVM tests, so coordinate-dependent assertions
 * are limited. We focus on structural correctness, null safety, and edge cases.
 */
public class WordsJsonTest {

  // ==================== parse ====================

  @Test
  public void parse_nullJson_returnsEmptyList() {
    // parse(null) should not throw
    List<RecognizedWord> result = WordsJson.parse(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void parse_emptyString_returnsEmptyList() {
    List<RecognizedWord> result = WordsJson.parse("");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void parse_emptyArray_returnsEmptyList() {
    List<RecognizedWord> result = WordsJson.parse("[]");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void parse_singleWord_flatCoords() {
    String json =
        "[{\"text\":\"Hello\",\"left\":10,\"top\":20,\"right\":50,\"bottom\":35,\"confidence\":0.92}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("Hello", result.get(0).getText());
  }

  @Test
  public void parse_multipleWords() {
    String json =
        "[{\"text\":\"A\",\"left\":0,\"top\":0,\"right\":10,\"bottom\":10,\"confidence\":0.5},"
            + "{\"text\":\"B\",\"left\":20,\"top\":0,\"right\":30,\"bottom\":10,\"confidence\":0.8}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(2, result.size());
    assertEquals("A", result.get(0).getText());
    assertEquals("B", result.get(1).getText());
  }

  @Test
  public void parse_wrappedInWordsKey() {
    String json =
        "{\"words\":[{\"text\":\"W\",\"left\":0,\"top\":0,\"right\":5,\"bottom\":5,\"confidence\":1}]}";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("W", result.get(0).getText());
  }

  @Test
  public void parse_wrappedInDataKey() {
    String json =
        "{\"data\":[{\"text\":\"D\",\"left\":0,\"top\":0,\"right\":5,\"bottom\":5,\"confidence\":0.5}]}";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("D", result.get(0).getText());
  }

  @Test
  public void parse_wrappedInItemsKey() {
    String json =
        "{\"items\":[{\"text\":\"I\",\"left\":1,\"top\":2,\"right\":3,\"bottom\":4,\"confidence\":0.1}]}";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
  }

  @Test
  public void parse_xywh_flatCoords() {
    String json = "[{\"text\":\"XY\",\"x\":10,\"y\":20,\"w\":30,\"h\":15,\"confidence\":0.7}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("XY", result.get(0).getText());
  }

  @Test
  public void parse_bboxArray() {
    String json = "[{\"text\":\"BA\",\"bbox\":[10,20,50,35],\"confidence\":0.6}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("BA", result.get(0).getText());
  }

  @Test
  public void parse_bboxObject() {
    String json =
        "[{\"text\":\"BO\",\"bbox\":{\"left\":1,\"top\":2,\"right\":3,\"bottom\":4},\"confidence\":0.5}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("BO", result.get(0).getText());
  }

  @Test
  public void parse_xminXmaxYminYmax() {
    String json =
        "[{\"text\":\"MM\",\"xmin\":5,\"ymin\":10,\"xmax\":50,\"ymax\":40,\"confidence\":0.3}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("MM", result.get(0).getText());
  }

  @Test
  public void parse_confidenceNormalization_0to100() {
    String json =
        "[{\"text\":\"C\",\"left\":0,\"top\":0,\"right\":10,\"bottom\":10,\"confidence\":85}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    // 85 > 1 → normalized to 0.85
    assertEquals(0.85f, result.get(0).getConfidence(), 0.01f);
  }

  @Test
  public void parse_confidenceNormalization_alreadyNormalized() {
    String json =
        "[{\"text\":\"C\",\"left\":0,\"top\":0,\"right\":10,\"bottom\":10,\"confidence\":0.42}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(0.42f, result.get(0).getConfidence(), 0.01f);
  }

  @Test
  public void parse_missingConfidence_defaultsToZero() {
    String json = "[{\"text\":\"NC\",\"left\":0,\"top\":0,\"right\":10,\"bottom\":10}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals(0f, result.get(0).getConfidence(), 0.001f);
  }

  @Test
  public void parse_malformedJson_returnsEmptyList() {
    List<RecognizedWord> result = WordsJson.parse("{not valid json!!!");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void parse_skipsEntriesWithoutRect() {
    // Entry without any coordinate fields should be skipped
    String json = "[{\"text\":\"NoRect\",\"confidence\":0.5}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertTrue(result.isEmpty());
  }

  @Test
  public void parse_htmlEntitiesDecoded() {
    String json =
        "[{\"text\":\"it&#39;s\",\"left\":0,\"top\":0,\"right\":10,\"bottom\":10,\"confidence\":0.9}]";
    List<RecognizedWord> result = WordsJson.parse(json);
    assertEquals(1, result.size());
    assertEquals("it's", result.get(0).getText());
  }

  // ==================== toWordsJson ====================

  @Test
  public void toWordsJson_null_returnsEmptyArray() {
    assertEquals("[]", WordsJson.toWordsJson(null));
  }

  @Test
  public void toWordsJson_emptyList_returnsEmptyArray() {
    assertEquals("[]", WordsJson.toWordsJson(new ArrayList<>()));
  }

  @Test
  public void toWordsJson_singleWord_containsText() {
    List<RecognizedWord> words = new ArrayList<>();
    words.add(new RecognizedWord("Test", new RectF(), 0.5f));
    String json = WordsJson.toWordsJson(words);
    assertTrue(json.contains("\"text\":\"Test\""));
    assertTrue(json.startsWith("["));
    assertTrue(json.endsWith("]"));
  }

  @Test
  public void toWordsJson_nullWordSkipped() {
    List<RecognizedWord> words = new ArrayList<>();
    words.add(null);
    words.add(new RecognizedWord("OK", new RectF(), 0.8f));
    String json = WordsJson.toWordsJson(words);
    assertTrue(json.contains("\"text\":\"OK\""));
    // Should not contain two objects
    assertEquals(1, json.chars().filter(c -> c == '{').count());
  }

  @Test
  public void toWordsJson_roundTrip() {
    // Create words, serialize, parse back
    List<RecognizedWord> original = new ArrayList<>();
    original.add(new RecognizedWord("Hello", new RectF(), 0.75f));
    original.add(new RecognizedWord("World", new RectF(), 0.90f));
    String json = WordsJson.toWordsJson(original);
    List<RecognizedWord> parsed = WordsJson.parse(json);
    assertEquals(2, parsed.size());
    assertEquals("Hello", parsed.get(0).getText());
    assertEquals("World", parsed.get(1).getText());
  }

  // ==================== escapeJsonString ====================

  @Test
  public void escapeJsonString_null_returnsEmptyQuoted() {
    assertEquals("\"\"", WordsJson.escapeJsonString(null));
  }

  @Test
  public void escapeJsonString_empty() {
    assertEquals("\"\"", WordsJson.escapeJsonString(""));
  }

  @Test
  public void escapeJsonString_simple() {
    assertEquals("\"hello\"", WordsJson.escapeJsonString("hello"));
  }

  @Test
  public void escapeJsonString_quotes() {
    assertEquals("\"say \\\"hi\\\"\"", WordsJson.escapeJsonString("say \"hi\""));
  }

  @Test
  public void escapeJsonString_backslash() {
    assertEquals("\"a\\\\b\"", WordsJson.escapeJsonString("a\\b"));
  }

  @Test
  public void escapeJsonString_newline() {
    assertEquals("\"a\\nb\"", WordsJson.escapeJsonString("a\nb"));
  }

  @Test
  public void escapeJsonString_tab() {
    assertEquals("\"a\\tb\"", WordsJson.escapeJsonString("a\tb"));
  }

  @Test
  public void escapeJsonString_controlChar() {
    // Control char \u0001 should be escaped as \\u0001
    String result = WordsJson.escapeJsonString("\u0001");
    assertEquals("\"\\u0001\"", result);
  }

  // ==================== formatFloat ====================

  @Test
  public void formatFloat_zero() {
    assertEquals("0.000000", WordsJson.formatFloat(0f));
  }

  @Test
  public void formatFloat_positive() {
    assertEquals("1.500000", WordsJson.formatFloat(1.5f));
  }

  @Test
  public void formatFloat_negative() {
    assertEquals("-2.750000", WordsJson.formatFloat(-2.75f));
  }
}
