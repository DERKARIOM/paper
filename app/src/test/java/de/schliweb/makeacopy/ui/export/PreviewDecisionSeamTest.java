package de.schliweb.makeacopy.ui.export;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.image.RotationPolicy;
import org.junit.Test;

/**
 * JVM-only seam tests for preview/thumbnail rotation decisions.
 *
 * <p>These tests mirror the logic used by preview loading and adapters by delegating decisions to
 * {@link RotationPolicy#shouldRotateForThumbnail(boolean, String, int)}. No Android runtime or
 * Bitmap instances are required.
 */
public class PreviewDecisionSeamTest {

  /**
   * Minimal decision seam mirroring Bitmap/preview callers: they pass whether the source is from
   * disk or in-memory, along with mode and degrees.
   */
  private static boolean shouldRotateForPreview(boolean fromDisk, String mode, int deg) {
    return RotationPolicy.shouldRotateForThumbnail(fromDisk, mode, deg);
  }

  @Test
  public void inMemory_rotateWhenDegNonZero() {
    assertTrue(shouldRotateForPreview(false, "baked", 90));
    assertTrue(shouldRotateForPreview(false, "metadata", 180));
  }

  @Test
  public void disk_baked_neverRotates() {
    assertFalse(shouldRotateForPreview(true, "baked", 90));
    assertFalse(shouldRotateForPreview(true, null, 180)); // legacy/null treated as baked
    assertFalse(shouldRotateForPreview(true, "", 270)); // empty treated as baked
  }

  @Test
  public void disk_metadata_rotatesWhenDegNonZero() {
    assertTrue(shouldRotateForPreview(true, "metadata", 270));
    assertFalse(shouldRotateForPreview(true, "metadata", 0));
  }

  @Test
  public void normalization_cases() {
    assertFalse(shouldRotateForPreview(false, "baked", 360)); // 360 -> 0
    assertTrue(shouldRotateForPreview(true, "metadata", -90)); // -90 -> 270
  }
}
