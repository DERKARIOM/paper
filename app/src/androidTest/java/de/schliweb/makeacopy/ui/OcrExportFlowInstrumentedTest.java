package de.schliweb.makeacopy.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.googlecode.tesseract.android.TessBaseAPI;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test that simulates the full OCR export flow using the same logic as the app: 1.
 * Loads an image from test_jpegs assets (simulating CameraFragment capture) 2. Uses the image as-is
 * (simulating CropFragment - no actual cropping needed for this test) 3. Runs OCR with German
 * language using app's OCR mode settings (simulating OCRFragment) 4. Exports OCR text to TXT file
 * with include_ocr option (simulating ExportFragment) 5. Compares the resulting TXT file with the
 * expected DOC_20251223_101951.txt
 *
 * <p>This test uses the same SharedPreferences-based OCR mode selection as OCRFragment, ensuring
 * that the test validates the actual app behavior without test-specific overrides.
 */
@RunWith(AndroidJUnit4.class)
public class OcrExportFlowInstrumentedTest {

  private static final String TEST_IMAGE_ASSET =
      "instrumented_test_data/20251007_183138_cropped.jpg";
  private static final String EXPECTED_TXT_ASSET = "instrumented_test_data/DOC_20251223_101951.txt";
  private static final String OCR_LANGUAGE = "deu"; // German

  // OCR mode constants - same as OCRFragment
  private static final String PREFS_NAME = "export_options";
  private static final String PREF_KEY_OCR_MODE = "ocr_prep_mode";
  private static final int OCR_MODE_ORIGINAL = 0;
  private static final int OCR_MODE_QUICK = 1;
  private static final int OCR_MODE_ROBUST = 2;

  private static Context context;

  @BeforeClass
  public static void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @Test
  public void testOcrExportFlow_GermanLanguage_CompareTxtOutput() throws Exception {
    // Step 1: Load image from assets (simulating CameraFragment)
    Bitmap testImage = loadBitmapFromAssets(TEST_IMAGE_ASSET);
    assertNotNull("Test image should be loaded from assets", testImage);
    assertTrue(
        "Test image should have valid dimensions",
        testImage.getWidth() > 0 && testImage.getHeight() > 0);

    // Step 2: Image is used as-is (simulating CropFragment - the test image is already a document
    // scan)
    // In a real scenario, CropFragment would detect and crop the document edges
    Bitmap croppedImage = testImage; // No cropping needed for this pre-scanned document

    // Step 3: Run OCR with German language using app's OCR mode settings (simulating OCRFragment)
    OCRHelper ocrHelper = new OCRHelper(context);
    Bitmap preprocessedImage = null;
    try {
      // Set German language and initialize Tesseract
      ocrHelper.setLanguage(OCR_LANGUAGE);
      boolean initialized = ocrHelper.initTesseract();
      assertTrue(
          "Tesseract should be initialized", initialized && ocrHelper.isTesseractInitialized());

      // Read OCR mode from SharedPreferences - same logic as OCRFragment.getSelectedOcrMode()
      int ocrMode = getSelectedOcrMode();
      System.out.println(
          "[DEBUG_LOG] OCR mode from SharedPreferences: "
              + ocrMode
              + " (0=Original, 1=Quick, 2=Robust)");

      // Set PSM based on OCR mode - same logic as OCRFragment
      int psm =
          (ocrMode == OCR_MODE_ROBUST)
              ? TessBaseAPI.PageSegMode.PSM_AUTO
              : TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
      ocrHelper.setPageSegMode(psm);
      System.out.println("[DEBUG_LOG] PSM set to: " + psm);

      // Apply preprocessing based on OCR mode - same logic as OCRFragment
      if (ocrMode == OCR_MODE_ORIGINAL) {
        // No preprocessing
        preprocessedImage = croppedImage;
        System.out.println("[DEBUG_LOG] Using ORIGINAL mode - no preprocessing");
      } else if (ocrMode == OCR_MODE_QUICK) {
        // Quick preprocessing
        preprocessedImage = OpenCVUtils.prepareForOCRQuick(croppedImage);
        System.out.println("[DEBUG_LOG] Using QUICK mode - prepareForOCRQuick");
      } else {
        // Robust preprocessing (default) - same as OCRFragment OCR_MODE_ROBUST
        // Use grayscale output to preserve fine details and holes in letters
        preprocessedImage = OpenCVUtils.prepareForOCR(croppedImage, /*binaryOutput*/ false);
        System.out.println("[DEBUG_LOG] Using ROBUST mode - prepareForOCR with grayscale");
      }
      assertNotNull("Preprocessed image should not be null", preprocessedImage);

      // Run OCR on the preprocessed image
      OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(preprocessedImage);
      assertNotNull("OCR result should not be null", ocrResult);
      assertNotNull("OCR text should not be null", ocrResult.text);
      assertFalse("OCR text should not be empty", ocrResult.text.isEmpty());

      // Step 4: Export OCR text to TXT file (simulating ExportFragment with include_ocr option)
      File outputDir = new File(context.getCacheDir(), "ocr_export_test");
      if (!outputDir.exists()) {
        assertTrue("Output directory should be created", outputDir.mkdirs());
      }
      File outputTxtFile = new File(outputDir, "DOC_20251223_101951_result.txt");
      if (outputTxtFile.exists()) {
        assertTrue("Old output file should be deleted", outputTxtFile.delete());
      }

      // Write OCR text to file
      try (OutputStream os = new FileOutputStream(outputTxtFile)) {
        os.write(ocrResult.text.getBytes(StandardCharsets.UTF_8));
        os.flush();
      }
      assertTrue("Output TXT file should exist", outputTxtFile.exists());
      assertTrue("Output TXT file should have content", outputTxtFile.length() > 0);

      // Step 5: Load expected TXT and compare
      String expectedText = loadTextFromAssets(EXPECTED_TXT_ASSET);
      assertNotNull("Expected text should be loaded from assets", expectedText);
      assertFalse("Expected text should not be empty", expectedText.isEmpty());

      String actualText = readFileContent(outputTxtFile);
      assertNotNull("Actual text should be read from output file", actualText);

      // Normalize texts for comparison (remove extra whitespace, normalize line endings)
      String normalizedExpected = normalizeText(expectedText);
      String normalizedActual = normalizeText(actualText);

      // Compare the texts
      // Note: OCR may have minor differences, so we check for similarity rather than exact match
      double similarity = calculateSimilarity(normalizedExpected, normalizedActual);

      // Log the results for debugging
      System.out.println("[DEBUG_LOG] Expected text length: " + normalizedExpected.length());
      System.out.println("[DEBUG_LOG] Actual text length: " + normalizedActual.length());
      System.out.println("[DEBUG_LOG] Similarity: " + (similarity * 100) + "%");
      System.out.println(
          "[DEBUG_LOG] Expected text (first 500 chars): "
              + normalizedExpected.substring(0, Math.min(500, normalizedExpected.length())));
      System.out.println(
          "[DEBUG_LOG] Actual text (first 500 chars): "
              + normalizedActual.substring(0, Math.min(500, normalizedActual.length())));

      // Assert that similarity is above threshold (OCR may have minor errors)
      // Using 70% threshold to account for OCR variations
      assertTrue(
          "OCR text should be at least 70% similar to expected text. Actual similarity: "
              + (similarity * 100)
              + "%",
          similarity >= 0.70);

    } finally {
      ocrHelper.shutdown();
      if (preprocessedImage != null
          && preprocessedImage != croppedImage
          && !preprocessedImage.isRecycled()) {
        preprocessedImage.recycle();
      }
      if (croppedImage != testImage && croppedImage != null && !croppedImage.isRecycled()) {
        croppedImage.recycle();
      }
      if (testImage != null && !testImage.isRecycled()) {
        testImage.recycle();
      }
    }
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

  /** Reads the content of a file as a string. */
  private String readFileContent(File file) throws IOException {
    try (InputStream is = new java.io.FileInputStream(file);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  /**
   * Normalizes text for comparison by: - Converting to lowercase - Replacing multiple whitespace
   * with single space - Trimming
   */
  private String normalizeText(String text) {
    if (text == null) return "";
    return text.toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[\\r\\n]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /**
   * Calculates the similarity between two strings using Levenshtein distance. Returns a value
   * between 0.0 (completely different) and 1.0 (identical).
   */
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

  /**
   * Gets the selected OCR mode from SharedPreferences. This is the same logic as
   * OCRFragment.getSelectedOcrMode().
   *
   * @return OCR mode: 0=Original, 1=Quick (legacy, migrated to Robust), 2=Robust (default)
   */
  private int getSelectedOcrMode() {
    try {
      SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      int stored = sp.getInt(PREF_KEY_OCR_MODE, OCR_MODE_ROBUST);
      // Migration: Quick is no longer a user-facing mode (FR#74 benchmark 2026-04-26b).
      if (stored == OCR_MODE_QUICK) stored = OCR_MODE_ROBUST;
      return stored;
    } catch (Throwable ignore) {
      return OCR_MODE_ROBUST;
    }
  }

  /**
   * Test that compares all three OCR modes (Original, Quick, Robust) and outputs the similarity
   * results for each mode.
   */
  @Test
  public void testCompareAllOcrModes() throws Exception {
    // Load image from assets
    Bitmap testImage = loadBitmapFromAssets(TEST_IMAGE_ASSET);
    assertNotNull("Test image should be loaded from assets", testImage);

    // Load expected text
    String expectedText = loadTextFromAssets(EXPECTED_TXT_ASSET);
    assertNotNull("Expected text should be loaded from assets", expectedText);
    String normalizedExpected = normalizeText(expectedText);

    String[] modeNames = {"ORIGINAL", "QUICK", "ROBUST"};
    double[] similarities = new double[3];

    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] COMPARING ALL THREE OCR MODES");
    System.out.println("[DEBUG_LOG] ========================================");

    for (int ocrMode = 0; ocrMode <= 2; ocrMode++) {
      System.out.println("[DEBUG_LOG] ");
      System.out.println(
          "[DEBUG_LOG] --- Testing OCR Mode " + ocrMode + " (" + modeNames[ocrMode] + ") ---");

      OCRHelper ocrHelper = new OCRHelper(context);
      Bitmap preprocessedImage = null;
      try {
        // Set German language and initialize Tesseract
        ocrHelper.setLanguage(OCR_LANGUAGE);
        boolean initialized = ocrHelper.initTesseract();
        assertTrue(
            "Tesseract should be initialized for mode " + modeNames[ocrMode],
            initialized && ocrHelper.isTesseractInitialized());

        // Set PSM based on OCR mode - same logic as OCRFragment
        int psm =
            (ocrMode == OCR_MODE_ROBUST)
                ? TessBaseAPI.PageSegMode.PSM_AUTO
                : TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
        ocrHelper.setPageSegMode(psm);
        System.out.println("[DEBUG_LOG] PSM set to: " + psm);

        // Apply preprocessing based on OCR mode
        long preprocessStart = System.currentTimeMillis();
        if (ocrMode == OCR_MODE_ORIGINAL) {
          preprocessedImage = testImage;
          System.out.println("[DEBUG_LOG] Using ORIGINAL mode - no preprocessing");
        } else if (ocrMode == OCR_MODE_QUICK) {
          preprocessedImage = OpenCVUtils.prepareForOCRQuick(testImage);
          System.out.println("[DEBUG_LOG] Using QUICK mode - prepareForOCRQuick");
        } else {
          preprocessedImage = OpenCVUtils.prepareForOCR(testImage, /*binaryOutput*/ false);
          System.out.println("[DEBUG_LOG] Using ROBUST mode - prepareForOCR with grayscale");
        }
        long preprocessTime = System.currentTimeMillis() - preprocessStart;
        System.out.println("[DEBUG_LOG] Preprocessing time: " + preprocessTime + "ms");

        if (preprocessedImage == null) {
          System.out.println(
              "[DEBUG_LOG] ERROR: Preprocessed image is null for mode " + modeNames[ocrMode]);
          similarities[ocrMode] = 0.0;
          continue;
        }

        // Run OCR
        long ocrStart = System.currentTimeMillis();
        OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(preprocessedImage);
        long ocrTime = System.currentTimeMillis() - ocrStart;
        System.out.println("[DEBUG_LOG] OCR time: " + ocrTime + "ms");

        if (ocrResult == null || ocrResult.text == null || ocrResult.text.isEmpty()) {
          System.out.println(
              "[DEBUG_LOG] ERROR: OCR result is empty for mode " + modeNames[ocrMode]);
          similarities[ocrMode] = 0.0;
          continue;
        }

        // Calculate similarity
        String normalizedActual = normalizeText(ocrResult.text);
        double similarity = calculateSimilarity(normalizedExpected, normalizedActual);
        similarities[ocrMode] = similarity;

        // Log results
        System.out.println("[DEBUG_LOG] Text length: " + normalizedActual.length());
        System.out.println("[DEBUG_LOG] Mean confidence: " + ocrResult.meanConfidence);
        System.out.println(
            "[DEBUG_LOG] Similarity: " + String.format("%.2f", similarity * 100) + "%");
        System.out.println(
            "[DEBUG_LOG] First 300 chars: "
                + normalizedActual.substring(0, Math.min(300, normalizedActual.length())));

      } finally {
        ocrHelper.shutdown();
        if (preprocessedImage != null
            && preprocessedImage != testImage
            && !preprocessedImage.isRecycled()) {
          preprocessedImage.recycle();
        }
      }
    }

    // Summary
    System.out.println("[DEBUG_LOG] ");
    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] SUMMARY - OCR MODE COMPARISON");
    System.out.println("[DEBUG_LOG] ========================================");
    System.out.println("[DEBUG_LOG] Expected text length: " + normalizedExpected.length());
    System.out.println("[DEBUG_LOG] ");
    for (int i = 0; i <= 2; i++) {
      System.out.println(
          "[DEBUG_LOG] "
              + modeNames[i]
              + ": "
              + String.format("%.2f", similarities[i] * 100)
              + "% similarity");
    }
    System.out.println("[DEBUG_LOG] ");

    // Find best mode
    int bestMode = 0;
    for (int i = 1; i <= 2; i++) {
      if (similarities[i] > similarities[bestMode]) {
        bestMode = i;
      }
    }
    System.out.println(
        "[DEBUG_LOG] BEST MODE: "
            + modeNames[bestMode]
            + " with "
            + String.format("%.2f", similarities[bestMode] * 100)
            + "% similarity");
    System.out.println("[DEBUG_LOG] ========================================");

    // Cleanup
    if (testImage != null && !testImage.isRecycled()) {
      testImage.recycle();
    }

    // Assert that at least one mode achieves reasonable similarity
    double maxSimilarity = Math.max(Math.max(similarities[0], similarities[1]), similarities[2]);
    assertTrue(
        "At least one OCR mode should achieve >= 70% similarity. Best: "
            + String.format("%.2f", maxSimilarity * 100)
            + "%",
        maxSimilarity >= 0.70);
  }
}
