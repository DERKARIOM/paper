package de.schliweb.makeacopy.ui.export.session;

import static org.junit.Assert.*;

import org.junit.Test;

/** Verifies defaulting behavior for CompletedScan regarding schemaVersion and orientationMode. */
public class CompletedScanDefaultsTest {

  @Test
  public void constructorDefaults_whenNullOrZero_thenBakedAndSchema1() {
    CompletedScan s =
        new CompletedScan(
            "id-1",
            null,
            0,
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
    assertEquals(1, s.schemaVersion());
    assertEquals("baked", s.orientationMode());
  }

  @Test
  public void constructorKeepsExplicitValues_whenProvided() {
    CompletedScan s =
        new CompletedScan(
            "id-2",
            "/tmp/x.jpg",
            90,
            null,
            null,
            null,
            System.currentTimeMillis(),
            10,
            20,
            null,
            2,
            "metadata");
    assertEquals(2, s.schemaVersion());
    assertEquals("metadata", s.orientationMode());
  }
}
