/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.infra;

import android.net.Uri;
import android.util.Log;

/**
 * Utility methods for deriving parent/folder URIs from document URIs returned by the system file
 * picker. These folder URIs are more suitable as {@code EXTRA_INITIAL_URI} hints for future picker
 * invocations than raw file URIs, which many OEM pickers (Xiaomi, Samsung, etc.) ignore.
 */
public final class DocumentUriUtils {

  private static final String TAG = "DocumentUriUtils";

  private DocumentUriUtils() {}

  /**
   * Attempts to derive a parent/folder document URI from a file document URI returned by the
   * picker. This folder URI is more suitable as {@code EXTRA_INITIAL_URI} for future picker
   * invocations. Returns {@code null} if derivation is not possible (e.g. non-document URI).
   */
  public static Uri deriveParentDocumentUri(Uri documentUri) {
    if (documentUri == null) return null;
    try {
      // Detect document URIs structurally (authority + path segments) without calling
      // DocumentsContract.isDocumentUri, which requires a non-null Context on some Android
      // versions and would otherwise NPE inside PackageManager lookup.
      if (!isStructurallyDocumentUri(documentUri)) {
        // Not a document URI from a DocumentsProvider — cannot derive parent.
        // Fallback: try heuristic path-based parent for content URIs.
        return deriveParentFromPathSegments(documentUri);
      }
      String docId = android.provider.DocumentsContract.getDocumentId(documentUri);
      if (docId == null) return null;
      // Common document ID formats use ':' or '/' as separators (e.g. "primary:DCIM/photo.jpg").
      // Derive the parent by stripping the last path segment.
      int lastSep = Math.max(docId.lastIndexOf('/'), docId.lastIndexOf(':'));
      if (lastSep <= 0) return null; // root or no separator — cannot go higher
      // For ':' separator (e.g. "primary:DCIM/photo.jpg"), keep the prefix including ':'
      // For '/' separator, strip the filename
      String parentDocId;
      if (docId.lastIndexOf('/') > docId.lastIndexOf(':')) {
        parentDocId = docId.substring(0, docId.lastIndexOf('/'));
      } else {
        // Only root:filename — parent is just "root:"
        parentDocId = docId.substring(0, lastSep + 1);
      }
      return android.provider.DocumentsContract.buildDocumentUri(
          documentUri.getAuthority(), parentDocId);
    } catch (Exception e) {
      Log.w(TAG, "Could not derive parent document URI", e);
      return null;
    }
  }

  /**
   * Lightweight, context-free check whether {@code uri} structurally looks like a document URI
   * exposed by a {@link android.provider.DocumentsProvider}. We deliberately avoid {@link
   * android.provider.DocumentsContract#isDocumentUri(android.content.Context, Uri)} because it
   * requires a non-null Context (it dereferences {@code ctx.getPackageManager()} internally) and
   * has been observed to NPE when called from background paths where no Context is available.
   *
   * <p>Document URIs from the SAF use the form {@code content://<authority>/document/<id>} or, for
   * tree URIs, {@code content://<authority>/tree/<treeId>/document/<id>}.
   */
  static boolean isStructurallyDocumentUri(Uri uri) {
    if (uri == null) return false;
    if (!"content".equals(uri.getScheme())) return false;
    java.util.List<String> seg = uri.getPathSegments();
    if (seg == null || seg.isEmpty()) return false;
    // Plain document URI: /document/<id>
    if (seg.size() >= 2 && "document".equals(seg.get(0))) return true;
    // Tree document URI: /tree/<treeId>/document/<id>
    return seg.size() >= 4 && "tree".equals(seg.get(0)) && "document".equals(seg.get(2));
  }

  /**
   * Fallback: derive a parent URI from path segments for content URIs that are not standard
   * document URIs. Returns {@code null} if not feasible.
   */
  static Uri deriveParentFromPathSegments(Uri uri) {
    if (uri == null || !"content".equals(uri.getScheme())) return null;
    try {
      java.util.List<String> segments = uri.getPathSegments();
      if (segments == null || segments.size() < 2) return null;
      // Rebuild without the last segment
      Uri.Builder builder = uri.buildUpon().path(null);
      for (int i = 0; i < segments.size() - 1; i++) {
        builder.appendPath(segments.get(i));
      }
      return builder.build();
    } catch (Exception e) {
      Log.w(TAG, "Could not derive parent from path segments", e);
      return null;
    }
  }
}
