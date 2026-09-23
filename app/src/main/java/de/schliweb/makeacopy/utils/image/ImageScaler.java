/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.image;

import android.graphics.Bitmap;
import android.util.Log;
import lombok.experimental.UtilityClass;

/**
 * Utility class for scaling images to predefined dimensions while maintaining aspect ratio.
 *
 * <p>The primary use case for this class is scaling images to fit A4 dimensions at 300 DPI without
 * distortion or stretching, making it useful for generating PDFs with accurate OCR.
 *
 * <p>The class provides a method to ensure that the scaled images match the desired dimensions with
 * high accuracy, enabling better alignment of OCR coordinates.
 *
 * <p>This class is not intended to be instantiated.
 */
@UtilityClass
public class ImageScaler {
  // A4 dimensions at 300 DPI (in pixels)
  public static final int A4_WIDTH_300DPI = 2480;
  public static final int A4_HEIGHT_300DPI = 3508;
  private static final String TAG = "ImageScaler";

  /**
   * Scales a given bitmap image to fit within A4 dimensions at 300 DPI while maintaining the aspect
   * ratio. If the original bitmap is already smaller than or equal to A4 dimensions, no scaling is
   * performed.
   *
   * @param originalBitmap The original bitmap to be scaled. Must not be null.
   * @return A new bitmap scaled to fit within A4 dimensions at 300 DPI, or the original bitmap if
   *     no scaling is needed. Returns null if the input bitmap is null.
   */
  public static Bitmap scaleToA4(Bitmap originalBitmap) {
    if (originalBitmap == null) {
      Log.e(TAG, "scaleToA4: originalBitmap is null");
      return null;
    }

    // Calculate scaling factor to fit the image within A4 dimensions
    float scale = 1.0f;
    if (originalBitmap.getWidth() > A4_WIDTH_300DPI
        || originalBitmap.getHeight() > A4_HEIGHT_300DPI) {
      float scaleWidth = (float) A4_WIDTH_300DPI / originalBitmap.getWidth();
      float scaleHeight = (float) A4_HEIGHT_300DPI / originalBitmap.getHeight();
      scale = Math.min(scaleWidth, scaleHeight);

      Log.d(
          TAG,
          "scaleToA4: Scaling image from "
              + originalBitmap.getWidth()
              + "x"
              + originalBitmap.getHeight()
              + " to fit within A4 dimensions ("
              + A4_WIDTH_300DPI
              + "x"
              + A4_HEIGHT_300DPI
              + ") with scale factor "
              + scale);
    } else {
      Log.d(TAG, "scaleToA4: Image already fits within A4 dimensions, no scaling needed");
      return originalBitmap;
    }

    // Create a scaled bitmap
    int scaledWidth = Math.round(originalBitmap.getWidth() * scale);
    int scaledHeight = Math.round(originalBitmap.getHeight() * scale);

    Bitmap scaledBitmap =
        Bitmap.createScaledBitmap(
            originalBitmap, scaledWidth, scaledHeight, true // Use filtering for better quality
            );

    Log.d(TAG, "scaleToA4: Scaled image to " + scaledWidth + "x" + scaledHeight);

    return scaledBitmap;
  }
}
