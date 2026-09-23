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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * A test {@link ContentProvider} that mimics the misbehavior of certain cloud providers (e.g.
 * Nextcloud) which strip the file extension from the display name when a document is created via
 * SAF.
 *
 * <p>Implemented as a plain {@link ContentProvider} (not {@link android.provider.DocumentsProvider})
 * to avoid the platform-enforced {@code MANAGE_DOCUMENTS} permission requirement. SAF client calls
 * {@link DocumentsContract#createDocument}, {@link DocumentsContract#renameDocument} and
 * {@link DocumentsContract#getDocumentId} are implemented via {@link #call(String, String, Bundle)}
 * because that is exactly how the framework routes those operations to the provider on the wire.
 *
 * <p>All documents are stored as real files inside the test app's cache directory.
 */
public class StripExtensionDocumentsProvider extends ContentProvider {

  public static final String AUTHORITY = "de.schliweb.makeacopy.test.stripext.documents";
  public static final String ROOT_ID = "root";

  /** Equivalent to the hidden {@code DocumentsContract.EXTRA_URI}. */
  private static final String EXTRA_URI = "uri";

  private static final String[] DEFAULT_DOCUMENT_PROJECTION =
      new String[] {
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE,
      };

  /** Map docId -> File on disk. */
  private final Map<String, File> docs = new HashMap<>();

  private File rootDir;

  @Override
  public boolean onCreate() {
    Context ctx = getContext();
    if (ctx == null) return false;
    rootDir = new File(ctx.getCacheDir(), "stripext-provider");
    if (!rootDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      rootDir.mkdirs();
    }
    docs.put(ROOT_ID, rootDir);
    return true;
  }

  // --- Document/queries ---

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    String docId = DocumentsContract.getDocumentId(uri);
    File f = docs.get(docId);
    if (f == null) return null;
    MatrixCursor c =
        new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
    int flags =
        Document.FLAG_SUPPORTS_WRITE
            | Document.FLAG_SUPPORTS_RENAME
            | Document.FLAG_SUPPORTS_DELETE;
    Object[] row = new Object[c.getColumnCount()];
    for (int i = 0; i < c.getColumnCount(); i++) {
      String col = c.getColumnName(i);
      switch (col) {
        case Document.COLUMN_DOCUMENT_ID:
          row[i] = docId;
          break;
        case Document.COLUMN_DISPLAY_NAME:
          row[i] = f.getName();
          break;
        case Document.COLUMN_MIME_TYPE:
          row[i] = ROOT_ID.equals(docId) ? Document.MIME_TYPE_DIR : "*/*";
          break;
        case Document.COLUMN_FLAGS:
          row[i] = flags;
          break;
        case Document.COLUMN_SIZE:
          row[i] = f.length();
          break;
        default:
          row[i] = null;
      }
    }
    c.addRow(row);
    return c;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    return null;
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    return 0;
  }

  @Nullable
  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
      throws FileNotFoundException {
    String docId = DocumentsContract.getDocumentId(uri);
    File f = docs.get(docId);
    if (f == null) throw new FileNotFoundException("Unknown docId: " + docId);
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
  }

  // --- SAF call routing ---

  @SuppressWarnings("deprecation")
  @Nullable
  @Override
  public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
    if (extras == null) extras = Bundle.EMPTY;
    try {
      switch (method) {
        case "android:createDocument": {
          Uri parentUri = extras.getParcelable(EXTRA_URI);
          String parentDocId = DocumentsContract.getDocumentId(parentUri);
          String mime = extras.getString(Document.COLUMN_MIME_TYPE);
          String name = extras.getString(Document.COLUMN_DISPLAY_NAME);
          String newDocId = createDocumentInternal(parentDocId, mime, name);
          Bundle out = new Bundle();
          out.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUri(AUTHORITY, newDocId));
          return out;
        }
        case "android:renameDocument": {
          Uri docUri = extras.getParcelable(EXTRA_URI);
          String docId = DocumentsContract.getDocumentId(docUri);
          String name = extras.getString(Document.COLUMN_DISPLAY_NAME);
          String newDocId = renameDocumentInternal(docId, name);
          Bundle out = new Bundle();
          out.putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUri(AUTHORITY, newDocId));
          return out;
        }
        case "android:deleteDocument": {
          Uri docUri = extras.getParcelable(EXTRA_URI);
          String docId = DocumentsContract.getDocumentId(docUri);
          File f = docs.remove(docId);
          if (f != null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
          }
          return Bundle.EMPTY;
        }
        default:
          return super.call(method, arg, extras);
      }
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /** Misbehaves on purpose: strips the trailing dot-extension from {@code displayName}. */
  @SuppressWarnings("UnusedVariable")
  private String createDocumentInternal(String parentDocumentId, String mimeType, String displayName)
      throws FileNotFoundException {
    String stripped = stripExtension(displayName);
    File f = new File(rootDir, stripped);
    try {
      int i = 1;
      while (f.exists()) {
        f = new File(rootDir, stripped + "_" + i++);
      }
      //noinspection ResultOfMethodCallIgnored
      f.createNewFile();
    } catch (Exception e) {
      throw new FileNotFoundException("Failed to create: " + e.getMessage());
    }
    String docId = "doc:" + f.getName();
    docs.put(docId, f);
    return docId;
  }

  private static String stripExtension(String name) {
    if (name == null) return "untitled";
    int dot = name.lastIndexOf('.');
    if (dot > 0 && dot > name.lastIndexOf('/')) {
      return name.substring(0, dot);
    }
    return name;
  }

  private String renameDocumentInternal(String documentId, String displayName)
      throws FileNotFoundException {
    File f = docs.get(documentId);
    if (f == null) throw new FileNotFoundException("Unknown docId: " + documentId);
    File renamed = new File(rootDir, displayName);
    if (renamed.exists()) {
      throw new FileNotFoundException("Already exists: " + displayName);
    }
    if (!f.renameTo(renamed)) {
      throw new FileNotFoundException("Rename failed");
    }
    docs.remove(documentId);
    String newDocId = "doc:" + renamed.getName();
    docs.put(newDocId, renamed);
    return newDocId;
  }

  /** Builds a content URI for the given documentId. */
  @NonNull
  public static Uri buildDocumentUri(@NonNull String documentId) {
    return DocumentsContract.buildDocumentUri(AUTHORITY, documentId);
  }
}
