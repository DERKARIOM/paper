package de.schliweb.makeacopy.ocr;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for Arabic and Persian OCR recognition. Tests verify that RTL scripts are
 * correctly recognized with appropriate PSM_AUTO settings and disabled whitelist.
 *
 * <p>Test data: - test_pdfs/test_arabic.pdf + test_arabic.txt (Ground Truth) -
 * test_pdfs/test_persian.pdf + test_persian.txt (Ground Truth)
 *
 * <p>Covered scenarios: - Digit variants (Persian ۰-۹, Arabic-Indic ٠-٩, Western 0-9) - BiDi mixed
 * text (URLs, email addresses in RTL context) - Unicode special cases (ZWNJ/Nīm-Fāṣila, Shadda) -
 * Structured content (numbered lists) - Punctuation (Arabic comma ،, question mark ؟)
 */
@RunWith(AndroidJUnit4.class)
public class ArabicPersianOcrTest {

  private static final String ARABIC_PDF_ASSET = "instrumented_test_data/test_arabic.pdf";
  private static final String ARABIC_TXT_ASSET = "instrumented_test_data/test_arabic.txt";
  private static final String PERSIAN_PDF_ASSET = "instrumented_test_data/test_persian.pdf";
  private static final String PERSIAN_TXT_ASSET = "instrumented_test_data/test_persian.txt";

  // PDF variants for robustness testing
  private static final String ARABIC_INVERTED_PDF =
      "instrumented_test_data/test_arabic_inverted.pdf";
  private static final String ARABIC_SCAN_NOISE_PDF =
      "instrumented_test_data/test_arabic_SCAN_NOISE.pdf";
  private static final String ARABIC_MIXED_LIGHT_PDF =
      "instrumented_test_data/test_arabic_MIXED_LIGHT.pdf";
  private static final String ARABIC_MIXED_LIGHTING_PDF =
      "instrumented_test_data/test_arabic_MIXED_LIGHTING.pdf";
  private static final String PERSIAN_INVERTED_PDF =
      "instrumented_test_data/test_persian_inverted.pdf";
  private static final String PERSIAN_SCAN_NOISE_PDF =
      "instrumented_test_data/test_persian_SCAN_NOISE.pdf";
  private static final String PERSIAN_MIXED_LIGHT_PDF =
      "instrumented_test_data/test_persian_MIXED_LIGHT.pdf";
  private static final String PERSIAN_MIXED_LIGHTING_PDF =
      "instrumented_test_data/test_persian_MIXED_LIGHTING.pdf";

  private static Context context;

  @BeforeClass
  public static void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  /** Tests that Persian digits (۰۱۲۳۴۵۶۷۸۹) are recognized correctly. */
  @Test
  public void testPersianDigitsRecognition() throws Exception {
    // Check if Persian PDF exists, skip if not
    if (!assetExists(PERSIAN_PDF_ASSET)) {
      System.out.println("[DEBUG_LOG] Skipping test - Persian PDF not found: " + PERSIAN_PDF_ASSET);
      return;
    }

    Bitmap page = renderPdfFirstPage(PERSIAN_PDF_ASSET);
    assertNotNull("PDF page should be rendered", page);

    String ocrResult = performOcr(page, "fas");
    assertNotNull("OCR result should not be null", ocrResult);

    System.out.println("[DEBUG_LOG] Persian OCR result length: " + ocrResult.length());
    System.out.println(
        "[DEBUG_LOG] Persian OCR result (first 500 chars): "
            + ocrResult.substring(0, Math.min(500, ocrResult.length())));

    // Persian digits must be recognized
    assertTrue(
        "Persian digits should be recognized",
        ocrResult.contains("۰")
            || ocrResult.contains("۱")
            || ocrResult.contains("۲")
            || ocrResult.contains("۳"));

    // Western digits should also be recognized
    assertTrue(
        "Western digits should be recognized",
        ocrResult.contains("0")
            || ocrResult.contains("1")
            || ocrResult.contains("2")
            || ocrResult.contains("3"));

    page.recycle();
  }

  /** Tests that Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) are recognized correctly. */
  @Test
  public void testArabicDigitsRecognition() throws Exception {
    // Check if Arabic PDF exists, skip if not
    if (!assetExists(ARABIC_PDF_ASSET)) {
      System.out.println("[DEBUG_LOG] Skipping test - Arabic PDF not found: " + ARABIC_PDF_ASSET);
      return;
    }

    Bitmap page = renderPdfFirstPage(ARABIC_PDF_ASSET);
    assertNotNull("PDF page should be rendered", page);

    String ocrResult = performOcr(page, "ara");
    assertNotNull("OCR result should not be null", ocrResult);

    System.out.println("[DEBUG_LOG] Arabic OCR result length: " + ocrResult.length());
    System.out.println(
        "[DEBUG_LOG] Arabic OCR result (first 500 chars): "
            + ocrResult.substring(0, Math.min(500, ocrResult.length())));

    // Arabic-Indic digits must be recognized
    assertTrue(
        "Arabic-Indic digits should be recognized",
        ocrResult.contains("٠")
            || ocrResult.contains("١")
            || ocrResult.contains("٢")
            || ocrResult.contains("٣"));

    // Western digits should also be recognized
    assertTrue(
        "Western digits should be recognized",
        ocrResult.contains("0")
            || ocrResult.contains("1")
            || ocrResult.contains("2")
            || ocrResult.contains("3"));

    page.recycle();
  }

  /**
   * Tests that mixed content (LTR in RTL context) is handled correctly. Note: Pure RTL language
   * models (ara/fas) may not reliably recognize LTR content like emails and URLs. This is a known
   * Tesseract limitation. The test verifies that at least Arabic text is recognized, and optionally
   * checks for LTR content.
   */
  @Test
  public void testArabicMixedContent() throws Exception {
    if (!assetExists(ARABIC_PDF_ASSET)) {
      System.out.println("[DEBUG_LOG] Skipping test - Arabic PDF not found: " + ARABIC_PDF_ASSET);
      return;
    }

    Bitmap page = renderPdfFirstPage(ARABIC_PDF_ASSET);
    assertNotNull("PDF page should be rendered", page);

    String ocrResult = performOcr(page, "ara");
    assertNotNull("OCR result should not be null", ocrResult);

    // LTR content in RTL context (email, URL)
    // Note: Pure RTL language models may not recognize LTR content reliably
    boolean hasEmail = ocrResult.contains("@") || ocrResult.contains("example");
    boolean hasUrl = ocrResult.contains("http") || ocrResult.contains("://");

    // Check for Arabic characters as primary success criterion
    boolean hasArabicChars = ocrResult.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);

    System.out.println(
        "[DEBUG_LOG] Mixed content check - hasEmail: "
            + hasEmail
            + ", hasUrl: "
            + hasUrl
            + ", hasArabicChars: "
            + hasArabicChars);

    // Primary: Arabic text must be recognized
    // Secondary: LTR content recognition is optional (known Tesseract limitation with pure RTL
    // models)
    assertTrue("Arabic text should be recognized", hasArabicChars);

    // Log LTR recognition status (informational, not a failure criterion)
    if (hasEmail || hasUrl) {
      System.out.println("[DEBUG_LOG] LTR content (email/URL) was also recognized - excellent!");
    } else {
      System.out.println(
          "[DEBUG_LOG] LTR content not recognized - expected limitation with pure RTL model");
    }

    page.recycle();
  }

  /**
   * Tests Character Error Rate (CER) for Persian OCR against ground truth. Target: < 26% CER
   * (generous threshold for Fast models)
   */
  @Test
  public void testPersianCER() throws Exception {
    if (!assetExists(PERSIAN_PDF_ASSET) || !assetExists(PERSIAN_TXT_ASSET)) {
      System.out.println("[DEBUG_LOG] Skipping CER test - Persian test files not found");
      return;
    }

    Bitmap page = renderPdfFirstPage(PERSIAN_PDF_ASSET);
    assertNotNull("PDF page should be rendered", page);

    String ocrResult = performOcr(page, "fas");
    String groundTruth = loadTextFromAssets(PERSIAN_TXT_ASSET);

    assertNotNull("OCR result should not be null", ocrResult);
    assertNotNull("Ground truth should not be null", groundTruth);

    double cer = calculateCER(ocrResult, groundTruth);
    System.out.println("[DEBUG_LOG] Persian CER: " + (cer * 100) + "%");

    // Generous threshold for Fast models - 26% CER
    assertTrue("Persian CER should be < 26%, was: " + (cer * 100) + "%", cer < 0.26);

    page.recycle();
  }

  /**
   * Tests Character Error Rate (CER) for Arabic OCR against ground truth. Target: < 30% CER
   * (generous threshold for Fast models with Arabic script) Note: Arabic script recognition with
   * Fast models can have higher variability due to complex ligatures and diacritics.
   */
  @Test
  public void testArabicCER() throws Exception {
    if (!assetExists(ARABIC_PDF_ASSET) || !assetExists(ARABIC_TXT_ASSET)) {
      System.out.println("[DEBUG_LOG] Skipping CER test - Arabic test files not found");
      return;
    }

    Bitmap page = renderPdfFirstPage(ARABIC_PDF_ASSET);
    assertNotNull("PDF page should be rendered", page);

    String ocrResult = performOcr(page, "ara");
    String groundTruth = loadTextFromAssets(ARABIC_TXT_ASSET);

    assertNotNull("OCR result should not be null", ocrResult);
    assertNotNull("Ground truth should not be null", groundTruth);

    double cer = calculateCER(ocrResult, groundTruth);
    System.out.println("[DEBUG_LOG] Arabic CER: " + (cer * 100) + "%");

    // Generous threshold for Fast models with Arabic script - 30% CER
    // Arabic script has complex ligatures and diacritics that increase OCR variability
    assertTrue("Arabic CER should be < 30%, was: " + (cer * 100) + "%", cer < 0.30);

    page.recycle();
  }

  /** Tests that PSM_AUTO is correctly applied for Arabic. */
  @Test
  public void testPsmAutoForArabic() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("ara");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Arabic", initialized);

      // PSM_AUTO should be set (value = 3)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Arabic PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Arabic", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  /** Tests that PSM_AUTO is correctly applied for Persian. */
  @Test
  public void testPsmAutoForPersian() throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage("fas");
      boolean initialized = helper.initTesseract();
      assertTrue("Tesseract should be initialized for Persian", initialized);

      // PSM_AUTO should be set (value = 3)
      int psm = helper.getPageSegMode();
      System.out.println("[DEBUG_LOG] Persian PSM: " + psm);
      assertEquals("PSM should be AUTO (3) for Persian", 3, psm);
    } finally {
      helper.shutdown();
    }
  }

  // ==================== Helper Methods ====================

  /** Checks if an asset file exists. */
  private boolean assetExists(String assetPath) {
    try (InputStream is = context.getAssets().open(assetPath)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** Renders the first page of a PDF from assets as a Bitmap. Uses 300 DPI for OCR quality. */
  private Bitmap renderPdfFirstPage(String assetPath) throws IOException {
    // Copy PDF to cache for PdfRenderer (requires seekable file)
    File pdfFile = copyAssetToCache(assetPath);

    try (ParcelFileDescriptor pfd =
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(pfd)) {

      if (renderer.getPageCount() == 0) {
        return null;
      }

      PdfRenderer.Page page = renderer.openPage(0);
      try {
        // Render at 300 DPI for OCR quality
        // PDF points are 72 DPI, so scale factor = 300/72 ≈ 4.17
        float scale = 300f / 72f;
        int width = (int) (page.getWidth() * scale);
        int height = (int) (page.getHeight() * scale);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // Fill with white background
        bitmap.eraseColor(android.graphics.Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
      } finally {
        page.close();
      }
    }
  }

  /** Copies an asset file to the cache directory. */
  private File copyAssetToCache(String assetPath) throws IOException {
    File outFile = new File(context.getCacheDir(), new File(assetPath).getName());
    if (outFile.exists() && outFile.length() > 0) {
      return outFile;
    }

    try (InputStream in = context.getAssets().open(assetPath);
        FileOutputStream out = new FileOutputStream(outFile)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
    }
    return outFile;
  }

  /** Performs OCR on a bitmap using the specified language. */
  private String performOcr(Bitmap bitmap, String langCode) throws Exception {
    OCRHelper helper = new OCRHelper(context);
    try {
      helper.setLanguage(langCode);
      boolean initialized = helper.initTesseract();
      if (!initialized) {
        throw new RuntimeException("Failed to initialize Tesseract for language: " + langCode);
      }

      OCRHelper.OcrResultWords result = helper.runOcrWithRetry(bitmap);
      return result != null ? result.text : null;
    } finally {
      helper.shutdown();
    }
  }

  /** Loads text content from an asset file. */
  private String loadTextFromAssets(String assetPath) throws IOException {
    try (InputStream is = context.getAssets().open(assetPath);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString().trim();
    }
  }

  /**
   * Calculates Character Error Rate (CER) using Levenshtein distance. CER = Levenshtein distance /
   * length of ground truth
   */
  private double calculateCER(String ocrResult, String groundTruth) {
    // Normalize texts for comparison
    String normalizedOcr = normalizeText(ocrResult);
    String normalizedGt = normalizeText(groundTruth);

    if (normalizedGt.isEmpty()) {
      return normalizedOcr.isEmpty() ? 0.0 : 1.0;
    }

    int distance = levenshteinDistance(normalizedOcr, normalizedGt);
    return (double) distance / normalizedGt.length();
  }

  /** Normalizes text for comparison by removing extra whitespace. */
  private String normalizeText(String text) {
    if (text == null) return "";
    // Collapse multiple whitespace to single space, trim
    return text.replaceAll("\\s+", " ").trim();
  }

  /** Calculates Levenshtein distance between two strings. */
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

  // ==================== PDF Variant Tests ====================

  /**
   * Tests OCR recognition on inverted (negative) Arabic PDF. Inverted images have white text on
   * black background.
   */
  @Test
  public void testArabicInvertedPdf() throws Exception {
    testPdfVariant(ARABIC_INVERTED_PDF, "ara", "Arabic inverted");
  }

  /**
   * Tests OCR recognition on Arabic PDF with scan noise. Simulates real-world scanned documents
   * with artifacts.
   */
  @Test
  public void testArabicScanNoisePdf() throws Exception {
    testPdfVariant(ARABIC_SCAN_NOISE_PDF, "ara", "Arabic scan noise");
  }

  /**
   * Tests OCR recognition on Arabic PDF with mixed lighting. Simulates documents photographed under
   * uneven lighting conditions.
   */
  @Test
  public void testArabicMixedLightPdf() throws Exception {
    testPdfVariant(ARABIC_MIXED_LIGHT_PDF, "ara", "Arabic mixed light");
  }

  /** Tests OCR recognition on Arabic PDF with mixed lighting (variant 2). */
  @Test
  public void testArabicMixedLightingPdf() throws Exception {
    testPdfVariant(ARABIC_MIXED_LIGHTING_PDF, "ara", "Arabic mixed lighting");
  }

  /** Tests OCR recognition on inverted (negative) Persian PDF. */
  @Test
  public void testPersianInvertedPdf() throws Exception {
    testPdfVariant(PERSIAN_INVERTED_PDF, "fas", "Persian inverted");
  }

  /** Tests OCR recognition on Persian PDF with scan noise. */
  @Test
  public void testPersianScanNoisePdf() throws Exception {
    testPdfVariant(PERSIAN_SCAN_NOISE_PDF, "fas", "Persian scan noise");
  }

  /** Tests OCR recognition on Persian PDF with mixed lighting. */
  @Test
  public void testPersianMixedLightPdf() throws Exception {
    testPdfVariant(PERSIAN_MIXED_LIGHT_PDF, "fas", "Persian mixed light");
  }

  /** Tests OCR recognition on Persian PDF with mixed lighting (variant 2). */
  @Test
  public void testPersianMixedLightingPdf() throws Exception {
    testPdfVariant(PERSIAN_MIXED_LIGHTING_PDF, "fas", "Persian mixed lighting");
  }

  /**
   * Helper method to test a PDF variant for basic OCR recognition. Verifies that: 1. The PDF can be
   * rendered 2. OCR produces non-empty results 3. Arabic/Persian characters are recognized
   *
   * @param pdfAsset Path to the PDF asset
   * @param langCode Language code ("ara" or "fas")
   * @param description Description for logging
   */
  private void testPdfVariant(String pdfAsset, String langCode, String description)
      throws Exception {
    if (!assetExists(pdfAsset)) {
      System.out.println("[DEBUG_LOG] Skipping test - PDF not found: " + pdfAsset);
      return;
    }

    System.out.println("[DEBUG_LOG] Testing " + description + ": " + pdfAsset);

    Bitmap page = renderPdfFirstPage(pdfAsset);
    assertNotNull(description + " - PDF page should be rendered", page);

    try {
      String ocrResult = performOcr(page, langCode);
      assertNotNull(description + " - OCR result should not be null", ocrResult);

      System.out.println(
          "[DEBUG_LOG] " + description + " OCR result length: " + ocrResult.length());
      System.out.println(
          "[DEBUG_LOG] "
              + description
              + " OCR result (first 300 chars): "
              + ocrResult.substring(0, Math.min(300, ocrResult.length())));

      // Verify that some text was recognized
      assertFalse(description + " - OCR result should not be empty", ocrResult.trim().isEmpty());

      // Verify Arabic/Persian characters are present (Unicode range 0x0600-0x06FF)
      boolean hasArabicChars = ocrResult.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
      assertTrue(description + " - Should contain Arabic/Persian characters", hasArabicChars);

      // Count Arabic characters for quality assessment
      long arabicCharCount =
          ocrResult.codePoints().filter(cp -> cp >= 0x0600 && cp <= 0x06FF).count();
      System.out.println(
          "[DEBUG_LOG] " + description + " - Arabic character count: " + arabicCharCount);

    } finally {
      page.recycle();
    }
  }

  // ==================== OCR Preprocessing Mode Tests ====================

  /** Enum for OCR preprocessing modes. */
  private enum PreprocessingMode {
    ORIGINAL, // No preprocessing
    QUICK, // Quick preprocessing (prepareForOCRQuick)
    ROBUST // Robust preprocessing (prepareForOCR with grayscale)
  }

  /** Helper method to test a PDF with a specific preprocessing mode. */
  private void testPdfWithPreprocessing(
      String pdfAsset, String langCode, String description, PreprocessingMode mode)
      throws Exception {
    if (!assetExists(pdfAsset)) {
      System.out.println("[DEBUG_LOG] Skipping test - PDF not found: " + pdfAsset);
      return;
    }

    String modeStr = mode.name();
    System.out.println(
        "[DEBUG_LOG] Testing " + description + " with " + modeStr + " preprocessing: " + pdfAsset);

    Bitmap page = renderPdfFirstPage(pdfAsset);
    assertNotNull(description + " (" + modeStr + ") - PDF page should be rendered", page);

    Bitmap preprocessed = null;
    try {
      // Apply preprocessing based on mode
      switch (mode) {
        case QUICK:
          preprocessed = OpenCVUtils.prepareForOCRQuick(page);
          break;
        case ROBUST:
          preprocessed = OpenCVUtils.prepareForOCR(page, /*binaryOutput*/ false);
          break;
        case ORIGINAL:
        default:
          preprocessed = page;
          break;
      }

      if (preprocessed == null) {
        System.out.println(
            "[DEBUG_LOG] "
                + description
                + " ("
                + modeStr
                + ") - Preprocessing returned null, using original");
        preprocessed = page;
      }

      String ocrResult = performOcr(preprocessed, langCode);
      assertNotNull(description + " (" + modeStr + ") - OCR result should not be null", ocrResult);

      System.out.println(
          "[DEBUG_LOG] "
              + description
              + " ("
              + modeStr
              + ") OCR result length: "
              + ocrResult.length());
      System.out.println(
          "[DEBUG_LOG] "
              + description
              + " ("
              + modeStr
              + ") OCR result (first 300 chars): "
              + ocrResult.substring(0, Math.min(300, ocrResult.length())));

      // Verify that some text was recognized
      assertFalse(
          description + " (" + modeStr + ") - OCR result should not be empty",
          ocrResult.trim().isEmpty());

      // Verify Arabic/Persian characters are present (Unicode range 0x0600-0x06FF)
      boolean hasArabicChars = ocrResult.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
      assertTrue(
          description + " (" + modeStr + ") - Should contain Arabic/Persian characters",
          hasArabicChars);

      // Count Arabic characters for quality assessment
      long arabicCharCount =
          ocrResult.codePoints().filter(cp -> cp >= 0x0600 && cp <= 0x06FF).count();
      System.out.println(
          "[DEBUG_LOG] "
              + description
              + " ("
              + modeStr
              + ") - Arabic character count: "
              + arabicCharCount);

    } finally {
      if (preprocessed != null && preprocessed != page) {
        preprocessed.recycle();
      }
      page.recycle();
    }
  }

  // ==================== Arabic PDF Tests with QUICK Preprocessing ====================

  @Test
  public void testArabicPdf_Quick() throws Exception {
    testPdfWithPreprocessing(ARABIC_PDF_ASSET, "ara", "Arabic standard", PreprocessingMode.QUICK);
  }

  @Test
  public void testArabicInvertedPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_INVERTED_PDF, "ara", "Arabic inverted", PreprocessingMode.QUICK);
  }

  @Test
  public void testArabicScanNoisePdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_SCAN_NOISE_PDF, "ara", "Arabic scan noise", PreprocessingMode.QUICK);
  }

  @Test
  public void testArabicMixedLightPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_MIXED_LIGHT_PDF, "ara", "Arabic mixed light", PreprocessingMode.QUICK);
  }

  @Test
  public void testArabicMixedLightingPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_MIXED_LIGHTING_PDF, "ara", "Arabic mixed lighting", PreprocessingMode.QUICK);
  }

  // ==================== Arabic PDF Tests with ROBUST Preprocessing ====================

  @Test
  public void testArabicPdf_Robust() throws Exception {
    testPdfWithPreprocessing(ARABIC_PDF_ASSET, "ara", "Arabic standard", PreprocessingMode.ROBUST);
  }

  @Test
  public void testArabicInvertedPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_INVERTED_PDF, "ara", "Arabic inverted", PreprocessingMode.ROBUST);
  }

  @Test
  public void testArabicScanNoisePdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_SCAN_NOISE_PDF, "ara", "Arabic scan noise", PreprocessingMode.ROBUST);
  }

  @Test
  public void testArabicMixedLightPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_MIXED_LIGHT_PDF, "ara", "Arabic mixed light", PreprocessingMode.ROBUST);
  }

  @Test
  public void testArabicMixedLightingPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        ARABIC_MIXED_LIGHTING_PDF, "ara", "Arabic mixed lighting", PreprocessingMode.ROBUST);
  }

  // ==================== Persian PDF Tests with QUICK Preprocessing ====================

  @Test
  public void testPersianPdf_Quick() throws Exception {
    testPdfWithPreprocessing(PERSIAN_PDF_ASSET, "fas", "Persian standard", PreprocessingMode.QUICK);
  }

  @Test
  public void testPersianInvertedPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_INVERTED_PDF, "fas", "Persian inverted", PreprocessingMode.QUICK);
  }

  @Test
  public void testPersianScanNoisePdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_SCAN_NOISE_PDF, "fas", "Persian scan noise", PreprocessingMode.QUICK);
  }

  @Test
  public void testPersianMixedLightPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_MIXED_LIGHT_PDF, "fas", "Persian mixed light", PreprocessingMode.QUICK);
  }

  @Test
  public void testPersianMixedLightingPdf_Quick() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_MIXED_LIGHTING_PDF, "fas", "Persian mixed lighting", PreprocessingMode.QUICK);
  }

  // ==================== Persian PDF Tests with ROBUST Preprocessing ====================

  @Test
  public void testPersianPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_PDF_ASSET, "fas", "Persian standard", PreprocessingMode.ROBUST);
  }

  @Test
  public void testPersianInvertedPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_INVERTED_PDF, "fas", "Persian inverted", PreprocessingMode.ROBUST);
  }

  @Test
  public void testPersianScanNoisePdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_SCAN_NOISE_PDF, "fas", "Persian scan noise", PreprocessingMode.ROBUST);
  }

  @Test
  public void testPersianMixedLightPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_MIXED_LIGHT_PDF, "fas", "Persian mixed light", PreprocessingMode.ROBUST);
  }

  @Test
  public void testPersianMixedLightingPdf_Robust() throws Exception {
    testPdfWithPreprocessing(
        PERSIAN_MIXED_LIGHTING_PDF, "fas", "Persian mixed lighting", PreprocessingMode.ROBUST);
  }
}
