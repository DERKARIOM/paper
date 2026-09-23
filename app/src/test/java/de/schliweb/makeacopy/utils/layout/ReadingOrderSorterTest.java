package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReadingOrderSorterTest {

  // ── TextDirection enum ────────────────────────────────────────────────────

  @Test
  public void textDirection_hasTwoValues() {
    assertEquals(2, ReadingOrderSorter.TextDirection.values().length);
  }

  @Test
  public void textDirection_ltrAndRtl() {
    assertEquals("LTR", ReadingOrderSorter.TextDirection.LTR.name());
    assertEquals("RTL", ReadingOrderSorter.TextDirection.RTL.name());
  }

  // ── getDirectionForLanguage ───────────────────────────────────────────────

  @Test
  public void getDirection_null_returnsLtr() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(null));
  }

  @Test
  public void getDirection_empty_returnsLtr() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(""));
  }

  @Test
  public void getDirection_english_returnsLtr() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("eng"));
  }

  @Test
  public void getDirection_german_returnsLtr() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("deu"));
  }

  @Test
  public void getDirection_arabic_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ara"));
  }

  @Test
  public void getDirection_arabicShort_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ar"));
  }

  @Test
  public void getDirection_hebrew_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("heb"));
  }

  @Test
  public void getDirection_hebrewShort_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("he"));
  }

  @Test
  public void getDirection_persian_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("fas"));
  }

  @Test
  public void getDirection_urdu_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("urd"));
  }

  @Test
  public void getDirection_yiddish_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("yid"));
  }

  @Test
  public void getDirection_pashto_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("pus"));
  }

  @Test
  public void getDirection_sindhi_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("snd"));
  }

  @Test
  public void getDirection_uyghur_returnsRtl() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("uig"));
  }

  @Test
  public void getDirection_caseInsensitive() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ARA"));
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("Heb"));
  }

  @Test
  public void getDirection_unknownLanguage_returnsLtr() {
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("xyz"));
  }
}
