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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data access object (DAO) for managing the many-to-many relationship between scans and collections
 * in the database.
 *
 * <p>This interface provides methods to insert, delete, and query associations between scans and
 * collections. It utilizes the Room persistence library to define and execute database operations.
 *
 * <p>Methods: - insert: Adds a new association between a scan and a collection. Ignores the
 * insertion if the association already exists. - remove: Deletes a specific association between a
 * scan and a collection using their IDs. - removeAllForScan: Deletes all associations for a
 * specific scan. - getScanIdsForCollection: Retrieves all scan IDs associated with a specific
 * collection ID. - getCollectionIdsForScan: Retrieves all collection IDs associated with a specific
 * scan ID.
 *
 * <p>Annotations: - @Dao: Indicates that this interface is a DAO and will be implemented by Room.
 * - @Insert: Used to define the Insert operation for a database table. - @Query: Used to define
 * custom SQL queries for other operations.
 */
@Dao
public interface ScanCollectionJoinDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  void insert(ScanCollectionCrossRef join);

  @Query("DELETE FROM scan_collection_join WHERE scanId = :scanId AND collectionId = :collectionId")
  void remove(String scanId, String collectionId);

  @Query("DELETE FROM scan_collection_join WHERE scanId = :scanId")
  void removeAllForScan(String scanId);

  @Query("SELECT scanId FROM scan_collection_join WHERE collectionId = :collectionId")
  List<String> getScanIdsForCollection(String collectionId);

  @Query("SELECT collectionId FROM scan_collection_join WHERE scanId = :scanId")
  List<String> getCollectionIdsForScan(String scanId);
}
