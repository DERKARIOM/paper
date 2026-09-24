package de.schliweb.makeacopy.utils.export;

import static org.junit.Assert.*;

import org.junit.Test;

/** Position and size of the Qwiper watermark on PDF pages of various formats. */
public class PdfWatermarkPlacementTest {

  private static final float LOGO_W = 1145f;
  private static final float LOGO_H = 348f;
  private static final float EPS = 0.01f;

  private static void assertInsideBottomRight(float pageW, float pageH, float[] r) {
    assertNotNull(r);
    float x = r[0], y = r[1], w = r[2], h = r[3];
    assertTrue("left edge inside page", x > 0);
    assertTrue("bottom margin", y >= PdfWatermark.MIN_MARGIN_PT - EPS);
    assertTrue("right edge inside page", x + w <= pageW - PdfWatermark.MIN_MARGIN_PT + EPS);
    assertTrue("top edge inside page", y + h < pageH);
    // bottom-right quadrant
    assertTrue(x > pageW / 2f);
    assertTrue(y + h < pageH / 2f);
    // aspect ratio preserved
    assertEquals(LOGO_W / LOGO_H, w / h, 0.001f);
  }

  @Test
  public void a4Portrait() {
    float[] r = PdfWatermark.placement(595f, 842f, LOGO_W, LOGO_H);
    assertInsideBottomRight(595f, 842f, r);
    assertEquals(595f * PdfWatermark.WIDTH_FRACTION, r[2], EPS); // ~95 pt (3.4 cm)
    // symmetric margins to the right and bottom edges
    assertEquals(r[1], 595f - r[0] - r[2], EPS);
  }

  @Test
  public void letterAndLandscape() {
    assertInsideBottomRight(612f, 792f, PdfWatermark.placement(612f, 792f, LOGO_W, LOGO_H));
    assertInsideBottomRight(842f, 595f, PdfWatermark.placement(842f, 595f, LOGO_W, LOGO_H));
  }

  @Test
  public void widthIsClampedOnLargeAndSmallPages() {
    // Image-sized page at high resolution (e.g. 3000 x 4000 pt)
    float[] big = PdfWatermark.placement(3000f, 4000f, LOGO_W, LOGO_H);
    assertInsideBottomRight(3000f, 4000f, big);
    assertEquals(PdfWatermark.MAX_WIDTH_PT, big[2], EPS);
    // Small receipt-like page
    float[] small = PdfWatermark.placement(200f, 500f, LOGO_W, LOGO_H);
    assertInsideBottomRight(200f, 500f, small);
    assertTrue(small[2] <= 200f / 3f + EPS);
  }

  @Test
  public void veryFlatPageLimitsHeight() {
    float[] r = PdfWatermark.placement(2000f, 150f, LOGO_W, LOGO_H);
    assertNotNull(r);
    assertTrue(r[3] <= 150f / 6f + EPS);
    assertEquals(LOGO_W / LOGO_H, r[2] / r[3], 0.001f);
  }

  @Test
  public void degenerateSizes() {
    assertNull(PdfWatermark.placement(0f, 842f, LOGO_W, LOGO_H));
    assertNull(PdfWatermark.placement(595f, 842f, 0f, LOGO_H));
  }
}
