package de.schliweb.makeacopy.data.library;

import static org.junit.Assert.*;

import org.junit.Test;

public class ScanIndexMetaTest {

  @Test
  public void nullId_generatesUUID() {
    ScanIndexMeta meta = new ScanIndexMeta(null, "Test", 1000L, 3, null, null, null);
    assertNotNull(meta.id());
    assertFalse(meta.id().isEmpty());
  }

  @Test
  public void emptyId_generatesUUID() {
    ScanIndexMeta meta = new ScanIndexMeta("", "Test", 1000L, 3, null, null, null);
    assertNotNull(meta.id());
    assertFalse(meta.id().isEmpty());
  }

  @Test
  public void providedId_isPreserved() {
    ScanIndexMeta meta = new ScanIndexMeta("my-id", "Title", 2000L, 5, "/cover.jpg", null, null);
    assertEquals("my-id", meta.id());
  }

  @Test
  public void fieldsAreStored() {
    ScanIndexMeta meta =
        new ScanIndexMeta("id1", "My Scan", 12345L, 10, "/cover.png", "[\"a.pdf\"]", "{\"src\":1}");
    assertEquals("id1", meta.id());
    assertEquals("My Scan", meta.title());
    assertEquals(12345L, meta.createdAt());
    assertEquals(10, meta.pageCount());
    assertEquals("/cover.png", meta.coverPath());
    assertEquals("[\"a.pdf\"]", meta.exportPathsJson());
    assertEquals("{\"src\":1}", meta.sourceMetaJson());
  }

  @Test
  public void nullOptionalFields_areNull() {
    ScanIndexMeta meta = new ScanIndexMeta("id2", "T", 0L, 0, null, null, null);
    assertNull(meta.coverPath());
    assertNull(meta.exportPathsJson());
    assertNull(meta.sourceMetaJson());
  }

  @Test
  public void twoNullIds_generateDifferentUUIDs() {
    ScanIndexMeta a = new ScanIndexMeta(null, "A", 0L, 0, null, null, null);
    ScanIndexMeta b = new ScanIndexMeta(null, "B", 0L, 0, null, null, null);
    assertNotEquals(a.id(), b.id());
  }
}
