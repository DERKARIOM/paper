/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.R;

/**
 * Small 5-segment focus-quality (sharpness) indicator shown near the shutter button while scanning
 * (feature-flagged, see {@code docs/focus_quality_indicator_design.md}).
 *
 * <p>The level is conveyed by the <b>number of filled segments</b> (▮▮▮□□), not by color alone; the
 * color shift (red→yellow→green) is only an optional enhancement. The view exposes a localized
 * content description per quality band for TalkBack.
 *
 * <p>Performance: {@link #setLevel(int)} only invalidates when the segment count actually changes;
 * drawing uses pre-allocated Paints and a single reused RectF.
 */
public class FocusQualityIndicatorView extends View {

  private static final int SEGMENTS = FocusQualityMeter.SEGMENT_COUNT;

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF segmentRect = new RectF();

  private int level = 0; // 0..SEGMENTS

  public FocusQualityIndicatorView(Context context) {
    super(context);
    init();
  }

  public FocusQualityIndicatorView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public FocusQualityIndicatorView(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    fillPaint.setStyle(Paint.Style.FILL);
    outlinePaint.setStyle(Paint.Style.STROKE);
    outlinePaint.setStrokeWidth(dp(1));
    outlinePaint.setColor(Color.argb(200, 255, 255, 255));
    outlinePaint.setShadowLayer(dp(1), 0, 0, Color.BLACK);
    updateContentDescription();
  }

  /**
   * Updates the number of filled segments ({@code 0..5}). Invalidates and refreshes the content
   * description only when the value actually changes.
   */
  public void setLevel(int segments) {
    int clamped = Math.max(0, Math.min(SEGMENTS, segments));
    if (clamped == level) return;
    level = clamped;
    updateContentDescription();
    invalidate();
  }

  /** Current number of filled segments (for tests/diagnostics). */
  public int getLevel() {
    return level;
  }

  private void updateContentDescription() {
    int resId;
    switch (FocusQualityMeter.bandForSegments(level)) {
      case EXCELLENT:
        resId = R.string.focus_quality_excellent;
        break;
      case GOOD:
        resId = R.string.focus_quality_good;
        break;
      case LOW:
      default:
        resId = R.string.focus_quality_low;
        break;
    }
    setContentDescription(getContext().getString(resId));
  }

  private int fillColorForLevel() {
    // Optional color enhancement; the segment count remains the primary signal.
    switch (FocusQualityMeter.bandForSegments(level)) {
      case EXCELLENT:
        return 0xFF4CAF50; // green
      case GOOD:
        return 0xFFFFC107; // amber
      case LOW:
      default:
        return 0xFFF44336; // red
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth() - getPaddingLeft() - getPaddingRight();
    int h = getHeight() - getPaddingTop() - getPaddingBottom();
    if (w <= 0 || h <= 0) return;
    float gap = dp(3);
    float segW = (w - gap * (SEGMENTS - 1)) / SEGMENTS;
    if (segW <= 0) return;
    float radius = dp(2);
    fillPaint.setColor(fillColorForLevel());
    float left = getPaddingLeft();
    float top = getPaddingTop();
    for (int i = 0; i < SEGMENTS; i++) {
      segmentRect.set(left, top, left + segW, top + h);
      if (i < level) {
        canvas.drawRoundRect(segmentRect, radius, radius, fillPaint);
      } else {
        canvas.drawRoundRect(segmentRect, radius, radius, outlinePaint);
      }
      left += segW + gap;
    }
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }
}
