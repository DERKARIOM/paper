package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import org.junit.Test;

/**
 * JVM unit tests for {@link RecognizedWord} construction, setText/setLang, and toString.
 *
 * <p>Note: transform(), clipTo(), midY(), centerX(), getBoundingBox() rely on {@code new
 * RectF(other)} which returns zeroed fields in Android unit-test stubs. Those methods are covered
 * by instrumented tests instead.
 */
public class RecognizedWordTest {

  private static RectF rect(float l, float t, float r, float b) {
    RectF rf = new RectF();
    rf.left = l;
    rf.top = t;
    rf.right = r;
    rf.bottom = b;
    return rf;
  }

  // ---- Constructor & getters ----

  @Test
  public void constructor_setsTextAndConfidence() {
    RecognizedWord w = new RecognizedWord("hello", rect(10, 20, 50, 40), 85f);
    assertEquals("hello", w.getText());
    assertEquals(85f, w.getConfidence(), 0.01f);
    assertNull(w.getLang());
  }

  @Test
  public void constructor_withLang() {
    RecognizedWord w = new RecognizedWord("hallo", rect(0, 0, 10, 10), 90f, "deu");
    assertEquals("deu", w.getLang());
  }

  @Test
  public void constructor_nullBoundingBox_createsNonNull() {
    RecognizedWord w = new RecognizedWord("x", null, 90f);
    // getBoundingBox returns a new RectF — at least non-null
    assertNotNull(w.getBoundingBox());
  }

  @Test
  public void getBoundingBox_returnsNewInstance() {
    RecognizedWord w = new RecognizedWord("x", rect(10, 20, 30, 40), 90f);
    RectF b1 = w.getBoundingBox();
    RectF b2 = w.getBoundingBox();
    assertNotSame(b1, b2);
  }

  // ---- setText / setLang ----

  @Test
  public void setText_updatesText() {
    RecognizedWord w = new RecognizedWord("old", rect(0, 0, 10, 10), 90f);
    w.setText("new");
    assertEquals("new", w.getText());
  }

  @Test
  public void setLang_updatesLang() {
    RecognizedWord w = new RecognizedWord("x", rect(0, 0, 10, 10), 90f);
    w.setLang("fra");
    assertEquals("fra", w.getLang());
  }

  // ---- transform returns new instance ----

  @Test
  public void transform_returnsNewWord() {
    RecognizedWord w = new RecognizedWord("x", rect(10, 20, 30, 40), 90f);
    RecognizedWord t = w.transform(2f, 3f, 5f, 10f);
    assertNotSame(w, t);
    assertEquals("x", t.getText());
    assertEquals(90f, t.getConfidence(), 0.01f);
  }

  @Test
  public void transform_uniformScale_returnsNewWord() {
    RecognizedWord w = new RecognizedWord("x", rect(10, 20, 30, 40), 90f);
    RecognizedWord t = w.transform(2f, 5f, 10f);
    assertNotSame(w, t);
    assertEquals("x", t.getText());
  }

  // ---- clipTo returns new instance ----

  @Test
  public void clipTo_returnsNewWord() {
    RecognizedWord w = new RecognizedWord("x", rect(-5, -10, 200, 300), 90f);
    RecognizedWord c = w.clipTo(100f, 150f);
    assertNotSame(w, c);
    assertEquals("x", c.getText());
    assertEquals(90f, c.getConfidence(), 0.01f);
  }

  // ---- toString ----

  @Test
  public void toString_containsText() {
    RecognizedWord w = new RecognizedWord("hello", rect(0, 0, 10, 10), 90f);
    assertTrue(w.toString().contains("hello"));
  }

  @Test
  public void toString_containsConfidence() {
    RecognizedWord w = new RecognizedWord("hello", rect(0, 0, 10, 10), 90f);
    assertTrue(w.toString().contains("90"));
  }
}
