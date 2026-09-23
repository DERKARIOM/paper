/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Persists the user's last {@link CropAspectRatio} selection and the optional custom (w:h) values.
 *
 * <p>Stored in a dedicated {@code crop_options} {@link SharedPreferences} file to keep the crop
 * domain strictly separated from the export domain ({@code export_options} / {@code PageFormat}).
 *
 * <p>See {@code docs/aspect_ratio_concept_v3.8.0.md} (§4).
 */
public final class CropPrefsHelper {

  static final String PREFS_NAME = "crop_options";
  static final String KEY_ASPECT = "aspect";
  static final String KEY_CUSTOM_W = "aspect_custom_w";
  static final String KEY_CUSTOM_H = "aspect_custom_h";
  static final String KEY_SNAP_RIGHT_ANGLE = "snap_right_angle";

  /** Lower bound for {@code min(w,h)/max(w,h)} of a custom ratio (i.e., up to 1:20). */
  static final double CUSTOM_MIN_RATIO = 0.05;

  private CropPrefsHelper() {}

  private static SharedPreferences prefs(@NonNull Context ctx) {
    return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  /** Returns the last selected aspect, defaulting to {@link CropAspectRatio#AUTO}. */
  @NonNull
  public static CropAspectRatio getLastAspect(@NonNull Context ctx) {
    String name = prefs(ctx).getString(KEY_ASPECT, null);
    return CropAspectRatio.fromName(name, CropAspectRatio.AUTO);
  }

  /** Persists the selected aspect. */
  public static void setLastAspect(@NonNull Context ctx, @NonNull CropAspectRatio v) {
    prefs(ctx).edit().putString(KEY_ASPECT, v.name()).apply();
  }

  /**
   * Returns the persisted custom ratio as {@code {w, h}} in raw form, or {@code null} if none has
   * been stored or the stored values are invalid.
   */
  @Nullable
  public static double[] getCustomRatio(@NonNull Context ctx) {
    SharedPreferences p = prefs(ctx);
    if (!p.contains(KEY_CUSTOM_W) || !p.contains(KEY_CUSTOM_H)) return null;
    float w = p.getFloat(KEY_CUSTOM_W, 0f);
    float h = p.getFloat(KEY_CUSTOM_H, 0f);
    if (!isValidCustom(w, h)) return null;
    return new double[] {w, h};
  }

  /**
   * Persists a custom (w:h) ratio. Invalid pairs (zero/negative values or extreme aspect ratios
   * beyond 1:20) are rejected and the call is a no-op returning {@code false}.
   */
  public static boolean setCustomRatio(@NonNull Context ctx, double w, double h) {
    if (!isValidCustom(w, h)) return false;
    prefs(ctx).edit().putFloat(KEY_CUSTOM_W, (float) w).putFloat(KEY_CUSTOM_H, (float) h).apply();
    return true;
  }

  /**
   * Resolves the active short/long ratio for the current selection. Returns {@code null} when stage
   * A (projective estimate) or the legacy heuristic should run instead.
   *
   * <ul>
   *   <li>{@link CropAspectRatio#AUTO} → {@code null} (stage A: projective estimate)
   *   <li>{@link CropAspectRatio#ORIGINAL} → {@code null} (callers must invoke the legacy path)
   *   <li>fixed entries → {@link CropAspectRatio#shortOverLong()}
   *   <li>{@link CropAspectRatio#CUSTOM} → {@code min(w,h) / max(w,h)} or {@code null} when the
   *       persisted custom value is missing/invalid
   * </ul>
   */
  @Nullable
  public static Double resolveActiveRatio(@NonNull Context ctx) {
    CropAspectRatio sel = getLastAspect(ctx);
    if (sel == CropAspectRatio.CUSTOM) {
      double[] wh = getCustomRatio(ctx);
      if (wh == null) return null;
      double mn = Math.min(wh[0], wh[1]);
      double mx = Math.max(wh[0], wh[1]);
      if (mx <= 0.0) return null;
      return mn / mx;
    }
    return sel.shortOverLong();
  }

  /**
   * Returns the persisted enabled-state for the Snap-to-Right-Angle assist. Defaults to {@code
   * false} when no value has been stored yet.
   */
  public static boolean getSnapRightAngleEnabled(@NonNull Context ctx) {
    return prefs(ctx).getBoolean(KEY_SNAP_RIGHT_ANGLE, false);
  }

  /** Persists the enabled-state for the Snap-to-Right-Angle assist. */
  public static void setSnapRightAngleEnabled(@NonNull Context ctx, boolean enabled) {
    prefs(ctx).edit().putBoolean(KEY_SNAP_RIGHT_ANGLE, enabled).apply();
  }

  /** True iff the supplied (w, h) form a valid custom ratio (>0 and within 1:1 .. 1:20). */
  public static boolean isValidCustom(double w, double h) {
    if (!(w > 0.0) || !(h > 0.0)) return false;
    if (Double.isNaN(w) || Double.isNaN(h) || Double.isInfinite(w) || Double.isInfinite(h)) {
      return false;
    }
    double mn = Math.min(w, h);
    double mx = Math.max(w, h);
    double r = mn / mx;
    return r >= CUSTOM_MIN_RATIO && r <= 1.0;
  }
}
