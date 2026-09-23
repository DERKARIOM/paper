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

import java.util.ArrayList;
import java.util.List;

final class CameraZoomOptions {
  static final float DEFAULT_ZOOM_RATIO = 1.0f;
  private static final float MAX_PRESET_ZOOM_RATIO = 5.0f;

  private CameraZoomOptions() {}

  static float normalize(float requested, float minZoomRatio, float maxZoomRatio) {
    if (Float.isNaN(requested) || requested <= 0f) return DEFAULT_ZOOM_RATIO;
    float min = Math.max(0.1f, minZoomRatio);
    float max = Math.max(min, maxZoomRatio);
    return Math.max(min, Math.min(requested, max));
  }

  static float[] buildPresetRatios(float minZoomRatio, float maxZoomRatio) {
    float min = Math.max(0.1f, minZoomRatio);
    float max = Math.max(min, Math.min(maxZoomRatio, MAX_PRESET_ZOOM_RATIO));
    float[] candidates = new float[] {1.0f, 2.0f, 3.0f, 5.0f};
    List<Float> ratios = new ArrayList<>();
    for (float candidate : candidates) {
      if (candidate >= min && candidate <= max) ratios.add(candidate);
    }
    if (ratios.isEmpty() || Math.abs(ratios.get(0) - min) > 0.05f) ratios.add(0, min);
    if (Math.abs(ratios.get(ratios.size() - 1) - max) > 0.05f) ratios.add(max);

    float[] result = new float[ratios.size()];
    for (int i = 0; i < ratios.size(); i++) result[i] = ratios.get(i);
    return result;
  }

  static int indexOfClosest(float[] ratios, float selectedRatio) {
    if (ratios == null || ratios.length == 0) return -1;
    int best = 0;
    float bestDistance = Math.abs(ratios[0] - selectedRatio);
    for (int i = 1; i < ratios.length; i++) {
      float distance = Math.abs(ratios[i] - selectedRatio);
      if (distance < bestDistance) {
        best = i;
        bestDistance = distance;
      }
    }
    return best;
  }
}
