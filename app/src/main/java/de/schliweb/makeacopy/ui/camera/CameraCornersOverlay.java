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
import android.graphics.*;
import android.util.AttributeSet;
import android.view.SoundEffectConstants;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * A custom view that draws a visual overlay representing the corners of a camera or crop guide
 * rectangle. The overlay is rendered as a closed polygon connecting four specified points with a
 * stroke and optional shadow effect.
 */
public class CameraCornersOverlay extends View {
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint modelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();

  @Nullable private PointF[] corners; // 4 points in view coords
  @Nullable private Double score; // optional: live detection score (0..1)
  @Nullable private RectF modelRect; // optional: model rect in view coords
  @Nullable private CharSequence debugText; // optional: metrics text

  public CameraCornersOverlay(Context context) {
    super(context);
    init();
  }

  public CameraCornersOverlay(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public CameraCornersOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  /**
   * Initializes the view by setting up paints and properties required for drawing. This method sets
   * up the appearance of the paint objects used for rendering visual elements, such as lines and
   * shadows, ensuring they are styled appropriately.
   *
   * <p>Specifically: - Enables drawing for the view by setting `setWillNotDraw` to false. -
   * Configures the `linePaint` for stroke style with specific width, color, and shadow properties.
   * - Configures the `shadowPaint` for potential future use, though currently set to transparent
   * fill style.
   */
  private void init() {
    setWillNotDraw(false);
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(dp(3));
    linePaint.setColor(Color.rgb(255, 102, 0)); // orange, consistent with crop UI
    linePaint.setShadowLayer(dp(2), dp(1), dp(1), Color.BLACK);

    shadowPaint.setStyle(Paint.Style.FILL);
    shadowPaint.setColor(Color.TRANSPARENT); // no fill; kept for potential future use

    textPaint.setStyle(Paint.Style.FILL);
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(dp(14));
    textPaint.setShadowLayer(dp(2), dp(1), dp(1), Color.BLACK);

    modelPaint.setStyle(Paint.Style.STROKE);
    modelPaint.setStrokeWidth(dp(2));
    modelPaint.setColor(Color.argb(220, 0, 200, 255)); // cyan-ish for model rect
    modelPaint.setPathEffect(new DashPathEffect(new float[] {dp(6), dp(6)}, 0));
  }

  /**
   * Accessibility: When this view is "clicked" (e.g., by an accessibility service invoking
   * performClick), we simply report the click and return true. The actual tap-to-focus action is
   * handled in the Fragment's OnTouchListener which also calls performClick() to be
   * accessibility-compliant.
   */
  @Override
  public boolean performClick() {
    // Call super to handle accessibility event TYPE_VIEW_CLICKED
    super.performClick();
    // Optionally provide user feedback
    try {
      playSoundEffect(SoundEffectConstants.CLICK);
    } catch (Throwable ignored) {
      // Sound effect is optional; ignore failures silently
    }
    return true;
  }

  /**
   * Sets the corner points for the object and processes them by sorting the points in a specific
   * order based on their angles relative to the centroid of the points. If the input is invalid
   * (null or not exactly four points), the corners are cleared.
   *
   * @param pts an array of four {@link PointF} objects representing the corner points. The method
   *     expects exactly four points. If the input is null or does not contain exactly four points,
   *     the corners will be set to null. Each point represents a corner's coordinates in 2D space.
   */
  public void setCorners(@Nullable PointF[] pts) {
    if (pts == null || pts.length != 4) {
      this.corners = null;
    } else {
      this.corners = sortByAngle(pts);
    }
    invalidate();
  }

  /**
   * Sets the live detection score to be rendered on top of the preview. Pass null to hide the
   * score. Expected range is [0..1].
   */
  public void setScore(@Nullable Double value) {
    this.score = value;
    invalidate();
  }

  /**
   * Sets the optional model rectangle to visualize the stable reference framing area (aka
   * modelRect). Pass null to hide it.
   */
  public void setModelRect(@Nullable RectF rect) {
    if (rect == null) {
      this.modelRect = null;
    } else {
      // keep a copy to avoid external mutation
      this.modelRect = new RectF(rect);
    }
    invalidate();
  }

  /** Sets optional debug metrics text to be shown in the overlay. Pass null to hide it. */
  public void setDebugText(@Nullable CharSequence text) {
    this.debugText = text;
    invalidate();
  }

  /**
   * Draws the overlay by rendering a polygon based on the provided corner points and using the
   * configured paint for styling. If the corner points are not set, the method exits without
   * performing any drawing.
   *
   * @param canvas the {@link Canvas} object on which the custom drawing will occur. This parameter
   *     should not be null and is provided by the Android system during the view's draw cycle.
   */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (corners != null) {
      path.reset();
      path.moveTo(corners[0].x, corners[0].y);
      for (int i = 1; i < 4; i++) path.lineTo(corners[i].x, corners[i].y);
      path.close();
      canvas.drawPath(path, linePaint);
    }

    // Draw modelRect if provided (as dashed cyan rectangle)
    if (modelRect != null && !modelRect.isEmpty()) {
      canvas.drawRect(modelRect, modelPaint);
    }

    // Draw score in the top-left corner if available
    if (score != null) {
      float pad = dp(8);
      // format to two decimals without allocating heavy objects repeatedly
      String txt;
      double s = score;
      // Clamp and format
      if (s < 0) s = 0;
      else if (s > 1) s = 1;
      int p = (int) Math.round(s * 100);
      // Show as percentage for better intuition
      txt = p + "%";
      canvas.drawText(txt, pad, pad + textPaint.getTextSize(), textPaint);
    }

    // Draw debug metrics text if available (below score)
    if (debugText != null && debugText.length() > 0) {
      float pad = dp(8);
      float y = pad + textPaint.getTextSize() * 2f; // one line below score
      for (String line : debugText.toString().split("\n", -1)) {
        canvas.drawText(line, pad, y, textPaint);
        y += textPaint.getTextSize() * 1.2f;
      }
    }
  }

  /**
   * Sorts the given array of {@link PointF} objects in clockwise order based on their angle
   * relative to the centroid of the points. The resulting array starts with the point that has the
   * smallest (x + y) sum and then rotates clockwise.
   *
   * @param pts an array of four {@link PointF} objects representing points in 2D space. Each point
   *     should have valid x and y coordinates. The method assumes the array contains exactly four
   *     points.
   * @return a new sorted array of {@link PointF} objects arranged in clockwise order starting from
   *     the point with the smallest (x + y) sum.
   */
  private PointF[] sortByAngle(PointF[] pts) {
    // Compute centroid
    float cx = 0, cy = 0;
    for (PointF p : pts) {
      cx += p.x;
      cy += p.y;
    }
    cx /= 4f;
    cy /= 4f;
    final float fcx = cx, fcy = cy;
    java.util.List<PointF> list = new java.util.ArrayList<>();
    for (PointF p : pts) list.add(new PointF(p.x, p.y));
    list.sort(
        (a, b) -> {
          double aa = Math.atan2(a.y - fcy, a.x - fcx);
          double bb = Math.atan2(b.y - fcy, b.x - fcx);
          return Double.compare(aa, bb);
        });
    // rotate so that first is the one with smallest (x+y)
    int start = 0;
    double best = Double.MAX_VALUE;
    for (int i = 0; i < 4; i++) {
      double s = list.get(i).x + list.get(i).y;
      if (s < best) {
        best = s;
        start = i;
      }
    }
    PointF[] out = new PointF[4];
    for (int i = 0; i < 4; i++) out[i] = list.get((start + i) % 4);
    return out;
  }

  /**
   * Converts a value in density-independent pixels (dp) to pixels (px) based on the current screen
   * density. This method is useful for scaling dimensions to appear consistent on devices with
   * different screen densities.
   *
   * @param v the value in density-independent pixels (dp) to be converted.
   * @return the equivalent value in pixels (px).
   */
  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }
}
