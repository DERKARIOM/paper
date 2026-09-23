package de.schliweb.makeacopy.data;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompletedScansRegistryTest {

  private File tempDir;
  private File indexFile;
  private CompletedScansRegistry registry;

  @Before
  public void setUp() throws Exception {
    tempDir = java.nio.file.Files.createTempDirectory("mac_registry_test").toFile();
    indexFile = new File(tempDir, "completed_scans.json");
    registry = new CompletedScansRegistry(indexFile);
  }

  @After
  public void tearDown() {
    if (tempDir != null && tempDir.exists()) {
      deleteRec(tempDir);
    }
  }

  private void deleteRec(File f) {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files != null) for (File c : files) deleteRec(c);
    }
    //noinspection ResultOfMethodCallIgnored
    f.delete();
  }

  private CompletedScan make(long createdAt, String id) {
    return new CompletedScan(
        id, "/tmp/" + id + ".jpg", 0, null, null, null, createdAt, 100, 200, null, 1, "baked");
  }

  @Test
  public void testInsertListOrderAndRemove() throws Exception {
    long now = System.currentTimeMillis();
    CompletedScan a = make(now - 1000, "id-a");
    CompletedScan b = make(now + 1000, "id-b");

    registry.insert(a);
    registry.insert(b);

    List<CompletedScan> all = registry.listAllOrderedByDateDesc();
    assertEquals(2, all.size());
    assertEquals("id-b", all.get(0).id());
    assertEquals("id-a", all.get(1).id());

    // remove unknown id → no-op
    registry.remove("unknown");
    all = registry.listAllOrderedByDateDesc();
    assertEquals(2, all.size());

    // remove existing id
    registry.remove("id-b");
    all = registry.listAllOrderedByDateDesc();
    assertEquals(1, all.size());
    assertEquals("id-a", all.get(0).id());
  }

  @Test
  public void testCorruptedFileHandledAsEmpty() throws Exception {
    try (FileOutputStream fos = new FileOutputStream(indexFile)) {
      fos.write("{not valid json".getBytes(StandardCharsets.UTF_8));
    }
    List<CompletedScan> list = registry.listAllOrderedByDateDesc();
    assertNotNull(list);
    assertEquals(0, list.size());

    // After a write it should become valid json again
    CompletedScan c = make(System.currentTimeMillis(), UUID.randomUUID().toString());
    registry.insert(c);
    List<CompletedScan> list2 = registry.listAllOrderedByDateDesc();
    assertEquals(1, list2.size());
    assertEquals(c.id(), list2.get(0).id());
  }

  @Test
  public void testOcrFormatRoundtrip() throws Exception {
    long now = System.currentTimeMillis();
    String id1 = "id-hocr";
    String id2 = "id-nullfmt";
    CompletedScan withHocr =
        new CompletedScan(
            id1,
            "/tmp/" + id1 + ".jpg",
            0,
            "/tmp/" + id1 + ".hocr",
            "hocr",
            null,
            now,
            100,
            200,
            null,
            1,
            "baked");
    CompletedScan withNullFmt =
        new CompletedScan(
            id2,
            "/tmp/" + id2 + ".jpg",
            0,
            "/tmp/" + id2 + ".txt",
            null,
            null,
            now + 1,
            100,
            200,
            null,
            1,
            "baked");

    registry.insert(withHocr);
    registry.insert(withNullFmt);

    List<CompletedScan> all = registry.listAllOrderedByDateDesc();
    // Build map by id for assertions
    java.util.Map<String, CompletedScan> map = new java.util.HashMap<>();
    for (CompletedScan s : all) {
      map.put(s.id(), s);
    }
    assertTrue(map.containsKey(id1));
    assertTrue(map.containsKey(id2));
    assertEquals("hocr", map.get(id1).ocrFormat());
    assertEquals("/tmp/" + id1 + ".hocr", map.get(id1).ocrTextPath());
    // Null format should remain null (treated as plain by consumers)
    assertNull(map.get(id2).ocrFormat());
    assertEquals("/tmp/" + id2 + ".txt", map.get(id2).ocrTextPath());
  }
}
