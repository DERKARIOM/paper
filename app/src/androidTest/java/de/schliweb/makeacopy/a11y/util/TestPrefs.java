package de.schliweb.makeacopy.a11y.util;

import android.content.Context;

public final class TestPrefs {
  private TestPrefs() {}

  public static void setAccessibilityMode(Context ctx, boolean enabled) {
    android.content.SharedPreferences prefs =
        ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
    prefs
        .edit()
        .putBoolean(
            de.schliweb.makeacopy.ui.camera.CameraOptionsDialogFragment.BUNDLE_ACCESSIBILITY_MODE,
            enabled)
        .apply();
  }

  public static void setAnalysisEnabled(Context ctx, boolean enabled) {
    android.content.SharedPreferences prefs =
        ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
    prefs.edit().putBoolean("analysis_enabled", enabled).apply();
  }
}
