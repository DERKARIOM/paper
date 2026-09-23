package de.schliweb.makeacopy;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.export.PageFormat;
import org.junit.Test;

/**
 * Unit tests for the {@link PageFormat} enum covering enum values, pixel calculations, and the
 * {@link PageFormat#fromName} helper.
 */
public class PageFormatTest {

  @Test
  public void allValuesPresent() {
    PageFormat[] values = PageFormat.values();
    assertEquals(4, values.length);
    assertEquals(PageFormat.FIT_TO_IMAGE, PageFormat.valueOf("FIT_TO_IMAGE"));
    assertEquals(PageFormat.A4, PageFormat.valueOf("A4"));
    assertEquals(PageFormat.US_LETTER, PageFormat.valueOf("US_LETTER"));
    assertEquals(PageFormat.LEGAL, PageFormat.valueOf("LEGAL"));
  }

  @Test
  public void pixelsForDpi_a4_300() {
    int[] px = PageFormat.A4.pixelsForDpi(300);
    assertNotNull(px);
    assertEquals(2, px.length);
    assertEquals(Math.round(8.27f * 300), px[0]);
    assertEquals(Math.round(11.69f * 300), px[1]);
  }

  @Test
  public void pixelsForDpi_usLetter_300() {
    int[] px = PageFormat.US_LETTER.pixelsForDpi(300);
    assertNotNull(px);
    assertEquals(Math.round(8.5f * 300), px[0]);
    assertEquals(Math.round(11.0f * 300), px[1]);
  }

  @Test
  public void pixelsForDpi_legal_300() {
    int[] px = PageFormat.LEGAL.pixelsForDpi(300);
    assertNotNull(px);
    assertEquals(Math.round(8.5f * 300), px[0]);
    assertEquals(Math.round(14.0f * 300), px[1]);
  }

  @Test
  public void pixelsForDpi_fitToImage_returnsNull() {
    assertNull(PageFormat.FIT_TO_IMAGE.pixelsForDpi(300));
  }

  @Test
  public void fromName_validNames() {
    assertEquals(PageFormat.A4, PageFormat.fromName("A4", PageFormat.FIT_TO_IMAGE));
    assertEquals(PageFormat.US_LETTER, PageFormat.fromName("US_LETTER", PageFormat.A4));
    assertEquals(PageFormat.LEGAL, PageFormat.fromName("LEGAL", PageFormat.A4));
    assertEquals(PageFormat.FIT_TO_IMAGE, PageFormat.fromName("FIT_TO_IMAGE", PageFormat.A4));
  }

  @Test
  public void fromName_nullReturnsDefault() {
    assertEquals(PageFormat.A4, PageFormat.fromName(null, PageFormat.A4));
  }

  @Test
  public void fromName_invalidReturnsDefault() {
    assertEquals(PageFormat.FIT_TO_IMAGE, PageFormat.fromName("INVALID", PageFormat.FIT_TO_IMAGE));
  }
}
