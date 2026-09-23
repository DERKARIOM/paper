package de.schliweb.makeacopy.ui.ocr.review.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class OcrDocTest {

  @Test
  public void defaultConstructor_hasDefaults() {
    OcrDoc doc = new OcrDoc();
    assertEquals(1, doc.schema);
    assertNotNull(doc.imageSize);
    assertEquals(0, doc.imageSize.w);
    assertEquals(0, doc.imageSize.h);
    assertNotNull(doc.words);
    assertTrue(doc.words.isEmpty());
    assertNotNull(doc.lines);
    assertTrue(doc.lines.isEmpty());
    assertNotNull(doc.blocks);
    assertTrue(doc.blocks.isEmpty());
  }

  // ── ImageSize ─────────────────────────────────────────────────────────────

  @Test
  public void imageSize_defaultConstructor() {
    OcrDoc.ImageSize size = new OcrDoc.ImageSize();
    assertEquals(0, size.w);
    assertEquals(0, size.h);
  }

  @Test
  public void imageSize_paramConstructor() {
    OcrDoc.ImageSize size = new OcrDoc.ImageSize(1920, 1080);
    assertEquals(1920, size.w);
    assertEquals(1080, size.h);
  }

  // ── Word ──────────────────────────────────────────────────────────────────

  @Test
  public void word_defaults() {
    OcrDoc.Word word = new OcrDoc.Word();
    assertEquals(0, word.id);
    assertNull(word.t);
    assertNotNull(word.b);
    assertEquals(4, word.b.length);
    assertEquals(0.0f, word.c, 0.0f);
    assertFalse(word.e);
    assertEquals(0, word.l);
    assertEquals(0, word.k);
    assertNull(word.lang);
  }

  @Test
  public void word_fieldsAreMutable() {
    OcrDoc.Word word = new OcrDoc.Word();
    word.id = 42;
    word.t = "Hello";
    word.b = new int[] {10, 20, 100, 30};
    word.c = 0.95f;
    word.e = true;
    word.l = 1;
    word.k = 2;
    word.lang = "eng";

    assertEquals(42, word.id);
    assertEquals("Hello", word.t);
    assertEquals(10, word.b[0]);
    assertEquals(0.95f, word.c, 0.001f);
    assertTrue(word.e);
    assertEquals(1, word.l);
    assertEquals(2, word.k);
    assertEquals("eng", word.lang);
  }

  // ── Line ──────────────────────────────────────────────────────────────────

  @Test
  public void line_defaults() {
    OcrDoc.Line line = new OcrDoc.Line();
    assertEquals(0, line.id);
    assertNull(line.w);
    assertNotNull(line.b);
    assertEquals(4, line.b.length);
  }

  @Test
  public void line_fieldsAreMutable() {
    OcrDoc.Line line = new OcrDoc.Line();
    line.id = 5;
    line.w = new int[] {1, 2, 3};
    line.b = new int[] {0, 0, 200, 40};

    assertEquals(5, line.id);
    assertArrayEquals(new int[] {1, 2, 3}, line.w);
    assertEquals(200, line.b[2]);
  }

  // ── Block ─────────────────────────────────────────────────────────────────

  @Test
  public void block_defaults() {
    OcrDoc.Block block = new OcrDoc.Block();
    assertEquals(0, block.id);
    assertNull(block.l);
    assertNotNull(block.b);
    assertEquals(4, block.b.length);
  }

  @Test
  public void block_fieldsAreMutable() {
    OcrDoc.Block block = new OcrDoc.Block();
    block.id = 3;
    block.l = new int[] {1, 2};
    block.b = new int[] {10, 10, 300, 500};

    assertEquals(3, block.id);
    assertArrayEquals(new int[] {1, 2}, block.l);
    assertEquals(300, block.b[2]);
  }

  // ── Doc with content ─────────────────────────────────────────────────────

  @Test
  public void doc_addWordLineBlock() {
    OcrDoc doc = new OcrDoc();
    doc.schema = 2;
    doc.imageSize = new OcrDoc.ImageSize(800, 600);

    OcrDoc.Word w = new OcrDoc.Word();
    w.id = 1;
    w.t = "test";
    doc.words.add(w);

    OcrDoc.Line ln = new OcrDoc.Line();
    ln.id = 1;
    ln.w = new int[] {1};
    doc.lines.add(ln);

    OcrDoc.Block bl = new OcrDoc.Block();
    bl.id = 1;
    bl.l = new int[] {1};
    doc.blocks.add(bl);

    assertEquals(2, doc.schema);
    assertEquals(800, doc.imageSize.w);
    assertEquals(1, doc.words.size());
    assertEquals(1, doc.lines.size());
    assertEquals(1, doc.blocks.size());
    assertEquals("test", doc.words.get(0).t);
  }
}
