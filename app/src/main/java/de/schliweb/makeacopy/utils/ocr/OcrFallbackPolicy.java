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
 * Policy that decides whether the OCR pipeline should run a full-page fallback OCR pass after the
 * layout-analysis path produced a clearly poor result.
 *
 * <p>Motivation: layout analysis can mis-segment a page (false table detection, sparse-text PSM on
 * the main body, …). When that happens the per-region OCR may return very few or very
 * low-confidence words. In that case running one additional full-page pass with PSM=AUTO on a
 * non-binarized image often recovers the bulk of the printed text. For documents where layout
 * analysis works well, the trigger stays inactive and there is no extra cost.
 *
 * <p>This class is a pure function and intentionally has no Android dependencies so that it is
 * fully covered by JVM unit tests.
 */
public final class OcrFallbackPolicy {

  /**
   * If the layout-analysis path produced fewer than this many words, the result is considered too
   * sparse to trust and a full-page fallback OCR pass is justified.
   */
  public static final int DEFAULT_MIN_TOTAL_WORDS = 20;

  /**
   * If the layout-analysis path produced a mean confidence below this value, the result is
   * considered too weak to trust and a full-page fallback OCR pass is justified — even if the word
   * count is high.
   */
  public static final int DEFAULT_MIN_MEAN_CONF = 50;

  private OcrFallbackPolicy() {
    // utility
  }

  /**
   * Returns {@code true} if the layout-analysis result is poor enough to justify running one
   * additional full-page OCR pass as a fallback.
   *
   * @param totalWords total number of recognized words across all regions
   * @param meanConf mean confidence reported across all regions (0..100)
   * @return {@code true} iff a fallback should run
   */
  public static boolean shouldRunFullPageFallback(int totalWords, int meanConf) {
    return shouldRunFullPageFallback(
        totalWords, meanConf, DEFAULT_MIN_TOTAL_WORDS, DEFAULT_MIN_MEAN_CONF);
  }

  /**
   * Variant with explicit thresholds, primarily for testing and tuning.
   *
   * @param totalWords total number of recognized words across all regions
   * @param meanConf mean confidence reported across all regions (0..100)
   * @param minTotalWords minimum acceptable word count
   * @param minMeanConf minimum acceptable mean confidence
   * @return {@code true} iff either threshold is breached
   */
  public static boolean shouldRunFullPageFallback(
      int totalWords, int meanConf, int minTotalWords, int minMeanConf) {
    return totalWords < minTotalWords || meanConf < minMeanConf;
  }
}
