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

/** Simple options for offline OpenCV-based document cleanup. */
public class DocumentCleanupOptions {
  public static final int STRENGTH_LOW = 0;
  public static final int STRENGTH_MEDIUM = 1;
  public static final int STRENGTH_HIGH = 2;

  public DocumentCleanupMode mode = DocumentCleanupMode.ORIGINAL;
  public int strength = STRENGTH_MEDIUM;
  public boolean preserveColor = true;
  public boolean optimizeForOcr = false;
  public int targetLongEdgePx = 0;

  public DocumentCleanupOptions() {}

  public DocumentCleanupOptions(DocumentCleanupMode mode) {
    this.mode = mode == null ? DocumentCleanupMode.ORIGINAL : mode;
  }

  public DocumentCleanupOptions(
      DocumentCleanupMode mode,
      int strength,
      boolean preserveColor,
      boolean optimizeForOcr,
      int targetLongEdgePx) {
    this.mode = mode == null ? DocumentCleanupMode.ORIGINAL : mode;
    this.strength = clampStrength(strength);
    this.preserveColor = preserveColor;
    this.optimizeForOcr = optimizeForOcr;
    this.targetLongEdgePx = Math.max(0, targetLongEdgePx);
  }

  public static DocumentCleanupOptions original() {
    return new DocumentCleanupOptions(DocumentCleanupMode.ORIGINAL);
  }

  public static DocumentCleanupOptions natural() {
    return new DocumentCleanupOptions(DocumentCleanupMode.NATURAL, STRENGTH_LOW, true, false, 0);
  }

  public static DocumentCleanupOptions enhanced(boolean preserveColor) {
    return new DocumentCleanupOptions(
        DocumentCleanupMode.ENHANCED, STRENGTH_MEDIUM, preserveColor, false, 0);
  }

  public static DocumentCleanupOptions cleanText() {
    return new DocumentCleanupOptions(
        DocumentCleanupMode.CLEAN_TEXT, STRENGTH_HIGH, false, true, 0);
  }

  public static int clampStrength(int strength) {
    return Math.max(STRENGTH_LOW, Math.min(STRENGTH_HIGH, strength));
  }
}
