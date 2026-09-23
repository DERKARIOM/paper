/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.export.jpeg;

import androidx.annotation.IntRange;
import de.schliweb.makeacopy.utils.image.DocumentCleanupMode;

/**
 * Options for JPEG export. Keep fields simple/primitive to stay parcel-agnostic and easy to extend.
 */
public class JpegExportOptions {

  public enum Mode {
    /** No enhancement, optional downscale only. */
    NONE,
    /** Black/White mode optimized for text via Otsu binarization. */
    BW_TEXT
  }

  /** JPEG quality (0..100). Default 85. */
  @IntRange(from = 0, to = 100)
  public int quality = 85;

  /** Target long edge in pixels. 0 = keep original size. Default 0. */
  public int longEdgePx = 0;

  /** Enhancement mode. Default NONE. */
  public Mode mode = Mode.NONE;

  /** Document cleanup mode applied before JPEG-specific export filters. Default ORIGINAL. */
  public DocumentCleanupMode cleanupMode = DocumentCleanupMode.ORIGINAL;

  /**
   * Export as grayscale JPEG (single channel). - Recommended for B/W text to reduce size and chroma
   * artifacts. - The exporter will force grayscale automatically for Mode.BW_TEXT; this flag allows
   * you to force it also for other modes (e.g., AUTO) when desired. Default: false.
   */
  public boolean forceGrayscaleJpeg = false;

  /**
   * Upper guard for downscaling to avoid OOM on huge images. 0 disables the guard. Default 4096
   * (matches exporter’s default).
   */
  public int maxLongEdgeGuardPx = 4096;

  /**
   * When resizing, round target width/height to a multiple of 8 (helps JPEG block alignment).
   * Default: true.
   */
  public boolean roundResizeToMultipleOf8 = true;

  public JpegExportOptions() {}

  public JpegExportOptions(@IntRange(from = 0, to = 100) int quality, int longEdgePx, Mode mode) {
    this.quality = clamp(quality, 0, 100);
    this.longEdgePx = Math.max(0, longEdgePx);
    this.mode = (mode == null) ? Mode.NONE : mode;
  }

  public JpegExportOptions(
      @IntRange(from = 0, to = 100) int quality,
      int longEdgePx,
      Mode mode,
      boolean forceGrayscaleJpeg,
      int maxLongEdgeGuardPx,
      boolean roundResizeToMultipleOf8) {
    this.quality = clamp(quality, 0, 100);
    this.longEdgePx = Math.max(0, longEdgePx);
    this.mode = (mode == null) ? Mode.NONE : mode;
    this.forceGrayscaleJpeg = forceGrayscaleJpeg;
    this.maxLongEdgeGuardPx = Math.max(0, maxLongEdgeGuardPx);
    this.roundResizeToMultipleOf8 = roundResizeToMultipleOf8;
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }
}
