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
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;

/**
 * Represents a collection in the database used to group related scans. This entity is part of the
 * Room persistence library and corresponds to a table named "collections" in the SQLite database.
 * Each collection has a unique ID, a name, a sort order for ordering purposes, and a timestamp
 * indicating when it was created.
 *
 * <ul>
 *   Key Properties: - id: Unique identifier for the collection (UUID as a string). - name: Display
 *   name of the collection. - sortOrder: Used to define an explicit ordering of collections. -
 *   createdAt: Epoch timestamp representing the creation time of the collection.
 * </ul>
 *
 * <p>Constructor: Allows manual creation of a CollectionEntity instance by providing all required
 * fields.
 *
 * <p>This entity is commonly used in conjunction with a DAO (Data Access Object), such as
 * CollectionsDao, to perform database operations like insertion, updates, queries, and deletions.
 */
@AllArgsConstructor
@Entity(tableName = "collections")
public class CollectionEntity {
  @PrimaryKey @NonNull public String id; // UUID

  @NonNull public String name;

  public int sortOrder;

  public long createdAt;
}
