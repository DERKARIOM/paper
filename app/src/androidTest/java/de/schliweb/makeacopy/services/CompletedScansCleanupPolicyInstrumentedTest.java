package de.schliweb.makeacopy.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.data.CompletedScansRegistry;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for {@link CompletedScansCleanupPolicy} running on a real device/emulator.
 * Also verifies integration with {@link CompletedScansRegistry} (insert, list, remove).
 */
@RunWith(AndroidJUnit4.class)
public class CompletedScansCleanupPolicyInstrumentedTest {

  private File tempIndexFile;
  private CompletedScansRegistry registry;

  private static CompletedScan scan(String id, long createdAt) {
    return new CompletedScan(id, null, 0, null, null, null, createdAt, 0, 0, null, 1, "baked");
  }

  @Before
  public void setUp() {
    File cacheDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
    tempIndexFile = new File(cacheDir, "test_completed_scans_" + System.nanoTime() + ".json");
    registry = new CompletedScansRegistry(tempIndexFile);
  }

  @After
  public void tearDown() {
    //noinspection ResultOfMethodCallIgnored
    tempIndexFile.delete();
  }

  // ---- Pure policy logic on device ----

  @Test
  public void byAge_oldScansRemoved_onDevice() {
    long now = System.currentTimeMillis();
    CompletedScan old = scan("old", now - TimeUnit.DAYS.toMillis(60));
    CompletedScan recent = scan("recent", now - TimeUnit.DAYS.toMillis(1));
    List<String> ids =
        CompletedScansCleanupPolicy.idsToRemoveByAge(Arrays.asList(recent, old), 30, now);
    assertEquals(1, ids.size());
    assertEquals("old", ids.get(0));
  }

  @Test
  public void byCount_overLimit_oldestRemoved_onDevice() {
    List<CompletedScan> scans =
        Arrays.asList(scan("a", 5), scan("b", 4), scan("c", 3), scan("d", 2), scan("e", 1));
    List<String> ids = CompletedScansCleanupPolicy.idsToRemoveByCount(scans, 3);
    assertEquals(2, ids.size());
    assertEquals("d", ids.get(0));
    assertEquals("e", ids.get(1));
  }

  @Test
  public void byStorage_overLimit_oldestRemovedFirst_onDevice() {
    List<CompletedScan> scans = Arrays.asList(scan("a", 3), scan("b", 2), scan("c", 1));
    Map<String, Long> sizes = new HashMap<>();
    sizes.put("a", 300L);
    sizes.put("b", 200L);
    sizes.put("c", 100L);
    List<String> ids = CompletedScansCleanupPolicy.idsToRemoveByStorage(scans, sizes, 400);
    assertEquals(2, ids.size());
    assertEquals("c", ids.get(0));
    assertEquals("b", ids.get(1));
  }

  // ---- Registry integration: insert, list, policy, remove ----

  @Test
  public void registry_insertAndList_roundTrip() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("s1", now - 1000));
    registry.insert(scan("s2", now));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    assertEquals(2, scans.size());
    // Newest first
    assertEquals("s2", scans.get(0).id());
    assertEquals("s1", scans.get(1).id());
  }

  @Test
  public void registry_noDuplicateInsert() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("dup", now));
    registry.insert(scan("dup", now));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    assertEquals(1, scans.size());
  }

  @Test
  public void registry_removeEntry() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("keep", now));
    registry.insert(scan("remove", now - 1000));

    registry.remove("remove");

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    assertEquals(1, scans.size());
    assertEquals("keep", scans.get(0).id());
  }

  @Test
  public void registry_policyThenRemove_maxAge() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("recent", now));
    registry.insert(scan("old1", now - TimeUnit.DAYS.toMillis(40)));
    registry.insert(scan("old2", now - TimeUnit.DAYS.toMillis(50)));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    List<String> toRemove = CompletedScansCleanupPolicy.idsToRemoveByAge(scans, 30, now);
    assertEquals(2, toRemove.size());

    for (String id : toRemove) {
      registry.remove(id);
    }

    List<CompletedScan> remaining = registry.listAllOrderedByDateDesc();
    assertEquals(1, remaining.size());
    assertEquals("recent", remaining.get(0).id());
  }

  @Test
  public void registry_policyThenRemove_maxCount() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("a", now));
    registry.insert(scan("b", now - 1000));
    registry.insert(scan("c", now - 2000));
    registry.insert(scan("d", now - 3000));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    List<String> toRemove = CompletedScansCleanupPolicy.idsToRemoveByCount(scans, 2);
    assertEquals(2, toRemove.size());

    for (String id : toRemove) {
      registry.remove(id);
    }

    List<CompletedScan> remaining = registry.listAllOrderedByDateDesc();
    assertEquals(2, remaining.size());
    assertEquals("a", remaining.get(0).id());
    assertEquals("b", remaining.get(1).id());
  }

  @Test
  public void registry_policyThenRemove_maxStorage() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("x", now));
    registry.insert(scan("y", now - 1000));
    registry.insert(scan("z", now - 2000));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    Map<String, Long> sizes = new HashMap<>();
    sizes.put("x", 300L);
    sizes.put("y", 200L);
    sizes.put("z", 100L);
    // Total=600, limit=400 → remove z(100), then y(200)
    List<String> toRemove = CompletedScansCleanupPolicy.idsToRemoveByStorage(scans, sizes, 400);
    assertEquals(2, toRemove.size());

    for (String id : toRemove) {
      registry.remove(id);
    }

    List<CompletedScan> remaining = registry.listAllOrderedByDateDesc();
    assertEquals(1, remaining.size());
    assertEquals("x", remaining.get(0).id());
  }

  @Test
  public void registry_emptyAfterRemovingAll() throws IOException {
    long now = System.currentTimeMillis();
    registry.insert(scan("a", now));
    registry.insert(scan("b", now - 1000));

    List<CompletedScan> scans = registry.listAllOrderedByDateDesc();
    List<String> toRemove = CompletedScansCleanupPolicy.idsToRemoveByCount(scans, 0);
    assertEquals(2, toRemove.size());

    for (String id : toRemove) {
      registry.remove(id);
    }

    assertTrue(registry.listAllOrderedByDateDesc().isEmpty());
  }
}
