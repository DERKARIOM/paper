package de.schliweb.makeacopy.ui.export;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.image.RotationPolicy;
import org.junit.Test;

/**
 * JVM-only seam tests that validate export rotation decisions for multi-page flows without
 * requiring Android bitmaps. Decisions are delegated to RotationPolicy just like in ExportFragment.
 */
public class ExportDecisionSeamTest {

  @Test
  public void mixedSession_inMemoryPlusBaked_diskNotRotated_inMemoryRotated() {
    // Page A: in-memory, deg=90 -> should rotate
    boolean a = RotationPolicy.shouldRotateForExport(false, "baked", 90);
    // Page B: disk baked, deg=90 -> should NOT rotate
    boolean b = RotationPolicy.shouldRotateForExport(true, "baked", 90);
    assertTrue(a);
    assertFalse(b);
  }

  @Test
  public void diskMetadata_rotatesOnce_whenDegNonZero() {
    assertTrue(RotationPolicy.shouldRotateForExport(true, "metadata", 90));
    assertTrue(RotationPolicy.shouldRotateForExport(true, "metadata", 270));
    assertFalse(RotationPolicy.shouldRotateForExport(true, "metadata", 0));
  }

  @Test
  public void normalization_negativeAndLargeDegrees() {
    // Negative degrees normalize and should rotate for metadata disk
    assertTrue(RotationPolicy.shouldRotateForExport(true, "metadata", -90));
    // Large multiples normalize to 0 -> no rotation
    assertFalse(RotationPolicy.shouldRotateForExport(false, "baked", 720));
    assertFalse(RotationPolicy.shouldRotateForExport(true, "metadata", 1080));
  }
}
