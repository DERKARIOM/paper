package de.schliweb.makeacopy.data;

import static org.junit.Assert.*;

import org.junit.Test;

/** JVM unit tests for {@link CompletedScanEntry}. */
public class CompletedScanEntryTest {

  @Test
  public void defaultConstructor_fieldsAreDefaults() {
    CompletedScanEntry e = new CompletedScanEntry();
    assertNull(e.id);
    assertNull(e.filePath);
    assertEquals(0, e.rotationDeg);
    assertNull(e.ocrTextPath);
    assertNull(e.ocrFormat);
    assertNull(e.thumbPath);
    assertEquals(0L, e.createdAt);
    assertEquals(0, e.widthPx);
    assertEquals(0, e.heightPx);
    assertEquals(0, e.schemaVersion);
    assertNull(e.orientationMode);
  }

  @Test
  public void fullConstructor_setsAllFields() {
    CompletedScanEntry e =
        new CompletedScanEntry(
            "id1",
            "/path/file.jpg",
            90,
            "/path/ocr.txt",
            "hocr",
            "/path/thumb.jpg",
            12345L,
            1920,
            1080,
            2,
            "metadata");
    assertEquals("id1", e.id);
    assertEquals("/path/file.jpg", e.filePath);
    assertEquals(90, e.rotationDeg);
    assertEquals("/path/ocr.txt", e.ocrTextPath);
    assertEquals("hocr", e.ocrFormat);
    assertEquals("/path/thumb.jpg", e.thumbPath);
    assertEquals(12345L, e.createdAt);
    assertEquals(1920, e.widthPx);
    assertEquals(1080, e.heightPx);
    assertEquals(2, e.schemaVersion);
    assertEquals("metadata", e.orientationMode);
  }

  @Test
  public void schemaVersion_zeroOrNegative_defaultsToOne() {
    CompletedScanEntry e =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 0, "baked");
    assertEquals(1, e.schemaVersion);

    CompletedScanEntry e2 =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, -5, "baked");
    assertEquals(1, e2.schemaVersion);
  }

  @Test
  public void schemaVersion_positive_preserved() {
    CompletedScanEntry e =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 3, "baked");
    assertEquals(3, e.schemaVersion);
  }

  @Test
  public void orientationMode_null_defaultsToBaked() {
    CompletedScanEntry e =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 1, null);
    assertEquals("baked", e.orientationMode);
  }

  @Test
  public void orientationMode_empty_defaultsToBaked() {
    CompletedScanEntry e = new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 1, "");
    assertEquals("baked", e.orientationMode);
  }

  @Test
  public void orientationMode_metadata_preserved() {
    CompletedScanEntry e =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 1, "metadata");
    assertEquals("metadata", e.orientationMode);
  }

  @Test
  public void nullableFields_acceptNull() {
    CompletedScanEntry e =
        new CompletedScanEntry("id", null, 0, null, null, null, 0L, 0, 0, 1, "baked");
    assertNull(e.filePath);
    assertNull(e.ocrTextPath);
    assertNull(e.ocrFormat);
    assertNull(e.thumbPath);
  }
}
