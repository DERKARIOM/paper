package de.schliweb.makeacopy.ui.export.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CompletedScanTest {

  @Test
  public void allFieldsStoredCorrectly() {
    CompletedScan scan =
        new CompletedScan(
            "uuid-1",
            "/path/file.jpg",
            90,
            "/path/ocr.txt",
            "plain",
            "/path/thumb.jpg",
            1700000000L,
            1920,
            1080,
            null,
            2,
            "exif");

    assertEquals("uuid-1", scan.id());
    assertEquals("/path/file.jpg", scan.filePath());
    assertEquals(90, scan.rotationDeg());
    assertEquals("/path/ocr.txt", scan.ocrTextPath());
    assertEquals("plain", scan.ocrFormat());
    assertEquals("/path/thumb.jpg", scan.thumbPath());
    assertEquals(1700000000L, scan.createdAt());
    assertEquals(1920, scan.widthPx());
    assertEquals(1080, scan.heightPx());
    assertNull(scan.inMemoryBitmap());
    assertEquals(2, scan.schemaVersion());
    assertEquals("exif", scan.orientationMode());
  }

  @Test
  public void nullOrientationMode_defaultsToBaked() {
    CompletedScan scan =
        new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, 1, null);

    assertEquals("baked", scan.orientationMode());
  }

  @Test
  public void emptyOrientationMode_defaultsToBaked() {
    CompletedScan scan = new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, 1, "");

    assertEquals("baked", scan.orientationMode());
  }

  @Test
  public void schemaVersionZero_defaultsToOne() {
    CompletedScan scan =
        new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, 0, "exif");

    assertEquals(1, scan.schemaVersion());
  }

  @Test
  public void schemaVersionNegative_defaultsToOne() {
    CompletedScan scan =
        new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, -5, "exif");

    assertEquals(1, scan.schemaVersion());
  }

  @Test
  public void nullOptionalFields_allowed() {
    CompletedScan scan =
        new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, 1, "baked");

    assertNull(scan.filePath());
    assertNull(scan.ocrTextPath());
    assertNull(scan.ocrFormat());
    assertNull(scan.thumbPath());
    assertNull(scan.inMemoryBitmap());
  }

  @Test
  public void validSchemaVersion_preserved() {
    CompletedScan scan =
        new CompletedScan("id", null, 0, null, null, null, 0L, 0, 0, null, 3, "baked");

    assertEquals(3, scan.schemaVersion());
  }

  @Test
  public void rotationDeg_storedAsIs() {
    for (int deg : new int[] {0, 90, 180, 270}) {
      CompletedScan scan =
          new CompletedScan("id", null, deg, null, null, null, 0L, 0, 0, null, 1, "baked");
      assertEquals(deg, scan.rotationDeg());
    }
  }
}
