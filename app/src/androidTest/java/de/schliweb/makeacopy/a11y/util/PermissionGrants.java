package de.schliweb.makeacopy.a11y.util;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Test-only helper to grant runtime permissions programmatically before launching UI under test.
 */
public final class PermissionGrants {
  private PermissionGrants() {}

  public static void grantCamera() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    Instrumentation instr = InstrumentationRegistry.getInstrumentation();
    Context app = ApplicationProvider.getApplicationContext();
    String pkg = app.getPackageName();
    UiAutomation ui = instr.getUiAutomation();
    try {
      ui.grantRuntimePermission(pkg, Manifest.permission.CAMERA);
    } catch (SecurityException se) {
      try {
        ui.revokeRuntimePermission(pkg, Manifest.permission.CAMERA);
        ui.grantRuntimePermission(pkg, Manifest.permission.CAMERA);
      } catch (Throwable ignored) {
        // Best effort; on some devices the initial grant is sufficient.
      }
    }
  }
}
