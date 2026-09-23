/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.utils.image.HighPassUtils;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for {@link HighPassUtils}. Uses small synthetic bitmaps to verify that the
 * background-division filter produces a near-white background while keeping dark content dark.
 */
@RunWith(AndroidJUnit4.class)
public class HighPassUtilsInstrumentedTest {

  private static Context appContext;

  @BeforeClass
  public static void setUpOnce() {
    appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    OpenCVUtils.init(appContext);
  }

  private static Bitmap createShadedDocument(int w, int h) {
    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    // Vertical gradient background simulating a strong shadow.
    // The high-pass background division is supposed to flatten this towards white.
    Paint bg = new Paint();
    for (int y = 0; y < h; y++) {
      int v = 190 - (y * 110 / Math.max(1, h - 1)); // 190 down to ~80
      bg.setColor(Color.rgb(v, v, v));
      c.drawLine(0, y, w, y, bg);
    }
    // Draw some "text" rectangles in dark gray.
    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setColor(Color.rgb(20, 20, 20));
    int lineH = Math.max(4, h / 20);
    for (int i = 2; i < 18; i += 2) {
      int y = i * lineH;
      c.drawRect(w * 0.1f, y, w * 0.9f, y + lineH * 0.6f, text);
    }
    return bmp;
  }

  /**
   * Creates a textless shaded background. Used by background-flattening tests where the
   * presence of dark text would dominate the global mean luminance and obscure the
   * shadow-flattening effect.
   */
  private static Bitmap createShadedBackground(int w, int h) {
    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    Paint bg = new Paint();
    for (int y = 0; y < h; y++) {
      int v = 190 - (y * 110 / Math.max(1, h - 1)); // 190 down to ~80
      bg.setColor(Color.rgb(v, v, v));
      c.drawLine(0, y, w, y, bg);
    }
    return bmp;
  }

  private static double stdLuminance(Bitmap b) {
    int w = b.getWidth();
    int h = b.getHeight();
    int[] px = new int[w * h];
    b.getPixels(px, 0, w, 0, 0, w, h);
    double sum = 0.0;
    double[] vals = new double[px.length];
    for (int i = 0; i < px.length; i++) {
      int p = px[i];
      int r = (p >> 16) & 0xFF;
      int g = (p >> 8) & 0xFF;
      int bl = p & 0xFF;
      double v = 0.2126 * r + 0.7152 * g + 0.0722 * bl;
      vals[i] = v;
      sum += v;
    }
    double mean = sum / vals.length;
    double sq = 0.0;
    for (double v : vals) sq += (v - mean) * (v - mean);
    return Math.sqrt(sq / vals.length);
  }


  private static void assumeOpenCvInitialized() {
    boolean ok;
    try {
      Bitmap dummy = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
      Bitmap res = OpenCVUtils.toGray(dummy);
      ok = (res != null);
    } catch (Throwable t) {
      ok = false;
    }
    Assume.assumeTrue("OpenCV not initialized – skipping image-based tests", ok);
  }

  @Test
  public void applyHighPassGray_nullInput_returnsNull() {
    assertNull(HighPassUtils.applyHighPassGray(null, true));
  }

  @Test
  public void applyHighPassGray_preservesDimensions() {
    assumeOpenCvInitialized();
    Bitmap src = createShadedDocument(320, 240);
    Bitmap out = HighPassUtils.applyHighPassGray(src, /*applyClahe*/ true);
    assertNotNull(out);
    assertEquals(src.getWidth(), out.getWidth());
    assertEquals(src.getHeight(), out.getHeight());
  }

  /**
   * The filter normalizes uneven illumination. After processing, the mean luminance of the output
   * should be noticeably brighter than the mean of the shaded input, because the background gets
   * pushed toward white.
   */
  @Test
  public void applyHighPassGray_flattensBackground() {
    assumeOpenCvInitialized();
    // The background-division filter flattens the *variability* of the shaded
    // background (it is meant to remove uneven lighting). A reliable observable
    // outcome is therefore: the standard deviation of the background luminance
    // drops considerably after the filter, while the global mean stays in a
    // sensible range.
    Bitmap src = createShadedBackground(320, 240);
    Bitmap out = HighPassUtils.applyHighPassGray(src, /*applyClahe*/ false);
    assertNotNull(out);
    double stdIn = stdLuminance(src);
    double stdOut = stdLuminance(out);
    assertTrue(
        "High-pass output should flatten background variability: stdIn="
            + stdIn
            + " stdOut="
            + stdOut,
        stdOut < stdIn * 0.85);
  }

  @Test
  public void applyHighPassColor_nullInput_returnsNull() {
    assertNull(HighPassUtils.applyHighPassColor(null, true));
  }

  @Test
  public void applyHighPassColor_preservesDimensions() {
    assumeOpenCvInitialized();
    Bitmap src = createShadedDocument(320, 240);
    Bitmap out = HighPassUtils.applyHighPassColor(src, /*applyClahe*/ true);
    assertNotNull(out);
    assertEquals(src.getWidth(), out.getWidth());
    assertEquals(src.getHeight(), out.getHeight());
  }

  /**
   * Color-clean filter should also brighten the mean luminance on a shaded document, analogous to
   * the grayscale variant, while keeping the a/b chroma channels untouched.
   */
  @Test
  public void applyHighPassColor_flattensBackground() {
    assumeOpenCvInitialized();
    // Same rationale as applyHighPassGray_flattensBackground: assert that the output
    // background is significantly more uniform (lower stddev) than the shaded input.
    Bitmap src = createShadedBackground(320, 240);
    Bitmap out = HighPassUtils.applyHighPassColor(src, /*applyClahe*/ false);
    assertNotNull(out);
    double stdIn = stdLuminance(src);
    double stdOut = stdLuminance(out);
    assertTrue(
        "Color high-pass output should flatten background variability: stdIn="
            + stdIn
            + " stdOut="
            + stdOut,
        stdOut < stdIn * 0.85);
  }

  /**
   * Exercises the convenience overload {@link HighPassUtils#backgroundDivideGray(Mat, double)} that
   * allocates and returns its own {@link Mat}. Verifies that the returned Mat has the same size and
   * type as the input and can be released without errors.
   */
  @Test
  public void backgroundDivideGray_convenienceOverload_returnsAllocatedMat() {
    assumeOpenCvInitialized();
    Bitmap src = createShadedDocument(160, 120);
    Mat rgba = new Mat();
    Mat gray = new Mat();
    Mat out = null;
    try {
      Utils.bitmapToMat(src, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
      out = HighPassUtils.backgroundDivideGray(gray, HighPassUtils.KERNEL_FRACTION_BW);
      assertNotNull(out);
      assertEquals(gray.width(), out.width());
      assertEquals(gray.height(), out.height());
      assertEquals(gray.type(), out.type());
    } finally {
      rgba.release();
      gray.release();
      if (out != null) out.release();
    }
  }
}
