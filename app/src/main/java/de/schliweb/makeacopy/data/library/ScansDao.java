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

import androidx.room.*;
import java.util.List;

/**
 * Data Access Object (DAO) interface for performing database operations on the "scans" table. It
 * provides methods for CRUD (Create, Read, Update, Delete) operations and custom queries to
 * interact with the underlying data store.
 *
 * <p>Methods: - insert(ScanEntity): Inserts a new scan record into the database. If a conflict
 * occurs, the operation aborts. - update(ScanEntity): Updates an existing scan record in the
 * database. - getAll(): Retrieves all scan records from the database, ordered by their creation
 * timestamps in descending order. - getById(String): Retrieves a scan record by its unique
 * identifier. - deleteById(String): Deletes a scan record by its unique identifier. -
 * getAllByCollection(String): Retrieves all scan records associated with a specific collection.
 */
@Dao
public interface ScansDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  void insert(ScanEntity scan);

  @Update
  void update(ScanEntity scan);

  @Query("SELECT * FROM scans ORDER BY createdAt DESC")
  List<ScanEntity> getAll();

  @Query("SELECT * FROM scans WHERE id = :id LIMIT 1")
  ScanEntity getById(String id);

  @Query("DELETE FROM scans WHERE id = :id")
  void deleteById(String id);

  @Query(
      "SELECT s.* FROM scans s INNER JOIN scan_collection_join j ON s.id = j.scanId WHERE j.collectionId = :collectionId ORDER BY s.createdAt DESC")
  List<ScanEntity> getAllByCollection(String collectionId);
}
