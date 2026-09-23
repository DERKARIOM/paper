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

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;

/** Stores normalized, rebuildable OCR text per scan page for local full-text search. */
@AllArgsConstructor
@Entity(
    tableName = "scan_page_text",
    foreignKeys =
        @ForeignKey(
            entity = ScanEntity.class,
            parentColumns = "id",
            childColumns = "scanId",
            onDelete = ForeignKey.CASCADE),
    indices = {
      @Index(
          value = {"scanId", "pageIndex"},
          unique = true),
      @Index("scanId")
    })
public class ScanPageTextEntity {
  @PrimaryKey(autoGenerate = true)
  public long id;

  public String scanId;

  public int pageIndex;

  public String text;

  public String ocrSourcePath;

  public String ocrSourceFormat;

  public String ocrTextHash;

  public long indexedAt;

  public long updatedAt;
}
