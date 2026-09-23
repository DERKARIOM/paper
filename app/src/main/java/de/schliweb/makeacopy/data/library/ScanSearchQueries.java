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

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

/** Builds parameterized SQL queries for OCR FTS search. */
public final class ScanSearchQueries {
  private ScanSearchQueries() {}

  public static SupportSQLiteQuery ocrFallbackSearch(String rawQuery, int limit) {
    String likeQuery = sanitizeLikeQuery(rawQuery);
    return new SimpleSQLiteQuery(
        "SELECT s.id AS scanId, s.title AS title, s.createdAt AS createdAt, s.coverPath AS coverPath, "
            + "p.pageIndex AS pageIndex, "
            + "p.text AS snippet, "
            + "0.0 AS rank "
            + "FROM scan_page_text p "
            + "JOIN scans s ON p.scanId = s.id "
            + "WHERE p.text LIKE ? ESCAPE '\\' "
            + "ORDER BY s.createdAt DESC "
            + "LIMIT ?",
        new Object[] {likeQuery, limit});
  }

  public static SupportSQLiteQuery ocrSearch(String ftsQuery, int limit, boolean useFts5) {
    if (useFts5) {
      return new SimpleSQLiteQuery(
          "SELECT s.id AS scanId, s.title AS title, s.createdAt AS createdAt, s.coverPath AS coverPath, "
              + "p.pageIndex AS pageIndex, "
              + "snippet(scan_page_text_fts, 0, '“', '”', '…', 12) AS snippet, "
              + "bm25(scan_page_text_fts) AS rank "
              + "FROM scan_page_text_fts "
              + "JOIN scan_page_text p ON scan_page_text_fts.rowid = p.id "
              + "JOIN scans s ON p.scanId = s.id "
              + "WHERE scan_page_text_fts MATCH ? "
              + "ORDER BY rank ASC, s.createdAt DESC "
              + "LIMIT ?",
          new Object[] {ftsQuery, limit});
    }
    return new SimpleSQLiteQuery(
        "SELECT s.id AS scanId, s.title AS title, s.createdAt AS createdAt, s.coverPath AS coverPath, "
            + "p.pageIndex AS pageIndex, "
            + "snippet(scan_page_text_fts, 0, '“', '”', '…', 12) AS snippet, "
            + "0.0 AS rank "
            + "FROM scan_page_text_fts "
            + "JOIN scan_page_text p ON scan_page_text_fts.rowid = p.id "
            + "JOIN scans s ON p.scanId = s.id "
            + "WHERE scan_page_text_fts MATCH ? "
            + "ORDER BY s.createdAt DESC "
            + "LIMIT ?",
        new Object[] {ftsQuery, limit});
  }

  public static String sanitizeFtsQuery(String rawQuery) {
    if (rawQuery == null) return "";
    String normalized = rawQuery.trim().replaceAll("\\s+", " ");
    if (normalized.isEmpty()) return "";
    StringBuilder builder = new StringBuilder();
    for (String term : normalized.split(" ", -1)) {
      String cleaned = term.replaceAll("[^\\p{L}\\p{N}_-]", "");
      if (cleaned.isEmpty()) continue;
      if (isFtsOperator(cleaned)) continue;
      if (builder.length() > 0) builder.append(' ');
      // Use prefix search by default for better user experience
      // Use 'term*' format which is compatible with most tokenizers in FTS4/5
      builder.append(cleaned.replace("\"", "\"\"")).append('*');
    }
    return builder.toString();
  }

  public static String sanitizeLikeQuery(String rawQuery) {
    if (rawQuery == null) return "%%";
    String normalized = rawQuery.trim().replaceAll("\\s+", " ");
    if (normalized.isEmpty()) return "%%";
    return "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  private static boolean isFtsOperator(String term) {
    return "AND".equalsIgnoreCase(term)
        || "OR".equalsIgnoreCase(term)
        || "NOT".equalsIgnoreCase(term);
  }
}
