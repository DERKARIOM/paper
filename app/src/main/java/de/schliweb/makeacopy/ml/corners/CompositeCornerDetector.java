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

/**
 * Deterministischer Orchestrator.
 *
 * <p>Policy: - Erst DocQuad - Dann Legacy (OpenCV-only)
 */
public final class CompositeCornerDetector implements CornerDetector {

  private final CornerDetector docQuad;
  private final CornerDetector legacy;

  public CompositeCornerDetector(CornerDetector docQuad, CornerDetector legacy) {
    this.docQuad = docQuad;
    this.legacy = legacy;
  }

  @Override
  public DetectionResult detect(Bitmap src, Context ctx) {
    DetectionResult r1 = null;
    try {
      r1 = docQuad.detect(src, ctx);
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    if (r1 != null && r1.success) return r1;

    DetectionResult r2 = null;
    try {
      r2 = legacy.detect(src, ctx);
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    if (r2 != null && r2.success) return r2;
    return DetectionResult.fail(Source.FALLBACK);
  }
}
