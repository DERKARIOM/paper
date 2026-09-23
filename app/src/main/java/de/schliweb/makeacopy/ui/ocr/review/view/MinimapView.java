/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr.review.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight minimap view for OCR review screen. Renders a pre-scaled thumbnail and a live
 * viewport rectangle. Interactions: - Tap to center main viewport - Drag viewport rectangle to pan
 * - Double-tap toggles zoom (fit / 100%) via callback - Long-press opens a mini menu to toggle
 * optional layers (e.g., OCR boxes)
 *
 * <p>The minimap does not fetch/generate thumbnails; caller must supply them via setThumbnail().
 */
public class MinimapView extends View {
  // Page size in image pixels
  private int pageW = 0, pageH = 0;
  // Pre-scaled/cached thumbnail (shortest edge ~512 recommended) provided by host
  private Bitmap thumbnail;

  // Viewport mapping inputs from main overlay
  private float userScale = 1f; // overlay's user scale factor
  private float userOffsetX = 0f; // overlay's user pan offsets (in overlay view px)
  private float userOffsetY = 0f;
  private int overlayWidth = 0; // overlay view content size (px)
  private int overlayHeight = 0;

  // Computed minimap scale
  private float miniScale = 1f;

  private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ocrBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF tmpRect = new RectF();

  @SuppressWarnings("UnusedVariable") // detector is retained by the view's touch listener
  private GestureDetector gestureDetector;

  private ValueAnimator autoHideAnimator;
  private boolean autoHidden = false;

  // Optional layer: simplified OCR word boxes in image coordinates [x,y,w,h]
  private List<int[]> ocrBoxes; // set by host; elements are 4-length int arrays
  private boolean showOcrBoxes = false;

  // Optional layer: confidence heatmap (binned grid). Values are 0..1 representing low-confidence
  // ratio per bin.
  private int heatCols = 0, heatRows = 0;
  @Nullable private float[] heatVals; // size = heatCols * heatRows
  private boolean showHeatmap = false;

  public interface OnNavigateListener {
    // Request centering the main viewport at document coords (px) or view-based center
    // We pass desired content center in image coordinates
    void onCenterRequested(float contentCenterX, float contentCenterY);

    // Toggle zoom action (fit or 1.0)
    void onToggleZoomRequested();
  }

  private OnNavigateListener onNavigateListener;

  public MinimapView(Context context) {
    super(context);
    init();
  }

  public MinimapView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MinimapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    bgPaint.setColor(Color.argb(180, 20, 20, 20));
    framePaint.setStyle(Paint.Style.STROKE);
    framePaint.setColor(Color.argb(255, 3, 218, 197)); // accent-ish
    framePaint.setStrokeWidth(dp(2f));

    ocrBoxPaint.setStyle(Paint.Style.STROKE);
    ocrBoxPaint.setColor(Color.argb(180, 255, 255, 255));
    ocrBoxPaint.setStrokeWidth(dp(1f));

    // Accessibility defaults
    try {
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setFocusable(true);
      if (getContentDescription() == null || getContentDescription().length() == 0) {
        setContentDescription("Minimap, tap to navigate, double-tap to toggle zoom");
      }
    } catch (Throwable ignore) {
      // Accessibility setup is best-effort
    }
    gestureDetector =
        new GestureDetector(
            getContext(),
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                cancelAutoHide();
                return true;
              }

              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                tapNavigate(e.getX(), e.getY());
                scheduleAutoHide();
                return true;
              }

              @Override
              public void onLongPress(MotionEvent e) {
                showMiniMenu();
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                if (onNavigateListener != null) onNavigateListener.onToggleZoomRequested();
                scheduleAutoHide();
                return true;
              }

              @Override
              public boolean onScroll(
                  MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // drag viewport rectangle
                dragViewport(-distanceX, -distanceY);
                return true;
              }
            });

    setAlpha(1f);
  }

  public void setOnNavigateListener(OnNavigateListener l) {
    this.onNavigateListener = l;
  }

  public void setThumbnail(@Nullable Bitmap bmp) {
    this.thumbnail = bmp;
    invalidateOnNextFrame();
  }

  public void setPageSize(int w, int h) {
    this.pageW = w;
    this.pageH = h;
    invalidateOnNextFrame();
  }

  /**
   * Provide simplified OCR boxes for optional overlay rendering. Each entry is an int[4] = [x, y,
   * w, h] in image pixels.
   */
  public void setOcrBoxes(@Nullable List<int[]> boxes) {
    if (boxes == null || boxes.isEmpty()) {
      this.ocrBoxes = null;
    } else {
      // Shallow copy to avoid external mutation
      this.ocrBoxes = new ArrayList<>(boxes);
    }
    invalidateOnNextFrame();
  }

  /**
   * Provide/update heatmap data as a grid of size cols x rows; values in [0..1] (low-confidence
   * ratio).
   */
  public void setHeatmap(int cols, int rows, @Nullable float[] values) {
    if (cols <= 0 || rows <= 0 || values == null || values.length != cols * rows) {
      this.heatCols = this.heatRows = 0;
      this.heatVals = null;
    } else {
      this.heatCols = cols;
      this.heatRows = rows;
      this.heatVals = values.clone();
    }
    invalidateOnNextFrame();
  }

  /** Toggle whether OCR boxes layer is visible. */
  public void setShowOcrBoxes(boolean show) {
    this.showOcrBoxes = show;
    invalidateOnNextFrame();
  }

  public boolean isShowOcrBoxes() {
    return showOcrBoxes;
  }

  /** Toggle whether Heatmap layer is visible. */
  public void setShowHeatmap(boolean show) {
    this.showHeatmap = show;
    invalidateOnNextFrame();
  }

  public boolean isShowHeatmap() {
    return showHeatmap;
  }

  /**
   * Update minimap with latest overlay viewport info. Call from OcrOverlayView listener.
   *
   * @param userScale overlay user scale
   * @param userOffsetX overlay user pan X (px)
   * @param userOffsetY overlay user pan Y (px)
   * @param overlayWidth overlay view width (px)
   * @param overlayHeight overlay view height (px)
   */
  public void setViewport(
      float userScale, float userOffsetX, float userOffsetY, int overlayWidth, int overlayHeight) {
    this.userScale = userScale;
    this.userOffsetX = userOffsetX;
    this.userOffsetY = userOffsetY;
    this.overlayWidth = overlayWidth;
    this.overlayHeight = overlayHeight;
    invalidateOnNextFrame();
    restartAutoHideCountdown();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int vw = getWidth();
    int vh = getHeight();
    if (vw == 0 || vh == 0) return;

    // background
    canvas.drawRect(0, 0, vw, vh, bgPaint);

    if (pageW <= 0 || pageH <= 0) return;

    // compute miniScale so image fits inside minimap
    float sx = vw / (float) pageW;
    float sy = vh / (float) pageH;
    miniScale = Math.min(sx, sy);
    float imgW = pageW * miniScale;
    float imgH = pageH * miniScale;
    float baseX = (vw - imgW) * 0.5f;
    float baseY = (vh - imgH) * 0.5f;

    // draw thumbnail if provided
    if (thumbnail != null && !thumbnail.isRecycled()) {
      // draw into center-fit rect
      tmpRect.set(baseX, baseY, baseX + imgW, baseY + imgH);
      canvas.drawBitmap(thumbnail, null, tmpRect, null);
    }

    // Optional layer: Heatmap (drawn before boxes so boxes remain crisp on top)
    if (showHeatmap && heatVals != null && heatCols > 0 && heatRows > 0) {
      float cellW = (pageW * miniScale) / heatCols;
      float cellH = (pageH * miniScale) / heatRows;
      for (int row = 0; row < heatRows; row++) {
        for (int col = 0; col < heatCols; col++) {
          float v = heatVals[row * heatCols + col]; // 0..1 low-confidence ratio
          if (v <= 0f) continue;
          // Map v to color from transparent -> orange -> red
          int alpha = (int) (clamp(v, 0f, 1f) * 140); // cap opacity
          int rC = 255;
          int gC = (int) (165 + (1f - clamp(v, 0f, 1f)) * 60); // 225..165
          int bC = 0;
          int color = Color.argb(alpha, rC, gC, bC);
          ocrBoxPaint.setStyle(Paint.Style.FILL);
          ocrBoxPaint.setColor(color);
          float l = baseX + col * cellW;
          float t = baseY + row * cellH;
          float r = l + cellW;
          float btm = t + cellH;
          tmpRect.set(l, t, r, btm);
          canvas.drawRect(tmpRect, ocrBoxPaint);
        }
      }
      // restore stroke for subsequent box drawing
      ocrBoxPaint.setStyle(Paint.Style.STROKE);
      ocrBoxPaint.setColor(Color.argb(180, 255, 255, 255));
    }

    // Optional layer: OCR word boxes (simplified vectors)
    if (showOcrBoxes && ocrBoxes != null && !ocrBoxes.isEmpty()) {
      // Draw after background but before viewport frame
      for (int[] b : ocrBoxes) {
        if (b == null || b.length < 4) continue;
        float l = baseX + b[0] * miniScale;
        float t = baseY + b[1] * miniScale;
        float r = baseX + (b[0] + b[2]) * miniScale;
        float btm = baseY + (b[1] + b[3]) * miniScale;
        tmpRect.set(l, t, r, btm);
        canvas.drawRect(tmpRect, ocrBoxPaint);
      }
    }

    // draw viewport rect based on overlay transform
    if (overlayWidth > 0 && overlayHeight > 0) {
      // We need absolute scale (base fit * userScale) and base offsets from overlay content frame.
      float baseFit = Math.min(overlayWidth / (float) pageW, overlayHeight / (float) pageH);
      float eff = baseFit * userScale;
      float contentW = pageW * eff;
      float contentH = pageH * eff;
      float baseOffsetX = (overlayWidth - contentW) * 0.5f;
      float baseOffsetY = (overlayHeight - contentH) * 0.5f;
      float ox = baseOffsetX + userOffsetX;
      float oy = baseOffsetY + userOffsetY;

      // The visible region in content/image coordinates corresponds to overlay view bounds
      // [0..overlayWidth]
      // Map overlay view rect back to image coords: x_img = (x_view - ox) / eff
      float leftImg = (0f - ox) / eff;
      float topImg = (0f - oy) / eff;
      float rightImg = (overlayWidth - ox) / eff;
      float bottomImg = (overlayHeight - oy) / eff;

      // Intersect with image bounds
      leftImg = clamp(leftImg, 0, pageW);
      topImg = clamp(topImg, 0, pageH);
      rightImg = clamp(rightImg, 0, pageW);
      bottomImg = clamp(bottomImg, 0, pageH);

      // Map to minimap coords
      float l = baseX + leftImg * miniScale;
      float t = baseY + topImg * miniScale;
      float r = baseX + rightImg * miniScale;
      float b = baseY + bottomImg * miniScale;

      tmpRect.set(l, t, r, b);
      canvas.drawRect(tmpRect, framePaint);
    }
  }

  private void tapNavigate(float vx, float vy) {
    if (pageW <= 0 || pageH <= 0 || onNavigateListener == null) return;
    int vw = getWidth();
    int vh = getHeight();
    float sx = vw / (float) pageW;
    float sy = vh / (float) pageH;
    float ms = Math.min(sx, sy);
    float imgW = pageW * ms;
    float imgH = pageH * ms;
    float baseX = (vw - imgW) * 0.5f;
    float baseY = (vh - imgH) * 0.5f;
    // Convert tap to image coords
    float xi = (vx - baseX) / ms;
    float yi = (vy - baseY) / ms;
    xi = clamp(xi, 0, pageW);
    yi = clamp(yi, 0, pageH);
    onNavigateListener.onCenterRequested(xi, yi);
  }

  private void dragViewport(float dx, float dy) {
    // Translate drag in minimap space to pan in main content space and clamp within page bounds
    if (overlayWidth <= 0
        || overlayHeight <= 0
        || pageW <= 0
        || pageH <= 0
        || onNavigateListener == null) return;
    float baseFitOverlay = Math.min(overlayWidth / (float) pageW, overlayHeight / (float) pageH);
    float eff = baseFitOverlay * userScale;
    float baseFitMini = Math.min(getWidth() / (float) pageW, getHeight() / (float) pageH);
    if (baseFitMini <= 0f || eff <= 0f) return;
    // movement in minimap corresponds to movement in image coords: dImg = dMini / baseFitMini
    float dImgX = dx / baseFitMini;
    float dImgY = dy / baseFitMini;
    // Compute current center in image coordinates from overlay view center
    float baseOffsetX = (overlayWidth - pageW * eff) * 0.5f + userOffsetX;
    float baseOffsetY = (overlayHeight - pageH * eff) * 0.5f + userOffsetY;
    float centerImgX = (overlayWidth * 0.5f - baseOffsetX) / eff;
    float centerImgY = (overlayHeight * 0.5f - baseOffsetY) / eff;
    // Visible viewport size in image coordinates
    float visImgW = overlayWidth / eff;
    float visImgH = overlayHeight / eff;
    float halfW = Math.min(pageW, visImgW) * 0.5f;
    float halfH = Math.min(pageH, visImgH) * 0.5f;
    // Target center, clamped so frame stays inside page
    float tx = clamp(centerImgX + dImgX, halfW, Math.max(halfW, pageW - halfW));
    float ty = clamp(centerImgY + dImgY, halfH, Math.max(halfH, pageH - halfH));
    onNavigateListener.onCenterRequested(tx, ty);
  }

  private void showMiniMenu() {
    try {
      androidx.appcompat.widget.PopupMenu pm =
          new androidx.appcompat.widget.PopupMenu(getContext(), this);
      final int ID_TOGGLE_BOXES = 1;
      final int ID_TOGGLE_HEAT = 2;
      String labelBoxes = showOcrBoxes ? "Hide OCR boxes" : "Show OCR boxes";
      String labelHeat = showHeatmap ? "Hide heatmap" : "Show heatmap";
      pm.getMenu().add(0, ID_TOGGLE_BOXES, 0, labelBoxes);
      pm.getMenu().add(0, ID_TOGGLE_HEAT, 1, labelHeat);
      pm.setOnMenuItemClickListener(
          item -> {
            if (item.getItemId() == ID_TOGGLE_BOXES) {
              setShowOcrBoxes(!showOcrBoxes);
              return true;
            } else if (item.getItemId() == ID_TOGGLE_HEAT) {
              setShowHeatmap(!showHeatmap);
              return true;
            }
            return false;
          });
      pm.show();
    } catch (Throwable ignore) {
      // Context menu display is best-effort
    }
  }

  private void invalidateOnNextFrame() {
    postOnAnimation(this::invalidate);
  }

  private void restartAutoHideCountdown() {
    cancelAutoHide();
    scheduleAutoHide();
  }

  private void scheduleAutoHide() {
    if (autoHideAnimator != null && autoHideAnimator.isRunning()) return;
    autoHideAnimator = ValueAnimator.ofFloat(getAlpha(), 0.35f);
    autoHideAnimator.setStartDelay(1500);
    autoHideAnimator.setDuration(300);
    autoHideAnimator.addUpdateListener(a -> setAlpha((float) a.getAnimatedValue()));
    autoHideAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            autoHidden = true;
          }
        });
    autoHideAnimator.start();
  }

  private void cancelAutoHide() {
    autoHidden = false;
    if (autoHideAnimator != null) {
      autoHideAnimator.cancel();
      autoHideAnimator = null;
    }
    setAlpha(1f);
  }

  private float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    try {
      info.setClassName(View.class.getName());
      AccessibilityNodeInfo.AccessibilityAction click =
          new AccessibilityNodeInfo.AccessibilityAction(
              AccessibilityNodeInfo.ACTION_CLICK, "Toggle zoom");
      info.addAction(click);
    } catch (Throwable ignore) {
      // Accessibility action setup is best-effort
    }
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle arguments) {
    if (action == AccessibilityNodeInfo.ACTION_CLICK) {
      if (onNavigateListener != null) {
        onNavigateListener.onToggleZoomRequested();
        return true;
      }
    }
    return super.performAccessibilityAction(action, arguments);
  }
}
