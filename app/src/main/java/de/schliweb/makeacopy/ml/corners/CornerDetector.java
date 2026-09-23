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
 * Schlanke Abstraktion für Corner-Detection.
 *
 * <p>Implementierungen dürfen niemals Exceptions nach außen werfen, sondern müssen Fehler als
 * {@link DetectionResult#success} zurückgeben.
 */
public interface CornerDetector {
  DetectionResult detect(Bitmap src, Context ctx);
}
