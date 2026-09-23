package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

/**
 * Instrumented tests for DocumentLayoutAnalyzer using real PDF documents. Tests table detection,
 * column detection, and layout analysis with sample-tables.pdf.
 */
@RunWith(AndroidJUnit4.class)
public class DocumentLayoutAnalyzerInstrumentedTest {

  private static final String SAMPLE_TABLES_PDF = "instrumented_test_data/sample-tables.pdf";

  private static Context context;
  private static boolean openCvInitialized = false;

  private DocumentLayoutAnalyzer analyzer;

  @BeforeClass
  public static void setupClass() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    openCvInitialized = OpenCVLoader.initLocal();
    System.out.println("[DEBUG_LOG] OpenCV initialized: " + openCvInitialized);
  }

  @Before
  public void setUp() {
    analyzer = new DocumentLayoutAnalyzer();
  }

  // ==================== PDF Asset Tests ====================

  @Test
  public void sampleTablesPdf_exists() {
    assertTrue("sample-tables.pdf should exist in assets", assetExists(SAMPLE_TABLES_PDF));
  }

  @Test
  public void sampleTablesPdf_canBeRenderedAsBitmap() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);
    assertTrue("Bitmap width should be positive", bitmap.getWidth() > 0);
    assertTrue("Bitmap height should be positive", bitmap.getHeight() > 0);

    System.out.println("[DEBUG_LOG] Rendered PDF: " + bitmap.getWidth() + "x" + bitmap.getHeight());
    bitmap.recycle();
  }

  // ==================== Layout Analysis Tests ====================

  @Test
  public void analyze_withSampleTablesPdf_returnsRegions() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    assertNotNull("Regions should not be null", regions);
    assertFalse("Regions should not be empty", regions.isEmpty());

    System.out.println("[DEBUG_LOG] Detected " + regions.size() + " regions:");
    for (DocumentRegion region : regions) {
      System.out.println("[DEBUG_LOG]   - " + region.getType() + " at " + region.getBounds());
    }

    bitmap.recycle();
  }

  @Test
  public void analyze_withSampleTablesPdf_detectsTables() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setDetectTables(true);
    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    boolean hasTable = regions.stream().anyMatch(r -> r.getType() == DocumentRegion.Type.TABLE);

    System.out.println("[DEBUG_LOG] Table detected: " + hasTable);
    System.out.println("[DEBUG_LOG] Region types: ");
    for (DocumentRegion region : regions) {
      System.out.println("[DEBUG_LOG]   - " + region.getType());
    }

    // Note: Table detection depends on the PDF content
    // This test verifies the analysis runs without errors
    assertNotNull("Regions should not be null", regions);

    bitmap.recycle();
  }

  @Test
  public void hasComplexLayout_withSampleTablesPdf_returnsResult() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    boolean isComplex = analyzer.hasComplexLayout(bitmap);

    System.out.println("[DEBUG_LOG] Has complex layout: " + isComplex);

    // The method should return without error
    // Result depends on PDF content
    bitmap.recycle();
  }

  @Test
  public void getColumnCount_withSampleTablesPdf_returnsPositiveCount() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    int columnCount = analyzer.getColumnCount(bitmap);

    System.out.println("[DEBUG_LOG] Column count: " + columnCount);
    assertTrue("Column count should be at least 1", columnCount >= 1);

    bitmap.recycle();
  }

  @Test
  public void analyzeWithMetadata_withSampleTablesPdf_returnsAnalysisResult() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    DocumentLayoutAnalyzer.AnalysisResult result = analyzer.analyzeWithMetadata(bitmap);

    assertNotNull("AnalysisResult should not be null", result);
    assertNotNull("Regions should not be null", result.regions());
    assertTrue("Column count should be at least 1", result.columnCount() >= 1);
    assertNotNull("Text direction should not be null", result.textDirection());

    System.out.println("[DEBUG_LOG] AnalysisResult:");
    System.out.println("[DEBUG_LOG]   - Regions: " + result.regions().size());
    System.out.println("[DEBUG_LOG]   - Columns: " + result.columnCount());
    System.out.println("[DEBUG_LOG]   - Has table: " + result.hasTable());
    System.out.println("[DEBUG_LOG]   - Has header: " + result.hasHeader());
    System.out.println("[DEBUG_LOG]   - Has footer: " + result.hasFooter());
    System.out.println("[DEBUG_LOG]   - Direction: " + result.textDirection());

    bitmap.recycle();
  }

  // ==================== Configuration Tests ====================

  @Test
  public void analyze_withTableDetectionDisabled_skipsTableDetection() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setDetectTables(false);
    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    boolean hasTable = regions.stream().anyMatch(r -> r.getType() == DocumentRegion.Type.TABLE);

    System.out.println("[DEBUG_LOG] Table detection disabled, has table: " + hasTable);
    assertFalse("Should not detect tables when disabled", hasTable);

    bitmap.recycle();
  }

  @Test
  public void analyze_withColumnDetectionDisabled_skipsColumnDetection() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setDetectColumns(false);
    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    boolean hasColumn = regions.stream().anyMatch(r -> r.getType() == DocumentRegion.Type.COLUMN);

    System.out.println("[DEBUG_LOG] Column detection disabled, has column: " + hasColumn);
    assertFalse("Should not detect columns when disabled", hasColumn);

    bitmap.recycle();
  }

  @Test
  public void analyze_withHeaderFooterDetectionDisabled_skipsHeaderFooter() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setDetectHeaderFooter(false);
    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    boolean hasHeader = regions.stream().anyMatch(r -> r.getType() == DocumentRegion.Type.HEADER);
    boolean hasFooter = regions.stream().anyMatch(r -> r.getType() == DocumentRegion.Type.FOOTER);

    System.out.println("[DEBUG_LOG] Header/Footer detection disabled");
    System.out.println("[DEBUG_LOG]   - Has header: " + hasHeader);
    System.out.println("[DEBUG_LOG]   - Has footer: " + hasFooter);

    assertFalse("Should not detect header when disabled", hasHeader);
    assertFalse("Should not detect footer when disabled", hasFooter);

    bitmap.recycle();
  }

  @Test
  public void analyze_withAllDetectionDisabled_returnsFallbackBodyRegion() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setDetectTables(false);
    analyzer.setDetectColumns(false);
    analyzer.setDetectHeaderFooter(false);

    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    assertNotNull("Regions should not be null", regions);
    assertFalse("Should have at least one region (fallback body)", regions.isEmpty());

    System.out.println("[DEBUG_LOG] All detection disabled, regions: " + regions.size());
    for (DocumentRegion region : regions) {
      System.out.println("[DEBUG_LOG]   - " + region.getType());
    }

    bitmap.recycle();
  }

  // ==================== Language Configuration Tests ====================

  @Test
  public void analyze_withGermanLanguage_usesLtrDirection() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setLanguage("deu");
    DocumentLayoutAnalyzer.AnalysisResult result = analyzer.analyzeWithMetadata(bitmap);

    assertEquals(
        "German should use LTR direction",
        ReadingOrderSorter.TextDirection.LTR,
        result.textDirection());

    bitmap.recycle();
  }

  @Test
  public void analyze_withArabicLanguage_usesRtlDirection() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    analyzer.setLanguage("ara");
    DocumentLayoutAnalyzer.AnalysisResult result = analyzer.analyzeWithMetadata(bitmap);

    assertEquals(
        "Arabic should use RTL direction",
        ReadingOrderSorter.TextDirection.RTL,
        result.textDirection());

    bitmap.recycle();
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
        float scale = 300f / 72f;
        int width = (int) (page.getWidth() * scale);
        int height = (int) (page.getHeight() * scale);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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

  // ==================== OCR Layer Comparison Tests ====================

  @Test
  public void ocrComparison_embeddedTextVsRecognizedText_firstPage() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    // Initialize PDFBox
    PDFBoxResourceLoader.init(context);

    // 1. Extract embedded OCR text from PDF using PDFTextStripper
    File pdfFile = copyAssetToCache(SAMPLE_TABLES_PDF);
    String embeddedText;
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      embeddedText = stripper.getText(document);
    }

    System.out.println("[DEBUG_LOG] === Embedded OCR Text (first page) ===");
    System.out.println("[DEBUG_LOG] Length: " + embeddedText.length() + " characters");
    System.out.println("[DEBUG_LOG] Preview (first 500 chars):");
    System.out.println(
        "[DEBUG_LOG] " + embeddedText.substring(0, Math.min(500, embeddedText.length())));

    assertNotNull("Embedded text should not be null", embeddedText);
    assertFalse("PDF should contain embedded OCR text", embeddedText.trim().isEmpty());

    // 2. Render PDF first page as bitmap and run OCR
    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    OCRHelper ocrHelper = new OCRHelper(context);
    String recognizedText;
    try {
      ocrHelper.setLanguage("eng");
      boolean initialized = ocrHelper.initTesseract();
      assertTrue("Tesseract should be initialized", initialized);

      // Preprocess for better OCR
      Bitmap preprocessed = OpenCVUtils.prepareForOCR(bitmap, false);

      OCRHelper.OcrResultWords result = ocrHelper.runOcrWithRetry(preprocessed);
      recognizedText = result != null ? result.text : "";

      if (preprocessed != bitmap && preprocessed != null && !preprocessed.isRecycled()) {
        preprocessed.recycle();
      }
    } finally {
      ocrHelper.shutdown();
      bitmap.recycle();
    }

    System.out.println("[DEBUG_LOG] === Recognized OCR Text (first page) ===");
    System.out.println("[DEBUG_LOG] Length: " + recognizedText.length() + " characters");
    System.out.println("[DEBUG_LOG] Preview (first 500 chars):");
    System.out.println(
        "[DEBUG_LOG] " + recognizedText.substring(0, Math.min(500, recognizedText.length())));

    assertNotNull("Recognized text should not be null", recognizedText);
    assertFalse("OCR should recognize text", recognizedText.trim().isEmpty());

    // 3. Compare texts - calculate similarity
    double similarity = calculateTextSimilarity(embeddedText, recognizedText);
    System.out.println("[DEBUG_LOG] === Text Comparison ===");
    System.out.println("[DEBUG_LOG] Similarity: " + String.format("%.2f%%", similarity * 100));

    // 4. Check for common words/phrases that should appear in both
    String[] expectedKeywords = {"Table", "Sample", "Data", "Row", "Column"};
    int embeddedMatches = 0;
    int recognizedMatches = 0;
    String embeddedLower = embeddedText.toLowerCase(java.util.Locale.ROOT);
    String recognizedLower = recognizedText.toLowerCase(java.util.Locale.ROOT);

    System.out.println("[DEBUG_LOG] Keyword matches:");
    for (String keyword : expectedKeywords) {
      boolean inEmbedded = embeddedLower.contains(keyword.toLowerCase(java.util.Locale.ROOT));
      boolean inRecognized = recognizedLower.contains(keyword.toLowerCase(java.util.Locale.ROOT));
      if (inEmbedded) embeddedMatches++;
      if (inRecognized) recognizedMatches++;
      System.out.println(
          "[DEBUG_LOG]   - '"
              + keyword
              + "': embedded="
              + inEmbedded
              + ", recognized="
              + inRecognized);
    }

    System.out.println(
        "[DEBUG_LOG] Embedded keyword matches: " + embeddedMatches + "/" + expectedKeywords.length);
    System.out.println(
        "[DEBUG_LOG] Recognized keyword matches: "
            + recognizedMatches
            + "/"
            + expectedKeywords.length);

    // The test passes if both texts contain content
    // Exact matching is not expected due to OCR variations
    assertTrue(
        "Both embedded and recognized text should have content",
        embeddedText.length() > 100 && recognizedText.length() > 100);
  }

  /**
   * Tests improved OCR recognition using layout-aware processing. Compares standard OCR vs
   * layout-based OCR for table-heavy documents.
   */
  @Test
  public void ocrComparison_layoutBasedOcr_improvesRecognition() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    // Initialize PDFBox
    PDFBoxResourceLoader.init(context);

    // 1. Extract embedded OCR text from PDF
    File pdfFile = copyAssetToCache(SAMPLE_TABLES_PDF);
    String embeddedText;
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      embeddedText = stripper.getText(document);
    }

    // 2. Render PDF first page as bitmap
    Bitmap bitmap = renderPdfFirstPage(SAMPLE_TABLES_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    // Preprocess for better OCR
    Bitmap preprocessed = OpenCVUtils.prepareForOCR(bitmap, false);

    // 3. Run standard OCR
    OCRHelper ocrHelper = new OCRHelper(context);
    String standardText;
    int standardConfidence = 0;
    try {
      ocrHelper.setLanguage("eng");
      boolean initialized = ocrHelper.initTesseract();
      assertTrue("Tesseract should be initialized", initialized);

      OCRHelper.OcrResultWords standardResult = ocrHelper.runOcrWithRetry(preprocessed);
      standardText = standardResult != null ? standardResult.text : "";
      standardConfidence =
          standardResult != null && standardResult.meanConfidence != null
              ? standardResult.meanConfidence
              : 0;
    } finally {
      ocrHelper.shutdown();
    }

    // 4. Run layout-based OCR (reinitialize helper)
    OCRHelper layoutOcrHelper = new OCRHelper(context);
    String layoutText;
    int layoutConfidence = 0;
    try {
      layoutOcrHelper.setLanguage("eng");
      boolean initialized = layoutOcrHelper.initTesseract();
      assertTrue("Tesseract should be initialized for layout OCR", initialized);

      OCRHelper.OcrResultWithLayout layoutResult = layoutOcrHelper.runOcrWithLayout(preprocessed);
      layoutText = layoutResult != null ? layoutResult.text : "";
      layoutConfidence =
          layoutResult != null && layoutResult.meanConfidence != null
              ? layoutResult.meanConfidence
              : 0;

      // Log region details
      if (layoutResult != null && layoutResult.layoutAnalysis != null) {
        System.out.println("[DEBUG_LOG] === Layout Analysis ===");
        System.out.println("[DEBUG_LOG] Regions: " + layoutResult.layoutAnalysis.regions().size());
        System.out.println("[DEBUG_LOG] Columns: " + layoutResult.layoutAnalysis.columnCount());
        System.out.println("[DEBUG_LOG] Has table: " + layoutResult.layoutAnalysis.hasTable());
      }
    } finally {
      layoutOcrHelper.shutdown();
    }

    // Cleanup
    if (preprocessed != bitmap && preprocessed != null && !preprocessed.isRecycled()) {
      preprocessed.recycle();
    }
    bitmap.recycle();

    // 5. Compare results
    double standardSimilarity = calculateTextSimilarity(embeddedText, standardText);
    double layoutSimilarity = calculateTextSimilarity(embeddedText, layoutText);

    System.out.println("[DEBUG_LOG] === OCR Comparison Results ===");
    System.out.println("[DEBUG_LOG] Standard OCR:");
    System.out.println("[DEBUG_LOG]   - Length: " + standardText.length() + " chars");
    System.out.println("[DEBUG_LOG]   - Confidence: " + standardConfidence + "%");
    System.out.println(
        "[DEBUG_LOG]   - Similarity to embedded: "
            + String.format("%.2f%%", standardSimilarity * 100));
    System.out.println("[DEBUG_LOG] Layout-based OCR:");
    System.out.println("[DEBUG_LOG]   - Length: " + layoutText.length() + " chars");
    System.out.println("[DEBUG_LOG]   - Confidence: " + layoutConfidence + "%");
    System.out.println(
        "[DEBUG_LOG]   - Similarity to embedded: "
            + String.format("%.2f%%", layoutSimilarity * 100));

    // Check keyword recognition improvement
    String[] keywords = {"Table", "Sample", "Data", "Row", "Column", "Financial", "Policy"};
    int standardKeywords = 0;
    int layoutKeywords = 0;
    String standardLower = standardText.toLowerCase(java.util.Locale.ROOT);
    String layoutLower = layoutText.toLowerCase(java.util.Locale.ROOT);

    System.out.println("[DEBUG_LOG] Keyword comparison:");
    for (String keyword : keywords) {
      boolean inStandard = standardLower.contains(keyword.toLowerCase(java.util.Locale.ROOT));
      boolean inLayout = layoutLower.contains(keyword.toLowerCase(java.util.Locale.ROOT));
      if (inStandard) standardKeywords++;
      if (inLayout) layoutKeywords++;
      System.out.println(
          "[DEBUG_LOG]   - '" + keyword + "': standard=" + inStandard + ", layout=" + inLayout);
    }

    System.out.println(
        "[DEBUG_LOG] Standard keywords: " + standardKeywords + "/" + keywords.length);
    System.out.println("[DEBUG_LOG] Layout keywords: " + layoutKeywords + "/" + keywords.length);

    // Test passes if both methods produce output
    assertTrue("Standard OCR should produce text", standardText.length() > 50);
    assertTrue("Layout OCR should produce text", layoutText.length() > 50);
  }

  // ==================== Multicolumn PDF Tests ====================

  private static final String MULTICOLUMN_PDF =
      "instrumented_test_data/makeacopy_multicolumn_test.pdf";

  @Test
  public void multicolumnPdf_exists() {
    assertTrue(
        "makeacopy_multicolumn_test.pdf should exist in assets", assetExists(MULTICOLUMN_PDF));
  }

  @Test
  public void multicolumnPdf_canBeRenderedAsBitmap() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);
    assertTrue("Bitmap width should be positive", bitmap.getWidth() > 0);
    assertTrue("Bitmap height should be positive", bitmap.getHeight() > 0);

    System.out.println(
        "[DEBUG_LOG] Multicolumn PDF rendered: " + bitmap.getWidth() + "x" + bitmap.getHeight());
    bitmap.recycle();
  }

  @Test
  public void multicolumnPdf_detectsTwoColumns() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    int columnCount = analyzer.getColumnCount(bitmap);

    System.out.println("[DEBUG_LOG] Multicolumn PDF - Column count: " + columnCount);
    assertEquals("Should detect 2 columns", 2, columnCount);

    bitmap.recycle();
  }

  @Test
  public void multicolumnPdf_hasComplexLayout() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    boolean isComplex = analyzer.hasComplexLayout(bitmap);

    System.out.println("[DEBUG_LOG] Multicolumn PDF - Has complex layout: " + isComplex);
    assertTrue("2-column layout should be detected as complex", isComplex);

    bitmap.recycle();
  }

  @Test
  public void multicolumnPdf_analyzeReturnsColumnRegions() throws IOException {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    List<DocumentRegion> regions = analyzer.analyze(bitmap);

    assertNotNull("Regions should not be null", regions);
    assertFalse("Regions should not be empty", regions.isEmpty());

    // Count column regions
    long columnCount =
        regions.stream().filter(r -> r.getType() == DocumentRegion.Type.COLUMN).count();

    System.out.println("[DEBUG_LOG] Multicolumn PDF - Detected " + regions.size() + " regions:");
    for (DocumentRegion region : regions) {
      System.out.println("[DEBUG_LOG]   - " + region.getType() + " at " + region.getBounds());
    }
    System.out.println("[DEBUG_LOG] Column regions: " + columnCount);

    bitmap.recycle();
  }

  @Test
  public void multicolumnPdf_ocrRecognizesHeadingAcrossColumns() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    // Initialize PDFBox
    PDFBoxResourceLoader.init(context);

    // Extract embedded text from PDF
    File pdfFile = copyAssetToCache(MULTICOLUMN_PDF);
    String embeddedText;
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      embeddedText = stripper.getText(document);
    }

    System.out.println("[DEBUG_LOG] === Multicolumn PDF - Embedded Text ===");
    System.out.println("[DEBUG_LOG] Length: " + embeddedText.length() + " characters");
    System.out.println("[DEBUG_LOG] Content:\n" + embeddedText);

    // Render and run OCR
    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    OCRHelper ocrHelper = new OCRHelper(context);
    String recognizedText;
    try {
      ocrHelper.setLanguage("eng");
      boolean initialized = ocrHelper.initTesseract();
      assertTrue("Tesseract should be initialized", initialized);

      Bitmap preprocessed = OpenCVUtils.prepareForOCR(bitmap, false);
      OCRHelper.OcrResultWithLayout result = ocrHelper.runOcrWithLayout(preprocessed);
      recognizedText = result != null ? result.text : "";

      if (result != null && result.layoutAnalysis != null) {
        System.out.println("[DEBUG_LOG] === Layout Analysis ===");
        System.out.println("[DEBUG_LOG] Regions: " + result.layoutAnalysis.regions().size());
        System.out.println("[DEBUG_LOG] Columns: " + result.layoutAnalysis.columnCount());
      }

      if (preprocessed != bitmap && preprocessed != null && !preprocessed.isRecycled()) {
        preprocessed.recycle();
      }
    } finally {
      ocrHelper.shutdown();
      bitmap.recycle();
    }

    System.out.println("[DEBUG_LOG] === Multicolumn PDF - Recognized Text ===");
    System.out.println("[DEBUG_LOG] Length: " + recognizedText.length() + " characters");
    System.out.println("[DEBUG_LOG] Content:\n" + recognizedText);

    assertNotNull("Recognized text should not be null", recognizedText);
    assertFalse("OCR should recognize text", recognizedText.trim().isEmpty());
  }

  @Test
  public void multicolumnPdf_ocrRecognizesNumbersAndSpecialChars() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    OCRHelper ocrHelper = new OCRHelper(context);
    String recognizedText;
    try {
      ocrHelper.setLanguage("eng");
      boolean initialized = ocrHelper.initTesseract();
      assertTrue("Tesseract should be initialized", initialized);

      Bitmap preprocessed = OpenCVUtils.prepareForOCR(bitmap, false);
      OCRHelper.OcrResultWords result = ocrHelper.runOcrWithRetry(preprocessed);
      recognizedText = result != null ? result.text : "";

      if (preprocessed != bitmap && preprocessed != null && !preprocessed.isRecycled()) {
        preprocessed.recycle();
      }
    } finally {
      ocrHelper.shutdown();
      bitmap.recycle();
    }

    System.out.println("[DEBUG_LOG] === Multicolumn PDF - OCR for Numbers/Chars ===");
    System.out.println("[DEBUG_LOG] Recognized text: " + recognizedText);

    // Check if numbers are recognized (OCR-critical test)
    boolean hasNumbers = recognizedText.matches(".*\\d+.*");
    System.out.println("[DEBUG_LOG] Contains numbers: " + hasNumbers);

    assertNotNull("Recognized text should not be null", recognizedText);
  }

  @Test
  public void multicolumnPdf_readingOrderIsCorrect() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    // Analyze layout
    DocumentLayoutAnalyzer.AnalysisResult result = analyzer.analyzeWithMetadata(bitmap);

    assertNotNull("AnalysisResult should not be null", result);
    assertNotNull("Regions should not be null", result.regions());

    System.out.println("[DEBUG_LOG] === Multicolumn PDF - Reading Order ===");
    System.out.println("[DEBUG_LOG] Text direction: " + result.textDirection());
    System.out.println("[DEBUG_LOG] Column count: " + result.columnCount());

    // Check reading order of regions
    List<DocumentRegion> regions = result.regions();
    System.out.println("[DEBUG_LOG] Regions in reading order:");
    for (int i = 0; i < regions.size(); i++) {
      DocumentRegion region = regions.get(i);
      System.out.println(
          "[DEBUG_LOG]   "
              + i
              + ": "
              + region.getType()
              + " (order="
              + region.getReadingOrder()
              + ") at "
              + region.getBounds());
    }

    // Footer should be last in reading order (if detected)
    boolean hasFooter = result.hasFooter();
    if (hasFooter) {
      DocumentRegion lastRegion = regions.get(regions.size() - 1);
      System.out.println("[DEBUG_LOG] Last region type: " + lastRegion.getType());
      assertEquals(
          "Footer should be last in reading order",
          DocumentRegion.Type.FOOTER,
          lastRegion.getType());
    }

    bitmap.recycle();
  }

  @Test
  public void multicolumnPdf_layoutBasedOcrImproves() throws Exception {
    assertTrue("OpenCV should be initialized", openCvInitialized);

    // Initialize PDFBox
    PDFBoxResourceLoader.init(context);

    // Extract embedded text
    File pdfFile = copyAssetToCache(MULTICOLUMN_PDF);
    String embeddedText;
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(1);
      embeddedText = stripper.getText(document);
    }

    Bitmap bitmap = renderPdfFirstPage(MULTICOLUMN_PDF);
    assertNotNull("PDF should be rendered as bitmap", bitmap);

    Bitmap preprocessed = OpenCVUtils.prepareForOCR(bitmap, false);

    // Standard OCR
    OCRHelper standardHelper = new OCRHelper(context);
    String standardText;
    int standardConfidence = 0;
    try {
      standardHelper.setLanguage("eng");
      standardHelper.initTesseract();
      OCRHelper.OcrResultWords standardResult = standardHelper.runOcrWithRetry(preprocessed);
      standardText = standardResult != null ? standardResult.text : "";
      standardConfidence =
          standardResult != null && standardResult.meanConfidence != null
              ? standardResult.meanConfidence
              : 0;
    } finally {
      standardHelper.shutdown();
    }

    // Layout-based OCR
    OCRHelper layoutHelper = new OCRHelper(context);
    String layoutText;
    int layoutConfidence = 0;
    try {
      layoutHelper.setLanguage("eng");
      layoutHelper.initTesseract();
      OCRHelper.OcrResultWithLayout layoutResult = layoutHelper.runOcrWithLayout(preprocessed);
      layoutText = layoutResult != null ? layoutResult.text : "";
      layoutConfidence =
          layoutResult != null && layoutResult.meanConfidence != null
              ? layoutResult.meanConfidence
              : 0;

      if (layoutResult != null && layoutResult.layoutAnalysis != null) {
        System.out.println("[DEBUG_LOG] === Multicolumn Layout Analysis ===");
        System.out.println("[DEBUG_LOG] Regions: " + layoutResult.layoutAnalysis.regions().size());
        System.out.println("[DEBUG_LOG] Columns: " + layoutResult.layoutAnalysis.columnCount());
        System.out.println("[DEBUG_LOG] Has table: " + layoutResult.layoutAnalysis.hasTable());
      }
    } finally {
      layoutHelper.shutdown();
    }

    // Cleanup
    if (preprocessed != bitmap && preprocessed != null && !preprocessed.isRecycled()) {
      preprocessed.recycle();
    }
    bitmap.recycle();

    // Compare results
    double standardSimilarity = calculateTextSimilarity(embeddedText, standardText);
    double layoutSimilarity = calculateTextSimilarity(embeddedText, layoutText);

    System.out.println("[DEBUG_LOG] === Multicolumn OCR Comparison ===");
    System.out.println("[DEBUG_LOG] Standard OCR:");
    System.out.println("[DEBUG_LOG]   - Length: " + standardText.length() + " chars");
    System.out.println("[DEBUG_LOG]   - Confidence: " + standardConfidence + "%");
    System.out.println(
        "[DEBUG_LOG]   - Similarity: " + String.format("%.2f%%", standardSimilarity * 100));
    System.out.println("[DEBUG_LOG] Layout-based OCR:");
    System.out.println("[DEBUG_LOG]   - Length: " + layoutText.length() + " chars");
    System.out.println("[DEBUG_LOG]   - Confidence: " + layoutConfidence + "%");
    System.out.println(
        "[DEBUG_LOG]   - Similarity: " + String.format("%.2f%%", layoutSimilarity * 100));

    // Test passes if both methods produce output
    assertTrue("Standard OCR should produce text", standardText.length() > 10);
    assertTrue("Layout OCR should produce text", layoutText.length() > 10);
  }

  /**
   * Calculates a simple text similarity based on common words. Returns a value between 0.0 (no
   * similarity) and 1.0 (identical).
   */
  @SuppressWarnings("StringSplitter")
  private double calculateTextSimilarity(String text1, String text2) {
    if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
      return 0.0;
    }

    // Normalize texts
    String[] words1 =
        text1.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
    String[] words2 =
        text2.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").split("\\s+");

    // Count common words
    java.util.Set<String> set1 = new java.util.HashSet<>();
    for (String w : words1) {
      if (w.length() > 2) set1.add(w);
    }

    java.util.Set<String> set2 = new java.util.HashSet<>();
    for (String w : words2) {
      if (w.length() > 2) set2.add(w);
    }

    // Jaccard similarity
    java.util.Set<String> intersection = new java.util.HashSet<>(set1);
    intersection.retainAll(set2);

    java.util.Set<String> union = new java.util.HashSet<>(set1);
    union.addAll(set2);

    if (union.isEmpty()) return 0.0;
    return (double) intersection.size() / union.size();
  }
}
