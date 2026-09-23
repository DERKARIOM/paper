package de.schliweb.makeacopy.ui.ocr.review.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OcrDocUtilsTest {

  @Test
  public void deepCopy_nullReturnsEmptyDoc() {
    OcrDoc result = OcrDocUtils.deepCopy(null);
    assertNotNull(result);
    assertEquals(1, result.schema);
    assertNotNull(result.words);
    assertEquals(0, result.words.size());
  }

  @Test
  public void deepCopy_copiesSchemaAndImageSize() {
    OcrDoc src = new OcrDoc();
    src.schema = 2;
    src.imageSize = new OcrDoc.ImageSize(1920, 1080);

    OcrDoc copy = OcrDocUtils.deepCopy(src);

    assertEquals(2, copy.schema);
    assertEquals(1920, copy.imageSize.w);
    assertEquals(1080, copy.imageSize.h);
    assertNotSame(src.imageSize, copy.imageSize);
  }

  @Test
  public void deepCopy_copiesWords() {
    OcrDoc src = new OcrDoc();
    OcrDoc.Word w = new OcrDoc.Word();
    w.id = 1;
    w.t = "hello";
    w.b = new int[] {10, 20, 100, 30};
    w.c = 0.95f;
    w.e = true;
    w.l = 0;
    w.k = 0;
    w.lang = "eng";
    src.words.add(w);

    OcrDoc copy = OcrDocUtils.deepCopy(src);

    assertEquals(1, copy.words.size());
    OcrDoc.Word cw = copy.words.get(0);
    assertNotSame(w, cw);
    assertEquals("hello", cw.t);
    assertEquals(1, cw.id);
    assertArrayEquals(new int[] {10, 20, 100, 30}, cw.b);
    assertEquals(0.95f, cw.c, 0.001f);
    assertEquals(true, cw.e);
    assertEquals("eng", cw.lang);
    // mutating copy does not affect source
    cw.b[0] = 999;
    assertEquals(10, w.b[0]);
  }

  @Test
  public void deepCopy_copiesLinesAndBlocks() {
    OcrDoc src = new OcrDoc();
    OcrDoc.Line line = new OcrDoc.Line();
    line.id = 0;
    line.w = new int[] {0, 1};
    line.b = new int[] {0, 0, 200, 40};
    src.lines.add(line);

    OcrDoc.Block block = new OcrDoc.Block();
    block.id = 0;
    block.l = new int[] {0};
    block.b = new int[] {0, 0, 200, 40};
    src.blocks.add(block);

    OcrDoc copy = OcrDocUtils.deepCopy(src);

    assertEquals(1, copy.lines.size());
    assertNotSame(line, copy.lines.get(0));
    assertArrayEquals(new int[] {0, 1}, copy.lines.get(0).w);

    assertEquals(1, copy.blocks.size());
    assertNotSame(block, copy.blocks.get(0));
    assertArrayEquals(new int[] {0}, copy.blocks.get(0).l);
  }

  @Test
  public void deepCopy_handlesNullElementsInLists() {
    OcrDoc src = new OcrDoc();
    src.words.add(null);
    src.lines.add(null);
    src.blocks.add(null);

    OcrDoc copy = OcrDocUtils.deepCopy(src);

    assertEquals(1, copy.words.size());
    assertNull(copy.words.get(0));
    assertEquals(1, copy.lines.size());
    assertNull(copy.lines.get(0));
    assertEquals(1, copy.blocks.size());
    assertNull(copy.blocks.get(0));
  }
}
