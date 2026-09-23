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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.googlecode.tesseract.android.TessBaseAPI;
import de.schliweb.makeacopy.utils.image.BinarizationUtils;
import de.schliweb.makeacopy.utils.image.HighPassUtils;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * OCR A/B benchmark as an instrumented test – the Java counterpart of {@code
 * scripts/ocr_ab_benchmark.py}. Runs three preprocessing pipelines (baseline / legacy / highpass)
 * over the synthetic FR #74 sample documents (shipped as androidTest assets), measures character
 * and word error rates via OCR and asserts that the new high-pass filter does not regress against
 * the legacy binarization path.
 *
 * <p>Assertion is intentionally "soft": {@code avgCER(highpass) <= avgCER(legacy) + epsilon}. Hard
 * thresholds would make the test brittle across Tesseract versions.
 */
@RunWith(AndroidJUnit4.class)
public class HighPassOcrBenchmarkInstrumentedTest {

  private static final String OCR_LANGUAGE = "eng";
  private static final String TESSDATA_BEST = "tessdata_best";
  private static final String TRAINEDDATA_EXT = ".traineddata";
  private static final double CER_TOLERANCE = 0.02; // allow up to 2pp worse on very small samples

  /** Test images + matching ground-truth files under {@code androidTest/assets}. */
  private static final List<String> IMAGES =
      Arrays.asList(
          "clean_scan",
          "small_text",
          "uneven_light",
          "color_highlights",
          "noisy_background",
          "phone_photo");

  private static Context context;
  private static File tessdataDir;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    OpenCVUtils.init(context);
    tessdataDir = new File(ContextCompat.getNoBackupFilesDir(context), "tessdata");
    if (!tessdataDir.exists() && !tessdataDir.mkdirs()) {
      throw new IllegalStateException("Cannot create tessdata dir: " + tessdataDir);
    }
    installBestModel(OCR_LANGUAGE);
  }

  @Test
  public void highpass_filter_is_not_worse_than_legacy_on_synthetic_fr74_samples() throws Exception {
    TessBaseAPI tess = new TessBaseAPI();
    boolean ok =
        tess.init(
            ContextCompat.getNoBackupFilesDir(context).getAbsolutePath(),
            OCR_LANGUAGE,
            TessBaseAPI.OEM_LSTM_ONLY);
    assertTrue("Tesseract init must succeed", ok);
    tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);

    double sumCerBaseline = 0, sumCerLegacy = 0, sumCerHighpass = 0;
    double sumWerBaseline = 0, sumWerLegacy = 0, sumWerHighpass = 0;
    // Pipelines actually used by OCRFragment: QUICK (default) and ROBUST.
    double sumCerQuick = 0, sumWerQuick = 0;
    double sumCerRobust = 0, sumWerRobust = 0;
    // Wall-clock timings (ms) per pipeline: preprocessing and OCR.
    double sumPrepBaseline = 0, sumPrepLegacy = 0, sumPrepHighpass = 0, sumPrepQuick = 0, sumPrepRobust = 0;
    double sumOcrBaseline = 0, sumOcrLegacy = 0, sumOcrHighpass = 0, sumOcrQuick = 0, sumOcrRobust = 0;

    System.out.println("[DEBUG_LOG] ===== FR #74 OCR A/B benchmark =====");
    System.out.println("[DEBUG_LOG] image | pipeline | CER | WER | prepMs | ocrMs");

    try {
      for (String name : IMAGES) {
        Bitmap src = loadBitmapFromAssets(name + ".jpg");
        assertNotNull("Missing asset: " + name + ".jpg", src);
        String gt = normalize(loadTextFromAssets(name + ".gt.txt"));
        assertNotNull("Missing asset: " + name + ".gt.txt", gt);

        // Timed preprocessing so we can log a fair Quality/Speed trade-off.
        long t0;
        Bitmap baseline = src;
        double prepBaselineMs = 0.0;

        t0 = System.nanoTime();
        Bitmap legacy = BinarizationUtils.toBw(src, new BinarizationUtils.BwOptions());
        double prepLegacyMs = nanosToMs(System.nanoTime() - t0);

        t0 = System.nanoTime();
        Bitmap highpass = HighPassUtils.applyHighPassGray(src, true);
        double prepHighpassMs = nanosToMs(System.nanoTime() - t0);

        // Exactly the preprocessing paths OCRFragment feeds to Tesseract:
        t0 = System.nanoTime();
        Bitmap quick = OpenCVUtils.prepareForOCRQuick(src); // OCR_MODE_QUICK (default)
        double prepQuickMs = nanosToMs(System.nanoTime() - t0);

        t0 = System.nanoTime();
        Bitmap robust = OpenCVUtils.prepareForOCR(src, /*binaryOutput*/ false); // OCR_MODE_ROBUST
        double prepRobustMs = nanosToMs(System.nanoTime() - t0);

        assertNotNull("legacy bitmap", legacy);
        assertNotNull("highpass bitmap", highpass);
        assertNotNull("quick bitmap", quick);
        assertNotNull("robust bitmap", robust);

        double[] baselineScore = ocrScore(tess, baseline, gt, prepBaselineMs);
        double[] legacyScore = ocrScore(tess, legacy, gt, prepLegacyMs);
        double[] highpassScore = ocrScore(tess, highpass, gt, prepHighpassMs);
        double[] quickScore = ocrScore(tess, quick, gt, prepQuickMs);
        double[] robustScore = ocrScore(tess, robust, gt, prepRobustMs);

        sumCerBaseline += baselineScore[0];
        sumWerBaseline += baselineScore[1];
        sumPrepBaseline += baselineScore[2];
        sumOcrBaseline += baselineScore[3];
        sumCerLegacy += legacyScore[0];
        sumWerLegacy += legacyScore[1];
        sumPrepLegacy += legacyScore[2];
        sumOcrLegacy += legacyScore[3];
        sumCerHighpass += highpassScore[0];
        sumWerHighpass += highpassScore[1];
        sumPrepHighpass += highpassScore[2];
        sumOcrHighpass += highpassScore[3];
        sumCerQuick += quickScore[0];
        sumWerQuick += quickScore[1];
        sumPrepQuick += quickScore[2];
        sumOcrQuick += quickScore[3];
        sumCerRobust += robustScore[0];
        sumWerRobust += robustScore[1];
        sumPrepRobust += robustScore[2];
        sumOcrRobust += robustScore[3];

        logRow(name, "baseline", baselineScore);
        logRow(name, "legacy", legacyScore);
        logRow(name, "highpass", highpassScore);
        logRow(name, "quick", quickScore);
        logRow(name, "robust", robustScore);

        // free non-input bitmaps
        if (legacy != src) legacy.recycle();
        if (highpass != src) highpass.recycle();
        if (quick != src) quick.recycle();
        if (robust != src) robust.recycle();
      }
    } finally {
      tess.recycle();
    }

    int n = IMAGES.size();
    double avgCerBaseline = sumCerBaseline / n;
    double avgCerLegacy = sumCerLegacy / n;
    double avgCerHighpass = sumCerHighpass / n;
    double avgCerQuick = sumCerQuick / n;
    double avgCerRobust = sumCerRobust / n;
    double avgWerBaseline = sumWerBaseline / n;
    double avgWerLegacy = sumWerLegacy / n;
    double avgWerHighpass = sumWerHighpass / n;
    double avgWerQuick = sumWerQuick / n;
    double avgWerRobust = sumWerRobust / n;

    double avgPrepBaseline = sumPrepBaseline / n;
    double avgPrepLegacy = sumPrepLegacy / n;
    double avgPrepHighpass = sumPrepHighpass / n;
    double avgPrepQuick = sumPrepQuick / n;
    double avgPrepRobust = sumPrepRobust / n;
    double avgOcrBaseline = sumOcrBaseline / n;
    double avgOcrLegacy = sumOcrLegacy / n;
    double avgOcrHighpass = sumOcrHighpass / n;
    double avgOcrQuick = sumOcrQuick / n;
    double avgOcrRobust = sumOcrRobust / n;

    System.out.println("[DEBUG_LOG] --------- averages ---------");
    System.out.printf(
        "[DEBUG_LOG] baseline  avgCER=%.3f avgWER=%.3f avgPrepMs=%6.1f avgOcrMs=%6.1f%n",
        avgCerBaseline, avgWerBaseline, avgPrepBaseline, avgOcrBaseline);
    System.out.printf(
        "[DEBUG_LOG] legacy    avgCER=%.3f avgWER=%.3f avgPrepMs=%6.1f avgOcrMs=%6.1f%n",
        avgCerLegacy, avgWerLegacy, avgPrepLegacy, avgOcrLegacy);
    System.out.printf(
        "[DEBUG_LOG] highpass  avgCER=%.3f avgWER=%.3f avgPrepMs=%6.1f avgOcrMs=%6.1f%n",
        avgCerHighpass, avgWerHighpass, avgPrepHighpass, avgOcrHighpass);
    System.out.printf(
        "[DEBUG_LOG] quick     avgCER=%.3f avgWER=%.3f avgPrepMs=%6.1f avgOcrMs=%6.1f%n",
        avgCerQuick, avgWerQuick, avgPrepQuick, avgOcrQuick);
    System.out.printf(
        "[DEBUG_LOG] robust    avgCER=%.3f avgWER=%.3f avgPrepMs=%6.1f avgOcrMs=%6.1f%n",
        avgCerRobust, avgWerRobust, avgPrepRobust, avgOcrRobust);
    System.out.printf(
        "[DEBUG_LOG] delta (quick-robust) avgCER=%+.3f avgWER=%+.3f avgTotalMs=%+.1f%n",
        avgCerQuick - avgCerRobust,
        avgWerQuick - avgWerRobust,
        (avgPrepQuick + avgOcrQuick) - (avgPrepRobust + avgOcrRobust));

    assertTrue(
        "highpass avgCER ("
            + avgCerHighpass
            + ") should be <= legacy avgCER ("
            + avgCerLegacy
            + ") + "
            + CER_TOLERANCE,
        avgCerHighpass <= avgCerLegacy + CER_TOLERANCE);
  }

  // ---------- helpers ----------

  private static double[] ocrScore(
      TessBaseAPI tess, Bitmap bmp, String groundTruth, double prepMs) {
    long t0 = System.nanoTime();
    tess.setImage(bmp);
    String ocr = normalize(tess.getUTF8Text());
    double ocrMs = nanosToMs(System.nanoTime() - t0);
    double cer = cer(ocr, groundTruth);
    double wer = wer(ocr, groundTruth);
    tess.clear();
    return new double[] {cer, wer, prepMs, ocrMs};
  }

  private static double nanosToMs(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static void logRow(String image, String pipeline, double[] s) {
    System.out.printf(
        "[DEBUG_LOG] %-18s | %-8s | %.3f | %.3f | %6.1f | %6.1f%n",
        image, pipeline, s[0], s[1], s[2], s[3]);
  }

  private static String normalize(String s) {
    if (s == null) return "";
    return s.toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[\\r\\n]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /** Character Error Rate = Levenshtein(ocr, gt) / max(1, |gt|). */
  private static double cer(String ocr, String gt) {
    if (gt.isEmpty()) return ocr.isEmpty() ? 0.0 : 1.0;
    return (double) levenshtein(ocr, gt) / (double) gt.length();
  }

  /** Word Error Rate = Levenshtein(tokens(ocr), tokens(gt)) / max(1, |tokens(gt)|). */
  private static double wer(String ocr, String gt) {
    String[] a = ocr.isEmpty() ? new String[0] : ocr.split(" ");
    String[] b = gt.isEmpty() ? new String[0] : gt.split(" ");
    if (b.length == 0) return a.length == 0 ? 0.0 : 1.0;
    return (double) levenshteinTokens(a, b) / (double) b.length;
  }

  private static int levenshtein(String s1, String s2) {
    int[] prev = new int[s2.length() + 1];
    int[] curr = new int[s2.length() + 1];
    for (int j = 0; j <= s2.length(); j++) prev[j] = j;
    for (int i = 1; i <= s1.length(); i++) {
      curr[0] = i;
      for (int j = 1; j <= s2.length(); j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] t = prev;
      prev = curr;
      curr = t;
    }
    return prev[s2.length()];
  }

  private static int levenshteinTokens(String[] a, String[] b) {
    int[] prev = new int[b.length + 1];
    int[] curr = new int[b.length + 1];
    for (int j = 0; j <= b.length; j++) prev[j] = j;
    for (int i = 1; i <= a.length; i++) {
      curr[0] = i;
      for (int j = 1; j <= b.length; j++) {
        int cost = a[i - 1].equals(b[j - 1]) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] t = prev;
      prev = curr;
      curr = t;
    }
    return prev[b.length];
  }

  private static Bitmap loadBitmapFromAssets(String assetPath) throws Exception {
    try (InputStream is = context.getAssets().open(assetPath)) {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inPreferredConfig = Bitmap.Config.ARGB_8888;
      return BitmapFactory.decodeStream(is, null, o);
    }
  }

  private static String loadTextFromAssets(String assetPath) throws Exception {
    try (InputStream is = context.getAssets().open(assetPath);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  private static void installBestModel(String lang) throws Exception {
    File target = new File(tessdataDir, lang + TRAINEDDATA_EXT);
    if (target.exists() && target.length() > 0) return;
    try (InputStream in = context.getAssets().open(TESSDATA_BEST + "/" + lang + TRAINEDDATA_EXT);
        OutputStream out = new FileOutputStream(target)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      out.flush();
    }
    System.out.println("[DEBUG_LOG] Installed BEST model: " + target.length() + " bytes");
  }
}
