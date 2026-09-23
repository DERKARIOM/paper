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

import lombok.experimental.UtilityClass;

/**
 * Centralized rotation decision helpers used by thumbnails/previews and export paths. Keeping the
 * policy in one place allows simple JVM tests without Android runtime.
 */
@UtilityClass
public final class RotationPolicy {

  /**
   * Returns true if a bitmap should be rotated for UI preview/thumbnail rendering. - In-memory
   * sources: rotate when rotationDeg != 0. - Disk sources: rotate only when orientationMode ==
   * "metadata" and rotationDeg != 0.
   */
  public static boolean shouldRotateForThumbnail(
      boolean fromDisk, String orientationMode, int rotationDeg) {
    int deg = normalize(rotationDeg);
    if (deg == 0) return false;
    boolean isMetadata = orientationMode != null && "metadata".equalsIgnoreCase(orientationMode);
    if (!fromDisk) return true; // in-memory
    return isMetadata; // disk
  }

  /**
   * Returns true if a bitmap should be rotated for export (PDF/ZIP/JPEG) before encoding. Mirrors
   * ExportFragment logic: - If loadedFromFile=false (in-memory) → rotate when deg != 0. - If
   * loadedFromFile=true (disk) → rotate only for metadata entries (deg != 0).
   */
  public static boolean shouldRotateForExport(
      boolean loadedFromFile, String orientationMode, int rotationDeg) {
    int deg = normalize(rotationDeg);
    if (deg == 0) return false;
    boolean isMetadata = orientationMode != null && "metadata".equalsIgnoreCase(orientationMode);
    if (!loadedFromFile) return true;
    return isMetadata;
  }

  private static int normalize(int d) {
    int x = d % 360;
    return (x < 0) ? (x + 360) : x;
  }
}
