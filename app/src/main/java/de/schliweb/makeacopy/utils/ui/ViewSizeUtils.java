/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ui;

import android.view.View;
import android.widget.ImageView;
import lombok.experimental.UtilityClass;

/**
 * Utility class for handling view size operations. This class provides methods to retrieve the
 * width and height of views or default values if the sizes are not available. It also includes a
 * method to retrieve both dimensions for an ImageView in one call.
 */
@UtilityClass
public class ViewSizeUtils {

  /**
   * Returns the current width of the provided view if it is available and greater than 0; otherwise
   * returns the specified default value.
   *
   * @param v The view whose width is to be retrieved. If null, the default value is returned.
   * @param def The default width to return if the view is null or its width is unavailable.
   * @return The width of the view if available and greater than 0, otherwise the default value.
   */
  public static int widthOrDefault(View v, int def) {
    if (v == null) return def;
    int w = 0;
    try {
      w = v.getWidth();
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return (w > 0) ? w : def;
  }

  /**
   * Returns the current height of the provided view if it is available and greater than 0;
   * otherwise returns the specified default value.
   *
   * @param v The view whose height is to be retrieved. If null, the default value is returned.
   * @param def The default height to return if the view is null or its height is unavailable.
   * @return The height of the view if available and greater than 0, otherwise the default value.
   */
  public static int heightOrDefault(View v, int def) {
    if (v == null) return def;
    int h = 0;
    try {
      h = v.getHeight();
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return (h > 0) ? h : def;
  }

  /**
   * Returns the width and height of the given ImageView as an array. If the dimensions are not
   * available (e.g., layout has not been performed), the provided default values are used instead.
   *
   * @param imageView The ImageView whose width and height are to be obtained. If null, the default
   *     dimensions will be returned.
   * @param defW The default width to use if the ImageView's width is unavailable or invalid.
   * @param defH The default height to use if the ImageView's height is unavailable or invalid.
   * @return An array of two integers, where the first element is the width and the second element
   *     is the height.
   */
  public static int[] sizeOrDefault(ImageView imageView, int defW, int defH) {
    int w = widthOrDefault(imageView, defW);
    int h = heightOrDefault(imageView, defH);
    return new int[] {w, h};
  }
}
