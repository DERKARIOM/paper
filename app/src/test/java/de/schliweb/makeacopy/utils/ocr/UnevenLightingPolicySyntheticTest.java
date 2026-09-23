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
 * Synthetic tests for {@link UnevenLightingPolicy} that complement
 * {@link UnevenLightingPolicyTest} by exercising property-style invariants and
 * realistic shadow patterns generated programmatically.
 *
 * <p>These tests focus on the <em>positive</em> side of the heuristic — the side
 * that the three real reference images never exercised, since all three were
 * classified as "even".
 */
public class UnevenLightingPolicySyntheticTest {

  // ── Realistic shadow patterns ─────────────────────────────────────────────

  @Test
  public void smoothLinearGradient_topToBottom_isUneven() {
    // Models a shadow falling smoothly from top (~60) to bottom (~210).
    int w = 96, h = 96;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = 60 + (int) Math.round((150.0 * y) / (h - 1));
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h));
  }

  @Test
  public void diagonalGradient_isUneven_onAtLeastOneAxis() {
    // Diagonal gradient (corner-to-corner shadow). Both axes see a sizeable diff.
    int w = 96, h = 96;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int v = 60 + (int) Math.round((150.0 * (x + y)) / (w + h - 2));
        px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
      }
    }
    assertTrue(UnevenLightingPolicy.isUneven(px, w, h));
  }

  @Test
  public void radialVignette_isEven_byHalfDiffMetric() {
    // Vignettes darken edges symmetrically — top/bottom and left/right means stay
    // close, so the half-diff metric should NOT classify this as uneven, even
    // though pixel-wise contrast is high. Documents the metric's known blind spot.
    int w = 96, h = 96;
    int[] px = new int[w * h];
    double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
    double maxR = Math.hypot(cx, cy);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        double r = Math.hypot(x - cx, y - cy) / maxR;
        int v = (int) Math.round(220 - 150 * r);
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
      }
    }
    // Half-diff is dominated by symmetry → close to 0.
    double d = UnevenLightingPolicy.maxHalfDiff(px, w, h);
    assertTrue("vignette should produce small half-diff, got " + d, d < 5.0);
    assertFalse(UnevenLightingPolicy.isUneven(px, w, h));
  }

  @Test
  public void colouredHeaderBand_isEven() {
    // Narrow coloured header band on an otherwise white page (mimics a real
    // letterhead, calibrated to match the appointment receipt image where the
    // measured top/bottom half-diff was ≈ 21 — i.e. below the default
    // threshold of 28). The exact RGB values matter less than reproducing
    // that geometry; the assertion is on the policy decision, not the value.
    int w = 96, h = 96;
    int[] px = new int[w * h];
    int headerEnd = (int) (h * 0.10); // ~10% header band
    int headerRgb = 0xFF000000 | (130 << 16) | (180 << 8) | 140;
    int whiteRgb = 0xFFF5F5F5;
    for (int y = 0; y < h; y++) {
      int rgb = (y < headerEnd) ? headerRgb : whiteRgb;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    double d = UnevenLightingPolicy.maxHalfDiff(px, w, h);
    assertTrue(
        "narrow letterhead band should stay under default threshold, got " + d,
        d < UnevenLightingPolicy.DEFAULT_HALF_DIFF_THRESHOLD);
    assertFalse(UnevenLightingPolicy.isUneven(px, w, h));
  }

  // ── Property-style invariants ─────────────────────────────────────────────

  @Test
  public void invertingImage_doesNotChangeIsUneven() {
    // |meanTop - meanBottom| is invariant under photometric inversion.
    int w = 64, h = 64;
    int[] a = new int[w * h];
    int[] b = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 70 : 200;
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      int inv = 255 - v;
      int invRgb = 0xFF000000 | (inv << 16) | (inv << 8) | inv;
      for (int x = 0; x < w; x++) {
        a[y * w + x] = rgb;
        b[y * w + x] = invRgb;
      }
    }
    assertEquals(
        UnevenLightingPolicy.maxHalfDiff(a, w, h),
        UnevenLightingPolicy.maxHalfDiff(b, w, h),
        0.5);
  }

  @Test
  public void verticallyMirroringImage_preservesMaxHalfDiff() {
    // Flipping top<->bottom only swaps the sign of (meanTop - meanBottom) and
    // therefore preserves the absolute value used by the metric.
    int w = 64, h = 64;
    int[] a = new int[w * h];
    int[] b = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = 60 + (int) Math.round((150.0 * y) / (h - 1));
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) {
        a[y * w + x] = rgb;
        b[(h - 1 - y) * w + x] = rgb;
      }
    }
    assertEquals(
        UnevenLightingPolicy.maxHalfDiff(a, w, h),
        UnevenLightingPolicy.maxHalfDiff(b, w, h),
        0.5);
  }

  @Test
  public void thresholdMonotonicity_higherThresholdNeverFiresMore() {
    // For any image, raising the threshold cannot turn a "false" into a "true".
    int w = 32, h = 32;
    java.util.Random rnd = new java.util.Random(42L);
    for (int trial = 0; trial < 25; trial++) {
      int[] px = new int[w * h];
      // Random gradient strength in [0..120].
      int span = rnd.nextInt(121);
      int base = rnd.nextInt(256 - span);
      for (int y = 0; y < h; y++) {
        int v = base + (int) Math.round(((double) span * y) / (h - 1));
        int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
        for (int x = 0; x < w; x++) px[y * w + x] = rgb;
      }
      for (double low = 5.0; low < 200.0; low += 25.0) {
        boolean fLow = UnevenLightingPolicy.isUneven(px, w, h, low);
        boolean fHigh = UnevenLightingPolicy.isUneven(px, w, h, low + 25.0);
        assertTrue(
            "monotonicity violated at threshold pair (" + low + "," + (low + 25) + ")",
            !fHigh || fLow);
      }
    }
  }

  @Test
  public void scalingLuminance_scalesMaxHalfDiff() {
    // Halving every pixel halves the half-diff (linear in luminance).
    int w = 64, h = 64;
    int[] a = new int[w * h];
    int[] b = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < h / 2) ? 80 : 200;
      int half = v / 2;
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      int rgbHalf = 0xFF000000 | (half << 16) | (half << 8) | half;
      for (int x = 0; x < w; x++) {
        a[y * w + x] = rgb;
        b[y * w + x] = rgbHalf;
      }
    }
    double da = UnevenLightingPolicy.maxHalfDiff(a, w, h);
    double db = UnevenLightingPolicy.maxHalfDiff(b, w, h);
    assertEquals(da / 2.0, db, 1.0);
  }

  // ── Boundary geometry ─────────────────────────────────────────────────────

  @Test
  public void minimal2x2Image_isHandled() {
    // Smallest valid input. Top row dark, bottom row bright.
    int[] px = new int[] {0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF};
    double d = UnevenLightingPolicy.maxHalfDiff(px, 2, 2);
    assertTrue("expected ~255, got " + d, d > 250.0);
  }

  @Test
  public void oddDimensions_areHandled() {
    // h=5 → midY=2; rows {0,1} top, rows {2,3,4} bottom. Verify no crash and
    // a meaningful difference is reported.
    int w = 5, h = 5;
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      int v = (y < 2) ? 50 : 200;
      int rgb = 0xFF000000 | (v << 16) | (v << 8) | v;
      for (int x = 0; x < w; x++) px[y * w + x] = rgb;
    }
    assertTrue(UnevenLightingPolicy.maxHalfDiff(px, w, h) > 100.0);
  }
}
