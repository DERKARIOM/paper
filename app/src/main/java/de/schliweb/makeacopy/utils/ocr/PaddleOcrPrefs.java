/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A utility class for managing application preferences related to the PaddleOCR feature. This class
 * provides methods to check the enablement of the PaddleOCR toggle, update its status, and
 * determine its visibility in the user interface.
 *
 * <p>This class is not intended to be instantiated.
 */
public final class PaddleOcrPrefs {

  /**
   * The name of the shared preferences file used to store application preferences related to the
   * PaddleOCR feature.
   *
   * <p>This file contains settings such as the enablement state of the PaddleOCR toggle. The
   * preferences stored here help manage the behavior and visibility of the experimental PaddleOCR
   * functionality.
   */
  public static final String PREFS_NAME = "export_options";

  /**
   * The key used in the shared preferences file to store and retrieve the enablement state of the
   * PaddleOCR feature toggle. This preference determines whether the PaddleOCR functionality is
   * active.
   *
   * <p>The associated preference value is a boolean flag, where {@code true} indicates that the
   * PaddleOCR feature is enabled, and {@code false} indicates that it is disabled.
   */
  public static final String KEY = "pref_ocr_paddle_enabled";

  private PaddleOcrPrefs() {}

  public static boolean isEnabled(Context context) {
    if (context == null) return false;
    SharedPreferences sp =
        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    return sp.getBoolean(KEY, false);
  }

  public static void setEnabled(Context context, boolean enabled) {
    if (context == null) return;
    context
        .getApplicationContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY, enabled)
        .apply();
  }

  /**
   * Determines whether the PaddleOCR toggle is visible in the user interface.
   *
   * <p>The visibility of the toggle depends on the value of the `FEATURE_PADDLE_OCR` build
   * configuration flag. If the feature is not enabled in the build configuration, the toggle will
   * not be visible.
   *
   * @return {@code true} if the PaddleOCR toggle is visible, {@code false} otherwise.
   */
  public static boolean isToggleVisible() {
    if (!de.schliweb.makeacopy.BuildConfig.FEATURE_PADDLE_OCR) return false;
    return true;
  }
}
