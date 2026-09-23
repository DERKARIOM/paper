package de.schliweb.makeacopy.a11y.util;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import org.junit.Before;
import org.junit.Rule;

/**
 * Base class enabling Google Accessibility Test Framework (ATF) checks for all instrumented UI
 * tests that extend it. This helps catch missing content descriptions, small touch targets,
 * improper roles, etc. Also grants CAMERA permission to avoid runtime permission dialogs during
 * tests.
 */
public abstract class A11yBaseTest {

  // Grant camera permission so FragmentScenario launches don't trigger a dialog
  @Rule
  public GrantPermissionRule grantCamera =
      GrantPermissionRule.grant(android.Manifest.permission.CAMERA);

  @Before
  public void enableAccessibilityChecks() {
    // Espresso requires animations/transitions to be disabled, otherwise UI actions can be flaky
    // or blocked entirely (see Espresso setup docs).
    disableSystemAnimationsBestEffort();

    // Use reflection to avoid compile-time dependency issues if ATF is not on classpath.
    try {
      Class<?> cls =
          Class.forName(
              "com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityChecks");
      java.lang.reflect.Method enable = cls.getMethod("enable");
      Object cfg = enable.invoke(null);
      try {
        java.lang.reflect.Method setRun =
            cfg.getClass().getMethod("setRunChecksFromRootView", boolean.class);
        setRun.invoke(cfg, true);
      } catch (Throwable ignored2) {
        // Method may not exist on older versions; ignore.
      }
    } catch (Throwable ignored) {
      // Be resilient if the framework is not available for any reason.
    }
  }

  private static void disableSystemAnimationsBestEffort() {
    try {
      var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
      // NOTE: `executeShellCommand` works on emulators/devices in instrumentation context.
      uiAutomation.executeShellCommand("settings put global window_animation_scale 0");
      uiAutomation.executeShellCommand("settings put global transition_animation_scale 0");
      uiAutomation.executeShellCommand("settings put global animator_duration_scale 0");
    } catch (Throwable ignored) {
      // Best-effort only.
    }
  }
}
