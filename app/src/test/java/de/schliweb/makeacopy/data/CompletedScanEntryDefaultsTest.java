package de.schliweb.makeacopy.data;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Verifies defaulting behavior for CompletedScanEntry regarding schemaVersion and orientationMode.
 */
public class CompletedScanEntryDefaultsTest {

  @Test
  public void constructorDefaults_whenZeroOrNull_thenBakedAndSchema1() {
    CompletedScanEntry e =
        new CompletedScanEntry(
            "id-1",
            "/tmp/x.jpg",
            0,
            null,
            null,
            null,
            System.currentTimeMillis(),
            100,
            200,
            0, // schemaVersion -> defaults to 1
            null // orientationMode -> defaults to "baked"
            );
    assertEquals(1, e.schemaVersion);
    assertEquals("baked", e.orientationMode);
  }

  @Test
  public void constructorKeepsExplicitValues_whenProvided() {
    CompletedScanEntry e =
        new CompletedScanEntry(
            "id-2",
            "/tmp/y.jpg",
            90,
            "/tmp/y.txt",
            "plain",
            "/tmp/y_thumb.jpg",
            System.currentTimeMillis(),
            10,
            20,
            2,
            "metadata");
    assertEquals(2, e.schemaVersion);
    assertEquals("metadata", e.orientationMode);
    assertEquals(90, e.rotationDeg);
  }
}
