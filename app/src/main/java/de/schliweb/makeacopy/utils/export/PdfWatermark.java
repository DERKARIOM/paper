/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.annotation.Nullable;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import de.schliweb.makeacopy.R;

/**
 * Qwiper logo watermark drawn in the bottom-right corner of every PDF page.
 *
 * <p>The logo (official artwork, transparent background, high resolution) is embedded once per
 * document as a lossless image with its alpha mask and referenced by every page, so a long
 * document does not grow by one copy per page. It is drawn last, semi-transparent, sized and
 * placed relative to the page so it looks the same on A4, Letter or image-sized pages.
 */
public final class PdfWatermark {
  private static final String TAG = "PdfWatermark";

  /** Opacity of the logo: visible as a mark, without hindering reading. */
  static final float OPACITY = 0.35f;
  /** Logo width as a fraction of the page width, clamped to [MIN_WIDTH_PT, MAX_WIDTH_PT]. */
  static final float WIDTH_FRACTION = 0.16f;

  static final float MIN_WIDTH_PT = 56f; // ~2 cm
  static final float MAX_WIDTH_PT = 120f; // ~4.2 cm
  /** Margin to the page edges as a fraction of the shorter page side, at least MIN_MARGIN_PT. */
  static final float MARGIN_FRACTION = 0.03f;

  static final float MIN_MARGIN_PT = 12f;

  private final PDImageXObject logo;
  private final PDExtendedGraphicsState alphaState;

  private PdfWatermark(PDImageXObject logo) {
    this.logo = logo;
    this.alphaState = new PDExtendedGraphicsState();
    alphaState.setNonStrokingAlphaConstant(OPACITY);
    alphaState.setStrokingAlphaConstant(OPACITY);
  }

  /**
   * Loads the logo into {@code document}. Returns {@code null} (no watermark, PDF still created)
   * if the resource cannot be decoded.
   */
  @Nullable
  public static PdfWatermark load(Context context, PDDocument document) {
    Bitmap bmp = null;
    try {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inScaled = false; // keep the full resolution (drawable-nodpi)
      o.inPreferredConfig = Bitmap.Config.ARGB_8888;
      bmp =
          BitmapFactory.decodeResource(
              context.getResources(), R.drawable.qwiper_logo_watermark, o);
      if (bmp == null) {
        Log.w(TAG, "Watermark logo could not be decoded");
        return null;
      }
      return new PdfWatermark(LosslessFactory.createFromImage(document, bmp));
    } catch (Throwable t) {
      Log.w(TAG, "Watermark unavailable", t);
      return null;
    } finally {
      if (bmp != null) bmp.recycle();
    }
  }

  /**
   * Draws the logo in the bottom-right corner of the page. Must be called with the page's content
   * stream in page coordinates (no transform applied).
   */
  public void draw(PDPageContentStream cs, float pageW, float pageH) {
    float[] r = placement(pageW, pageH, logo.getWidth(), logo.getHeight());
    if (r == null) return;
    try {
      cs.saveGraphicsState();
      cs.setGraphicsStateParameters(alphaState);
      cs.drawImage(logo, r[0], r[1], r[2], r[3]);
      cs.restoreGraphicsState();
    } catch (Throwable t) {
      Log.w(TAG, "Drawing the watermark failed", t);
    }
  }

  /**
   * Logo rectangle {x, y, width, height} in PDF page coordinates (origin bottom-left), keeping
   * the logo's aspect ratio. Returns {@code null} for degenerate sizes.
   */
  @Nullable
  static float[] placement(float pageW, float pageH, float logoPxW, float logoPxH) {
    if (pageW <= 0 || pageH <= 0 || logoPxW <= 0 || logoPxH <= 0) return null;
    float margin = Math.max(MIN_MARGIN_PT, Math.min(pageW, pageH) * MARGIN_FRACTION);
    float w = Math.max(MIN_WIDTH_PT, Math.min(MAX_WIDTH_PT, pageW * WIDTH_FRACTION));
    // Never wider than a third of the page (tiny pages).
    w = Math.min(w, pageW / 3f);
    float h = w * logoPxH / logoPxW;
    if (h > pageH / 6f) { // very flat pages: limit the height instead
      h = pageH / 6f;
      w = h * logoPxW / logoPxH;
    }
    return new float[] {pageW - margin - w, margin, w, h};
  }
}
