/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM unit tests for {@link CropViewTransform}.
 *
 * <p>Covers the Phase-2 step-1 invariants from
 * {@code docs/edge_drag_pan_zoom_concept.md} §4.1 / §4.3:
 *
 * <ul>
 *   <li>Identity round-trip ({@code mapViewToLocal} ∘ {@code mapLocalToView} = id).
 *   <li>Hit-test invariance under non-trivial transforms when touch events are routed
 *       through {@code mapViewToLocal} (§4.1, ViewMatrixMappingTest case in §10.1).
 *   <li>{@code postScale} clamps to {@code [MIN_SCALE, MAX_SCALE]} and preserves the
 *       focal-point invariant.
 *   <li>{@code postTranslate} is additive and commutes with itself.
 * </ul>
 */
public class CropViewTransformTest {

  private static final float EPS = 1e-3f;

  @Test
  public void identity_roundTrip() {
    CropViewTransform t = new CropViewTransform();
    assertTrue(t.isIdentity());
    assertEquals(1.0f, t.getScale(), 0f);

    float[] out = new float[2];
    t.mapViewToLocal(123.5f, 87.25f, out);
    assertEquals(123.5f, out[0], 0f);
    assertEquals(87.25f, out[1], 0f);

    t.mapLocalToView(123.5f, 87.25f, out);
    assertEquals(123.5f, out[0], 0f);
    assertEquals(87.25f, out[1], 0f);
  }

  @Test
  public void mapViewToLocal_consistentWithLocalToView_underScaleAndTranslate() {
    CropViewTransform t = new CropViewTransform();
    t.set(2.5f, -100f, -50f);

    float[] out = new float[2];
    // Round-trip through both directions.
    t.mapLocalToView(40f, 20f, out);
    float vx = out[0];
    float vy = out[1];
    t.mapViewToLocal(vx, vy, out);
    assertEquals(40f, out[0], EPS);
    assertEquals(20f, out[1], EPS);
  }

  /**
   * §10.1 ViewMatrixMappingTest: a hit-test that returns index {@code k} at view
   * coordinates {@code (x, y)} under identity must return the same index under
   * {@code scale=2.5, translate=(-100,-50)} when the touch point is first mapped via
   * {@link CropViewTransform#mapViewToLocal(float, float, float[])}.
   *
   * <p>Concretely: for any local point {@code P}, applying the forward transform yields
   * a view-space point {@code P'}, and {@code mapViewToLocal(P')} must recover {@code P}
   * exactly (within {@link #EPS}). That guarantees hit-tests in local frame are
   * invariant under {@code viewMatrix}.
   */
  @Test
  public void hitTestInvariance_acrossFiveLocalPoints() {
    CropViewTransform t = new CropViewTransform();
    t.set(2.5f, -100f, -50f);

    float[] localPoints = {
      0f, 0f,
      100f, 50f,
      540f, 787f,
      1080f, 1575f,
      -42f, 17f
    };

    float[] out = new float[2];
    for (int i = 0; i < localPoints.length; i += 2) {
      float lx = localPoints[i];
      float ly = localPoints[i + 1];
      t.mapLocalToView(lx, ly, out);
      float vx = out[0];
      float vy = out[1];
      t.mapViewToLocal(vx, vy, out);
      assertEquals("local x at index " + (i / 2), lx, out[0], EPS);
      assertEquals("local y at index " + (i / 2), ly, out[1], EPS);
    }
  }

  @Test
  public void postScale_clampsToMinScale() {
    CropViewTransform t = new CropViewTransform();
    t.set(1.5f, 0f, 0f);
    // Try to shrink below MIN_SCALE.
    t.postScale(0.1f, 0f, 0f);
    assertEquals(CropViewTransform.MIN_SCALE, t.getScale(), 0f);
  }

  @Test
  public void postScale_clampsToMaxScale() {
    CropViewTransform t = new CropViewTransform();
    t.set(5.0f, 0f, 0f);
    t.postScale(10.0f, 100f, 100f); // would yield 50 → clamp to MAX_SCALE
    assertEquals(CropViewTransform.MAX_SCALE, t.getScale(), 0f);
  }

  @Test
  public void postScale_aroundFocalPoint_preservesFocalPointInvariant() {
    // Around a focal point, that focal point should map to the same view coordinate
    // before and after the scale. This is the canonical postScale(focal) invariant.
    CropViewTransform t = new CropViewTransform();
    t.set(1.0f, 30f, 60f); // arbitrary translate
    float fx = 200f;
    float fy = 300f;

    float[] before = new float[2];
    t.mapViewToLocal(fx, fy, before);

    t.postScale(2.0f, fx, fy);

    float[] after = new float[2];
    // Apply the same view→local mapping; the focal point's *local* image must be the
    // same one we'd recover by applying the new transform.
    // Equivalent invariant: forward-mapping the previously-recovered local point under
    // the new transform must yield (fx, fy) again.
    float[] forward = new float[2];
    t.mapLocalToView(before[0], before[1], forward);
    assertEquals(fx, forward[0], EPS);
    assertEquals(fy, forward[1], EPS);
    // And the new scale equals the requested one (not clamped).
    assertEquals(2.0f, t.getScale(), EPS);
    // sanity: the local image of (fx,fy) under the new transform must differ from
    // 'before' iff the focal point isn't already the local origin (pre-translate).
    t.mapViewToLocal(fx, fy, after);
    // Focal-point local image is preserved under postScale-around-focal:
    assertEquals(before[0], after[0], EPS);
    assertEquals(before[1], after[1], EPS);
  }

  @Test
  public void postScale_ignoresInvalidFactors() {
    CropViewTransform t = new CropViewTransform();
    t.set(2.0f, 10f, 20f);

    t.postScale(0f, 0f, 0f); // zero
    assertEquals(2.0f, t.getScale(), 0f);
    t.postScale(-1f, 0f, 0f); // negative
    assertEquals(2.0f, t.getScale(), 0f);
    t.postScale(Float.NaN, 0f, 0f); // NaN
    assertEquals(2.0f, t.getScale(), 0f);
    t.postScale(Float.POSITIVE_INFINITY, 0f, 0f); // infinity
    assertEquals(2.0f, t.getScale(), 0f);
  }

  @Test
  public void postTranslate_isAdditive() {
    CropViewTransform t = new CropViewTransform();
    t.postTranslate(10f, -5f);
    t.postTranslate(-3f, 8f);
    assertEquals(7f, t.getTx(), EPS);
    assertEquals(3f, t.getTy(), EPS);
  }

  @Test
  public void reset_returnsToIdentity() {
    CropViewTransform t = new CropViewTransform();
    t.set(3.5f, 100f, -50f);
    assertFalse(t.isIdentity());
    t.reset();
    assertTrue(t.isIdentity());
    assertEquals(1.0f, t.getScale(), 0f);
    assertEquals(0f, t.getTx(), 0f);
    assertEquals(0f, t.getTy(), 0f);
  }

  @Test
  public void set_clampsScaleButPreservesTranslation() {
    CropViewTransform t = new CropViewTransform();
    t.set(100.0f, 50f, 60f);
    assertEquals(CropViewTransform.MAX_SCALE, t.getScale(), 0f);
    assertEquals(50f, t.getTx(), 0f);
    assertEquals(60f, t.getTy(), 0f);

    t.set(0.01f, -20f, -30f);
    assertEquals(CropViewTransform.MIN_SCALE, t.getScale(), 0f);
    assertEquals(-20f, t.getTx(), 0f);
    assertEquals(-30f, t.getTy(), 0f);
  }

  // === Phase 2 step 2/4 invariants ===

  /**
   * Pinch-zoom then pinch back almost to identity: the residual scale is well below the
   * snap threshold (1.05) used by {@code TrapezoidSelectionView.maybeSnapToIdentity}.
   * After {@link CropViewTransform#reset()} the transform is identity and the round-trip
   * is again the identity mapping.
   */
  @Test
  public void pinchSequence_almostBackToIdentity_isSnappable() {
    CropViewTransform t = new CropViewTransform();
    // Pinch out to 2.5x around (200, 300).
    t.postScale(2.5f, 200f, 300f);
    assertEquals(2.5f, t.getScale(), EPS);
    // Pinch back by 1/2.4 (slightly less than the inverse) — leaves a ~1.04 residual
    // scale, which the host view's snap threshold (1.05) collapses to identity.
    t.postScale(1f / 2.4f, 200f, 300f);
    assertTrue("scale should be < 1.05 (snap region)", t.getScale() < 1.05f);
    assertTrue("scale should be >= MIN_SCALE", t.getScale() >= CropViewTransform.MIN_SCALE);
    // Simulate the snap.
    t.reset();
    assertTrue(t.isIdentity());
    float[] out = new float[2];
    t.mapViewToLocal(42f, 17f, out);
    assertEquals(42f, out[0], 0f);
    assertEquals(17f, out[1], 0f);
  }

  /**
   * Double-tap toggle from identity: postScale(2.5x) around the focal point puts the
   * transform into a non-identity state; the focal point then maps back to itself.
   * A subsequent {@code reset} recovers identity (the `else` branch of
   * {@code TrapezoidSelectionView.onDoubleTapZoom}).
   */
  @Test
  public void doubleTapToggle_zoomsThenResets() {
    CropViewTransform t = new CropViewTransform();
    float fx = 540f, fy = 787f;
    // First tap: identity → 2.5x around focal.
    assertTrue(t.isIdentity());
    t.postScale(2.5f, fx, fy);
    assertFalse(t.isIdentity());
    assertEquals(2.5f, t.getScale(), EPS);
    // Focal-point invariant: after postScale(focal), the focal view-point still maps to
    // the same local point as before the scale (canonical pinch invariant).
    float[] localAtFocal = new float[2];
    t.mapViewToLocal(fx, fy, localAtFocal);
    float[] forward = new float[2];
    t.mapLocalToView(localAtFocal[0], localAtFocal[1], forward);
    assertEquals(fx, forward[0], EPS);
    assertEquals(fy, forward[1], EPS);
    // Second tap: reset to identity.
    t.reset();
    assertTrue(t.isIdentity());
  }

  /**
   * Repeated postScale() never leaves the [MIN_SCALE, MAX_SCALE] interval, even when
   * the gesture pumps many tiny incremental factors (simulates an overzealous pinch).
   */
  @Test
  public void repeatedPostScale_staysWithinClampInterval() {
    CropViewTransform t = new CropViewTransform();
    for (int i = 0; i < 50; i++) {
      t.postScale(2.0f, 100f, 100f);
      assertTrue("scale must stay <= MAX after step " + i,
          t.getScale() <= CropViewTransform.MAX_SCALE + 1e-6f);
      assertTrue("scale must stay >= MIN after step " + i,
          t.getScale() >= CropViewTransform.MIN_SCALE - 1e-6f);
    }
    assertEquals(CropViewTransform.MAX_SCALE, t.getScale(), 0f);
    for (int i = 0; i < 50; i++) {
      t.postScale(0.5f, 100f, 100f);
    }
    assertEquals(CropViewTransform.MIN_SCALE, t.getScale(), 0f);
  }

  // === Phase 2 step 3 (implicit Pan) invariants — clampTranslateToView ===

  /**
   * Default identity (scale = 1) is below {@code mappedW >= minOverlapW} only if the image
   * fills less than the required overlap, which it does at scale=1. The clamp is a no-op for
   * an axis whose mapped extent is below the required overlap; this avoids "centring" the
   * transform when the image is smaller than the view.
   */
  @Test
  public void clampTranslate_atIdentityWithSmallerImage_isNoOp() {
    CropViewTransform t = new CropViewTransform();
    t.postTranslate(500f, 500f);
    // Image rect is 100x80 in local coords; view is 1000x1000. mappedW=100 < 100 (10% of 1000).
    // Clamp must not change tx/ty — image is smaller than required overlap on both axes.
    t.clampTranslateToView(0f, 0f, 100f, 80f, 1000f, 1000f, 0.10f);
    assertEquals(500f, t.getTx(), EPS);
    assertEquals(500f, t.getTy(), EPS);
  }

  /**
   * With scale = 2 and a 1000x800 image displayed in a 1000x800 view, the mapped image is
   * 2000x1600 — i.e. larger than the view in both axes. Trying to translate it far off-screen
   * (tx=5000, ty=5000) must be clamped so that at least 10% of the view is still covered,
   * which yields a fixed maximum positive translation in each axis.
   */
  @Test
  public void clampTranslate_keepsAtLeast10pctViewOverlap_whenZoomedIn() {
    CropViewTransform t = new CropViewTransform();
    t.set(2.0f, 5000f, 5000f);
    t.clampTranslateToView(0f, 0f, 1000f, 800f, 1000f, 800f, 0.10f);
    // Allowed range:
    //   txMin = minOverlapW - scale*imgRight = 100 - 2*1000 = -1900
    //   txMax = viewWidth - minOverlapW - scale*imgLeft = 1000 - 100 - 0 = 900
    // tx was 5000 → clamped to 900.
    assertEquals(900f, t.getTx(), EPS);
    //   tyMin = minOverlapH - scale*imgBottom = 80 - 2*800 = -1520
    //   tyMax = viewHeight - minOverlapH - scale*imgTop = 800 - 80 - 0 = 720
    // ty was 5000 → clamped to 720.
    assertEquals(720f, t.getTy(), EPS);
  }

  /** Negative-runaway translation is clamped to the corresponding minimum. */
  @Test
  public void clampTranslate_negativeRunaway_isClampedToMin() {
    CropViewTransform t = new CropViewTransform();
    t.set(2.0f, -10000f, -10000f);
    t.clampTranslateToView(0f, 0f, 1000f, 800f, 1000f, 800f, 0.10f);
    // txMin = 100 - 2*1000 = -1900
    // tyMin = 80 - 2*800 = -1520
    assertEquals(-1900f, t.getTx(), EPS);
    assertEquals(-1520f, t.getTy(), EPS);
  }
}
