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
import android.os.Build;
import android.util.Log;
import lombok.experimental.UtilityClass;

/**
 * Centralized helper for short haptic signals with modern APIs.
 *
 * <p>minSdk = 29 → We can simplify to two paths only: - Android S+ (31+): VibratorManager +
 * VibrationEffect - Android O–R (26..30): Vibrator + VibrationEffect
 */
@UtilityClass
public final class HapticsUtils {
  private static final String HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled";

  /** Optional test hook to observe vibrations in androidTest. */
  public interface TestListener {
    void onVibrate(long durationMs);
  }

  private static volatile TestListener sTestListener;

  /** Sets or clears the test listener (used by androidTest). */
  public static void setTestListener(TestListener l) {
    sTestListener = l;
  }

  /**
   * Triggers a single haptic vibration of the specified duration on devices running Android O (API
   * 26) and above. On Android S (API 31) and later, the vibration is handled using VibratorManager.
   *
   * <p>Respects the system "haptic feedback" preference: if the user disabled haptic feedback in
   * the system sound/vibration settings, no vibration is emitted.
   *
   * @param ctx the context used to retrieve the Vibrator service. If null, the method does nothing.
   * @param durationMs the duration of the vibration in milliseconds. Values less than 1 millisecond
   *     will be treated as 1 millisecond.
   */
  public static void vibrateOneShot(Context ctx, long durationMs) {
    if (ctx == null) return;
    long d = Math.max(1L, durationMs);
    try {
      // Notify tests first; keep real vibration for end-to-end when device allows it
      TestListener tl = sTestListener;
      if (tl != null) {
        try {
          tl.onVibrate(d);
        } catch (Exception e) {
          Log.w("HapticsUtils", "TestListener.onVibrate failed", e);
        }
      }

      // Respect the user's system-wide haptic feedback preference
      if (!isHapticFeedbackEnabled(ctx)) return;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.os.VibratorManager vm =
            (android.os.VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        if (vm == null) return;
        android.os.Vibrator vib = vm.getDefaultVibrator();
        android.os.VibrationEffect effect =
            android.os.VibrationEffect.createOneShot(
                d, android.os.VibrationEffect.DEFAULT_AMPLITUDE);
        vib.vibrate(effect);
        return;
      }
      // minSdk >= 29 ⇒ we are guaranteed to be on O+ here; prefer class-based lookup to avoid
      // deprecation
      android.os.Vibrator vib = ctx.getSystemService(android.os.Vibrator.class);
      if (vib == null) return;
      android.os.VibrationEffect effect =
          android.os.VibrationEffect.createOneShot(d, android.os.VibrationEffect.DEFAULT_AMPLITUDE);
      vib.vibrate(effect);
    } catch (Exception e) {
      Log.w("HapticsUtils", "Vibration failed", e);
    }
  }

  /**
   * Returns whether the user's system-wide "haptic feedback" setting is enabled. Defaults to
   * enabled if the setting cannot be read.
   */
  private static boolean isHapticFeedbackEnabled(Context ctx) {
    try {
      return android.provider.Settings.System.getInt(
              ctx.getContentResolver(), HAPTIC_FEEDBACK_ENABLED, 1)
          != 0;
    } catch (Exception e) {
      Log.w("HapticsUtils", "Reading HAPTIC_FEEDBACK_ENABLED failed", e);
      return true;
    }
  }
}
