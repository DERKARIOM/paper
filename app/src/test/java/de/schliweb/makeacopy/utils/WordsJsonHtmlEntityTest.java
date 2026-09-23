package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.WordsJson;
import java.util.List;
import org.junit.Test;

/**
 * Tests for HTML entity decoding in WordsJson. Verifies that numeric HTML entities in the text
 * field are properly decoded when parsing JSON for PDF text layer.
 */
public class WordsJsonHtmlEntityTest {

  @Test
  public void testDecimalEntityApostropheInWordsJson() {
    // JSON with &#39; (decimal entity for apostrophe)
    String json =
        "[{\"text\":\"It&#39;s\",\"left\":10,\"top\":20,\"right\":50,\"bottom\":40,\"confidence\":0.95}]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(1, words.size());
    assertEquals("It's", words.get(0).getText());
  }

  @Test
  public void testHexEntityApostropheInWordsJson() {
    // JSON with &#x27; (hex entity for apostrophe)
    String json =
        "[{\"text\":\"It&#x27;s\",\"left\":10,\"top\":20,\"right\":50,\"bottom\":40,\"confidence\":0.95}]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(1, words.size());
    assertEquals("It's", words.get(0).getText());
  }

  @Test
  public void testMultipleEntitiesInWordsJson() {
    // JSON with multiple entities
    String json =
        "[{\"text\":\"&#34;Hello&#34; &#38; &#39;World&#39;\",\"left\":10,\"top\":20,\"right\":100,\"bottom\":40,\"confidence\":0.95}]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(1, words.size());
    assertEquals("\"Hello\" & 'World'", words.get(0).getText());
  }

  @Test
  public void testGermanUmlautsInWordsJson() {
    // JSON with German umlauts as numeric entities
    String json =
        "[{\"text\":\"&#228;&#246;&#252;\",\"left\":10,\"top\":20,\"right\":50,\"bottom\":40,\"confidence\":0.95}]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(1, words.size());
    assertEquals("äöü", words.get(0).getText());
  }

  @Test
  public void testNoEntitiesInWordsJson() {
    // JSON without entities should remain unchanged
    String json =
        "[{\"text\":\"Hello World\",\"left\":10,\"top\":20,\"right\":100,\"bottom\":40,\"confidence\":0.95}]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(1, words.size());
    assertEquals("Hello World", words.get(0).getText());
  }

  @Test
  public void testMultipleWordsWithEntities() {
    // Multiple words, some with entities
    String json =
        "["
            + "{\"text\":\"It&#39;s\",\"left\":10,\"top\":20,\"right\":50,\"bottom\":40,\"confidence\":0.95},"
            + "{\"text\":\"a\",\"left\":55,\"top\":20,\"right\":65,\"bottom\":40,\"confidence\":0.95},"
            + "{\"text\":\"test&#33;\",\"left\":70,\"top\":20,\"right\":120,\"bottom\":40,\"confidence\":0.95}"
            + "]";
    List<RecognizedWord> words = WordsJson.parse(json);

    assertNotNull(words);
    assertEquals(3, words.size());
    assertEquals("It's", words.get(0).getText());
    assertEquals("a", words.get(1).getText());
    assertEquals("test!", words.get(2).getText());
  }
}
