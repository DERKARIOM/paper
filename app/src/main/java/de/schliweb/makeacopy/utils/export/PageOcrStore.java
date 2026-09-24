/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.export;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import de.schliweb.makeacopy.data.CompletedScansRegistry;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import de.schliweb.makeacopy.utils.ocr.OCRPostProcessor;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.WordsJson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Per-page storage of text extraction (OCR) results: {@code filesDir/scans/<pageId>/text.txt} and
 * {@code words.json}, referenced from the page's {@link CompletedScansRegistry} entry.
 *
 * <p>Shared by the background OCR job and the on-demand "Extract text" screen so both write the
 * same layout, and by the export/TXT code to read it back.
 */
public final class PageOcrStore {
  private static final String TAG = "PageOcrStore";
  public static final String TEXT_FILE = "text.txt";
  public static final String WORDS_FILE = "words.json";
  public static final String FORMAT_PLAIN = "plain";
  public static final String FORMAT_WORDS_JSON = "words_json";

  private PageOcrStore() {}

  /**
   * Writes the OCR result of a page and points its registry entry to it.
   *
   * @param words recognized words with boxes in the persisted page's coordinate space, or {@code
   *     null} to store the plain text only (e.g. when the boxes would not match the stored image)
   * @return the updated registry entry, or {@code null} when the page is not in the registry
   */
  @WorkerThread
  @Nullable
  public static CompletedScan save(
      Context context, String pageId, String text, @Nullable List<RecognizedWord> words)
      throws IOException {
    if (context == null || pageId == null) return null;
    Context app = context.getApplicationContext();
    CompletedScansRegistry reg = CompletedScansRegistry.get(app);
    CompletedScan entry = find(reg, pageId);
    if (entry == null) return null;

    File dir = new File(app.getFilesDir(), "scans/" + pageId);
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
    File txt = new File(dir, TEXT_FILE);
    writeUtf8(txt, text != null ? text : "");
    String path = txt.getAbsolutePath();
    String format = FORMAT_PLAIN;
    File wordsFile = new File(dir, WORDS_FILE);
    if (words != null) {
      writeUtf8(wordsFile, WordsJson.toWordsJson(words));
      path = wordsFile.getAbsolutePath();
      format = FORMAT_WORDS_JSON;
    } else if (wordsFile.exists() && !wordsFile.delete()) {
      Log.w(TAG, "Could not delete stale " + wordsFile);
    }

    CompletedScan updated =
        new CompletedScan(
            entry.id(),
            entry.filePath(),
            entry.rotationDeg(),
            path,
            format,
            entry.thumbPath(),
            entry.createdAt(),
            entry.widthPx(),
            entry.heightPx(),
            entry.inMemoryBitmap(),
            entry.schemaVersion(),
            entry.orientationMode());
    try {
      reg.remove(entry.id());
    } catch (Throwable ignore) {
      // Best-effort; insert below replaces the entry anyway
    }
    reg.insert(updated);
    return updated;
  }

  /** Returns the registry entry of a page, or {@code null} when it is not persisted (yet). */
  @Nullable
  public static CompletedScan find(Context context, String pageId) {
    if (context == null || pageId == null) return null;
    return find(CompletedScansRegistry.get(context.getApplicationContext()), pageId);
  }

  private static CompletedScan find(CompletedScansRegistry reg, String pageId) {
    for (CompletedScan it : reg.listAllOrderedByDateDesc()) {
      if (it != null && pageId.equals(it.id())) return it;
    }
    return null;
  }

  /** True when the page references an existing OCR result file. */
  public static boolean hasOcr(@Nullable CompletedScan s) {
    String p = s != null ? s.ocrTextPath() : null;
    if (p == null) return false;
    File f = new File(p);
    return f.isFile();
  }

  /**
   * Reads the extracted text of a page: the plain text file, the {@code text.txt} next to a
   * words file, or the text rebuilt from the words. Returns {@code null} when there is none.
   */
  @WorkerThread
  @Nullable
  public static String readText(@Nullable CompletedScan s) {
    String p = s != null ? s.ocrTextPath() : null;
    if (p == null) return null;
    File f = new File(p);
    String fmt = s.ocrFormat();
    try {
      if (fmt == null || FORMAT_PLAIN.equalsIgnoreCase(fmt)) {
        return f.isFile() ? readUtf8(f) : null;
      }
      File dir = f.getParentFile();
      File txt = dir != null ? new File(dir, TEXT_FILE) : null;
      if (txt != null && txt.isFile()) return readUtf8(txt);
      if (f.isFile()) return OCRPostProcessor.wordsToText(WordsJson.parseFile(f));
    } catch (IOException | RuntimeException e) {
      Log.w(TAG, "Failed to read OCR text of page " + s.id(), e);
    }
    return null;
  }

  private static void writeUtf8(File f, String content) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(f)) {
      fos.write(content.getBytes(StandardCharsets.UTF_8));
      fos.flush();
    }
  }

  private static String readUtf8(File f) throws IOException {
    return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
  }
}
