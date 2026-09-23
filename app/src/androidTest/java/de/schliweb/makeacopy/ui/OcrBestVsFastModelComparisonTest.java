package de.schliweb.makeacopy.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.googlecode.tesseract.android.TessBaseAPI;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test that compares OCR results between Fast and Best Tesseract models.
 *
 * <p>This test: 1. Loads a test image from assets 2. Runs OCR with the Fast model (default from app
 * assets) 3. Runs OCR with the Best model (from tessdata_best assets) 4. Compares the results
 * against expected text 5. Reports similarity percentages for both models
 *
 * <p>The Best model is expected to produce higher quality OCR results but may be slower.
 */
@RunWith(AndroidJUnit4.class)
public class OcrBestVsFastModelComparisonTest {

  private static final String TEST_IMAGE_ASSET =
      "instrumented_test_data/20251007_183138_cropped.jpg";
  private static final String EXPECTED_TXT_ASSET = "instrumented_test_data/DOC_20251223_101951.txt";
  private static final String OCR_LANGUAGE = "deu"; // German

  // Tessdata directories
  private static final String TESSDATA_FAST = "tessdata";
  private static final String TESSDATA_BEST = "tessdata_best";
  private static final String TRAINEDDATA_EXT = ".traineddata";

  // OCR mode constants - same as OCRFragment
  private static final int OCR_MODE_ORIGINAL = 0;
  private static final int OCR_MODE_QUICK = 1;
  private static final int OCR_MODE_ROBUST = 2;

  private static Context context;
  private static File tessdataDir;
  private static File fastModelBackup;

  @BeforeClass
  public static void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    tessdataDir = new File(ContextCompat.getNoBackupFilesDir(context), "tessdata");
  }

  @AfterClass
  public static void cleanup() {
    // Restore fast model if it was backed up
    if (fastModelBackup != null && fastModelBackup.exists()) {
      File target = new File(tessdataDir, OCR_LANGUAGE + TRAINEDDATA_EXT);
      if (target.exists()) {
        target.delete();
      }
      fastModelBackup.renameTo(target);
      System.out.println("[DEBUG_LOG] Restored fast model from backup");
    }
  }

  /** Main test that compares Fast and Best models across all OCR modes. */
  @Test
  public void testCompareFastVsBestModels_AllModes() throws Exception {
    // Load test image
    Bitmap testImage = loadBitmapFromAssets(TEST_IMAGE_ASSET);
    assertNotNull("Test image should be loaded from assets", testImage);

    // Load expected text
    String expectedText = loadTextFromAssets(EXPECTED_TXT_ASSET);
    assertNotNull("Expected text should be loaded from assets", expectedText);
    String normalizedExpected = normalizeText(expectedText);

    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] COMPARING FAST vs BEST MODELS");
    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] Expected text length: " + normalizedExpected.length());

    String[] modeNames = {"ORIGINAL", "QUICK", "ROBUST"};
    double[] fastSimilarities = new double[3];
    double[] bestSimilarities = new double[3];
    long[] fastTimes = new long[3];
    long[] bestTimes = new long[3];

    // Test with Fast model first
    System.out.println("[DEBUG_LOG] ");
    System.out.println("[DEBUG_LOG] === TESTING FAST MODEL ===");
    for (int ocrMode = 0; ocrMode <= 2; ocrMode++) {
      OcrTestResult result = runOcrWithModel(testImage, normalizedExpected, ocrMode, false);
      fastSimilarities[ocrMode] = result.similarity;
      fastTimes[ocrMode] = result.totalTime;
      System.out.println(
          "[DEBUG_LOG] FAST "
              + modeNames[ocrMode]
              + ": "
              + String.format("%.2f", result.similarity * 100)
              + "% similarity, "
              + result.totalTime
              + "ms total");
    }

    // Now test with Best model
    System.out.println("[DEBUG_LOG] ");
    System.out.println("[DEBUG_LOG] === TESTING BEST MODEL ===");
    for (int ocrMode = 0; ocrMode <= 2; ocrMode++) {
      OcrTestResult result = runOcrWithModel(testImage, normalizedExpected, ocrMode, true);
      bestSimilarities[ocrMode] = result.similarity;
      bestTimes[ocrMode] = result.totalTime;
      System.out.println(
          "[DEBUG_LOG] BEST "
              + modeNames[ocrMode]
              + ": "
              + String.format("%.2f", result.similarity * 100)
              + "% similarity, "
              + result.totalTime
              + "ms total");
    }

    // Summary comparison
    System.out.println("[DEBUG_LOG] ");
    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] SUMMARY - FAST vs BEST MODEL COMPARISON");
    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] ");
    System.out.println(
        "[DEBUG_LOG] | Mode     | Fast Similarity | Best Similarity | Improvement | Fast Time | Best Time |");
    System.out.println(
        "[DEBUG_LOG] |----------|-----------------|-----------------|-------------|-----------|-----------|");

    for (int i = 0; i <= 2; i++) {
      double improvement = (bestSimilarities[i] - fastSimilarities[i]) * 100;
      String improvementStr =
          improvement >= 0
              ? "+" + String.format("%.2f", improvement)
              : String.format("%.2f", improvement);
      System.out.println(
          "[DEBUG_LOG] | "
              + String.format("%-8s", modeNames[i])
              + " | "
              + String.format("%14.2f%%", fastSimilarities[i] * 100)
              + " | "
              + String.format("%14.2f%%", bestSimilarities[i] * 100)
              + " | "
              + String.format("%11s%%", improvementStr)
              + " | "
              + String.format("%7dms", fastTimes[i])
              + " | "
              + String.format("%7dms", bestTimes[i])
              + " |");
    }
    System.out.println("[DEBUG_LOG] ");

    // Find best results
    int bestFastMode = findBestMode(fastSimilarities);
    int bestBestMode = findBestMode(bestSimilarities);

    System.out.println(
        "[DEBUG_LOG] Best FAST result: "
            + modeNames[bestFastMode]
            + " with "
            + String.format("%.2f", fastSimilarities[bestFastMode] * 100)
            + "% similarity");
    System.out.println(
        "[DEBUG_LOG] Best BEST result: "
            + modeNames[bestBestMode]
            + " with "
            + String.format("%.2f", bestSimilarities[bestBestMode] * 100)
            + "% similarity");

    double overallImprovement = bestSimilarities[bestBestMode] - fastSimilarities[bestFastMode];
    System.out.println(
        "[DEBUG_LOG] Overall improvement (best of each): "
            + String.format("%.2f", overallImprovement * 100)
            + " percentage points");
    System.out.println("[DEBUG_LOG] ========================================");

    // Cleanup
    if (testImage != null && !testImage.isRecycled()) {
      testImage.recycle();
    }

    // Assert that at least one mode achieves reasonable similarity
    double maxFast =
        Math.max(Math.max(fastSimilarities[0], fastSimilarities[1]), fastSimilarities[2]);
    double maxBest =
        Math.max(Math.max(bestSimilarities[0], bestSimilarities[1]), bestSimilarities[2]);

    assertTrue(
        "Fast model should achieve >= 50% similarity. Best: "
            + String.format("%.2f", maxFast * 100)
            + "%",
        maxFast >= 0.50);
    assertTrue(
        "Best model should achieve >= 50% similarity. Best: "
            + String.format("%.2f", maxBest * 100)
            + "%",
        maxBest >= 0.50);
  }

  /** Test specifically for ROBUST mode comparing Fast vs Best models. */
  @Test
  public void testCompareFastVsBestModels_RobustMode() throws Exception {
    // Load test image
    Bitmap testImage = loadBitmapFromAssets(TEST_IMAGE_ASSET);
    assertNotNull("Test image should be loaded from assets", testImage);

    // Load expected text
    String expectedText = loadTextFromAssets(EXPECTED_TXT_ASSET);
    assertNotNull("Expected text should be loaded from assets", expectedText);
    String normalizedExpected = normalizeText(expectedText);

    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] ROBUST MODE: FAST vs BEST MODEL");
    System.out.println("[DEBUG_LOG] ========================================");

    // Test Fast model with ROBUST mode
    OcrTestResult fastResult =
        runOcrWithModel(testImage, normalizedExpected, OCR_MODE_ROBUST, false);
    System.out.println(
        "[DEBUG_LOG] FAST ROBUST: "
            + String.format("%.2f", fastResult.similarity * 100)
            + "% similarity, "
            + fastResult.totalTime
            + "ms");
    System.out.println(
        "[DEBUG_LOG] FAST text (first 300 chars): "
            + fastResult.ocrText.substring(0, Math.min(300, fastResult.ocrText.length())));

    // Test Best model with ROBUST mode
    OcrTestResult bestResult =
        runOcrWithModel(testImage, normalizedExpected, OCR_MODE_ROBUST, true);
    System.out.println(
        "[DEBUG_LOG] BEST ROBUST: "
            + String.format("%.2f", bestResult.similarity * 100)
            + "% similarity, "
            + bestResult.totalTime
            + "ms");
    System.out.println(
        "[DEBUG_LOG] BEST text (first 300 chars): "
            + bestResult.ocrText.substring(0, Math.min(300, bestResult.ocrText.length())));

    double improvement = (bestResult.similarity - fastResult.similarity) * 100;
    System.out.println("[DEBUG_LOG] ");
    System.out.println(
        "[DEBUG_LOG] Improvement: " + String.format("%.2f", improvement) + " percentage points");
    System.out.println("[DEBUG_LOG] ========================================");

    // Cleanup
    if (testImage != null && !testImage.isRecycled()) {
      testImage.recycle();
    }

    // Assert reasonable results
    assertTrue("Fast model ROBUST should achieve >= 50% similarity", fastResult.similarity >= 0.50);
    assertTrue("Best model ROBUST should achieve >= 50% similarity", bestResult.similarity >= 0.50);
  }

  /** Runs OCR with the specified model (Fast or Best) and OCR mode. */
  private OcrTestResult runOcrWithModel(
      Bitmap testImage, String normalizedExpected, int ocrMode, boolean useBestModel)
      throws Exception {
    long startTime = System.currentTimeMillis();

    // Prepare the model
    if (useBestModel) {
      installBestModel();
    } else {
      installFastModel();
    }

    OCRHelper ocrHelper = new OCRHelper(context);
    Bitmap preprocessedImage = null;
    String ocrText = "";
    double similarity = 0.0;
    int meanConfidence = 0;

    try {
      ocrHelper.setReinitPerRun(false);
      ocrHelper.setLanguage(OCR_LANGUAGE);
      ocrHelper.setUseBestModelSettings(useBestModel);
      boolean initialized = ocrHelper.initTesseract();
      assertTrue(
          "Tesseract should be initialized", initialized && ocrHelper.isTesseractInitialized());

      // Set PSM based on OCR mode
      int psm =
          (ocrMode == OCR_MODE_ROBUST)
              ? TessBaseAPI.PageSegMode.PSM_AUTO
              : TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
      ocrHelper.setPageSegMode(psm);

      // Apply preprocessing based on OCR mode
      if (ocrMode == OCR_MODE_ORIGINAL) {
        preprocessedImage = testImage;
      } else if (ocrMode == OCR_MODE_QUICK) {
        preprocessedImage = OpenCVUtils.prepareForOCRQuick(testImage);
      } else {
        preprocessedImage = OpenCVUtils.prepareForOCR(testImage, false);
      }

      if (preprocessedImage == null) {
        preprocessedImage = testImage;
      }

      // Run OCR
      OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(preprocessedImage);

      if (ocrResult != null && ocrResult.text != null && !ocrResult.text.isEmpty()) {
        ocrText = normalizeText(ocrResult.text);
        similarity = calculateSimilarity(normalizedExpected, ocrText);
        meanConfidence = ocrResult.meanConfidence != null ? ocrResult.meanConfidence : 0;
      }

    } finally {
      ocrHelper.shutdown();
      if (preprocessedImage != null
          && preprocessedImage != testImage
          && !preprocessedImage.isRecycled()) {
        preprocessedImage.recycle();
      }
    }

    long totalTime = System.currentTimeMillis() - startTime;
    return new OcrTestResult(ocrText, similarity, meanConfidence, totalTime);
  }

  /** Installs the Fast model by copying from app assets. */
  private void installFastModel() throws IOException {
    if (!tessdataDir.exists()) {
      tessdataDir.mkdirs();
    }

    File target = new File(tessdataDir, OCR_LANGUAGE + TRAINEDDATA_EXT);

    // If best model is currently installed, back it up
    if (target.exists() && target.length() > 5_000_000) { // Best model is > 5MB
      fastModelBackup = new File(tessdataDir, OCR_LANGUAGE + ".fast.backup");
      // Actually we need to copy fast model, not backup best
    }

    // Delete existing and copy fast model from assets
    if (target.exists()) {
      target.delete();
    }

    try (InputStream in =
            context.getAssets().open(TESSDATA_FAST + "/" + OCR_LANGUAGE + TRAINEDDATA_EXT);
        OutputStream out = new FileOutputStream(target)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      out.flush();
    }
    System.out.println("[DEBUG_LOG] Installed FAST model: " + target.length() + " bytes");
  }

  /** Installs the Best model by copying from tessdata_best assets. */
  private void installBestModel() throws IOException {
    if (!tessdataDir.exists()) {
      tessdataDir.mkdirs();
    }

    File target = new File(tessdataDir, OCR_LANGUAGE + TRAINEDDATA_EXT);

    // Delete existing and copy best model from assets
    if (target.exists()) {
      target.delete();
    }

    try (InputStream in =
            context.getAssets().open(TESSDATA_BEST + "/" + OCR_LANGUAGE + TRAINEDDATA_EXT);
        OutputStream out = new FileOutputStream(target)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      out.flush();
    }
    System.out.println("[DEBUG_LOG] Installed BEST model: " + target.length() + " bytes");
  }

  private int findBestMode(double[] similarities) {
    int best = 0;
    for (int i = 1; i < similarities.length; i++) {
      if (similarities[i] > similarities[best]) {
        best = i;
      }
    }
    return best;
  }

  /** Loads a bitmap from the assets folder. */
  private Bitmap loadBitmapFromAssets(String assetPath) throws IOException {
    try (InputStream is = context.getAssets().open(assetPath)) {
      return BitmapFactory.decodeStream(is);
    }
  }

  /** Loads text content from the assets folder. */
  private String loadTextFromAssets(String assetPath) throws IOException {
    try (InputStream is = context.getAssets().open(assetPath);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  /** Normalizes text for comparison. */
  private String normalizeText(String text) {
    if (text == null) return "";
    return text.toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[\\r\\n]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /** Calculates the similarity between two strings using Levenshtein distance. */
  private double calculateSimilarity(String s1, String s2) {
    if (s1 == null || s2 == null) return 0.0;
    if (s1.equals(s2)) return 1.0;
    if (s1.isEmpty() || s2.isEmpty()) return 0.0;

    int maxLen = Math.max(s1.length(), s2.length());
    int distance = levenshteinDistance(s1, s2);
    return 1.0 - ((double) distance / maxLen);
  }

  /** Calculates the Levenshtein distance between two strings. */
  private int levenshteinDistance(String s1, String s2) {
    int[] prev = new int[s2.length() + 1];
    int[] curr = new int[s2.length() + 1];

    for (int j = 0; j <= s2.length(); j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= s1.length(); i++) {
      curr[0] = i;
      for (int j = 1; j <= s2.length(); j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] temp = prev;
      prev = curr;
      curr = temp;
    }

    return prev[s2.length()];
  }

  /** Result holder for OCR test runs. */
  private record OcrTestResult(
      String ocrText, double similarity, int meanConfidence, long totalTime) {}
}
