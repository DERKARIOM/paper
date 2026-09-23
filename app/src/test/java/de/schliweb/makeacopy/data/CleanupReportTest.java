package de.schliweb.makeacopy.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CleanupReportTest {

  @Test
  public void defaults_allZero() {
    RegistryCleaner.CleanupReport r = new RegistryCleaner.CleanupReport();
    assertEquals(0, r.prunedMissingEntries);
    assertEquals(0, r.deletedOrphanDirs);
    assertEquals(0, r.deletedFilesInRemove);
  }

  @Test
  public void fieldsAreMutable() {
    RegistryCleaner.CleanupReport r = new RegistryCleaner.CleanupReport();
    r.prunedMissingEntries = 3;
    r.deletedOrphanDirs = 5;
    r.deletedFilesInRemove = 12;

    assertEquals(3, r.prunedMissingEntries);
    assertEquals(5, r.deletedOrphanDirs);
    assertEquals(12, r.deletedFilesInRemove);
  }

  @Test
  public void toString_containsAllFields() {
    RegistryCleaner.CleanupReport r = new RegistryCleaner.CleanupReport();
    r.prunedMissingEntries = 1;
    r.deletedOrphanDirs = 2;
    r.deletedFilesInRemove = 3;

    String s = r.toString();
    assertNotNull(s);
    assertTrue(s.contains("prunedMissingEntries=1"));
    assertTrue(s.contains("deletedOrphanDirs=2"));
    assertTrue(s.contains("deletedFilesInRemove=3"));
  }
}
