package de.schliweb.makeacopy.ui.ocr;

import static org.junit.Assert.assertEquals;

import de.schliweb.makeacopy.ui.ocr.OCRViewModel.OcrTransform;
import org.junit.Test;

public class OcrTransformTest {

  @Test
  public void allFieldsStoredCorrectly() {
    OcrTransform t = new OcrTransform(1920, 1080, 2480, 3508, 1.5f, 2.0f, 10, 20);

    assertEquals(1920, t.srcW());
    assertEquals(1080, t.srcH());
    assertEquals(2480, t.dstW());
    assertEquals(3508, t.dstH());
    assertEquals(1.5f, t.scaleX(), 1e-6);
    assertEquals(2.0f, t.scaleY(), 1e-6);
    assertEquals(10, t.offsetX());
    assertEquals(20, t.offsetY());
  }

  @Test
  public void zeroOffset_noLetterboxing() {
    OcrTransform t = new OcrTransform(100, 100, 200, 200, 2.0f, 2.0f, 0, 0);

    assertEquals(0, t.offsetX());
    assertEquals(0, t.offsetY());
  }

  @Test
  public void identityTransform() {
    OcrTransform t = new OcrTransform(100, 100, 100, 100, 1.0f, 1.0f, 0, 0);

    assertEquals(t.srcW(), t.dstW());
    assertEquals(t.srcH(), t.dstH());
    assertEquals(1.0f, t.scaleX(), 1e-6);
    assertEquals(1.0f, t.scaleY(), 1e-6);
  }
}
