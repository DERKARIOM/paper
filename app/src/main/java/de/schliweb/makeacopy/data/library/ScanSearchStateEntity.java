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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;

/** Tracks rebuildable OCR search indexing state for a scan. */
@AllArgsConstructor
@Entity(
    tableName = "scan_search_state",
    foreignKeys =
        @ForeignKey(
            entity = ScanEntity.class,
            parentColumns = "id",
            childColumns = "scanId",
            onDelete = ForeignKey.CASCADE),
    indices = @Index("scanId"))
public class ScanSearchStateEntity {
  @PrimaryKey @NonNull public String scanId;

  public String state;

  public Long lastIndexedAt;

  public String lastError;
}
