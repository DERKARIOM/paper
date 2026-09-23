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
import androidx.room.Index;

/**
 * Represents a many-to-many relationship between scans and collections in the database.
 *
 * <p>This class is used as a join entity in the Room persistence library to associate ScanEntity
 * and CollectionEntity objects. Each association has a primary composite key consisting of a scan
 * ID and a collection ID, and it also tracks the timestamp when the association was created.
 *
 * <p>Annotations: - @Entity: Defines this class as a Room entity for the "scan_collection_join"
 * table. - @PrimaryKey: Specifies "scanId" and "collectionId" as the composite primary key.
 * - @Index: Defines an index on the "collectionId" column to improve query performance.
 *
 * <p>Fields: - scanId: The unique identifier of the ScanEntity associated with this relationship. -
 * collectionId: The unique identifier of the CollectionEntity associated with this relationship. -
 * addedAt: The epoch timestamp when this scan was added to the collection.
 *
 * <p>Constructor: - Initializes the instance with the specified scan ID, collection ID, and added
 * timestamp.
 */
@Entity(
    tableName = "scan_collection_join",
    primaryKeys = {"scanId", "collectionId"},
    indices = {@Index(value = {"collectionId"})})
public class ScanCollectionCrossRef {
  @NonNull public String scanId;

  @NonNull public String collectionId;

  public long addedAt;

  public ScanCollectionCrossRef(
      @NonNull String scanId, @NonNull String collectionId, long addedAt) {
    this.scanId = scanId;
    this.collectionId = collectionId;
    this.addedAt = addedAt;
  }
}
