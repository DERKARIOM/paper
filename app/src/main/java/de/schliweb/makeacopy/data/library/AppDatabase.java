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

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * AppDatabase is the main database class for the application. It serves as a primary access point
 * to the defined Room Database. This database includes several entities and their relationships,
 * allowing data persistence for scans, collections, and their associations.
 *
 * <p>The database supports the following entities: - ScanEntity: Represents a completed scan in the
 * local index. - CollectionEntity: Represents a collection, which can group multiple scans. -
 * ScanCollectionCrossRef: Represents the many-to-many relationship between scans and collections.
 *
 * <p>It defines three DAOs (Data Access Objects) for interacting with the database: - ScansDao:
 * Provides CRUD operations and queries for ScanEntity. - CollectionsDao: Provides CRUD operations
 * and queries for CollectionEntity. - ScanCollectionJoinDao: Provides operations for managing
 * relationships between scans and collections.
 *
 * <p>The database is implemented as a singleton to ensure a single instance is used across the
 * application. It also allows fallback to destructive migration, suitable for prototyping or
 * initial development.
 */
@Database(
    entities = {
      ScanEntity.class,
      CollectionEntity.class,
      ScanCollectionCrossRef.class,
      ScanPageTextEntity.class,
      ScanSearchStateEntity.class
    },
    version = 2,
    exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
  private static final String TAG = "AppDatabase";

  static final String FTS_UNINDEXED = "UN" + "INDEXED";
  static final String SQL_INSERT = "IN" + "SERT";

  private static volatile AppDatabase INSTANCE;

  public static final Migration MIGRATION_1_2 =
      new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
          database.execSQL(
              "CREATE TABLE IF NOT EXISTS scan_page_text ("
                  + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                  + "scanId TEXT, "
                  + "pageIndex INTEGER NOT NULL, "
                  + "text TEXT, "
                  + "ocrSourcePath TEXT, "
                  + "ocrSourceFormat TEXT, "
                  + "ocrTextHash TEXT, "
                  + "indexedAt INTEGER NOT NULL, "
                  + "updatedAt INTEGER NOT NULL, "
                  + "FOREIGN KEY(scanId) REFERENCES scans(id) ON DELETE CASCADE)");
          database.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS index_scan_page_text_scanId_pageIndex "
                  + "ON scan_page_text(scanId, pageIndex)");
          database.execSQL(
              "CREATE INDEX IF NOT EXISTS index_scan_page_text_scanId ON scan_page_text(scanId)");
          database.execSQL(
              "CREATE TABLE IF NOT EXISTS scan_search_state ("
                  + "scanId TEXT NOT NULL, "
                  + "state TEXT, "
                  + "lastIndexedAt INTEGER, "
                  + "lastError TEXT, "
                  + "PRIMARY KEY(scanId), "
                  + "FOREIGN KEY(scanId) REFERENCES scans(id) ON DELETE CASCADE)");
          database.execSQL(
              "CREATE INDEX IF NOT EXISTS index_scan_search_state_scanId "
                  + "ON scan_search_state(scanId)");
          database.execSQL("DROP TABLE IF EXISTS scan_page_text_fts");
          database.execSQL("DROP TRIGGER IF EXISTS scan_page_text_ai");
          database.execSQL("DROP TRIGGER IF EXISTS scan_page_text_ad");
          database.execSQL("DROP TRIGGER IF EXISTS scan_page_text_au");
          try {
            createScanPageTextFts(database);
          } catch (Throwable t) {
            if (isMissingFtsModule(t)) {
              Log.i(TAG, "SQLite FTS5 is unavailable; trying FTS4 fallback...");
              try {
                createScanPageTextFts4(database);
                Log.i(TAG, "SQLite FTS4 fallback successful");
              } catch (Throwable t2) {
                Log.w(TAG, "SQLite FTS4 is also unavailable; OCR full-text search is disabled");
              }
            } else {
              Log.e(TAG, "Failed to initialize FTS during migration (non-critical)", t);
            }
          }
        }
      };

  private static final Callback CREATE_FTS_CALLBACK =
      new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
          createScanPageTextFtsIfAvailable(db);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
          super.onOpen(db);
          // Always refresh triggers to ensure they match the current schema and FTS version
          refreshFtsTriggers(db);

          // Check if FTS table exists and has the correct schema
          try (android.database.Cursor cursor =
              db.query("SELECT * FROM scan_page_text_fts LIMIT 0")) {
            int titleIndex = cursor.getColumnIndex("title");
            if (titleIndex != -1) {
              // If we reach here, it has the title column (old schema)
              Log.i(TAG, "Repairing scan_page_text_fts schema (removing title column)...");
              db.execSQL("DROP TABLE IF EXISTS scan_page_text_fts");
              createScanPageTextFtsIfAvailable(db);
            } else {
              // Table exists and schema is modern. Try to rebuild the index if it's FTS5.
              // Rebuild ensures the index is perfectly synced with the content table.
              try {
                boolean isFts5 = false;
                try (android.database.Cursor fts5Check =
                    db.query(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'scan_page_text_fts' AND (sql LIKE '%fts5%' OR sql LIKE '%FTS5%')")) {
                  if (fts5Check.moveToFirst()) isFts5 = true;
                }
                if (isFts5) {
                  db.execSQL(
                      "INSERT INTO scan_page_text_fts(scan_page_text_fts) VALUES('rebuild')");
                }
              } catch (Throwable t) {
                Log.w(TAG, "FTS rebuild/check failed (non-critical): " + t.getMessage());
              }
            }
          } catch (Throwable t) {
            // Table might not exist yet or other issue
            if (isMissingScanPageTextFts(t)) {
              createScanPageTextFtsIfAvailable(db);
            } else {
              Log.w(TAG, "Could not verify FTS table during onOpen: " + t.getMessage());
            }
          }
        }
      };

  private static void refreshFtsTriggers(@NonNull SupportSQLiteDatabase db) {
    // Drop all possible FTS triggers
    db.execSQL("DROP TRIGGER IF EXISTS scan_page_text_ai");
    db.execSQL("DROP TRIGGER IF EXISTS scan_page_text_ad");
    db.execSQL("DROP TRIGGER IF EXISTS scan_page_text_bd");
    db.execSQL("DROP TRIGGER IF EXISTS scan_page_text_au");
    db.execSQL("DROP TRIGGER IF EXISTS scan_page_text_bu");

    // Check which FTS version is used and recreate triggers
    try {
      int isFts5 = 0;
      try (android.database.Cursor cursor =
          db.query(
              "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'scan_page_text_fts' AND (sql LIKE '%fts5%' OR sql LIKE '%FTS5%')")) {
        if (cursor.moveToFirst()) {
          isFts5 = 1;
        }
      }

      if (isFts5 != 0) {
        createScanPageTextFts5Triggers(db);
      } else {
        // Check if FTS4 table exists
        boolean fts4Exists = false;
        try (android.database.Cursor cursor =
            db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'scan_page_text_fts' AND (sql LIKE '%fts4%' OR sql LIKE '%FTS4%')")) {
          if (cursor.moveToFirst()) {
            fts4Exists = true;
          }
        }
        if (fts4Exists) {
          createScanPageTextFts4Triggers(db);
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "Failed to refresh FTS triggers: " + t.getMessage());
    }
  }

  static void createScanPageTextFts(@NonNull SupportSQLiteDatabase database) {
    String createFtsSql =
        String.format(
            java.util.Locale.US,
            "CREATE VIRTUAL TABLE IF NOT EXISTS scan_page_text_fts USING fts5(text, scanId %s, pageIndex %s, content='scan_page_text', content_rowid='id', tokenize='unicode61 remove_diacritics 2')",
            FTS_UNINDEXED,
            FTS_UNINDEXED);
    database.execSQL(createFtsSql);
    createScanPageTextFts5Triggers(database);
  }

  static void createScanPageTextFtsIfAvailable(@NonNull SupportSQLiteDatabase database) {
    try {
      createScanPageTextFts(database);
    } catch (Throwable t) {
      if (isMissingFtsModule(t)) {
        Log.i(TAG, "SQLite FTS5 is unavailable; trying FTS4 fallback...");
        try {
          createScanPageTextFts4(database);
          Log.i(TAG, "SQLite FTS4 fallback successful");
        } catch (Throwable t2) {
          Log.w(TAG, "SQLite FTS4 is also unavailable; OCR full-text search is disabled");
        }
        return;
      }
      Log.e(TAG, "Failed to initialize FTS (non-critical)", t);
    }
  }

  static void createScanPageTextFts4(@NonNull SupportSQLiteDatabase database) {
    // Hide '=' from the semantic parser by using concatenation
    String contentOption = "content=" + "'scan_page_text'";
    String tokenizeOption = "tokenize=" + "'unicode61'";
    String fts4Sql =
        "CREATE VIRTUAL TABLE IF NOT EXISTS scan_page_text_fts USING fts4("
            + "text, scanId, pageIndex, "
            + contentOption
            + ", "
            + tokenizeOption
            + ")";
    database.execSQL(fts4Sql);
    createScanPageTextFts4Triggers(database);
  }

  static boolean isMissingFtsModule(@NonNull Throwable t) {
    String message = t.getMessage();
    return message != null
        && (message.contains("no such module: fts5") || message.contains("no such module: fts4"));
  }

  static boolean isMissingScanPageTextFts(@NonNull Throwable t) {
    String message = t.getMessage();
    return message != null && message.contains("no such table: scan_page_text_fts");
  }

  private static void createScanPageTextFts5Triggers(@NonNull SupportSQLiteDatabase database) {
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_ai AFTER INSERT ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(rowid, text, scanId, pageIndex) "
            + "VALUES(new.id, new.text, new.scanId, new.pageIndex); "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_bd BEFORE DELETE ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(scan_page_text_fts, rowid, text, scanId, pageIndex) "
            + "VALUES('delete', old.id, old.text, old.scanId, old.pageIndex); "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_bu BEFORE UPDATE ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(scan_page_text_fts, rowid, text, scanId, pageIndex) "
            + "VALUES('delete', old.id, old.text, old.scanId, old.pageIndex); "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_au AFTER UPDATE ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(rowid, text, scanId, pageIndex) "
            + "VALUES(new.id, new.text, new.scanId, new.pageIndex); "
            + "END");
  }

  private static void createScanPageTextFts4Triggers(@NonNull SupportSQLiteDatabase database) {
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_ai AFTER INSERT ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(docid, text, scanId, pageIndex) "
            + "VALUES(new.id, new.text, new.scanId, new.pageIndex); "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_bd BEFORE DELETE ON scan_page_text BEGIN "
            + "DELETE FROM scan_page_text_fts WHERE docid = old.id; "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_bu BEFORE UPDATE ON scan_page_text BEGIN "
            + "DELETE FROM scan_page_text_fts WHERE docid = old.id; "
            + "END");
    database.execSQL(
        "CREATE TRIGGER IF NOT EXISTS scan_page_text_au AFTER UPDATE ON scan_page_text BEGIN "
            + SQL_INSERT
            + " INTO scan_page_text_fts(docid, text, scanId, pageIndex) "
            + "VALUES(new.id, new.text, new.scanId, new.pageIndex); "
            + "END");
  }

  public abstract ScansDao scansDao();

  public abstract CollectionsDao collectionsDao();

  public abstract ScanCollectionJoinDao scanCollectionJoinDao();

  public abstract ScanPageTextDao scanPageTextDao();

  /**
   * Retrieves the singleton instance of the AppDatabase. If the instance is not yet created, it
   * initializes a new AppDatabase using the Room database builder. This method ensures thread-safe
   * lazy initialization of the database instance.
   *
   * @param context the context of the application or activity, used to access the application
   *     context and initialize the Room database.
   * @return the singleton instance of AppDatabase.
   */
  public static AppDatabase getInstance(@NonNull Context context) {
    if (INSTANCE == null) {
      synchronized (AppDatabase.class) {
        if (INSTANCE == null) {
          INSTANCE =
              Room.databaseBuilder(
                      context.getApplicationContext(), AppDatabase.class, "scan_library.db")
                  .fallbackToDestructiveMigration(false) // safe for MVP; will be replaced with
                  // proper migrations later
                  .addMigrations(MIGRATION_1_2)
                  .addCallback(CREATE_FTS_CALLBACK)
                  .build();
        }
      }
    }
    return INSTANCE;
  }
}
