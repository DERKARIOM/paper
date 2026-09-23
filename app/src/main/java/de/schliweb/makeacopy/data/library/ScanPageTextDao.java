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
import androidx.room.RawQuery;
import androidx.room.SkipQueryVerification;
import androidx.sqlite.db.SupportSQLiteQuery;
import java.util.List;

/** DAO for OCR search text projection and FTS-backed search. */
@Dao
public interface ScanPageTextDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void upsert(ScanPageTextEntity entity);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void upsertState(ScanSearchStateEntity entity);

  @Query("DELETE FROM scan_page_text WHERE scanId = :scanId")
  void deleteForScan(String scanId);

  @SkipQueryVerification
  @RawQuery
  int hasOcrFtsTable(SupportSQLiteQuery query);

  @SkipQueryVerification
  @RawQuery
  List<ScanSearchResult> searchOcr(SupportSQLiteQuery query);

  @SkipQueryVerification
  @RawQuery
  List<ScanSearchResult> searchOcrFallback(SupportSQLiteQuery query);
}
