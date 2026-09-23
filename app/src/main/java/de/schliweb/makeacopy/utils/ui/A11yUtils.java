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

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import lombok.experimental.UtilityClass;

/**
 * Centralized Accessibility helper.
 *
 * <p>Provides a single place to emit spoken announcements without scattering platform-specific or
 * deprecated API calls throughout the codebase. As of current platform guidance, we rely on
 * View.announceForAccessibility, confined to this utility only.
 */
@UtilityClass
public final class A11yUtils {
  /** Optional test hook to capture announcements in androidTest. */
  public interface AnnounceListener {
    void onAnnounce(CharSequence text);
  }

  // Not synchronized: single-threaded usage on main thread in app/tests.
  private static volatile AnnounceListener sAnnounceListener;

  /** Sets or clears the announce listener (androidTest uses this). */
  public static void setAnnounceListener(AnnounceListener l) {
    sAnnounceListener = l;
  }

  /**
   * Makes an accessibility announcement using the specified view and text. Ensures that any
   * platform-specific or deprecated APIs are handled centrally to reduce code duplication and
   * improve maintainability.
   *
   * @param view the View from which the accessibility announcement will originate; must not be
   *     null.
   * @param text the text to be announced for accessibility purposes; must not be null or empty.
   */
  public static void announce(View view, CharSequence text) {
    if (view == null || text == null || text.length() == 0) return;
    // Test hook: when installed, always notify the listener (even if A11y is disabled on device)
    AnnounceListener listener = sAnnounceListener;
    if (listener != null) {
      try {
        listener.onAnnounce(text);
      } catch (Exception ignored) {
        // Best-effort; failure is non-critical
      }
      // Do not return early: still attempt a real announcement for end-to-end coverage when
      // possible
    }

    Context ctx = view.getContext();
    if (ctx == null) return;

    AccessibilityManager am =
        (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (am == null || !am.isEnabled()) return;
    // Single, centralized announce to confine any deprecations here only.
    legacyAnnounce(view, text);
  }

  @SuppressWarnings("deprecation")
  private static void legacyAnnounce(View view, CharSequence text) {
    try {
      view.announceForAccessibility(text);
    } catch (Exception ignored) {
      // Best-effort; failure is non-critical
    }
  }
}
