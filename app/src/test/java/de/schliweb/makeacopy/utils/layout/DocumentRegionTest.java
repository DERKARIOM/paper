package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link DocumentRegion}. Uses returnDefaultValues = true for android.graphics.Rect
 * stubs; tests focus on pure-logic methods that don't depend on real Rect behavior.
 */
public class DocumentRegionTest {

  // ── Type enum ──

  @Test
  public void typeEnum_allValuesPresent() {
    DocumentRegion.Type[] values = DocumentRegion.Type.values();
    assertEquals(10, values.length);
    assertEquals(DocumentRegion.Type.HEADER, DocumentRegion.Type.valueOf("HEADER"));
    assertEquals(DocumentRegion.Type.BODY, DocumentRegion.Type.valueOf("BODY"));
    assertEquals(DocumentRegion.Type.FOOTER, DocumentRegion.Type.valueOf("FOOTER"));
    assertEquals(DocumentRegion.Type.TABLE, DocumentRegion.Type.valueOf("TABLE"));
    assertEquals(DocumentRegion.Type.FIGURE, DocumentRegion.Type.valueOf("FIGURE"));
    assertEquals(DocumentRegion.Type.CAPTION, DocumentRegion.Type.valueOf("CAPTION"));
    assertEquals(DocumentRegion.Type.SIDEBAR, DocumentRegion.Type.valueOf("SIDEBAR"));
    assertEquals(DocumentRegion.Type.MARGIN_NOTE, DocumentRegion.Type.valueOf("MARGIN_NOTE"));
    assertEquals(DocumentRegion.Type.COLUMN, DocumentRegion.Type.valueOf("COLUMN"));
    assertEquals(DocumentRegion.Type.UNKNOWN, DocumentRegion.Type.valueOf("UNKNOWN"));
  }

  // ── Constructor (2-arg) ──

  @Test
  public void constructor_setsTypeAndDefaults() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    assertEquals(DocumentRegion.Type.BODY, region.getType());
    assertEquals(1.0f, region.getConfidence(), 0.001f);
    assertEquals(0, region.getReadingOrder());
    assertNotNull(region.getChildren());
    assertTrue(region.getChildren().isEmpty());
    assertNull(region.getBounds());
  }

  // ── Constructor (4-arg) ──

  @Test
  public void constructor_fullParams() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.TABLE, 0.85f, 3);
    assertEquals(DocumentRegion.Type.TABLE, region.getType());
    assertEquals(0.85f, region.getConfidence(), 0.001f);
    assertEquals(3, region.getReadingOrder());
  }

  // ── hasChildren / addChild ──

  @Test
  public void hasChildren_falseWhenEmpty() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    assertFalse(region.hasChildren());
  }

  @Test
  public void addChild_setsParentAndAddsToList() {
    DocumentRegion parent = new DocumentRegion(null, DocumentRegion.Type.BODY);
    DocumentRegion child = new DocumentRegion(null, DocumentRegion.Type.COLUMN);

    parent.addChild(child);

    assertTrue(parent.hasChildren());
    assertEquals(1, parent.getChildren().size());
    assertSame(child, parent.getChildren().get(0));
    assertSame(parent, child.getParent());
  }

  @Test
  public void addChild_multipleChildren() {
    DocumentRegion parent = new DocumentRegion(null, DocumentRegion.Type.TABLE);
    DocumentRegion c1 = new DocumentRegion(null, DocumentRegion.Type.COLUMN);
    DocumentRegion c2 = new DocumentRegion(null, DocumentRegion.Type.COLUMN);

    parent.addChild(c1);
    parent.addChild(c2);

    assertEquals(2, parent.getChildren().size());
  }

  // ── getArea with null bounds ──

  @Test
  public void getArea_nullBounds_returnsZero() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.FIGURE);
    assertEquals(0, region.getArea());
  }

  // ── getOptimalPsm ──

  @Test
  public void getOptimalPsm_header() {
    assertEquals(6, new DocumentRegion(null, DocumentRegion.Type.HEADER).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_body() {
    assertEquals(3, new DocumentRegion(null, DocumentRegion.Type.BODY).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_footer() {
    assertEquals(7, new DocumentRegion(null, DocumentRegion.Type.FOOTER).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_table() {
    // TABLE uses PSM_SINGLE_BLOCK (6); see DocumentRegion#getOptimalPsm.
    assertEquals(6, new DocumentRegion(null, DocumentRegion.Type.TABLE).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_caption() {
    assertEquals(7, new DocumentRegion(null, DocumentRegion.Type.CAPTION).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_sidebar() {
    assertEquals(6, new DocumentRegion(null, DocumentRegion.Type.SIDEBAR).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_marginNote() {
    assertEquals(6, new DocumentRegion(null, DocumentRegion.Type.MARGIN_NOTE).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_column() {
    assertEquals(4, new DocumentRegion(null, DocumentRegion.Type.COLUMN).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_unknown_fallsBackToAuto() {
    assertEquals(3, new DocumentRegion(null, DocumentRegion.Type.UNKNOWN).getOptimalPsm());
  }

  @Test
  public void getOptimalPsm_figure_fallsBackToAuto() {
    assertEquals(3, new DocumentRegion(null, DocumentRegion.Type.FIGURE).getOptimalPsm());
  }

  // ── Lombok setters ──

  @Test
  public void setters_work() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    region.setConfidence(0.5f);
    region.setReadingOrder(7);
    region.setColumnIndex(2);
    region.setRowIndex(3);
    region.setLanguage("deu");

    assertEquals(0.5f, region.getConfidence(), 0.001f);
    assertEquals(7, region.getReadingOrder());
    assertEquals(2, region.getColumnIndex());
    assertEquals(3, region.getRowIndex());
    assertEquals("deu", region.getLanguage());
  }

  @Test
  public void defaultColumnAndRowIndex() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    assertEquals(-1, region.getColumnIndex());
    assertEquals(-1, region.getRowIndex());
  }

  // ── toString ──

  @Test
  public void toString_containsType() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.HEADER);
    String s = region.toString();
    assertTrue(s.contains("HEADER"));
    assertTrue(s.contains("children=0"));
  }

  // ── intersects with null bounds ──

  @Test
  public void intersects_nullBounds_returnsFalse() {
    DocumentRegion a = new DocumentRegion(null, DocumentRegion.Type.BODY);
    DocumentRegion b = new DocumentRegion(null, DocumentRegion.Type.FOOTER);
    assertFalse(a.intersects(b));
  }

  // ── contains with null bounds ──

  @Test
  public void contains_nullBounds_returnsFalse() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    assertFalse(region.contains(10, 10));
  }
}
