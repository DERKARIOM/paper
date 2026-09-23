/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the back cameras exposed to the app via CameraX and selects an explicit camera for a
 * requested zoom ratio (issue #75). Some devices do not switch to a dedicated telephoto lens when
 * only {@code CONTROL_ZOOM_RATIO} is set on the logical back camera; in that case the telephoto
 * camera must be bound explicitly via its own {@link CameraSelector}.
 *
 * <p>The relative zoom factor of each back camera is derived from its focal length and physical
 * sensor size, normalized to the default back camera (factor 1.0 = main lens). All failures are
 * swallowed; callers fall back to {@link CameraSelector#DEFAULT_BACK_CAMERA}.
 */
final class CameraLensSelector {
  /** Minimum relative zoom factor for a camera to be treated as a dedicated telephoto lens. */
  private static final float MIN_TELE_FACTOR = 1.2f;

  /** Tolerance when matching the requested zoom ratio against a lens factor. */
  private static final float FACTOR_TOLERANCE = 0.05f;

  private CameraLensSelector() {}

  /** A back camera exposed by CameraX with its zoom factor relative to the default back camera. */
  static final class LensInfo {
    final String cameraId;
    final float zoomFactor;

    LensInfo(String cameraId, float zoomFactor) {
      this.cameraId = cameraId;
      this.zoomFactor = zoomFactor;
    }

    @NonNull
    @Override
    public String toString() {
      return "LensInfo{id=" + cameraId + ", factor=" + zoomFactor + "}";
    }
  }

  /**
   * Enumerates all back cameras bindable via CameraX and computes their zoom factor relative to the
   * default back camera (the first back camera in the provider's list, which is the camera {@link
   * CameraSelector#DEFAULT_BACK_CAMERA} binds).
   */
  @OptIn(markerClass = ExperimentalCamera2Interop.class)
  static List<LensInfo> resolveBackLenses(@NonNull ProcessCameraProvider provider) {
    List<LensInfo> result = new ArrayList<>();
    try {
      Float baseline = null;
      for (CameraInfo info : provider.getAvailableCameraInfos()) {
        try {
          Camera2CameraInfo c2 = Camera2CameraInfo.from(info);
          Integer facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
          if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
          float[] focals =
              c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
          SizeF sensor =
              c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
          if (focals == null || focals.length == 0 || sensor == null || sensor.getWidth() <= 0f) {
            continue;
          }
          float fov = focals[0] / (float) Math.hypot(sensor.getWidth(), sensor.getHeight());
          if (baseline == null) baseline = fov;
          result.add(new LensInfo(c2.getCameraId(), fov / baseline));
        } catch (Throwable ignore) {
          // Skip cameras whose characteristics cannot be read; diagnostics only
        }
      }
    } catch (Throwable ignore) {
      // Lens resolution is best-effort; callers fall back to the default back camera
    }
    return result;
  }

  /**
   * Picks the dedicated telephoto lens best matching the requested zoom ratio, or {@code null} if
   * the default back camera should be used (no tele lens exposed, or the requested ratio is below
   * the smallest tele factor).
   */
  @Nullable
  static LensInfo chooseLens(@Nullable List<LensInfo> lenses, float requestedRatio) {
    if (lenses == null || requestedRatio < MIN_TELE_FACTOR) return null;
    LensInfo best = null;
    for (LensInfo lens : lenses) {
      if (lens.zoomFactor < MIN_TELE_FACTOR) continue;
      if (lens.zoomFactor > requestedRatio + FACTOR_TOLERANCE) continue;
      if (best == null || lens.zoomFactor > best.zoomFactor) best = lens;
    }
    return best;
  }

  /**
   * Builds a selector that binds exactly the given back camera id. If the id cannot be matched
   * during filtering, the filter falls back to all candidates so binding never fails because of the
   * lens selection.
   */
  @OptIn(markerClass = ExperimentalCamera2Interop.class)
  static CameraSelector buildSelector(@NonNull String cameraId) {
    return new CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .addCameraFilter(
            cameraInfos -> {
              List<CameraInfo> matches = new ArrayList<>();
              for (CameraInfo info : cameraInfos) {
                try {
                  if (cameraId.equals(Camera2CameraInfo.from(info).getCameraId())) {
                    matches.add(info);
                  }
                } catch (Throwable ignore) {
                  // Skip cameras whose id cannot be read
                }
              }
              return matches.isEmpty() ? cameraInfos : matches;
            })
        .build();
  }
}
