package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import org.junit.Test;

/** Unit tests for {@link ReadingOrderSorter#getDirectionForLanguage(String)}. */
public class ReadingOrderSorterDirectionTest {

  @Test
  public void null_returnsLTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(null));
  }

  @Test
  public void empty_returnsLTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(""));
  }

  @Test
  public void english_LTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("en"));
  }

  @Test
  public void german_LTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("deu"));
  }

  @Test
  public void arabic_iso2_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ar"));
  }

  @Test
  public void arabic_iso3_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ara"));
  }

  @Test
  public void hebrew_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("he"));
  }

  @Test
  public void hebrew_iso3_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("heb"));
  }

  @Test
  public void persian_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("fa"));
  }

  @Test
  public void urdu_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ur"));
  }

  @Test
  public void yiddish_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("yi"));
  }

  @Test
  public void pashto_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ps"));
  }

  @Test
  public void sindhi_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("sd"));
  }

  @Test
  public void uyghur_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ug"));
  }

  @Test
  public void upperCase_arabic_RTL() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("AR"));
  }

  @Test
  public void french_LTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("fr"));
  }

  @Test
  public void chinese_LTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("zh"));
  }

  @Test
  public void unknown_LTR() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("xyz"));
  }
}
