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

/**
 * Pure 2D affine view-transform helper for the crop overlay (Pan/Zoom).
 *
 * <p>Represents a uniform-scale + translate transformation {@code viewMatrix = T(tx, ty) ·
 * S(scale)} that is later applied to the canvas in {@code TrapezoidSelectionView.onDraw} and
 * inverted to map raw touch coordinates back into the unscaled view frame in which {@code corners}
 * live (see {@code docs/edge_drag_pan_zoom_concept.md} §4.1).
 *
 * <p>This class is intentionally framework-free so it can be unit-tested on the JVM without an
 * Android dependency. The runtime {@code TrapezoidSelectionView} mirrors the same math via {@link
 * android.graphics.Matrix} for canvas concat / inversion.
 *
 * <p><b>Thread-safety:</b> instances are mutable and not thread-safe; intended for use on the UI
 * thread.
 */
public final class CropViewTransform {

  /** Default minimum scale; below this the matrix is treated as identity. */
  public static final float MIN_SCALE = 1.0f;

  /** Default maximum scale; pinch gestures are clamped to this upper bound. */
  public static final float MAX_SCALE = 6.0f;

  private float scale = 1.0f;
  private float tx = 0f;
  private float ty = 0f;

  public CropViewTransform() {
    // identity
  }

  /** Reset to identity ({@code scale=1, tx=ty=0}). */
  public void reset() {
    this.scale = 1.0f;
    this.tx = 0f;
    this.ty = 0f;
  }

  public float getScale() {
    return scale;
  }

  public float getTx() {
    return tx;
  }

  public float getTy() {
    return ty;
  }

  /** True if the transform is (approximately) identity. */
  public boolean isIdentity() {
    return Math.abs(scale - 1.0f) < 1e-4f && Math.abs(tx) < 1e-4f && Math.abs(ty) < 1e-4f;
  }

  /** Set the transform directly. Scale is clamped to {@code [MIN_SCALE, MAX_SCALE]}. */
  public void set(float scale, float tx, float ty) {
    this.scale = clampScale(scale);
    this.tx = tx;
    this.ty = ty;
  }

  /**
   * Apply a uniform scale around a focal point in <em>view</em> coordinates (analogous to {@link
   * android.graphics.Matrix#postScale(float, float, float, float)}).
   *
   * @param s scale factor (≥0); the resulting absolute scale is clamped to {@code [MIN_SCALE,
   *     MAX_SCALE]}.
   * @param focusX focal point X in view coordinates.
   * @param focusY focal point Y in view coordinates.
   */
  public void postScale(float s, float focusX, float focusY) {
    if (!(s > 0f) || Float.isNaN(s) || Float.isInfinite(s)) {
      return;
    }
    float newScale = clampScale(scale * s);
    if (newScale == scale) {
      return; // no change after clamping
    }
    float effective = newScale / scale;
    // Standard postScale around (focusX, focusY):
    //   tx' = focusX + effective * (tx - focusX)
    //   ty' = focusY + effective * (ty - focusY)
    this.tx = focusX + effective * (tx - focusX);
    this.ty = focusY + effective * (ty - focusY);
    this.scale = newScale;
  }

  /**
   * Translate by a delta in view coordinates (analogous to {@link
   * android.graphics.Matrix#postTranslate(float, float)}).
   */
  public void postTranslate(float dx, float dy) {
    this.tx += dx;
    this.ty += dy;
  }

  /**
   * Clamp the current translation so that the mapped image rectangle still overlaps the supplied
   * view rectangle by at least {@code minOverlapFraction} of the view area.
   *
   * <p>This implements the soft-clamp from {@code docs/edge_drag_pan_zoom_concept.md} §4.2: users
   * may pan a zoomed image around freely, but cannot drift it so far that the image disappears
   * off-screen (default minimum overlap: 10 % of the view in each axis).
   *
   * <p>Mapped image rect (in view coordinates) is computed as: {@code [tx + scale*imgLeft, tx +
   * scale*imgRight] × [ty + scale*imgTop, ty + scale*imgBottom]}. The clamp adjusts {@code tx, ty}
   * so that {@code mappedImg ∩ view} has at least {@code minOverlapFraction · viewExtent} pixels in
   * each axis, while remaining as close as possible to the requested {@code (tx, ty)}.
   *
   * <p>If the mapped image is smaller than the view in an axis (only possible when {@code scale <
   * 1}, which the clamp here forbids), the clamp is a no-op for that axis.
   *
   * @param imgLeft left X of the image in local coordinates (typically 0).
   * @param imgTop top Y of the image in local coordinates (typically 0).
   * @param imgRight right X of the image in local coordinates.
   * @param imgBottom bottom Y of the image in local coordinates.
   * @param viewWidth view width in pixels.
   * @param viewHeight view height in pixels.
   * @param minOverlapFraction fraction of view extent that must remain covered by image in each
   *     axis; clamped to {@code [0, 1]}.
   */
  public void clampTranslateToView(
      float imgLeft,
      float imgTop,
      float imgRight,
      float imgBottom,
      float viewWidth,
      float viewHeight,
      float minOverlapFraction) {
    if (!(viewWidth > 0f) || !(viewHeight > 0f)) return;
    if (!(imgRight > imgLeft) || !(imgBottom > imgTop)) return;
    float frac = minOverlapFraction;
    if (frac < 0f) frac = 0f;
    if (frac > 1f) frac = 1f;

    // Mapped image extent in view coords.
    float mappedW = scale * (imgRight - imgLeft);
    float mappedH = scale * (imgBottom - imgTop);

    // Required overlap in view pixels.
    float minOverlapW = frac * viewWidth;
    float minOverlapH = frac * viewHeight;

    // X axis: mapped image spans [tx + scale*imgLeft, tx + scale*imgRight].
    // For overlap with view [0, viewWidth] of at least minOverlapW we need:
    //   right >= minOverlapW   AND   left <= viewWidth - minOverlapW
    //   tx + scale*imgRight >= minOverlapW   ->  tx >= minOverlapW - scale*imgRight
    //   tx + scale*imgLeft <= viewWidth - minOverlapW  ->  tx <= viewWidth - minOverlapW -
    // scale*imgLeft
    if (mappedW >= minOverlapW) {
      float txMin = minOverlapW - scale * imgRight;
      float txMax = viewWidth - minOverlapW - scale * imgLeft;
      if (txMin > txMax) {
        // Geometrically impossible (mapped image smaller than required overlap on this axis):
        // fall back to centring.
        float center = 0.5f * (txMin + txMax);
        this.tx = center;
      } else if (this.tx < txMin) {
        this.tx = txMin;
      } else if (this.tx > txMax) {
        this.tx = txMax;
      }
    }

    if (mappedH >= minOverlapH) {
      float tyMin = minOverlapH - scale * imgBottom;
      float tyMax = viewHeight - minOverlapH - scale * imgTop;
      if (tyMin > tyMax) {
        float center = 0.5f * (tyMin + tyMax);
        this.ty = center;
      } else if (this.ty < tyMin) {
        this.ty = tyMin;
      } else if (this.ty > tyMax) {
        this.ty = tyMax;
      }
    }
  }

  /**
   * Map a point from raw view coordinates (as delivered by {@code MotionEvent}) to local unscaled
   * coordinates (the frame in which {@code corners} are stored).
   *
   * <pre>
   *   xLocal = (xView - tx) / scale
   *   yLocal = (yView - ty) / scale
   * </pre>
   *
   * @param xView raw X (event coordinate).
   * @param yView raw Y (event coordinate).
   * @param out 2-element array receiving {@code {xLocal, yLocal}}; required.
   */
  public void mapViewToLocal(float xView, float yView, float[] out) {
    if (out == null || out.length < 2) {
      throw new IllegalArgumentException("out must have length >= 2");
    }
    out[0] = (xView - tx) / scale;
    out[1] = (yView - ty) / scale;
  }

  /**
   * Map a point from local unscaled coordinates to raw view coordinates.
   *
   * <pre>
   *   xView = xLocal * scale + tx
   *   yView = yLocal * scale + ty
   * </pre>
   */
  public void mapLocalToView(float xLocal, float yLocal, float[] out) {
    if (out == null || out.length < 2) {
      throw new IllegalArgumentException("out must have length >= 2");
    }
    out[0] = xLocal * scale + tx;
    out[1] = yLocal * scale + ty;
  }

  private static float clampScale(float s) {
    if (s < MIN_SCALE) return MIN_SCALE;
    if (s > MAX_SCALE) return MAX_SCALE;
    return s;
  }
}
