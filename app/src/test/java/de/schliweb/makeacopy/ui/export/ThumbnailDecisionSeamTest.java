package de.schliweb.makeacopy.ui.export;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.image.RotationPolicy;
import org.junit.Test;

/**
 * JVM-only seam tests for adapter/preview thumbnail rotation decisions. These mirror the logic used
 * by adapters and BitmapUtils via RotationPolicy.
 */
public class ThumbnailDecisionSeamTest {

  @Test
  public void inMemory_rotateWhenDegNonZero() {
    assertTrue(RotationPolicy.shouldRotateForThumbnail(false, "baked", 90));
    assertTrue(RotationPolicy.shouldRotateForThumbnail(false, "metadata", 180));
    assertFalse(RotationPolicy.shouldRotateForThumbnail(false, "baked", 0));
  }

  @Test
  public void disk_baked_neverRotates() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, "baked", 90));
    assertFalse(
        RotationPolicy.shouldRotateForThumbnail(true, null, 270)); // legacy/null treated as baked
  }

  @Test
  public void disk_metadata_rotatesWhenDegNonZero() {
    assertTrue(RotationPolicy.shouldRotateForThumbnail(true, "metadata", 90));
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, "metadata", 0));
  }

  @Test
  public void normalization_degrees() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(false, "baked", 360));
    assertTrue(RotationPolicy.shouldRotateForThumbnail(true, "metadata", -90));
  }
}
