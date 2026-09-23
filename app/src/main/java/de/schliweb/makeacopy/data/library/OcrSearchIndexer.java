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
import java.io.File;
import javax.inject.Inject;

/** Maintains the rebuildable OCR search projection for app-private scans. */
public class OcrSearchIndexer {
  private static final String TAG = "OcrSearchIndexer";
  private static final String STATE_INDEXED = "indexed";
  private static final String STATE_EMPTY = "empty";
  private static final String STATE_ERROR = "error";

  private final ScanPageTextDao scanPageTextDao;
  private final OcrTextExtractor extractor;

  @Inject
  public OcrSearchIndexer(ScanPageTextDao scanPageTextDao) {
    this(scanPageTextDao, new OcrTextExtractor());
  }

  OcrSearchIndexer(ScanPageTextDao scanPageTextDao, OcrTextExtractor extractor) {
    this.scanPageTextDao = scanPageTextDao;
    this.extractor = extractor;
  }

  public void indexScan(Context context, ScanEntity scan) {
    if (context == null || scan == null) return;
    long now = System.currentTimeMillis();
    try {
      File scanDir = resolveScanDir(context, scan);
      OcrTextExtractor.ExtractedText extracted = extractor.extract(scanDir, null, null);
      if (extracted.isEmpty()) {
        scanPageTextDao.deleteForScan(scan.id);
        scanPageTextDao.upsertState(new ScanSearchStateEntity(scan.id, STATE_EMPTY, now, null));
        return;
      }

      ScanPageTextEntity entity =
          new ScanPageTextEntity(
              0L,
              scan.id,
              0,
              extracted.text,
              extracted.sourcePath,
              extracted.sourceFormat,
              extracted.textHash,
              now,
              extracted.sourceUpdatedAt);
      scanPageTextDao.upsert(entity);
      scanPageTextDao.upsertState(new ScanSearchStateEntity(scan.id, STATE_INDEXED, now, null));
    } catch (Throwable t) {
      Log.w(TAG, "OCR search indexing failed for scan " + scan.id, t);
      scanPageTextDao.upsertState(
          new ScanSearchStateEntity(scan.id, STATE_ERROR, null, String.valueOf(t.getMessage())));
    }
  }

  private File resolveScanDir(Context context, ScanEntity scan) {
    return new File(context.getFilesDir(), "scans/" + scan.id);
  }
}
