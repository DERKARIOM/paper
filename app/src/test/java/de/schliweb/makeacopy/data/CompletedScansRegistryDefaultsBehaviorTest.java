package de.schliweb.makeacopy.data;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.io.File;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that CompletedScansRegistry preserves/normalizes rotation schema fields across
 * persistence boundaries: - Insert with schemaVersion=0 and orientationMode=null → list() returns
 * schemaVersion=1, orientationMode="baked". - Insert with explicit values → list() preserves them.
 */
public class CompletedScansRegistryDefaultsBehaviorTest {

  private File tempDir;
  private File indexFile;
  private CompletedScansRegistry registry;

  @Before
  public void setUp() throws Exception {
    tempDir = java.nio.file.Files.createTempDirectory("mac_registry_defaults_test").toFile();
    indexFile = new File(tempDir, "completed_scans.json");
    registry = new CompletedScansRegistry(indexFile);
  }

  @After
  public void tearDown() {
    if (tempDir != null && tempDir.exists()) deleteRec(tempDir);
  }

  private void deleteRec(File f) {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files != null) for (File c : files) deleteRec(c);
    }
    //noinspection ResultOfMethodCallIgnored
    f.delete();
  }

  @Test
  public void insertWithZeroAndNull_schemaDefaultsAppliedOnRead() throws Exception {
    String id = UUID.randomUUID().toString();
    CompletedScan s =
        new CompletedScan(
            id,
            "/tmp/" + id + ".jpg",
            90, // any rotation
            null,
            null,
            null,
            System.currentTimeMillis(),
            100,
            200,
            null,
            0, // schemaVersion → should default to 1
            null // orientationMode → should default to "baked"
            );
    registry.insert(s);

    List<CompletedScan> list = registry.listAllOrderedByDateDesc();
    assertEquals(1, list.size());
    CompletedScan out = list.get(0);
    assertEquals(1, out.schemaVersion());
    assertEquals("baked", out.orientationMode());
    // RotationDeg should be preserved; it is documentation/history at this point
    assertEquals(90, out.rotationDeg());
  }

  @Test
  public void insertWithExplicitValues_preservedOnRead() throws Exception {
    String id = UUID.randomUUID().toString();
    CompletedScan s =
        new CompletedScan(
            id,
            "/tmp/" + id + ".jpg",
            270,
            null,
            null,
            null,
            System.currentTimeMillis(),
            123,
            456,
            null,
            2,
            "metadata");
    registry.insert(s);

    List<CompletedScan> list = registry.listAllOrderedByDateDesc();
    assertEquals(1, list.size());
    CompletedScan out = list.get(0);
    assertEquals(2, out.schemaVersion());
    assertEquals("metadata", out.orientationMode());
    assertEquals(270, out.rotationDeg());
    assertEquals(123, out.widthPx());
    assertEquals(456, out.heightPx());
  }
}
