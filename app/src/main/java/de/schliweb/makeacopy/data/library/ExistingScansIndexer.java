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
import de.schliweb.makeacopy.data.CompletedScansRegistry;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.util.List;

/**
 * Utility class to handle indexing of completed scans into the scan library database. Provides both
 * a one-time bootstrap and a repeatable incremental pass that can be executed safely multiple times
 * without creating duplicates.
 */
public final class ExistingScansIndexer {
  private static final String TAG = "ExistingScansIndexer";
  private static final String PREFS = "scan_library";
  private static final String KEY_DONE = "existing_index_done";
  // Marker used in ScanEntity.sourceMetaJson to denote a single-page registry entry
  // that should not appear on the Library home screen (only in the Completed Scans collection).
  private static final String META_COMPLETED_SCAN_ENTRY = "{\"kind\":\"CompletedScanEntry\"}";

  private ExistingScansIndexer() {}

  /** Returns whether the one-time indexing has already been completed. */
  public static boolean isAlreadyIndexed(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false);
  }

  /** Marks the one-time indexing as completed. */
  private static void markDone(Context context) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DONE, true)
        .apply();
  }

  /**
   * Runs the one-time bootstrap indexing if it hasn't been executed yet. Internally delegates to
   * the incremental pass. Returns the number of newly inserted items.
   */
  public static int runOnceIfNeeded(
      Context context, ScansRepository scansRepo, CollectionsRepository collectionsRepo) {
    if (isAlreadyIndexed(context)) return 0;
    int count = runIncremental(context, scansRepo, collectionsRepo);
    markDone(context);
    return count;
  }

  /**
   * Performs a best-effort indexing pass of items from CompletedScansRegistry into the scan library
   * DB. Safe to call multiple times; only inserts items that are not yet present in the DB.
   *
   * @return number of newly inserted items (duplicates are skipped)
   */
  public static int runIncremental(
      Context context, ScansRepository repo, CollectionsRepository cr) {
    try {
      Context app = context.getApplicationContext();
      List<CompletedScan> items = CompletedScansRegistry.get(app).listAllOrderedByDateDesc();
      if (items == null || items.isEmpty()) return 0;
      int newCount = 0;
      for (CompletedScan s : items) {
        if (s == null) continue;
        String id = s.id();
        if (id == null || id.isEmpty()) continue;
        // Skip insert if already present, but repair generic title if needed
        try {
          ScanEntity existing = repo.getScanById(app, id);
          if (existing != null) {
            try {
              String curTitle = existing.title;
              String better = deriveTitle(s);
              if ((curTitle == null || curTitle.trim().isEmpty() || isGenericPlaceholder(curTitle))
                  && better != null
                  && !better.trim().isEmpty()
                  && !isGenericPlaceholder(better)
                  && !better.equals(curTitle)) {
                repo.updateTitle(app, id, better);
              }
            } catch (Throwable ignoreRepair) {
              // Best-effort; failure is non-critical
            }
            // Repair missing export path if registry has a readable file path
            try {
              if (isNullOrEmpty(existing.exportPathsJson)) {
                String fp = safeFilePath(s);
                String exportJsonFix = makeSingleFileUriJson(fp);
                if (exportJsonFix != null) {
                  repo.updateExportPathsJson(app, id, exportJsonFix);
                }
              }
            } catch (Throwable ignoreRepair2) {
              // Best-effort; failure is non-critical
            }
            // Ensure sourceMetaJson marks this as a CompletedScanEntry for UI filtering
            try {
              String sm = existing.sourceMetaJson;
              if (sm == null || sm.isEmpty() || !sm.contains("\"CompletedScanEntry\"")) {
                // Use indexExportedScan to update only sourceMetaJson, leaving other fields intact
                ScanIndexMeta patchMeta =
                    new ScanIndexMeta(id, null, 0L, 0, null, null, META_COMPLETED_SCAN_ENTRY);
                repo.indexExportedScan(app, patchMeta);
              }
            } catch (Throwable ignoreMark) {
              // Best-effort; failure is non-critical
            }
            // Ensure membership in default collection
            try {
              CollectionEntity def = cr.getOrCreateDefaultCompletedCollection(app);
              if (def != null) {
                cr.assignScanToCollection(app, id, def.id);
              }
            } catch (Throwable ignoreMembership) {
              // Best-effort; failure is non-critical
            }
            try {
              repo.reindexOcrText(app, id);
            } catch (Throwable ignoreOcrIndex) {
              // Best-effort; failure is non-critical
            }
            continue;
          }
        } catch (Throwable ignore) {
          // Best-effort; failure is non-critical
        }
        String title = deriveTitle(s);
        long created = s.createdAt();
        int pages = 1; // Registry tracks single pages; multi-page support may extend this later
        String cover = s.thumbPath();
        String exportJson = makeSingleFileUriJson(safeFilePath(s));
        String metaJson = META_COMPLETED_SCAN_ENTRY; // mark registry single-page entries
        ScanIndexMeta meta =
            new ScanIndexMeta(id, title, created, pages, cover, exportJson, metaJson);
        repo.indexExportedScan(app, meta);
        // Assign to default collection
        try {
          CollectionEntity def = cr.getOrCreateDefaultCompletedCollection(app);
          if (def != null) {
            cr.assignScanToCollection(app, id, def.id);
          }
        } catch (Throwable ignoreAssign) {
          // Best-effort; failure is non-critical
        }
        newCount++;
      }
      return newCount;
    } catch (Throwable t) {
      Log.w(TAG, "Incremental indexing of existing scans failed", t);
      return 0;
    }
  }

  /**
   * Legacy: Performs indexing for all items regardless of prior existence. Kept for debugging.
   * Returns the number of items attempted (not necessarily newly inserted).
   */
  public static int runNow(Context context, ScansRepository repo) {
    try {
      Context app = context.getApplicationContext();
      List<CompletedScan> items = CompletedScansRegistry.get(app).listAllOrderedByDateDesc();
      if (items == null || items.isEmpty()) return 0;
      int n = 0;
      for (CompletedScan s : items) {
        if (s == null) continue;
        String id = s.id();
        if (id == null || id.isEmpty()) continue;
        String title = deriveTitle(s);
        long created = s.createdAt();
        int pages = 1; // Registry tracks single pages; multi-page support may extend this later
        String cover = s.thumbPath();
        String exportJson = null; // keep null for MVP
        String metaJson = null; // keep null for MVP
        ScanIndexMeta meta =
            new ScanIndexMeta(id, title, created, pages, cover, exportJson, metaJson);
        repo.indexExportedScan(app, meta);
        n++;
      }
      return n;
    } catch (Throwable t) {
      Log.w(TAG, "Indexing existing scans failed", t);
      return 0;
    }
  }

  // Helpers to prepare exportPathsJson for registry-backed items
  private static boolean isNullOrEmpty(String s) {
    return s == null || s.trim().isEmpty();
  }

  @androidx.annotation.Nullable
  private static String safeFilePath(CompletedScan s) {
    try {
      String p = (s != null) ? s.filePath() : null;
      if (p == null || p.trim().isEmpty()) return null;
      java.io.File f = new java.io.File(p);
      if (f.exists() && f.isFile()) return f.getAbsolutePath();
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    return null;
  }

  @androidx.annotation.Nullable
  private static String makeSingleFileUriJson(@androidx.annotation.Nullable String absPath) {
    try {
      if (absPath == null || absPath.trim().isEmpty()) return null;
      java.io.File f = new java.io.File(absPath);
      if (!f.exists() || !f.isFile()) return null;
      String s = android.net.Uri.fromFile(f).toString();
      String esc = s.replace("\"", "\\\"");
      return "[\"" + esc + "\"]";
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Derives a meaningful title for a completed scan based on its associated file or thumbnail
   * paths, or falls back to the scan's unique identifier or a default value if necessary.
   *
   * <p>The method prioritizes deriving the title from the file name of the `filePath` if it is
   * available and non-generic. If the file name is deemed generic (e.g., "page" or "thumb"), it
   * attempts to use the parent directory name. As a next fallback, the file name from the scan's
   * thumbnail path is used if available and valid. If no meaningful title can be derived from these
   * sources, it defaults to the scan's identifier. A final fallback of "scan" is used if all other
   * options are unavailable or invalid.
   *
   * @param s The completed scan object containing details such as file paths, thumbnail paths, and
   *     a unique scan identifier.
   * @return A derived meaningful title for the scan. If no meaningful title can be determined, the
   *     method returns a fallback identifier or "scan".
   */
  private static String deriveTitle(CompletedScan s) {
    // Prefer a meaningful file/dir-based title; avoid generic placeholders like "page".
    try {
      String p = s.filePath();
      if (p != null && !p.isEmpty()) {
        // Derive from file name first
        String name = null;
        try {
          java.io.File f = new java.io.File(p);
          String base = f.getName();
          int dot = base.lastIndexOf('.');
          if (dot > 0) base = base.substring(0, dot);
          name = base;
          // If the file name is a generic placeholder (e.g., "page"), try the parent folder name
          if (name != null && isGenericPlaceholder(name)) {
            java.io.File parent = f.getParentFile();
            if (parent != null) {
              String parentName = parent.getName();
              if (parentName != null
                  && !parentName.trim().isEmpty()
                  && !isGenericPlaceholder(parentName)) {
                return parentName;
              }
            }
            // As another fallback, try the thumbnail's file name
            String tp = s.thumbPath();
            if (tp != null && !tp.isEmpty()) {
              java.io.File tf = new java.io.File(tp);
              String tbase = tf.getName();
              int tdot = tbase.lastIndexOf('.');
              if (tdot > 0) tbase = tbase.substring(0, tdot);
              if (tbase != null && !tbase.trim().isEmpty() && !isGenericPlaceholder(tbase)) {
                return tbase;
              }
            }
            // Otherwise, fall through to ID below
          }
        } catch (Throwable ignoreInner) {
          /* fall back below */
        }
        if (name != null && !name.trim().isEmpty() && !isGenericPlaceholder(name)) {
          return name;
        }
      }
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    String id = s.id();
    return (id != null && !id.isEmpty()) ? id : "scan";
  }

  /**
   * Determines if the input string represents a generic placeholder name. A generic placeholder is
   * considered as one of the following case-insensitive strings: "page", "thumb", "image", or
   * "img".
   *
   * @param name The input string to check. This may be null or empty.
   * @return True if the input string matches one of the predefined placeholder names, otherwise
   *     false.
   */
  private static boolean isGenericPlaceholder(String name) {
    String n = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    return n.equals("page") || n.equals("thumb") || n.equals("image") || n.equals("img");
  }
}
