/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import de.schliweb.makeacopy.jobs.OcrBackgroundJobs;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel;
import de.schliweb.makeacopy.utils.export.PageOcrStore;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.SessionOcrUpdater;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Text extraction for a multi-page document, started on demand from the final document screen.
 *
 * <p>Reuses the background OCR pipeline ({@link OcrBackgroundJobs}): one job per page, run
 * sequentially off the main thread, results stored per page ({@link PageOcrStore}) and applied to
 * the export session so the PDF text layer and the TXT export use them. Pages that already have a
 * text are not processed again unless a new run is forced (e.g. after a language change).
 */
final class DocumentTextExtraction {
  private static final String TAG = "DocumentTextExtraction";

  /** Progress/result callbacks, always invoked on the main thread. */
  interface Listener {
    void onProgress(int processed, int total);

    /**
     * Called once every page was processed (or none needed processing).
     *
     * @param text the text of all pages in document order ({@code ""} when none was found)
     * @param failedPages number of pages whose extraction failed
     */
    void onFinished(String text, int failedPages);
  }

  private final Context app;
  private final ExportSessionViewModel session;
  private final Supplier<OCRHelper> helperSupplier;
  private final String pageHeaderFormat;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Set<String> pending = new LinkedHashSet<>();
  private Listener listener;
  private int total;
  private int failed;
  private boolean running;
  private boolean receiverRegistered;

  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent == null) return;
          String pageId = intent.getStringExtra(OcrBackgroundJobs.EXTRA_PAGE_ID);
          boolean success = intent.getBooleanExtra(OcrBackgroundJobs.EXTRA_SUCCESS, false);
          onPageDone(pageId, success);
        }
      };

  /**
   * Creates the extraction for the pages of an export session.
   *
   * @param pageHeaderFormat format with one {@code %d} placeholder, used to separate pages in the
   *     combined text
   */
  DocumentTextExtraction(
      Context context,
      ExportSessionViewModel session,
      Supplier<OCRHelper> helperSupplier,
      String pageHeaderFormat) {
    this.app = context.getApplicationContext();
    this.session = session;
    this.helperSupplier = helperSupplier;
    this.pageHeaderFormat = pageHeaderFormat;
  }

  boolean isRunning() {
    return running;
  }

  /**
   * Starts the extraction.
   *
   * @param language OCR language spec (as used by the single-page extraction)
   * @param force re-run pages that already have a text
   */
  @MainThread
  void start(String language, boolean force, Listener l) {
    if (running) return;
    listener = l;
    pending.clear();
    failed = 0;
    List<CompletedScan> pages = currentPages();
    for (CompletedScan p : pages) {
      if (p != null && p.id() != null && (force || !PageOcrStore.hasOcr(p))) pending.add(p.id());
    }
    total = pending.size();
    if (total == 0) {
      collectText();
      return;
    }
    running = true;
    registerReceiver();
    notifyProgress();
    for (String id : new ArrayList<>(pending)) {
      OcrBackgroundJobs.enqueueReprocess(app, id, language, helperSupplier);
    }
  }

  /** Stops waiting for results and cancels the page jobs that did not finish yet. */
  @MainThread
  void cancel() {
    for (String id : pending) OcrBackgroundJobs.cancel(id);
    pending.clear();
    running = false;
    unregisterReceiver();
  }

  /** Cancels any running extraction and releases resources. */
  @MainThread
  void release() {
    cancel();
    listener = null;
    io.shutdownNow();
  }

  private void onPageDone(String pageId, boolean success) {
    if (!running || pageId == null || !pending.remove(pageId)) return;
    if (success) {
      SessionOcrUpdater.applyOcrResultToSession(app, session, pageId);
    } else {
      failed++;
    }
    notifyProgress();
    if (pending.isEmpty()) {
      running = false;
      unregisterReceiver();
      collectText();
    }
  }

  private void notifyProgress() {
    if (listener != null) listener.onProgress(total - pending.size(), total);
  }

  /** Reads the text of every page (off the main thread) and reports the combined result. */
  private void collectText() {
    final List<CompletedScan> pages = currentPages();
    final int failedPages = failed;
    try {
      io.execute(
          () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
              CompletedScan p = pages.get(i);
              if (p == null) continue;
              // Prefer the registry entry: it is updated by the job before the session is.
              CompletedScan stored = PageOcrStore.find(app, p.id());
              String text = PageOcrStore.readText(stored != null ? stored : p);
              if (text == null || text.trim().isEmpty()) continue;
              if (sb.length() > 0) sb.append("\n\n");
              sb.append(String.format(pageHeaderFormat, i + 1)).append('\n').append(text.trim());
            }
            final String combined = sb.toString();
            main.post(
                () -> {
                  if (listener != null) listener.onFinished(combined, failedPages);
                });
          });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      Log.w(TAG, "collectText after release", e);
    }
  }

  private List<CompletedScan> currentPages() {
    List<CompletedScan> pages = session != null ? session.getPages().getValue() : null;
    return pages != null ? new ArrayList<>(pages) : new ArrayList<>();
  }

  private void registerReceiver() {
    if (receiverRegistered) return;
    try {
      ContextCompat.registerReceiver(
          app,
          receiver,
          new IntentFilter(OcrBackgroundJobs.ACTION_OCR_UPDATED),
          ContextCompat.RECEIVER_NOT_EXPORTED);
      receiverRegistered = true;
    } catch (Throwable t) {
      Log.w(TAG, "registerReceiver failed", t);
    }
  }

  private void unregisterReceiver() {
    if (!receiverRegistered) return;
    try {
      app.unregisterReceiver(receiver);
    } catch (Throwable ignore) {
      // Already unregistered
    }
    receiverRegistered = false;
  }
}
