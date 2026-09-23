/*
 * Copyright (c) 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package de.schliweb.makeacopy.utils.ocr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for {@link OcrFallbackPolicy}. */
public class OcrFallbackPolicyTest {

  @Test
  public void fewWords_triggersFallback() {
    // Layout-analysis path returned only 12 words on the appointment receipt.
    assertTrue(OcrFallbackPolicy.shouldRunFullPageFallback(12, 55));
  }

  @Test
  public void lowMeanConf_triggersFallback_evenWithEnoughWords() {
    // Many words but they're garbage — still warrants a fallback pass.
    assertTrue(OcrFallbackPolicy.shouldRunFullPageFallback(100, 40));
  }

  @Test
  public void healthyResult_doesNotTriggerFallback() {
    // Clean salary statement: hundreds of words, conf in the 80s.
    assertFalse(OcrFallbackPolicy.shouldRunFullPageFallback(250, 80));
  }

  @Test
  public void exactlyAtMinTotalWordsAndMinConf_doesNotTrigger() {
    assertFalse(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS,
            OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF));
  }

  @Test
  public void oneWordBelowMin_triggers() {
    assertTrue(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS - 1,
            OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF));
  }

  @Test
  public void oneConfBelowMin_triggers() {
    assertTrue(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS,
            OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF - 1));
  }

  @Test
  public void customThresholds_areHonored() {
    assertTrue(OcrFallbackPolicy.shouldRunFullPageFallback(9, 80, 10, 50));
    assertFalse(OcrFallbackPolicy.shouldRunFullPageFallback(10, 50, 10, 50));
  }
}
