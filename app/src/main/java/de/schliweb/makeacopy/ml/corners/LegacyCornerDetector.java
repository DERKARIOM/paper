/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ml.corners;

import android.content.Context;
import android.graphics.Bitmap;

/** Legacy-Adapter: OpenCV-only Corner-Detection (Phase C: DocAligner entfernt). */
public final class LegacyCornerDetector implements CornerDetector {
  @Override
  public DetectionResult detect(Bitmap src, Context ctx) {
    return new OpenCvCornerDetector().detect(src, ctx);
  }
}
