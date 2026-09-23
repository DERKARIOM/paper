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

/** Optional document cleanup presets applied after perspective correction. */
public enum DocumentCleanupMode {
  /** Keep the corrected page unchanged. */
  ORIGINAL,
  /** Mild luminance cleanup while preserving color and a natural appearance. */
  NATURAL,
  /** Stronger background flattening and local contrast for scanner-like output. */
  ENHANCED,
  /** Aggressive readability/OCR-focused cleanup; visual fidelity is secondary. */
  CLEAN_TEXT
}
