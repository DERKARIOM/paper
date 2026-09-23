package de.schliweb.makeacopy.ui.export;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.utils.image.RotationPolicy;
import org.junit.Test;

/**
 * JVM-only seam tests for the single-JPEG export path invariant.
 *
 * <p>Context: - In the single-JPEG export flow, `ExportFragment` uses the already oriented
 * preview/document bitmap for encoding and must NOT rotate it again. - This seam encodes that
 * invariant and documents expected behavior explicitly without requiring Android runtime or
 * bitmaps.
 */
public class SingleJpegDecisionSeamTest {

  /**
   * Minimal decision seam mirroring ExportFragment's single-JPEG behavior: If the preview/document
   * bitmap is already oriented for display, never rotate again. Otherwise, fall back to the general
   * export policy for in-memory sources.
   */
  private static boolean shouldRotateForSingleJpeg(
      boolean previewAlreadyOriented, String orientationMode, int rotationDeg) {
    if (previewAlreadyOriented) return false;
    // Fallback to generic decision for in-memory sources (loadedFromFile=false)
    return RotationPolicy.shouldRotateForExport(false, orientationMode, rotationDeg);
  }

  @Test
  public void alreadyOriented_preview_neverRotates_evenIfDegNonZero() {
    assertFalse(shouldRotateForSingleJpeg(true, "baked", 90));
    assertFalse(shouldRotateForSingleJpeg(true, "metadata", 270));
    assertFalse(shouldRotateForSingleJpeg(true, null, -90));
  }

  @Test
  public void notOriented_yet_followInMemoryPolicy() {
    // When not already oriented, behave like in-memory export policy
    assertTrue(shouldRotateForSingleJpeg(false, "baked", 90));
    assertTrue(shouldRotateForSingleJpeg(false, "metadata", 180));
    assertFalse(shouldRotateForSingleJpeg(false, "metadata", 0));
  }

  @Test
  public void normalization_cases() {
    // 360 -> 0 => no rotation regardless of mode when in-memory policy applies
    assertFalse(shouldRotateForSingleJpeg(false, "baked", 360));
    // -90 normalizes to 270 -> would rotate under in-memory policy
    assertTrue(shouldRotateForSingleJpeg(false, null, -90));
    // When preview is already oriented, still never rotate
    assertFalse(shouldRotateForSingleJpeg(true, "baked", 360));
    assertFalse(shouldRotateForSingleJpeg(true, "metadata", -90));
  }
}
