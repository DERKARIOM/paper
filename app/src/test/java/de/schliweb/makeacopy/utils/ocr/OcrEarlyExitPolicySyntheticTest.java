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
 * Synthetic / property-style tests for {@link OcrEarlyExitPolicy}.
 *
 * <p>Complements {@link OcrEarlyExitPolicyTest} by sampling random inputs across
 * the relevant ranges and verifying invariants:
 *
 * <ul>
 *   <li>Monotonicity: improving any single input never turns a {@code true} into
 *       a {@code false}.
 *   <li>The default-arg overload agrees with the parameterized one when called
 *       with the documented defaults.
 *   <li>The decision is the conjunction of three independent gates.
 * </ul>
 */
public class OcrEarlyExitPolicySyntheticTest {

  private static final int TRIALS = 500;

  // ── Monotonicity ──────────────────────────────────────────────────────────

  @Test
  public void increasingMeanConf_preservesShouldExit() {
    Random rnd = new Random(7L);
    for (int i = 0; i < TRIALS; i++) {
      int mc = rnd.nextInt(101);
      int w = rnd.nextInt(500);
      int t = rnd.nextInt(3000);
      boolean before = OcrEarlyExitPolicy.shouldExit(mc, w, t);
      // Increase meanConf by up to 20 (capped at 100).
      int mcUp = Math.min(100, mc + rnd.nextInt(21));
      boolean after = OcrEarlyExitPolicy.shouldExit(mcUp, w, t);
      assertTrue(
          "raising meanConf flipped true→false at (" + mc + "," + w + "," + t + ")",
          !before || after);
    }
  }

  @Test
  public void increasingWords_preservesShouldExit() {
    Random rnd = new Random(11L);
    for (int i = 0; i < TRIALS; i++) {
      int mc = rnd.nextInt(101);
      int w = rnd.nextInt(500);
      int t = rnd.nextInt(3000);
      boolean before = OcrEarlyExitPolicy.shouldExit(mc, w, t);
      int wUp = w + rnd.nextInt(50);
      boolean after = OcrEarlyExitPolicy.shouldExit(mc, wUp, t);
      assertTrue(
          "raising words flipped true→false at (" + mc + "," + w + "," + t + ")",
          !before || after);
    }
  }

  @Test
  public void increasingTextLen_preservesShouldExit() {
    Random rnd = new Random(13L);
    for (int i = 0; i < TRIALS; i++) {
      int mc = rnd.nextInt(101);
      int w = rnd.nextInt(500);
      int t = rnd.nextInt(3000);
      boolean before = OcrEarlyExitPolicy.shouldExit(mc, w, t);
      int tUp = t + rnd.nextInt(500);
      boolean after = OcrEarlyExitPolicy.shouldExit(mc, w, tUp);
      assertTrue(
          "raising textLen flipped true→false at (" + mc + "," + w + "," + t + ")",
          !before || after);
    }
  }

  // ── Default vs. parameterized agreement ───────────────────────────────────

  @Test
  public void defaultsAgreeWithParameterizedOverload() {
    Random rnd = new Random(17L);
    for (int i = 0; i < TRIALS; i++) {
      int mc = rnd.nextInt(101);
      int w = rnd.nextInt(500);
      int t = rnd.nextInt(3000);
      assertEquals(
          OcrEarlyExitPolicy.shouldExit(mc, w, t),
          OcrEarlyExitPolicy.shouldExit(
              mc,
              w,
              t,
              OcrEarlyExitPolicy.DEFAULT_MIN_MEAN_CONF,
              OcrEarlyExitPolicy.DEFAULT_MIN_WORDS,
              OcrEarlyExitPolicy.DEFAULT_MIN_TEXT_LEN));
    }
  }

  // ── Decision is a strict AND of three gates ───────────────────────────────

  @Test
  public void anySingleGateBelowDefaults_blocksExit() {
    int mc = OcrEarlyExitPolicy.DEFAULT_MIN_MEAN_CONF;
    int w = OcrEarlyExitPolicy.DEFAULT_MIN_WORDS;
    int t = OcrEarlyExitPolicy.DEFAULT_MIN_TEXT_LEN;
    // Each axis dropped one below its default must veto, even if the other two
    // are far above their defaults.
    assertFalse(OcrEarlyExitPolicy.shouldExit(mc - 1, w + 1000, t + 1000));
    assertFalse(OcrEarlyExitPolicy.shouldExit(mc + 30, w - 1, t + 1000));
    assertFalse(OcrEarlyExitPolicy.shouldExit(mc + 30, w + 1000, t - 1));
  }

  // ── Robustness against extreme but legal inputs ───────────────────────────

  @Test
  public void zeroAndMaxInputs_doNotThrow() {
    OcrEarlyExitPolicy.shouldExit(0, 0, 0);
    OcrEarlyExitPolicy.shouldExit(100, Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Test
  public void zeroDocument_doesNotEarlyExit() {
    assertFalse(OcrEarlyExitPolicy.shouldExit(0, 0, 0));
  }

  @Test
  public void maxedOutDocument_earlyExits() {
    assertTrue(
        OcrEarlyExitPolicy.shouldExit(100, Integer.MAX_VALUE, Integer.MAX_VALUE));
  }
}
