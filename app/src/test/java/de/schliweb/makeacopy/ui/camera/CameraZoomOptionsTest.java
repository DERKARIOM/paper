package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraZoomOptionsTest {
  @Test
  public void buildPresetRatios_keepsCommonDocumentZoomLevelsWithinCameraRange() {
    assertArrayEquals(
        new float[] {1.0f, 2.0f, 3.0f, 5.0f},
        CameraZoomOptions.buildPresetRatios(1.0f, 8.0f),
        0.001f);
  }

  @Test
  public void buildPresetRatios_addsDeviceMinimumAndMaximumWhenNoPresetMatchesExactly() {
    assertArrayEquals(
        new float[] {0.6f, 1.0f, 2.0f, 2.4f},
        CameraZoomOptions.buildPresetRatios(0.6f, 2.4f),
        0.001f);
  }

  @Test
  public void normalize_clampsPersistedRatioToCurrentDeviceRange() {
    assertEquals(3.0f, CameraZoomOptions.normalize(4.0f, 1.0f, 3.0f), 0.001f);
    assertEquals(1.0f, CameraZoomOptions.normalize(0.5f, 1.0f, 3.0f), 0.001f);
  }

  @Test
  public void indexOfClosest_selectsNearestRatio() {
    assertEquals(2, CameraZoomOptions.indexOfClosest(new float[] {1.0f, 2.0f, 3.0f}, 2.8f));
  }
}