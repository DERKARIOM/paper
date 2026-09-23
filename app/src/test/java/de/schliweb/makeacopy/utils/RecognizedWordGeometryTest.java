package de.schliweb.makeacopy.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import org.junit.Test;

/**
 * Tests for {@link RecognizedWord} construction and methods that don't depend on RectF field values
 * (RectF is stubbed in JVM unit tests).
 */
public class RecognizedWordGeometryTest {

  // ── Constructor edge cases ────────────────────────────────────────────────

  @Test
  public void constructor_nullBoundingBox_returnsNonNullBox() {
    RecognizedWord w = new RecognizedWord("hello", null, 95f);
    assertNotNull(w.getBoundingBox());
  }

  @Test
  public void constructor_preservesText() {
    RecognizedWord w = new RecognizedWord("hello", new RectF(), 95f);
    assertEquals("hello", w.getText());
  }

  @Test
  public void constructor_preservesConfidence() {
    RecognizedWord w = new RecognizedWord("hello", new RectF(), 42.5f);
    assertEquals(42.5f, w.getConfidence(), 0.01f);
  }

  @Test
  public void constructor_fourArg_preservesLang() {
    RecognizedWord w = new RecognizedWord("hello", new RectF(), 90f, "deu");
    assertEquals("deu", w.getLang());
  }

  @Test
  public void constructor_threeArg_langIsNull() {
    RecognizedWord w = new RecognizedWord("hello", new RectF(), 90f);
    assertEquals(null, w.getLang());
  }

  // ── getBoundingBox returns defensive copy ──────────────────────────────────

  @Test
  public void getBoundingBox_returnsDifferentInstance() {
    RecognizedWord w = new RecognizedWord("test", new RectF(), 90f);
    RectF bb1 = w.getBoundingBox();
    RectF bb2 = w.getBoundingBox();
    assertNotSame(bb1, bb2);
  }

  // ── setters ───────────────────────────────────────────────────────────────

  @Test
  public void setText_updatesText() {
    RecognizedWord w = new RecognizedWord("old", new RectF(), 90f);
    w.setText("new");
    assertEquals("new", w.getText());
  }

  @Test
  public void setLang_updatesLang() {
    RecognizedWord w = new RecognizedWord("test", new RectF(), 90f);
    w.setLang("fra");
    assertEquals("fra", w.getLang());
  }

  // ── transform returns new instance ────────────────────────────────────────

  @Test
  public void transform_returnsNewInstance() {
    RecognizedWord w = new RecognizedWord("test", new RectF(), 90f);
    RecognizedWord t = w.transform(2f, 3f, 0f, 0f);
    assertNotNull(t);
    assertNotSame(w, t);
    assertEquals("test", t.getText());
    assertEquals(90f, t.getConfidence(), 0.01f);
  }

  @Test
  public void transform_uniform_returnsNewInstance() {
    RecognizedWord w = new RecognizedWord("test", new RectF(), 90f);
    RecognizedWord t = w.transform(2f, 0f, 0f);
    assertNotNull(t);
    assertNotSame(w, t);
  }

  // ── clipTo returns new instance ───────────────────────────────────────────

  @Test
  public void clipTo_returnsNewInstance() {
    RecognizedWord w = new RecognizedWord("test", new RectF(), 90f);
    RecognizedWord c = w.clipTo(100, 100);
    assertNotNull(c);
    assertNotSame(w, c);
  }

  // ── toString ──────────────────────────────────────────────────────────────

  @Test
  public void toString_containsTextAndConfidence() {
    RecognizedWord w = new RecognizedWord("hello", new RectF(), 85f);
    String s = w.toString();
    assertTrue(s.contains("hello"));
    assertTrue(s.contains("85"));
  }
}
