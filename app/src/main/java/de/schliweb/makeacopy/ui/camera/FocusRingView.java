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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight overlay that visualizes tap-to-focus: a ring with crosshair ticks is shown at the
 * tapped position, shrinks while the camera is focusing, and then signals the result.
 *
 * <p>The result state is not color-only (accessibility): success is drawn as a solid green ring
 * with a check mark, failure as a red ring with a cross. The indicator fades out automatically.
 */
public class FocusRingView extends View {

  private static final int COLOR_FOCUSING = Color.WHITE;
  private static final int COLOR_SUCCESS = 0xFF4CAF50; // green
  private static final int COLOR_FAILURE = 0xFFF44336; // red

  private enum State {
    HIDDEN,
    FOCUSING,
    SUCCESS,
    FAILURE
  }

  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private State state = State.HIDDEN;
  private float cx;
  private float cy;
  private float radius;
  @Nullable private ValueAnimator shrinkAnimator;

  // Visibility duration is driven by postDelayed instead of the animator's start delay so the
  // result indicator stays visible even when animator scaling is 0 (battery saver, "remove
  // animations" accessibility setting, developer options).
  private final Runnable fadeOutRunnable = this::fadeOut;

  public FocusRingView(Context context) {
    super(context);
    init();
  }

  public FocusRingView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public FocusRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(dp(2));
    ringPaint.setColor(COLOR_FOCUSING);
    ringPaint.setShadowLayer(dp(2), 0, 0, Color.BLACK);

    glyphPaint.setStyle(Paint.Style.STROKE);
    glyphPaint.setStrokeWidth(dp(2));
    glyphPaint.setStrokeCap(Paint.Cap.ROUND);
    glyphPaint.setColor(COLOR_FOCUSING);
    glyphPaint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
  }

  /** Shows the focusing ring at the given position (view coordinates) with a shrink animation. */
  public void showAt(float x, float y) {
    cancelPending();
    cx = x;
    cy = y;
    state = State.FOCUSING;
    ringPaint.setColor(COLOR_FOCUSING);
    glyphPaint.setColor(COLOR_FOCUSING);
    setAlpha(1f);
    ValueAnimator anim = ValueAnimator.ofFloat(dp(36), dp(26));
    anim.setDuration(200);
    anim.addUpdateListener(
        va -> {
          radius = (float) va.getAnimatedValue();
          invalidate();
        });
    shrinkAnimator = anim;
    anim.start();
    invalidate();
  }

  /** Updates the indicator with the focus result and fades it out. */
  public void onFocusResult(boolean success) {
    if (state == State.HIDDEN) return;
    state = success ? State.SUCCESS : State.FAILURE;
    int color = success ? COLOR_SUCCESS : COLOR_FAILURE;
    ringPaint.setColor(color);
    glyphPaint.setColor(color);
    invalidate();
    removeCallbacks(fadeOutRunnable);
    postDelayed(fadeOutRunnable, success ? 800 : 1000);
  }

  /** Hides the indicator immediately. */
  public void hide() {
    cancelPending();
    state = State.HIDDEN;
    setAlpha(1f);
    invalidate();
  }

  private void cancelPending() {
    removeCallbacks(fadeOutRunnable);
    animate().cancel();
    if (shrinkAnimator != null) {
      shrinkAnimator.cancel();
      shrinkAnimator = null;
    }
  }

  private void fadeOut() {
    if (ValueAnimator.areAnimatorsEnabled()) {
      animate().alpha(0f).setStartDelay(0).setDuration(300).withEndAction(this::hide).start();
    } else {
      hide();
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    if (state == State.HIDDEN) return;
    float r = radius > 0 ? radius : dp(26);
    canvas.drawCircle(cx, cy, r, ringPaint);
    // Crosshair ticks on the ring (document-scanner look)
    for (int i = 0; i < 4; i++) {
      double ang = Math.toRadians(90.0 * i);
      float cos = (float) Math.cos(ang);
      float sin = (float) Math.sin(ang);
      canvas.drawLine(
          cx + cos * (r - dp(5)), cy + sin * (r - dp(5)), cx + cos * r, cy + sin * r, ringPaint);
    }
    // Distinguish result by shape, not only color
    if (state == State.SUCCESS) {
      // Check mark
      canvas.drawLine(cx - dp(6), cy + dp(1), cx - dp(2), cy + dp(5), glyphPaint);
      canvas.drawLine(cx - dp(2), cy + dp(5), cx + dp(6), cy - dp(5), glyphPaint);
    } else if (state == State.FAILURE) {
      // Cross
      canvas.drawLine(cx - dp(5), cy - dp(5), cx + dp(5), cy + dp(5), glyphPaint);
      canvas.drawLine(cx - dp(5), cy + dp(5), cx + dp(5), cy - dp(5), glyphPaint);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelPending();
    super.onDetachedFromWindow();
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }
}
