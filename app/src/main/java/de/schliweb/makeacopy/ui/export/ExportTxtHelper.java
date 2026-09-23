/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.utils.ui.UIUtils;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Helper class that encapsulates OCR text export logic (TXT file creation). Extracted from
 * ExportFragment to reduce its size.
 *
 * <p>This class cannot be instantiated.
 */
@UtilityClass
final class ExportTxtHelper {

  private static final String TAG = "ExportTxtHelper";

  static String readAllUtf8(File file) throws IOException {
    byte[] buf = Files.readAllBytes(file.toPath());
    return new String(buf, StandardCharsets.UTF_8);
  }

  /**
   * Exports OCR text to a TXT file at the given URI. For multi-page sessions, concatenates per-page
   * OCR text in filmstrip order.
   *
   * @param context the context for content resolver access
   * @param exportViewModel the export view model to update TXT URI
   * @param exportSessionViewModel the session view model for multi-page access
   * @param txtUri the target URI for the TXT file
   * @param currentText the in-memory OCR text for the current page
   * @param currentPreviewBitmap the currently previewed bitmap (to match in-memory OCR)
   * @param deferAssignCallback callback to clear the deferAssignUntilTxt flag on success
   */
  static void exportOcrTextToTxt(
      Context context,
      ExportViewModel exportViewModel,
      de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel exportSessionViewModel,
      Uri txtUri,
      String currentText,
      Bitmap currentPreviewBitmap,
      Runnable deferAssignCallback) {
    if (txtUri == null) return;

    List<de.schliweb.makeacopy.ui.export.session.CompletedScan> pages =
        exportSessionViewModel != null ? exportSessionViewModel.getPages().getValue() : null;
    boolean isMulti = pages != null && pages.size() > 1;

    // Single-page: Just use current in-memory OCR text if present
    if (!isMulti) {
      if (currentText == null || currentText.isEmpty()) {
        Log.d(TAG, "exportOcrTextToTxt: No OCR text available to export (single page)");
        return;
      }
      writeTxtToUri(context, exportViewModel, txtUri, currentText, deferAssignCallback);
      return;
    }

    // Multi-page: concatenate per-page OCR from registry
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pages.size(); i++) {
      de.schliweb.makeacopy.ui.export.session.CompletedScan s = pages.get(i);
      String pageText = null;
      String p = (s != null) ? s.ocrTextPath() : null;
      String fmt = (s != null) ? s.ocrFormat() : null;
      boolean isPlain = (fmt == null) || "plain".equalsIgnoreCase(fmt);
      if (p != null) {
        if (isPlain) {
          File f = new File(p);
          if (f.exists() && f.isFile()) {
            try {
              pageText = readAllUtf8(f);
            } catch (IOException e) {
              Log.w(TAG, "Failed reading plain OCR text for page: " + p, e);
            }
          }
        } else {
          File f = new File(p);
          File dir = f.getParentFile();
          if (dir != null) {
            File txtFile = new File(dir, "text.txt");
            if (txtFile.exists() && txtFile.isFile()) {
              try {
                pageText = readAllUtf8(txtFile);
              } catch (IOException e) {
                Log.w(
                    TAG,
                    "Failed reading fallback text.txt for page: " + txtFile.getAbsolutePath(),
                    e);
              }
            }
          }
        }
      }
      if ((pageText == null || pageText.isEmpty())
          && s != null
          && s.inMemoryBitmap() != null
          && currentPreviewBitmap == s.inMemoryBitmap()) {
        pageText = currentText;
      }
      if (pageText != null) sb.append(pageText);
      if (i < pages.size() - 1) sb.append("\n\n");
    }

    writeTxtToUri(context, exportViewModel, txtUri, sb.toString(), deferAssignCallback);
  }

  static void writeTxtToUri(
      Context context,
      ExportViewModel exportViewModel,
      Uri txtUri,
      String content,
      Runnable deferAssignCallback) {
    try (OutputStream os = context.getContentResolver().openOutputStream(txtUri)) {
      if (os == null) {
        Log.e(TAG, "writeTxtToUri: Failed to open output stream for TXT file");
        return;
      }
      byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
      os.write(bytes);
      exportViewModel.setTxtExportUri(txtUri);
      UIUtils.showToast(
          context, context.getString(R.string.ocr_text_exported_as_txt), Toast.LENGTH_SHORT);

      if (deferAssignCallback != null) {
        deferAssignCallback.run();
      }
    } catch (java.io.FileNotFoundException | SecurityException e) {
      Log.e(TAG, "writeTxtToUri: Permission or file error during TXT export", e);
      UIUtils.showToast(
          context,
          context.getString(R.string.error_exporting_ocr_text_with_reason, e.getMessage()),
          Toast.LENGTH_SHORT);
    } catch (IOException e) {
      Log.e(TAG, "writeTxtToUri: I/O error during TXT export", e);
      UIUtils.showToast(
          context,
          context.getString(R.string.error_exporting_ocr_text_with_reason, e.getMessage()),
          Toast.LENGTH_SHORT);
    }
  }
}
