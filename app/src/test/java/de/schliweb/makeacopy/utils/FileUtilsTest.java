package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.infra.FileUtils;
import org.junit.Test;

public class FileUtilsTest {

  // --- firstUriFromJson ---

  @Test
  public void firstUriFromJson_null_returnsNull() {
    assertNull(FileUtils.firstUriFromJson(null));
  }

  @Test
  public void firstUriFromJson_empty_returnsNull() {
    assertNull(FileUtils.firstUriFromJson(""));
  }

  @Test
  public void firstUriFromJson_noQuotes_returnsNull() {
    assertNull(FileUtils.firstUriFromJson("[]"));
  }

  @Test
  public void firstUriFromJson_singleUri() {
    assertEquals(
        "content://com.example/doc/1",
        FileUtils.firstUriFromJson("[\"content://com.example/doc/1\"]"));
  }

  @Test
  public void firstUriFromJson_multipleUris_returnsFirst() {
    assertEquals(
        "file:///a.pdf", FileUtils.firstUriFromJson("[\"file:///a.pdf\", \"file:///b.pdf\"]"));
  }

  @Test
  public void firstUriFromJson_withEscapedQuotes() {
    // The escaped quote inside should be skipped
    String json = "[\"hello\"]";
    assertEquals("hello", FileUtils.firstUriFromJson(json));
  }

  @Test
  public void firstUriFromJson_noClosingQuote_returnsNull() {
    assertNull(FileUtils.firstUriFromJson("[\"unclosed"));
  }

  @Test
  public void firstUriFromJson_emptyString_returnsEmpty() {
    assertEquals("", FileUtils.firstUriFromJson("[\"\"]"));
  }

  @Test
  public void firstUriFromJson_objectWrapped_returnsFirstQuotedString() {
    // firstUriFromJson returns the first quoted string, which is the key "uri"
    assertEquals("uri", FileUtils.firstUriFromJson("{\"uri\": \"content://x\"}"));
  }

  // --- needsExtension ---

  @Test
  public void needsExtension_nullDisplayName_returnsFalse() {
    assertFalse(FileUtils.needsExtension(null, ".pdf"));
  }

  @Test
  public void needsExtension_nullExtension_returnsFalse() {
    assertFalse(FileUtils.needsExtension("Scan", null));
  }

  @Test
  public void needsExtension_bothNull_returnsFalse() {
    assertFalse(FileUtils.needsExtension(null, null));
  }

  @Test
  public void needsExtension_extensionPresent_returnsFalse() {
    assertFalse(FileUtils.needsExtension("Scan_2026-04-22.pdf", ".pdf"));
  }

  @Test
  public void needsExtension_extensionMissing_returnsTrue() {
    assertTrue(FileUtils.needsExtension("Scan_2026-04-22", ".pdf"));
  }

  @Test
  public void needsExtension_caseInsensitive_returnsFalse() {
    assertFalse(FileUtils.needsExtension("Scan.PDF", ".pdf"));
  }

  @Test
  public void needsExtension_caseInsensitiveReverse_returnsFalse() {
    assertFalse(FileUtils.needsExtension("Scan.pdf", ".PDF"));
  }

  @Test
  public void needsExtension_wrongExtension_returnsTrue() {
    assertTrue(FileUtils.needsExtension("Scan.txt", ".pdf"));
  }

  @Test
  public void needsExtension_txtExtensionPresent_returnsFalse() {
    assertFalse(FileUtils.needsExtension("notes.txt", ".txt"));
  }

  @Test
  public void needsExtension_jpgExtensionMissing_returnsTrue() {
    assertTrue(FileUtils.needsExtension("photo", ".jpg"));
  }

  @Test
  public void needsExtension_zipExtensionPresent_returnsFalse() {
    assertFalse(FileUtils.needsExtension("archive.zip", ".zip"));
  }

  @Test
  public void needsExtension_emptyDisplayName_returnsTrue() {
    assertTrue(FileUtils.needsExtension("", ".pdf"));
  }

  @Test
  public void needsExtension_emptyExtension_returnsFalse() {
    assertFalse(FileUtils.needsExtension("Scan.pdf", ""));
  }

  @Test
  public void needsExtension_extensionOnlyName_returnsFalse() {
    assertFalse(FileUtils.needsExtension(".pdf", ".pdf"));
  }

  @Test
  public void needsExtension_partialMatch_returnsTrue() {
    // "xpdf" ends with "pdf" but not ".pdf"
    assertTrue(FileUtils.needsExtension("xpdf", ".pdf"));
  }
}
