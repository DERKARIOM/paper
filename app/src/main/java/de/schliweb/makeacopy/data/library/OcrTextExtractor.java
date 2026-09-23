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

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.WordsJson;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/** Extracts normalized OCR text from app-private scan sidecar files for search indexing. */
public class OcrTextExtractor {
  public static final String FORMAT_PAGE_OCR_JSON = "page_ocr_json";
  public static final String FORMAT_WORDS_JSON = "words_json";
  public static final String FORMAT_PLAIN = "plain";

  public ExtractedText extract(File scanDir, String registeredOcrPath, String registeredOcrFormat)
      throws IOException {
    if (scanDir == null) return ExtractedText.empty();

    File pageOcr = new File(scanDir, "page.ocr.json");
    ExtractedText fromPageOcr = extractWords(pageOcr, FORMAT_PAGE_OCR_JSON);
    if (!fromPageOcr.isEmpty()) return fromPageOcr;

    File words = new File(scanDir, "words.json");
    ExtractedText fromWords = extractWords(words, FORMAT_WORDS_JSON);
    if (!fromWords.isEmpty()) return fromWords;

    File text = new File(scanDir, "text.txt");
    ExtractedText fromText = extractPlain(text);
    if (!fromText.isEmpty()) return fromText;

    if (registeredOcrPath != null && !registeredOcrPath.trim().isEmpty()) {
      File registered = new File(registeredOcrPath);
      if (FORMAT_WORDS_JSON.equals(registeredOcrFormat)) {
        ExtractedText extracted = extractWords(registered, FORMAT_WORDS_JSON);
        if (!extracted.isEmpty()) return extracted;
      }
      ExtractedText extracted = extractPlain(registered);
      if (!extracted.isEmpty()) return extracted;
    }

    return ExtractedText.empty();
  }

  private ExtractedText extractWords(File file, String format) throws IOException {
    if (missingReadableFile(file)) return ExtractedText.empty();
    List<RecognizedWord> words = WordsJson.parseFile(file);
    if (words == null || words.isEmpty()) return ExtractedText.empty();
    words.sort(
        Comparator.comparingDouble(RecognizedWord::midY)
            .thenComparingDouble(RecognizedWord::centerX));
    StringBuilder builder = new StringBuilder();
    for (RecognizedWord word : words) {
      if (word == null || word.getText() == null) continue;
      String text = word.getText().trim();
      if (text.isEmpty()) continue;
      if (builder.length() > 0) builder.append(' ');
      builder.append(text);
    }
    return fromText(file, format, builder.toString());
  }

  private ExtractedText extractPlain(File file) throws IOException {
    if (missingReadableFile(file)) return ExtractedText.empty();
    return fromText(file, FORMAT_PLAIN, readUtf8(file));
  }

  private static String readUtf8(File file) throws IOException {
    byte[] data = new byte[(int) file.length()];
    int offset = 0;
    try (FileInputStream input = new FileInputStream(file)) {
      while (offset < data.length) {
        int read = input.read(data, offset, data.length - offset);
        if (read < 0) break;
        offset += read;
      }
    }
    return new String(data, 0, offset, StandardCharsets.UTF_8);
  }

  private ExtractedText fromText(File file, String format, String text) throws IOException {
    String normalized = normalize(text);
    if (normalized.isEmpty()) return ExtractedText.empty();
    return new ExtractedText(
        normalized, file.getAbsolutePath(), format, sha256(normalized), file.lastModified());
  }

  private static boolean missingReadableFile(File file) {
    return file == null || !file.isFile() || !file.canRead();
  }

  private static String normalize(String text) {
    if (text == null) return "";
    return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String sha256(String text) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        builder.append(String.format(java.util.Locale.US, "%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 unavailable", e);
    }
  }

  public static class ExtractedText {
    public final String text;
    public final String sourcePath;
    public final String sourceFormat;
    public final String textHash;
    public final long sourceUpdatedAt;

    ExtractedText(
        String text,
        String sourcePath,
        String sourceFormat,
        String textHash,
        long sourceUpdatedAt) {
      this.text = text;
      this.sourcePath = sourcePath;
      this.sourceFormat = sourceFormat;
      this.textHash = textHash;
      this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public boolean isEmpty() {
      return text == null || text.isEmpty();
    }

    static ExtractedText empty() {
      return new ExtractedText("", null, null, null, 0L);
    }
  }
}
