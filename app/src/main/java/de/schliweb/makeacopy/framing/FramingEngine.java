/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.framing;

import android.graphics.PointF;
import android.graphics.RectF;

/**
 * UI-free core that evaluates framing from an optional detected quad and a fallback rectangle. This
 * implementation computes simple, inexpensive metrics suitable for ~5–6 FPS usage in the camera
 * preview thread.
 *
 * <p>Notes: - All coordinates are expected in an upright image space. - If {@code fallbackRect} is
 * null, a centered model rect is derived from the image size with ~70% of the image height and the
 * image aspect ratio. - The quad is assumed to be given clockwise; if not, estimates still produce
 * reasonable magnitudes though the sign of tilt may be off. This is OK for early logging and A11y
 * scaffolding behind feature flags.
 */
public class FramingEngine {

  public static class Input {
    /** Upright image width/height in pixels. */
    public final int imageWidth;

    public final int imageHeight;

    /** Optional detected quad in upright image coordinates (length 4, clockwise). */
    public final PointF[] quad;

    /** Optional fallback rect in upright image coordinates. */
    public final RectF fallbackRect;

    /** Optional explicit model height override (test/dev only). If > 0, it will be used. */
    public final Float modelHeightOverride;

    public Input(int imageWidth, int imageHeight, PointF[] quad, RectF fallbackRect) {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.quad = quad;
      this.fallbackRect = fallbackRect;
      this.modelHeightOverride = null;
    }

    /**
     * Overload allowing explicit model height override (useful for deterministic tests).
     *
     * @param imageWidth upright image width
     * @param imageHeight upright image height
     * @param quad optional detected quad
     * @param fallbackRect optional fallback rect
     * @param modelHeightOverride if not null and > 0, used as the model height
     */
    public Input(
        int imageWidth,
        int imageHeight,
        PointF[] quad,
        RectF fallbackRect,
        Float modelHeightOverride) {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.quad = quad;
      this.fallbackRect = fallbackRect;
      this.modelHeightOverride = modelHeightOverride;
    }
  }

  /**
   * Evaluate framing. Computes normalized translation (dx/dy), scale ratio against a derived model
   * rectangle, and crude tilt estimates from edge length differences. Heuristically selects a
   * single GuidanceHint.
   */
  public FramingResult evaluate(Input in) {
    if (in == null) {
      return new FramingResult(0f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.OK, false);
    }

    final int W = Math.max(1, in.imageWidth);
    final int H = Math.max(1, in.imageHeight);

    // Derive model height (avoid android RectF usage in tests where possible)
    float modelH = deriveModelHeight(in, W, H);

    boolean hasDoc = in.quad != null && in.quad.length == 4;
    if (!hasDoc) {
      // No document → neutral metrics, but maintain hint based on missing document
      return new FramingResult(0f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.MOVE_CLOSER, false);
    }

    PointF[] q = in.quad;

    // Center of quad vs. center of image
    float cx = (q[0].x + q[1].x + q[2].x + q[3].x) * 0.25f;
    float cy = (q[0].y + q[1].y + q[2].y + q[3].y) * 0.25f;
    float imgCx = W * 0.5f;
    float imgCy = H * 0.5f;
    float dxNorm = clamp01Signed((cx - imgCx) / (float) W * 2f); // ~[-1..1]
    float dyNorm = clamp01Signed((cy - imgCy) / (float) H * 2f);

    // Scale: compare quad bounding-rect height to model height
    float qHeight = quadHeight(q);
    float scaleRatio = modelH > 1f ? (qHeight / modelH) : 1f;

    // Tilt: differences of opposite edge lengths, normalized by average
    float left = dist(q[0], q[3]);
    float right = dist(q[1], q[2]);
    float top = dist(q[0], q[1]);
    float bottom = dist(q[2], q[3]);
    float tiltH = normDiff(left, right); // left-vs-right
    float tiltV = normDiff(top, bottom); // top-vs-bottom

    // Heuristic hint selection (thresholds from concept analysis)
    GuidanceHint hint = GuidanceHint.OK;
    final float TILT_BAD = 0.10f; // 10% of average side length
    final float SHIFT_OK = 0.12f; // 12% normalized
    final float SCALE_BACK = 1.20f;
    final float SCALE_CLOSER = 0.85f;

    if (tiltH > TILT_BAD) {
      hint = (left > right) ? GuidanceHint.TILT_LEFT : GuidanceHint.TILT_RIGHT;
    } else if (tiltV > TILT_BAD) {
      hint = (top > bottom) ? GuidanceHint.TILT_FORWARD : GuidanceHint.TILT_BACK;
    } else if (Math.abs(dxNorm) > SHIFT_OK || Math.abs(dyNorm) > SHIFT_OK) {
      if (Math.abs(dxNorm) >= Math.abs(dyNorm)) {
        hint = dxNorm < 0 ? GuidanceHint.MOVE_LEFT : GuidanceHint.MOVE_RIGHT;
      } else {
        hint = dyNorm < 0 ? GuidanceHint.MOVE_UP : GuidanceHint.MOVE_DOWN;
      }
    } else if (scaleRatio > SCALE_BACK) {
      hint = GuidanceHint.MOVE_BACK;
    } else if (scaleRatio < SCALE_CLOSER) {
      hint = GuidanceHint.MOVE_CLOSER;
    } else {
      hint = GuidanceHint.OK;
    }

    // Quality: simple inverse of combined deviations (crude, bounded to [0..1])
    float devTilt = Math.max(tiltH, tiltV) / TILT_BAD; // >1 bad
    float devShift = Math.max(Math.abs(dxNorm), Math.abs(dyNorm)) / SHIFT_OK;
    float devScale =
        (scaleRatio > 1f)
            ? (scaleRatio / SCALE_BACK)
            : (SCALE_CLOSER / Math.max(0.0001f, scaleRatio));
    float dev = Math.max(0f, Math.max(devTilt, Math.max(devShift, devScale)));
    float quality = 1f / (1f + dev); // monotonically decreases with deviation

    return new FramingResult(quality, dxNorm, dyNorm, scaleRatio, tiltH, tiltV, hint, true);
  }

  private static float deriveModelHeight(Input in, int W, int H) {
    // Test/dev override first
    if (in != null && in.modelHeightOverride != null) {
      try {
        float v = in.modelHeightOverride;
        if (v > 0f) return v;
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
    }
    // Target height ~70% of image height; if fallback has valid aspect, ensure width fits image
    RectF fallback = (in != null) ? in.fallbackRect : null;
    if (fallback != null) {
      try {
        float fw = fallback.width();
        float fh = fallback.height();
        if (fw > 1f && fh > 1f) {
          float aspect = fw / fh;
          float targetH = 0.70f * H;
          float targetW = targetH * aspect;
          if (targetW > W) {
            targetW = 0.70f * W;
            targetH = targetW / Math.max(0.0001f, aspect);
          }
          return Math.max(1f, targetH);
        }
      } catch (Throwable ignore) {
        // In JVM tests, android.graphics may not be available; fall back to image aspect branch
      }
    }
    // Fallback to image aspect branch: simply 70% of image height
    return Math.max(1f, 0.70f * H);
  }

  private static float quadHeight(PointF[] q) {
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (PointF p : q) {
      if (p.y < minY) minY = p.y;
      if (p.y > maxY) maxY = p.y;
    }
    return Math.max(0f, maxY - minY);
  }

  private static float dist(PointF a, PointF b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    return (float) Math.hypot(dx, dy);
  }

  private static float normDiff(float a, float b) {
    float mean = (a + b) * 0.5f;
    if (mean <= 0f) return 0f;
    return Math.abs(a - b) / mean;
  }

  private static float clamp01Signed(float v) {
    if (v < -1f) return -1f;
    if (v > 1f) return 1f;
    return v;
  }
}
