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
import android.graphics.BitmapFactory;
import lombok.experimental.UtilityClass;

/**
 * A utility class for decoding and resizing bitmap images to optimize memory usage. This class
 * provides methods to decode images from files while scaling them down based on the required
 * dimensions.
 *
 * <p>This class is not intended to be instantiated.
 */
@UtilityClass
public final class ImageDecodeUtils {
  /**
   * Decodes a bitmap from a given file path, scaling it down to approximately match the specified
   * width and height. This method helps reduce memory usage by decoding the image at a lower
   * resolution if the full size is not required.
   *
   * @param path The file path of the image to decode. Must not be null.
   * @param reqW The target width of the bitmap after scaling. Must be greater than 0.
   * @param reqH The target height of the bitmap after scaling. Must be greater than 0.
   * @return A scaled down Bitmap object if decoding is successful, or null if an error occurs.
   */
  public static Bitmap decodeSampled(String path, int reqW, int reqH) {
    try {
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, opts);
      int inSampleSize = 1;
      int halfH = Math.max(1, opts.outHeight) / 2;
      int halfW = Math.max(1, opts.outWidth) / 2;
      while ((halfH / inSampleSize) >= Math.max(1, reqH)
          && (halfW / inSampleSize) >= Math.max(1, reqW)) {
        inSampleSize *= 2;
      }
      BitmapFactory.Options real = new BitmapFactory.Options();
      real.inSampleSize = Math.max(1, inSampleSize);
      real.inPreferredConfig = Bitmap.Config.RGB_565;
      return BitmapFactory.decodeFile(path, real);
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Decodes an image from file at original resolution without applying EXIF-based rotation.
   * Intended for baked files where visual orientation on disk is already upright. Returns null on
   * failure.
   */
  public static Bitmap decodeFull(String path) {
    try {
      BitmapFactory.Options opts = new BitmapFactory.Options();
      // Keep defaults; do not apply any extra transformations
      return BitmapFactory.decodeFile(path, opts);
    } catch (Throwable t) {
      return null;
    }
  }
}
