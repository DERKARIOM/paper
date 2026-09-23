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

import org.junit.Test;

/**
 * Unit tests for {@link UnevenLightingPolicy}.
 *
 * <p>Calibrated against the two production reference images:
 *
 * <ul>
 *   <li>{@code DOC_20260427_162929.jpg} (appointment receipt with green letterhead):
 *       measured T/B-diff ≈ 21.3 — must be classified as <b>even</b> with the new
 *       threshold of 28, since the difference comes from layout (green band) rather
 *       than a real shadow.
 *   <li>{@code DOC_20260427_163504.jpg} (clean salary statement): measured max diff
 *       ≈ 9.9 — must be classified as <b>even</b>.
 * </ul>
 */
public class UnevenLightingPolicyTest {

  // ── Edge cases ────────────────────────────────────────────────────────────

  @Test
  public void nullInput_returnsZero() {
    assertEquals(0.0, UnevenLightingPolicy.maxHalfDiff(null, 10, 10), 0.0);
  }

  @Test
  public void tooSmall_returnsZero() {
    assertEquals(0.0, UnevenLightingPolicy.maxHalfDiff(new int[1], 1, 1), 0.0);
  }

  @Test
  public void uniformImage_isEven() {
    int[] px = filled(64, 64, 0xFF808080);
    assertEquals(0.0, UnevenLightingPolicy.maxHalfDiff(px, 64, 64), 0.5);
    assertFalse(UnevenLightingPolicy.isUneven(px, 64, 64));
  }

  // ── Synthetic gradients ───────────────────────────────────────────────────

  @Test
  public void verticalShadow_isUneven() {
    // Top half ~ luminance 80, bottom half ~ luminance 220 → diff ≈ 140.
    int w = 64, h = 64;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 80 : 220;
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.maxHalfDiff(px, w, h) > 100);
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h));
  }

  @Test
  public void horizontalShadow_isUneven() {
    int w = 64, h = 64;
    int[] px = new int[w * h];
    for (int x = 0; x < w; x++) {
      int v = (x < w / 2) ? 80 : 220;
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int y = 0; y < h; y++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h));
  }

  // ── Threshold calibration cases (mirrors production reference images) ────

  @Test
  public void diffOf21_isEven_atDefaultThreshold() {
    // Approximates the appointment-receipt case (green header on white page):
    // top half slightly darker than bottom by ~21 luminance units.
    int w = 64, h = 64;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 189 : 210; // diff = 21
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    double d = UnevenLightingPolicy.maxHalfDiff(px, w, h);
    assertEquals(21.0, d, 0.5);
    assertFalse("21 must be considered even at default threshold 28",
        UnevenLightingPolicy.isUneven(px, w, h));
  }

  @Test
  public void diffOf30_isUneven_atDefaultThreshold() {
    // Genuine shadow gradient — must trigger.
    int w = 64, h = 64;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 180 : 210; // diff = 30
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h));
  }

  // ── Custom threshold ──────────────────────────────────────────────────────

  @Test
  public void customThreshold_isHonored() {
    int w = 64, h = 64;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 100 : 110; // diff = 10
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h, 5.0));
    assertFalse(UnevenLightingPolicy.isUneven(px, w, h, 15.0));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static int[] filled(int w, int h, int argb) {
    int[] px = new int[w * h];
    java.util.Arrays.fill(px, argb);
    return px;
  }
}
