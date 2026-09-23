/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export.session;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;

/**
 * Represents a completed scan, encapsulating all necessary data about the scan result and its
 * metadata.
 *
 * <p>Instances of this class provide information such as the scan's unique identifier, file paths
 * for the scanned document and associated data, orientation details, dimensions, and creation
 * timestamp. An optional in-memory bitmap reference can be included for use in UI operations, such
 * as rendering thumbnails.
 *
 * <p>Fields: - id: A unique identifier for the scan (e.g., UUID string). - filePath: An optional
 * file path to the scanned document. - rotationDeg: The rotation angle of the scan in degrees
 * (valid values are 0, 90, 180, 270). - ocrTextPath: An optional file path to the OCR (Optical
 * Character Recognition) text data for the scan. - thumbPath: An optional file path to a thumbnail
 * version of the scan. - createdAt: The timestamp (in milliseconds) when the scan was created. -
 * widthPx: The width of the scanned image in pixels. - heightPx: The height of the scanned image in
 * pixels. - inMemoryBitmap: An optional in-memory bitmap used for UI thumbnails or other runtime
 * purposes.
 *
 * @param id could be UUID string
 * @param filePath optional until registry is added
 * @param rotationDeg 0, 90, 180, 270
 * @param ocrTextPath not used in v1 increment
 * @param thumbPath not used in v1 increment
 * @param inMemoryBitmap Convenience: keep a reference to the in-memory bitmap for v1 (UI
 *     thumbnails)
 */
public record CompletedScan(
    String id,
    @Nullable String filePath,
    int rotationDeg,
    @Nullable String ocrTextPath,
    @Nullable String ocrFormat,
    @Nullable String thumbPath,
    long createdAt,
    int widthPx,
    int heightPx,
    @Nullable Bitmap inMemoryBitmap,
    int schemaVersion,
    @Nullable String orientationMode) {
  /**
   * Constructs a CompletedScan object representing a completed scan, encapsulating various metadata
   * and associated information about the scan.
   *
   * @param id A unique identifier for the scan, typically a UUID string.
   * @param filePath An optional file path pointing to the scanned document. May be null if not
   *     available.
   * @param rotationDeg The rotation angle of the scanned document, specified in degrees. Valid
   *     values are 0, 90, 180, or 270.
   * @param ocrTextPath An optional file path pointing to the OCR payload for the scan. May be null
   *     if not available.
   * @param ocrFormat Optional string describing the OCR payload format (e.g., "plain", "hocr",
   *     "alto", "words_json"). May be null to imply "plain" for backward compatibility.
   * @param thumbPath An optional file path pointing to a thumbnail version of the scan. May be null
   *     if not available.
   * @param createdAt The timestamp when the scan was created, specified in milliseconds since the
   *     Unix epoch.
   * @param widthPx The width of the scanned image, measured in pixels.
   * @param heightPx The height of the scanned image, measured in pixels.
   * @param inMemoryBitmap An optional in-memory bitmap representation of the scanned document,
   *     typically used for UI thumbnails. May be null if not available.
   */
  public CompletedScan {
    // Normalize defaults for backward compatibility when callers pass 0/ null
    if (orientationMode == null || orientationMode.isEmpty()) {
      orientationMode = "baked"; // safe default for legacy entries
    }
    if (schemaVersion <= 0) {
      schemaVersion = 1; // legacy entries
    }
  }
}
