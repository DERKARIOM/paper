/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.infra;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Simple helper to provide a stable per-session scan ID so we can persist autosave artifacts (like
 * editable OCR JSON) before the Export screen creates/persists the CompletedScan entry.
 */
@UtilityClass
public class SessionIds {

  private static final String PREFS = "session_ids";
  private static final String KEY_CURRENT_SCAN_ID = "current_scan_id";

  /** Returns an existing current scan id or creates a new UUID, stores it, and returns it. */
  public static String getOrCreateCurrentScanId(Context ctx) {
    if (ctx == null) return UUID.randomUUID().toString();
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String id = sp.getString(KEY_CURRENT_SCAN_ID, null);
    if (id == null || id.trim().isEmpty()) {
      id = UUID.randomUUID().toString();
      sp.edit().putString(KEY_CURRENT_SCAN_ID, id).apply();
    }
    return id;
  }

  /**
   * Allows resetting the current scan id (e.g., after successful export or when starting a new
   * scan).
   */
  public static void resetCurrentScanId(Context ctx) {
    if (ctx == null) return;
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    sp.edit().remove(KEY_CURRENT_SCAN_ID).apply();
  }

  /** Sets the current scan id explicitly (used to align Export session id with Review autosave). */
  public static void setCurrentScanId(Context ctx, String id) {
    if (ctx == null || id == null || id.trim().isEmpty()) return;
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    sp.edit().putString(KEY_CURRENT_SCAN_ID, id).apply();
  }
}
