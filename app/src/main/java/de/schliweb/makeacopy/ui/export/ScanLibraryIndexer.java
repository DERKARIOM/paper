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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import de.schliweb.makeacopy.data.library.ScanIndexMeta;
import de.schliweb.makeacopy.data.library.ScansRepository;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/**
 * Helper class that indexes exported scans into the scan library. Handles cover image generation
 * from exported PDFs or images and persists metadata via {@link ScansRepository}. Extracted from
 * ExportFragment to reduce its size.
 *
 * <p>This class cannot be instantiated.
 */
@UtilityClass
final class ScanLibraryIndexer {

  private static final String TAG = "ScanLibraryIndexer";

  /**
   * Indexes an exported scan asynchronously in a background thread. Generates a cover image from
   * the export URI and persists metadata into the scans repository.
   *
   * @param ctx the application context
   * @param scansRepository the repository to persist scan metadata
   * @param title the display title for the scan
   * @param pageCount the number of pages in the export
   * @param exportUri the URI of the exported file
   * @param defaultBaseName fallback base name if title is null
   */
  static void indexAsync(
      Context ctx,
      ScansRepository scansRepository,
      String title,
      int pageCount,
      Uri exportUri,
      String ocrText,
      String defaultBaseName) {
    if (title == null) title = defaultBaseName;
    final String titleFinal = title;
    final int pages = Math.max(1, pageCount);
    final String generatedId = UUID.randomUUID().toString();
    final String exportJson = (exportUri != null) ? ("[\"" + exportUri + "\"]") : null;
    new Thread(
            () -> {
              try {
                persistOcrText(ctx, generatedId, ocrText);
                String coverPath = generateCover(ctx, exportUri, generatedId);
                ScanIndexMeta meta =
                    new ScanIndexMeta(
                        generatedId,
                        titleFinal,
                        System.currentTimeMillis(),
                        pages,
                        coverPath,
                        exportJson,
                        null);
                scansRepository.indexExportedScan(ctx, meta);
              } catch (Exception e) {
                Log.d(TAG, "indexAsync: suppressed", e);
              }
            })
        .start();
  }

  private static void persistOcrText(Context ctx, String generatedId, String ocrText)
      throws IOException {
    if (ocrText == null || ocrText.trim().isEmpty()) return;
    File scanDir = new File(ctx.getFilesDir(), "scans/" + generatedId);
    if (!scanDir.isDirectory() && !scanDir.mkdirs()) {
      throw new IOException("Unable to create scan OCR directory: " + scanDir);
    }
    File textFile = new File(scanDir, "text.txt");
    try (FileOutputStream output = new FileOutputStream(textFile)) {
      output.write(ocrText.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String generateCover(Context ctx, Uri exportUri, String generatedId) {
    if (exportUri == null) return null;
    android.content.ContentResolver cr = ctx.getContentResolver();
    String mime = null;
    try {
      mime = cr.getType(exportUri);
    } catch (SecurityException se) {
      Log.w(TAG, "generateCover: cannot query MIME type", se);
    }
    boolean isImage = mime != null && mime.startsWith("image/");
    boolean isPdf =
        mime != null
            && ("application/pdf".equalsIgnoreCase(mime)
                || mime.toLowerCase(Locale.ROOT).contains("pdf"));
    if (!isImage && !isPdf) {
      String u = exportUri.toString();
      String lower = (u != null) ? u.toLowerCase(Locale.ROOT) : null;
      if (lower != null) {
        isImage =
            lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
        if (!isImage) isPdf = lower.endsWith(".pdf");
      }
    }
    if (isImage) {
      return generateImageCover(ctx, cr, exportUri, generatedId);
    } else if (isPdf) {
      return generatePdfCover(ctx, cr, exportUri, generatedId);
    }
    return null;
  }

  private static String generateImageCover(
      Context ctx, android.content.ContentResolver cr, Uri exportUri, String generatedId) {
    try (InputStream is = cr.openInputStream(exportUri)) {
      if (is == null) return null;
      BitmapFactory.Options dec = new BitmapFactory.Options();
      dec.inPreferredConfig = Bitmap.Config.RGB_565;
      dec.inSampleSize = 2;
      Bitmap bmp = BitmapFactory.decodeStream(is, null, dec);
      if (bmp != null) {
        try {
          return saveCover(ctx, bmp, generatedId);
        } finally {
          try {
            bmp.recycle();
          } catch (Exception ignored) {
            // Best-effort; failure is non-critical
          }
        }
      }
    } catch (FileNotFoundException e) {
      Log.w(TAG, "generateImageCover: source not found", e);
    } catch (SecurityException | IOException e) {
      Log.w(TAG, "generateImageCover: io/security error", e);
    }
    return null;
  }

  private static String generatePdfCover(
      Context ctx, android.content.ContentResolver cr, Uri exportUri, String generatedId) {
    try (ParcelFileDescriptor pfd = cr.openFileDescriptor(exportUri, "r")) {
      if (pfd == null) return null;
      try (PdfRenderer renderer = new PdfRenderer(pfd)) {
        if (renderer.getPageCount() <= 0) return null;
        PdfRenderer.Page page = renderer.openPage(0);
        try {
          int pageW = page.getWidth();
          int pageH = page.getHeight();
          int targetW = 320;
          int targetH = (pageW > 0) ? Math.max(1, (int) (targetW * (pageH / (float) pageW))) : 320;
          Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(bmp);
          canvas.drawColor(Color.WHITE);
          Matrix m = new Matrix();
          float scaleX = targetW / (float) pageW;
          float scaleY = targetH / (float) pageH;
          m.setScale(scaleX, scaleY);
          page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          try {
            return saveCover(ctx, bmp, generatedId);
          } finally {
            try {
              bmp.recycle();
            } catch (Exception ignored) {
              // Best-effort; failure is non-critical
            }
          }
        } finally {
          try {
            page.close();
          } catch (Exception ignored) {
            // Best-effort; failure is non-critical
          }
        }
      }
    } catch (FileNotFoundException e) {
      Log.w(TAG, "generatePdfCover: pdf not found", e);
    } catch (SecurityException | IOException e) {
      Log.w(TAG, "generatePdfCover: io/security error", e);
    }
    return null;
  }

  private static String saveCover(Context ctx, Bitmap bmp, String generatedId) {
    File dir = new File(ctx.getFilesDir(), "scans_covers");
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();
    File out = new File(dir, generatedId + ".jpg");
    try (FileOutputStream fos = new FileOutputStream(out)) {
      bmp.compress(Bitmap.CompressFormat.JPEG, 82, fos);
      fos.flush();
      return out.getAbsolutePath();
    } catch (IOException ioe) {
      Log.w(TAG, "saveCover: failed", ioe);
      return null;
    }
  }
}
