package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.image.RotationPolicy;
import org.junit.Test;

/**
 * JVM unit tests for RotationPolicy to lock down rotation invariants for adapter thumbnails and
 * export paths.
 */
public class RotationPolicyTest {

  // ===== Thumbnail policy =====

  @Test
  public void thumbnail_inMemory_deg90_true() {
    assertTrue(RotationPolicy.shouldRotateForThumbnail(false, "baked", 90));
  }

  @Test
  public void thumbnail_inMemory_deg0_false() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(false, "metadata", 0));
  }

  @Test
  public void thumbnail_disk_baked_deg90_false() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, "baked", 90));
  }

  @Test
  public void thumbnail_disk_metadata_deg90_true() {
    assertTrue(RotationPolicy.shouldRotateForThumbnail(true, "metadata", 90));
  }

  @Test
  public void thumbnail_disk_legacyNullMode_deg180_false() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, null, 180));
  }

  // ===== Export policy =====

  @Test
  public void export_inMemory_deg270_true() {
    assertTrue(RotationPolicy.shouldRotateForExport(false, "baked", 270));
  }

  @Test
  public void export_inMemory_deg0_false() {
    assertFalse(RotationPolicy.shouldRotateForExport(false, "metadata", 0));
  }

  @Test
  public void export_disk_baked_deg90_false() {
    assertFalse(RotationPolicy.shouldRotateForExport(true, "baked", 90));
  }

  @Test
  public void export_disk_metadata_deg90_true() {
    assertTrue(RotationPolicy.shouldRotateForExport(true, "metadata", 90));
  }

  @Test
  public void export_negativeDegrees_normalized() {
    assertTrue(RotationPolicy.shouldRotateForExport(true, "metadata", -90));
    assertFalse(RotationPolicy.shouldRotateForExport(true, "baked", -90));
  }

  // ===== Boundary/normalization cases =====

  @Test
  public void thumbnail_degrees360_treatedAsZero() {
    assertFalse(RotationPolicy.shouldRotateForThumbnail(false, "baked", 360));
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, "metadata", 360));
  }

  @Test
  public void export_degrees720_treatedAsZero() {
    assertFalse(RotationPolicy.shouldRotateForExport(false, "metadata", 720));
    assertFalse(RotationPolicy.shouldRotateForExport(true, "metadata", 720));
  }

  @Test
  public void nullOrEmptyMode_treatedAsBaked() {
    // Disk with null/empty mode should act like baked → no rotation
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, null, 90));
    assertFalse(RotationPolicy.shouldRotateForThumbnail(true, "", 180));
    assertFalse(RotationPolicy.shouldRotateForExport(true, null, 90));
    assertFalse(RotationPolicy.shouldRotateForExport(true, "", 180));
  }
}
