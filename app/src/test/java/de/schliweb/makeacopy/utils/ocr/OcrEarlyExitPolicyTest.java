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

/**
 * Unit tests for {@link OcrEarlyExitPolicy}.
 *
 * <p>The reference cases mirror the two production documents that motivated the
 * change: a hard appointment receipt (must NOT early-exit) and a clean salary
 * statement (must early-exit).
 */
public class OcrEarlyExitPolicyTest {

  // ── Production reference cases ────────────────────────────────────────────

  @Test
  public void appointmentReceipt_doesNotEarlyExit() {
    // Logged values for DOC_20260427_162929.jpg: meanConf=55, words=12, textLen=52.
    // Old policy (meanConf >= 55) would have early-exited and prevented recovery.
    assertFalse(OcrEarlyExitPolicy.shouldExit(55, 12, 52));
  }

  @Test
  public void salaryStatement_earlyExits() {
    // Representative for a clean printed document — high meanConf, lots of words.
    assertTrue(OcrEarlyExitPolicy.shouldExit(85, 250, 1500));
  }

  // ── Threshold boundary cases ──────────────────────────────────────────────

  @Test
  public void exactlyAtAllDefaults_earlyExits() {
    assertTrue(
        OcrEarlyExitPolicy.shouldExit(
            OcrEarlyExitPolicy.DEFAULT_MIN_MEAN_CONF,
            OcrEarlyExitPolicy.DEFAULT_MIN_WORDS,
            OcrEarlyExitPolicy.DEFAULT_MIN_TEXT_LEN));
  }

  @Test
  public void oneBelowMeanConf_doesNotEarlyExit() {
    assertFalse(OcrEarlyExitPolicy.shouldExit(69, 30, 200));
  }

  @Test
  public void oneBelowWords_doesNotEarlyExit() {
    assertFalse(OcrEarlyExitPolicy.shouldExit(70, 29, 200));
  }

  @Test
  public void oneBelowTextLen_doesNotEarlyExit() {
    assertFalse(OcrEarlyExitPolicy.shouldExit(70, 30, 199));
  }

  // ── Misleading "high confidence on tiny text" case ────────────────────────

  @Test
  public void highConfButTinyContent_doesNotEarlyExit() {
    // 90% mean confidence on five short words is not a real document.
    assertFalse(OcrEarlyExitPolicy.shouldExit(90, 5, 20));
  }

  // ── Custom thresholds ─────────────────────────────────────────────────────

  @Test
  public void customThresholds_areHonored() {
    assertTrue(OcrEarlyExitPolicy.shouldExit(50, 10, 50, 50, 10, 50));
    assertFalse(OcrEarlyExitPolicy.shouldExit(49, 10, 50, 50, 10, 50));
  }
}
