/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import de.schliweb.makeacopy.R;

/**
 * Fixed framing guide drawn on top of the camera preview: a portrait A4-shaped frame with rounded
 * corner brackets, centered in the visible preview content. The inside stays fully transparent;
 * the outside is only slightly dimmed so the camera image remains clearly visible.
 *
 * <p>The frame never moves with the camera. It only changes color when the live document detection
 * (existing corner detector, see {@link CameraCornersOverlay}) reports a well framed document.
 *
 * <p>Purely visual: not clickable, ignored by accessibility (the textual hint carries the message).
 */
public class ScanGuideOverlay extends View {

  /** Framing state reflected by the frame color. */
  public enum State {
    /** No document detected (or detection disabled): neutral white frame. */
    IDLE,
    /** A document is detected and correctly placed inside the frame. */
    READY
  }

  /** A4 portrait ratio (height / width), also close enough for US Letter. */
  private static final float PAGE_RATIO = 1.4142f;
  /** Share of the available width / height the frame may use. */
  private static final float MAX_WIDTH_SHARE = 0.84f;

  private static final float MAX_HEIGHT_SHARE = 0.90f;

  private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path cornerPath = new Path();
  private final Path dimPath = new Path();
  private final RectF frame = new RectF();
  private final RectF content = new RectF();

  private float contentAspect = 3f / 4f; // preview width / height (portrait 4:3 sensor)
  private float bottomReservePx;
  private float topReservePx;
  private int idleColor;
  private int readyColor;
  private int currentColor;
  private State state = State.IDLE;
  @Nullable private ValueAnimator colorAnimator;

  public ScanGuideOverlay(Context context) {
    this(context, null);
  }

  public ScanGuideOverlay(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ScanGuideOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setWillNotDraw(false);
    setClickable(false);
    setFocusable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

    idleColor = Color.WHITE;
    readyColor = ContextCompat.getColor(context, R.color.paper_scan_accent);
    currentColor = idleColor;

    dimPaint.setStyle(Paint.Style.FILL);
    dimPaint.setColor(ContextCompat.getColor(context, R.color.scan_outside_dim));

    edgePaint.setStyle(Paint.Style.STROKE);
    edgePaint.setStrokeWidth(dp(1));

    cornerPaint.setStyle(Paint.Style.STROKE);
    cornerPaint.setStrokeWidth(dp(4));
    cornerPaint.setStrokeCap(Paint.Cap.ROUND);
    cornerPaint.setStrokeJoin(Paint.Join.ROUND);
    cornerPaint.setShadowLayer(dp(3), 0, dp(1), Color.argb(110, 0, 0, 0));

    bottomReservePx = dp(56);
    topReservePx = dp(16);
    applyColor(idleColor);
  }

  /**
   * Sets the aspect ratio (width / height) of the camera image shown by the preview (FIT_CENTER),
   * so the frame is placed inside the visible image and never over the letterbox bars.
   */
  public void setContentAspect(float widthOverHeight) {
    if (widthOverHeight <= 0f || Math.abs(widthOverHeight - contentAspect) < 0.001f) return;
    contentAspect = widthOverHeight;
    computeFrame(getWidth(), getHeight());
    invalidate();
  }

  /** Space kept free at the bottom of the view (for the hint message). */
  public void setBottomReservePx(float px) {
    bottomReservePx = Math.max(0f, px);
    computeFrame(getWidth(), getHeight());
    invalidate();
  }

  /** Current framing state. */
  @NonNull
  public State getState() {
    return state;
  }

  /** Updates the framing state; the frame color changes with a short animation. */
  public void setState(@NonNull State newState) {
    if (newState == state) return;
    state = newState;
    int target = newState == State.READY ? readyColor : idleColor;
    if (colorAnimator != null) colorAnimator.cancel();
    ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, target);
    anim.setDuration(180);
    anim.addUpdateListener(a -> applyColor((Integer) a.getAnimatedValue()));
    colorAnimator = anim;
    anim.start();
  }

  /** The guide frame in this view's coordinates (a copy). */
  @NonNull
  public RectF getFrameRect() {
    return new RectF(frame);
  }

  /**
   * Evaluates detected document corners (same coordinate space as this view) against the frame.
   *
   * @return {@link Placement#NONE} without corners, {@link Placement#TOO_SMALL} when the document
   *     covers too little of the frame, {@link Placement#OUTSIDE} when it sticks out of the frame,
   *     {@link Placement#GOOD} otherwise
   */
  @NonNull
  public Placement evaluate(@Nullable PointF[] corners) {
    return evaluate(corners, frame);
  }

  /** Result of {@link #evaluate(PointF[])}. */
  public enum Placement {
    NONE,
    TOO_SMALL,
    OUTSIDE,
    GOOD
  }

  private static Placement evaluate(@Nullable PointF[] corners, @NonNull RectF frame) {
    if (corners == null || corners.length != 4 || frame.isEmpty()) return Placement.NONE;
    float[] xs = new float[4];
    float[] ys = new float[4];
    for (int i = 0; i < 4; i++) {
      if (corners[i] == null) return Placement.NONE;
      xs[i] = corners[i].x;
      ys[i] = corners[i].y;
    }
    return evaluate(xs, ys, frame.left, frame.top, frame.right, frame.bottom);
  }

  /**
   * Pure placement rule on plain coordinates (unit-testable without Android graphics classes).
   * Corners may exceed the frame by 8 % (hand shake, frame drawn slightly inside the page); a
   * document covering less than 30 % of the frame is considered too far away.
   */
  @NonNull
  static Placement evaluate(
      float[] xs, float[] ys, float left, float top, float right, float bottom) {
    float fw = right - left;
    float fh = bottom - top;
    if (xs == null || ys == null || xs.length != 4 || ys.length != 4 || fw <= 0 || fh <= 0) {
      return Placement.NONE;
    }
    float tolX = fw * 0.08f;
    float tolY = fh * 0.08f;
    for (int i = 0; i < 4; i++) {
      boolean outX = xs[i] < left - tolX || xs[i] > right + tolX;
      boolean outY = ys[i] < top - tolY || ys[i] > bottom + tolY;
      if (outX || outY) {
        return Placement.OUTSIDE;
      }
    }
    double area = 0;
    for (int i = 0; i < 4; i++) {
      int j = (i + 1) % 4;
      area += (double) xs[i] * ys[j] - (double) xs[j] * ys[i];
    }
    area = Math.abs(area) / 2.0;
    return area < 0.30 * fw * fh ? Placement.TOO_SMALL : Placement.GOOD;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    computeFrame(w, h);
  }

  /** Visible preview image (FIT_CENTER) and the frame centered inside it. */
  private void computeFrame(int w, int h) {
    if (w <= 0 || h <= 0) {
      frame.setEmpty();
      return;
    }
    float cw = w;
    float ch = cw / contentAspect;
    if (ch > h) {
      ch = h;
      cw = ch * contentAspect;
    }
    content.set((w - cw) / 2f, (h - ch) / 2f, (w + cw) / 2f, (h + ch) / 2f);

    // Available area: the visible image, minus the space reserved for the hint (bottom of the
    // view) and a small top margin, so the frame is never cut off or covered.
    float availTop = Math.max(content.top, topReservePx);
    float availBottom = Math.min(content.bottom, h - bottomReservePx);
    float availW = content.width();
    float availH = Math.max(0f, availBottom - availTop);

    float fw = availW * MAX_WIDTH_SHARE;
    float fh = fw * PAGE_RATIO;
    if (fh > availH * MAX_HEIGHT_SHARE) {
      fh = availH * MAX_HEIGHT_SHARE;
      fw = fh / PAGE_RATIO;
    }
    float cx = content.centerX();
    float cy = (availTop + availBottom) / 2f;
    frame.set(cx - fw / 2f, cy - fh / 2f, cx + fw / 2f, cy + fh / 2f);
  }

  private void applyColor(int color) {
    currentColor = color;
    cornerPaint.setColor(color);
    edgePaint.setColor(Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
    invalidate();
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    if (frame.isEmpty()) return;
    float radius = dp(18);

    // Light dim outside the frame, inside the visible image only.
    dimPath.reset();
    dimPath.setFillType(Path.FillType.EVEN_ODD);
    dimPath.addRect(content, Path.Direction.CW);
    dimPath.addRoundRect(frame, radius, radius, Path.Direction.CW);
    canvas.drawPath(dimPath, dimPaint);

    // Thin, discreet edge so the frame shape is readable.
    canvas.drawRoundRect(frame, radius, radius, edgePaint);

    // Rounded corner brackets.
    float len = Math.min(dp(34), Math.min(frame.width(), frame.height()) / 4f);
    float l = frame.left;
    float t = frame.top;
    float r = frame.right;
    float b = frame.bottom;
    cornerPath.reset();
    // top-left
    cornerPath.moveTo(l, t + len);
    cornerPath.lineTo(l, t + radius);
    cornerPath.quadTo(l, t, l + radius, t);
    cornerPath.lineTo(l + len, t);
    // top-right
    cornerPath.moveTo(r - len, t);
    cornerPath.lineTo(r - radius, t);
    cornerPath.quadTo(r, t, r, t + radius);
    cornerPath.lineTo(r, t + len);
    // bottom-right
    cornerPath.moveTo(r, b - len);
    cornerPath.lineTo(r, b - radius);
    cornerPath.quadTo(r, b, r - radius, b);
    cornerPath.lineTo(r - len, b);
    // bottom-left
    cornerPath.moveTo(l + len, b);
    cornerPath.lineTo(l + radius, b);
    cornerPath.quadTo(l, b, l, b - radius);
    cornerPath.lineTo(l, b - len);
    canvas.drawPath(cornerPath, cornerPaint);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (colorAnimator != null) colorAnimator.cancel();
    super.onDetachedFromWindow();
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }
}
