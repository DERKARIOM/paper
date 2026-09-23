package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.schliweb.makeacopy.utils.ocr.OCRWhitelist;
import org.junit.Test;

public class OCRWhitelistFilterTest {

  @Test
  public void filterByWhitelist_removesNonWhitelistedChars() {
    String whitelist = "ABCabc123";
    assertEquals("Abc123", OCRWhitelist.filterByWhitelist("Abc123™©®", whitelist));
  }

  @Test
  public void filterByWhitelist_preservesWhitespace() {
    String whitelist = "ABCabcdef";
    assertEquals("Abc def", OCRWhitelist.filterByWhitelist("Abc def", whitelist));
    assertEquals("A\nB\tC", OCRWhitelist.filterByWhitelist("A\nB\tC", whitelist));
  }

  @Test
  public void filterByWhitelist_emptyWhitelistReturnsOriginal() {
    assertEquals("Hello™", OCRWhitelist.filterByWhitelist("Hello™", ""));
    assertEquals("Hello™", OCRWhitelist.filterByWhitelist("Hello™", null));
  }

  @Test
  public void filterByWhitelist_nullOrEmptyTextReturnsAsIs() {
    assertNull(OCRWhitelist.filterByWhitelist(null, "abc"));
    assertEquals("", OCRWhitelist.filterByWhitelist("", "abc"));
  }

  @Test
  public void filterByWhitelist_germanWhitelistKeepsUmlautsAndSymbols() {
    String text = "Über™ Straße© 42§";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.DE);
    assertEquals("Über™ Straße© 42§", filtered);
  }

  @Test
  public void filterByWhitelist_englishWhitelistKeepsCommonSymbols() {
    String text = "Hello© World™ 2024";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.EN);
    assertEquals("Hello© World™ 2024", filtered);
  }

  @Test
  public void filterByWhitelist_turkishWhitelistKeepsTurkishChars() {
    String text = "İstanbul'da güzel bir gün";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.TR);
    assertEquals(text, filtered);
  }

  @Test
  public void filterByWhitelist_turkishWhitelistKeepsAllSpecialChars() {
    String text = "İıĞğŞşÇçÖöÜü 123";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.TR);
    assertEquals(text, filtered);
  }

  @Test
  public void filterByWhitelist_turkishWhitelistRemovesCyrillic() {
    // Turkish whitelist should not contain Cyrillic characters
    String text = "İstanbul Москва";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.TR);
    assertEquals("İstanbul ", filtered);
  }

  @Test
  public void filterByWhitelist_englishWhitelistRemovesTurkishSpecific() {
    // English whitelist should not contain İ, ı, Ğ, ğ, Ş, ş
    String text = "İstanbul";
    String filtered = OCRWhitelist.filterByWhitelist(text, OCRWhitelist.EN);
    assertEquals("stanbul", filtered);
  }
}
