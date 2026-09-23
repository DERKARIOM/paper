/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.data.library;

import androidx.annotation.Nullable;
import java.util.UUID;

/**
 * Represents metadata for a scanned index. This class encapsulates essential details about a scan,
 * such as its identifier, title, creation time, number of pages, and optional additional metadata.
 *
 * <p>Instances of this class are typically associated with scanned documents and their metadata as
 * stored or processed by the application.
 *
 * <p>Fields: - id: A unique, stable identifier for the scan. If not provided during instantiation,
 * a new UUID is generated. - title: The human-readable title of the scan. - createdAt: The
 * timestamp in milliseconds since the epoch when the scan was created. - pageCount: The total
 * number of pages in the scan. - coverPath: An optional path or URI to the cover image or thumbnail
 * of the scan. - exportPathsJson: An optional JSON-encoded string containing paths to the exported
 * files. - sourceMetaJson: An optional JSON-encoded string containing the source metadata.
 *
 * <p>This class serves as a lightweight data holder that might be used in conjunction with other
 * application components (e.g., data transfer, entities, or processing tools).
 *
 * @param id stable ID; if null provided, a new UUID will be generated
 */
public record ScanIndexMeta(
    String id,
    String title,
    long createdAt,
    int pageCount,
    @Nullable String coverPath,
    @Nullable String exportPathsJson,
    @Nullable String sourceMetaJson) {
  public ScanIndexMeta(
      String id,
      String title,
      long createdAt,
      int pageCount,
      @Nullable String coverPath,
      @Nullable String exportPathsJson,
      @Nullable String sourceMetaJson) {
    this.id = (id != null && !id.isEmpty()) ? id : UUID.randomUUID().toString();
    this.title = title;
    this.createdAt = createdAt;
    this.pageCount = pageCount;
    this.coverPath = coverPath;
    this.exportPathsJson = exportPathsJson;
    this.sourceMetaJson = sourceMetaJson;
  }
}
