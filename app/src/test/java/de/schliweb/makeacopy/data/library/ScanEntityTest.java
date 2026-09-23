package de.schliweb.makeacopy.data.library;

import static org.junit.Assert.*;

import org.junit.Test;

/** Tests for {@link ScanEntity} (Lombok @AllArgsConstructor and public fields). */
public class ScanEntityTest {

  @Test
  public void allArgsConstructor_setsAllFields() {
    ScanEntity e =
        new ScanEntity(
            "uuid-1", "My Scan", 1700000000L, 3, "/cover.jpg", "[\"a.pdf\"]", "{\"k\":1}");
    assertEquals("uuid-1", e.id);
    assertEquals("My Scan", e.title);
    assertEquals(1700000000L, e.createdAt);
    assertEquals(3, e.pageCount);
    assertEquals("/cover.jpg", e.coverPath);
    assertEquals("[\"a.pdf\"]", e.exportPathsJson);
    assertEquals("{\"k\":1}", e.sourceMetaJson);
  }

  @Test
  public void nullOptionalFields_allowed() {
    ScanEntity e = new ScanEntity("uuid-2", null, 0L, 0, null, null, null);
    assertEquals("uuid-2", e.id);
    assertNull(e.title);
    assertNull(e.coverPath);
    assertNull(e.exportPathsJson);
    assertNull(e.sourceMetaJson);
  }

  @Test
  public void fieldsAreMutable() {
    ScanEntity e = new ScanEntity("id", "title", 100L, 1, null, null, null);
    e.title = "Updated";
    e.pageCount = 5;
    e.createdAt = 200L;
    e.coverPath = "/new.jpg";
    e.exportPathsJson = "[]";
    e.sourceMetaJson = "{}";
    assertEquals("Updated", e.title);
    assertEquals(5, e.pageCount);
    assertEquals(200L, e.createdAt);
    assertEquals("/new.jpg", e.coverPath);
    assertEquals("[]", e.exportPathsJson);
    assertEquals("{}", e.sourceMetaJson);
  }

  @Test
  public void idField_isNonNullByContract() {
    // The @NonNull annotation is on id; Lombok constructor accepts it but Room enforces non-null
    ScanEntity e = new ScanEntity("abc", null, 0L, 0, null, null, null);
    assertNotNull(e.id);
  }
}
