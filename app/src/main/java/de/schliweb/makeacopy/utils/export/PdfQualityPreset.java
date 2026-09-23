/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.export;

/**
 * Represents a set of predefined quality presets for generating PDF files. Each preset defines
 * specific settings for the target DPI, JPEG compression quality, and whether to force the output
 * to grayscale.
 */
public enum PdfQualityPreset {
  HIGH(300, 85, false),
  STANDARD(200, 72, false),
  SMALL(150, 62, false),
  VERY_SMALL(110, 52, false);

  public final int targetDpi;
  public final int jpegQuality; // 0..100
  public final boolean forceGrayscale;

  /**
   * Constructs a new PdfQualityPreset with specified settings for target DPI, JPEG quality, and
   * grayscale mode.
   *
   * @param targetDpi The target DPI (dots per inch) for the PDF. Defines the resolution for images
   *     in the PDF.
   * @param jpegQuality The JPEG compression quality (0-100) for images in the PDF. Higher values
   *     indicate better quality.
   * @param forceGrayscale A boolean flag indicating whether the PDF output should be forced to
   *     grayscale.
   */
  PdfQualityPreset(int targetDpi, int jpegQuality, boolean forceGrayscale) {
    this.targetDpi = targetDpi;
    this.jpegQuality = jpegQuality;
    this.forceGrayscale = forceGrayscale;
  }

  /**
   * Converts the given name to a corresponding {@link PdfQualityPreset} instance. If the name is
   * null or does not match any predefined preset, the provided default {@link PdfQualityPreset} is
   * returned.
   *
   * @param name The name of the preset to retrieve. This string is case-insensitive and trimmed
   *     before matching against the preset names.
   * @param def The default {@link PdfQualityPreset} to return if the name is null or invalid.
   * @return The matching {@link PdfQualityPreset} if the name is valid; otherwise, the provided
   *     default preset.
   */
  public static PdfQualityPreset fromName(String name, PdfQualityPreset def) {
    if (name == null) return def;
    try {
      return PdfQualityPreset.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return def;
    }
  }
}
