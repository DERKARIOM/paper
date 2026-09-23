package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;

import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Tests for HTML entity decoding in OCRHelper. Verifies that numeric HTML entities (both decimal
 * and hexadecimal) are properly decoded.
 */
public class OCRHelperHtmlEntityTest {

  /** Helper method to invoke the private cleanHtmlText method via reflection. */
  private String invokeCleanHtmlText(String html) throws Exception {
    Method method = OCRHelper.class.getDeclaredMethod("cleanHtmlText", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, html);
  }

  @Test
  public void testDecimalEntityApostrophe() throws Exception {
    // &#39; is the decimal HTML entity for apostrophe (')
    String input = "It&#39;s a test";
    String expected = "It's a test";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testHexEntityApostrophe() throws Exception {
    // &#x27; is the hexadecimal HTML entity for apostrophe (')
    String input = "It&#x27;s a test";
    String expected = "It's a test";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testHexEntityUpperCase() throws Exception {
    // &#X27; with uppercase X should also work
    String input = "It&#X27;s a test";
    String expected = "It's a test";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testMultipleEntities() throws Exception {
    // Multiple entities in one string
    String input = "&#34;Hello&#34; &#38; &#39;World&#39;";
    String expected = "\"Hello\" & 'World'";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testMixedNamedAndNumericEntities() throws Exception {
    // Mix of named entities (&amp;) and numeric entities (&#39;)
    String input = "Tom &amp; Jerry&#39;s Adventure";
    String expected = "Tom & Jerry's Adventure";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testGermanUmlauts() throws Exception {
    // German umlauts as numeric entities
    String input = "&#228;&#246;&#252;"; // äöü
    String expected = "äöü";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testEuroSign() throws Exception {
    // Euro sign as numeric entity
    String input = "Price: 10&#8364;"; // €
    String expected = "Price: 10€";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testNoEntities() throws Exception {
    // Plain text without entities should remain unchanged
    String input = "Hello World";
    String expected = "Hello World";
    assertEquals(expected, invokeCleanHtmlText(input));
  }

  @Test
  public void testNullInput() throws Exception {
    // Null input should return empty string
    assertEquals("", invokeCleanHtmlText(null));
  }

  @Test
  public void testEmptyInput() throws Exception {
    // Empty input should return empty string
    assertEquals("", invokeCleanHtmlText(""));
  }

  @Test
  public void testHtmlTagsRemoved() throws Exception {
    // HTML tags should be removed
    String input = "<span>It&#39;s</span> a <b>test</b>";
    String expected = "It's a test";
    assertEquals(expected, invokeCleanHtmlText(input));
  }
}
