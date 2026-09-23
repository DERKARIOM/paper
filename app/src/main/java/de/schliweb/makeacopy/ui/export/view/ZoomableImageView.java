/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * A simple ImageView supporting pinch-to-zoom and pan via touch gestures, plus double-tap to toggle
 * between fit and 2x zoom. Mirrors the zoom UX provided by the OCR review screen for the Export
 * preview.
 */
public class ZoomableImageView extends AppCompatImageView {

  private static final float MIN_SCALE = 1.0f;
  private static final float MAX_SCALE = 5.0f;
  private static final float DOUBLE_TAP_SCALE = 2.0f;

  private final Matrix baseMatrix = new Matrix();
  private final Matrix drawMatrix = new Matrix();
  private final float[] tmpValues = new float[9];

  private ScaleGestureDetector scaleDetector;
  private GestureDetector gestureDetector;

  public ZoomableImageView(Context context) {
    super(context);
    init();
  }

  public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ZoomableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    super.setScaleType(ScaleType.MATRIX);
    scaleDetector =
        new ScaleGestureDetector(
            getContext(),
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float current = getCurrentScale();
                float target = current * factor;
                if (target < MIN_SCALE) factor = MIN_SCALE / current;
                else if (target > MAX_SCALE) factor = MAX_SCALE / current;
                drawMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                clampTranslation();
                setImageMatrix(drawMatrix);
                return true;
              }
            });
    gestureDetector =
        new GestureDetector(
            getContext(),
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onScroll(
                  MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (getCurrentScale() <= MIN_SCALE + 0.001f) return false;
                drawMatrix.postTranslate(-distanceX, -distanceY);
                clampTranslation();
                setImageMatrix(drawMatrix);
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                float current = getCurrentScale();
                if (current > MIN_SCALE + 0.01f) {
                  resetZoom();
                } else {
                  float factor = DOUBLE_TAP_SCALE / current;
                  drawMatrix.postScale(factor, factor, e.getX(), e.getY());
                  clampTranslation();
                  setImageMatrix(drawMatrix);
                }
                return true;
              }
            });
  }

  @Override
  public void setScaleType(ScaleType scaleType) {
    // Always use MATRIX internally; ignore external attempts to change this.
    super.setScaleType(ScaleType.MATRIX);
  }

  @Override
  protected boolean setFrame(int l, int t, int r, int b) {
    boolean changed = super.setFrame(l, t, r, b);
    if (changed) updateBaseMatrix();
    return changed;
  }

  @Override
  public void setImageBitmap(android.graphics.Bitmap bm) {
    super.setImageBitmap(bm);
    updateBaseMatrix();
  }

  @Override
  public void setImageDrawable(@Nullable android.graphics.drawable.Drawable drawable) {
    super.setImageDrawable(drawable);
    updateBaseMatrix();
  }

  private void updateBaseMatrix() {
    if (getDrawable() == null) return;
    int viewW = getWidth() - getPaddingLeft() - getPaddingRight();
    int viewH = getHeight() - getPaddingTop() - getPaddingBottom();
    if (viewW <= 0 || viewH <= 0) return;
    int drawW = getDrawable().getIntrinsicWidth();
    int drawH = getDrawable().getIntrinsicHeight();
    if (drawW <= 0 || drawH <= 0) return;

    baseMatrix.reset();
    float scale = Math.min((float) viewW / drawW, (float) viewH / drawH);
    float dx = (viewW - drawW * scale) * 0.5f + getPaddingLeft();
    float dy = (viewH - drawH * scale) * 0.5f + getPaddingTop();
    baseMatrix.postScale(scale, scale);
    baseMatrix.postTranslate(dx, dy);
    drawMatrix.set(baseMatrix);
    setImageMatrix(drawMatrix);
  }

  private float getCurrentScale() {
    drawMatrix.getValues(tmpValues);
    float baseScale;
    baseMatrix.getValues(tmpValues);
    baseScale = tmpValues[Matrix.MSCALE_X];
    if (baseScale == 0f) return 1f;
    drawMatrix.getValues(tmpValues);
    return tmpValues[Matrix.MSCALE_X] / baseScale;
  }

  private void clampTranslation() {
    if (getDrawable() == null) return;
    RectF rect =
        new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
    drawMatrix.mapRect(rect);
    int viewW = getWidth();
    int viewH = getHeight();
    float dx = 0f, dy = 0f;
    if (rect.width() <= viewW) {
      dx = (viewW - rect.width()) / 2f - rect.left;
    } else if (rect.left > 0) {
      dx = -rect.left;
    } else if (rect.right < viewW) {
      dx = viewW - rect.right;
    }
    if (rect.height() <= viewH) {
      dy = (viewH - rect.height()) / 2f - rect.top;
    } else if (rect.top > 0) {
      dy = -rect.top;
    } else if (rect.bottom < viewH) {
      dy = viewH - rect.bottom;
    }
    drawMatrix.postTranslate(dx, dy);
  }

  /** Resets the zoom and pan state to the base aspect-fit transform. */
  public void resetZoom() {
    drawMatrix.set(baseMatrix);
    setImageMatrix(drawMatrix);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (getDrawable() == null) return super.onTouchEvent(event);
    boolean handled = scaleDetector.onTouchEvent(event);
    handled = gestureDetector.onTouchEvent(event) || handled;
    if (handled) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    return handled || super.onTouchEvent(event);
  }
}
