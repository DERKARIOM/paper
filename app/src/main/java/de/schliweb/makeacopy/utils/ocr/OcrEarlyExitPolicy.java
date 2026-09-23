/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

/**
 * Policy that decides whether the OCR pipeline can stop after the first rotation attempt (extra=0).
 * The intent is: only skip further rotations when the result is <em>genuinely</em> good — not
 * merely "non-empty".
 *
 * <p>Historically the pipeline early-exited on {@code meanConf >= 55} alone. This is too lenient: a
 * result with 12 garbage words at meanConf 55 would trigger the exit and prevent any recovery via
 * rotation or full-page fallback. The new policy additionally requires a minimum word count and a
 * minimum text length, which together act as a "this looks like a real document"-gate.
 *
 * <p>This class is a pure function and intentionally has no Android dependencies so that it is
 * fully covered by JVM unit tests.
 */
public final class OcrEarlyExitPolicy {

  /**
   * Default minimum mean confidence required to early-exit.
   *
   * <p>Tuned against two reference documents: a hard appointment receipt (meanConf around 55, ~12
   * words — must NOT exit) and a clean salary statement (meanConf around 80, hundreds of words —
   * must exit).
   */
  public static final int DEFAULT_MIN_MEAN_CONF = 70;

  /** Default minimum number of recognized words required to early-exit. */
  public static final int DEFAULT_MIN_WORDS = 30;

  /** Default minimum length of the recognized text required to early-exit. */
  public static final int DEFAULT_MIN_TEXT_LEN = 200;

  private OcrEarlyExitPolicy() {
    // utility
  }

  /**
   * Returns {@code true} if the OCR pipeline should stop after the first attempt because the result
   * is already strong enough.
   *
   * @param meanConf mean confidence reported by Tesseract (0..100)
   * @param words number of recognized words
   * @param textLen length of the recognized text in characters
   * @return {@code true} iff all three minima are satisfied
   */
  public static boolean shouldExit(int meanConf, int words, int textLen) {
    return shouldExit(
        meanConf, words, textLen, DEFAULT_MIN_MEAN_CONF, DEFAULT_MIN_WORDS, DEFAULT_MIN_TEXT_LEN);
  }

  /**
   * Variant with explicit thresholds, primarily for testing and tuning.
   *
   * @param meanConf mean confidence reported by Tesseract (0..100)
   * @param words number of recognized words
   * @param textLen length of the recognized text in characters
   * @param minMeanConf minimum mean confidence
   * @param minWords minimum word count
   * @param minTextLen minimum text length
   * @return {@code true} iff all three minima are satisfied
   */
  public static boolean shouldExit(
      int meanConf, int words, int textLen, int minMeanConf, int minWords, int minTextLen) {
    return meanConf >= minMeanConf && words >= minWords && textLen >= minTextLen;
  }
}
