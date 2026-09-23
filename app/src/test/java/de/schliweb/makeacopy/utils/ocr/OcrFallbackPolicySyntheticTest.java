/*
 * Copyright (c) 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package de.schliweb.makeacopy.utils.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

/**
 * Synthetic / property-style tests for {@link OcrFallbackPolicy}.
 *
 * <p>The fallback decision is the OR of two gates:
 *
 * <pre>
 *   trigger  ⇔  totalWords &lt; minTotalWords  ∨  meanConf &lt; minMeanConf
 * </pre>
 *
 * <p>The tests below verify monotonicity (improving any gate cannot turn a
 * trigger off), default/parameterized agreement, and that each gate can fire
 * independently.
 */
public class OcrFallbackPolicySyntheticTest {

  private static final int TRIALS = 500;

  // ── Monotonicity: improving inputs cannot create a trigger ────────────────

  @Test
  public void increasingWords_neverTurnsOff_intoOn() {
    Random rnd = new Random(23L);
    for (int i = 0; i < TRIALS; i++) {
      int w = rnd.nextInt(300);
      int mc = rnd.nextInt(101);
      boolean before = OcrFallbackPolicy.shouldRunFullPageFallback(w, mc);
      int wUp = w + rnd.nextInt(60);
      boolean after = OcrFallbackPolicy.shouldRunFullPageFallback(wUp, mc);
      // If "before" was false, "after" must also be false (more words can only
      // help; it can never start the trigger on its own).
      assertTrue(
          "raising words turned trigger from off to on at (" + w + "," + mc + ")",
          before || !after);
    }
  }

  @Test
  public void increasingMeanConf_neverTurnsOff_intoOn() {
    Random rnd = new Random(29L);
    for (int i = 0; i < TRIALS; i++) {
      int w = rnd.nextInt(300);
      int mc = rnd.nextInt(101);
      boolean before = OcrFallbackPolicy.shouldRunFullPageFallback(w, mc);
      int mcUp = Math.min(100, mc + rnd.nextInt(21));
      boolean after = OcrFallbackPolicy.shouldRunFullPageFallback(w, mcUp);
      assertTrue(
          "raising meanConf turned trigger from off to on at (" + w + "," + mc + ")",
          before || !after);
    }
  }

  // ── Default vs. parameterized overload ────────────────────────────────────

  @Test
  public void defaultsAgreeWithParameterizedOverload() {
    Random rnd = new Random(31L);
    for (int i = 0; i < TRIALS; i++) {
      int w = rnd.nextInt(300);
      int mc = rnd.nextInt(101);
      assertEquals(
          OcrFallbackPolicy.shouldRunFullPageFallback(w, mc),
          OcrFallbackPolicy.shouldRunFullPageFallback(
              w,
              mc,
              OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS,
              OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF));
    }
  }

  // ── Each gate fires independently ─────────────────────────────────────────

  @Test
  public void wordGateFires_evenWithVeryHighConf() {
    // Few words but perfect confidence — still triggers (gate 1).
    assertTrue(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS - 1, 100));
  }

  @Test
  public void confGateFires_evenWithManyWords() {
    // Many words but garbage confidence — still triggers (gate 2).
    assertTrue(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS * 100,
            OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF - 1));
  }

  @Test
  public void exactlyAtBothMinima_doesNotFire() {
    // Strict less-than on both gates → being exactly at the minimum is healthy.
    assertFalse(
        OcrFallbackPolicy.shouldRunFullPageFallback(
            OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS,
            OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF));
  }

  // ── Boundary sweep ────────────────────────────────────────────────────────

  @Test
  public void boundarySweep_aroundDefaults_isStable() {
    int mw = OcrFallbackPolicy.DEFAULT_MIN_TOTAL_WORDS;
    int mc = OcrFallbackPolicy.DEFAULT_MIN_MEAN_CONF;
    for (int dw = -2; dw <= 2; dw++) {
      for (int dc = -2; dc <= 2; dc++) {
        int w = mw + dw;
        int c = mc + dc;
        boolean expected = (w < mw) || (c < mc);
        assertEquals(
            "boundary sweep mismatch at (" + w + "," + c + ")",
            expected,
            OcrFallbackPolicy.shouldRunFullPageFallback(w, c));
      }
    }
  }

  // ── Custom thresholds and degenerate cases ────────────────────────────────

  @Test
  public void customThresholds_areApplied() {
    // Use very loose custom thresholds → almost nothing should trigger.
    assertFalse(OcrFallbackPolicy.shouldRunFullPageFallback(1, 1, 0, 0));
    assertTrue(OcrFallbackPolicy.shouldRunFullPageFallback(0, 1, 1, 0));
  }

  @Test
  public void zeroInputs_alwaysTrigger_atDefaults() {
    assertTrue(OcrFallbackPolicy.shouldRunFullPageFallback(0, 0));
  }

  @Test
  public void perfectInputs_neverTrigger_atDefaults() {
    assertFalse(OcrFallbackPolicy.shouldRunFullPageFallback(Integer.MAX_VALUE, 100));
  }
}
