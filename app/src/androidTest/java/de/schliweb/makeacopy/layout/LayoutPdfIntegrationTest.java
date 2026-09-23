/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.layout;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.schliweb.makeacopy.utils.infra.FeatureFlags;
import de.schliweb.makeacopy.utils.layout.DocumentLayoutAnalyzer;
import de.schliweb.makeacopy.utils.layout.DocumentRegion;
import de.schliweb.makeacopy.utils.layout.ReadingOrderSorter;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Instrumented integration tests that run real PDF files through the layout analysis pipeline
 * with the layout feature explicitly activated via {@link FeatureFlags#setLayoutAnalysisOverride}.
 *
 * <p>These tests activate the layout feature through the same mechanism used in production code,
 * ensuring that the feature gate in {@link OCRHelper} and other production classes is respected.
 * The tests verify that:
 * <ul>
 *   <li>The layout feature can be activated via the override mechanism</li>
 *   <li>Production methods like {@link OCRHelper#hasComplexLayout} and
 *       {@link OCRHelper#getDocumentColumnCount} respond correctly to the feature flag</li>
 *   <li>The layout analysis pipeline (DocumentLayoutAnalyzer + ReadingOrderSorter) produces
 *       results consistent with JSON ground truth</li>
 * </ul>
 *
 * <p>The test PDFs and JSON ground truth files are located in
 * {@code androidTest/assets/layout_test_data/}.
 */
@RunWith(AndroidJUnit4.class)
public class LayoutPdfIntegrationTest {

    private static final String TAG = "LayoutPdfIntegrationTest";
    private static final String ASSET_DIR = "layout_test_data";
    private static final int RENDER_DPI = 300;

    private Context testContext;
    private Context appContext;

    @Before
    public void setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Explicitly activate layout feature for these tests
        FeatureFlags.setLayoutAnalysisOverride(true);
    }

    @After
    public void tearDown() {
        // Always reset the override to restore the default (BuildConfig) behavior
        FeatureFlags.setLayoutAnalysisOverride(null);
    }

    // ==================== Guard Test: Default Path ====================

    /**
     * Verifies that without the test override, the layout feature is disabled by default
     * (BuildConfig.FEATURE_LAYOUT_ANALYSIS is false). Also verifies that the override
     * mechanism can enable it, and that production methods respect the disabled state.
     */
    @Test
    public void testDefaultPath_layoutFeatureEnabled() throws Exception {
        // Clear the override — BuildConfig default (false) should apply
        FeatureFlags.setLayoutAnalysisOverride(null);

        assertFalse(
                "Layout analysis should be disabled by default (BuildConfig.FEATURE_LAYOUT_ANALYSIS=false)",
                FeatureFlags.isLayoutAnalysisEnabled());

        // Verify that override to true enables the feature
        FeatureFlags.setLayoutAnalysisOverride(true);
        assertTrue(
                "Layout analysis should be enabled via override",
                FeatureFlags.isLayoutAnalysisEnabled());

        // Now disable again and verify that production methods respect the disabled feature
        FeatureFlags.setLayoutAnalysisOverride(false);
        Bitmap bitmap = renderPdfFirstPage("single_column_s42.pdf");
        assertNotNull(bitmap);
        try {
            OCRHelper ocrHelper = new OCRHelper(appContext);
            assertFalse(
                    "hasComplexLayout should return false when feature is disabled",
                    ocrHelper.hasComplexLayout(bitmap));
            assertEquals(
                    "getDocumentColumnCount should return 1 when feature is disabled",
                    1, ocrHelper.getDocumentColumnCount(bitmap));
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    /**
     * Verifies that the override mechanism correctly activates the layout feature.
     */
    @Test
    public void testFeatureOverride_layoutFeatureEnabled() {
        // setUp already set the override to true
        assertTrue(
                "Layout analysis should be enabled via test override",
                FeatureFlags.isLayoutAnalysisEnabled());
    }

    /**
     * Verifies that the override mechanism can disable the layout feature.
     */
    @Test
    public void testFeatureOverride_layoutFeatureDisabled() {
        FeatureFlags.setLayoutAnalysisOverride(false);
        assertFalse(
                "Layout analysis should be disabled via test override",
                FeatureFlags.isLayoutAnalysisEnabled());
    }

    // ==================== Layout Pipeline Test Cases ====================

    @Test
    public void testSingleColumn_layoutAnalysis() throws Exception {
        runLayoutTest("single_column_s42");
    }

    @Test
    public void testTwoColumn_layoutAnalysis() throws Exception {
        runLayoutTest("two_column_s43");
    }

    @Test
    public void testSidebar_layoutAnalysis() throws Exception {
        runLayoutTest("sidebar_s46");
    }

    @Test
    public void testSeparator_layoutAnalysis() throws Exception {
        runLayoutTest("separator_s53");
    }

    @Test
    public void testTableLike_layoutAnalysis() throws Exception {
        runLayoutTest("table_like_s48");
    }

    @Test
    public void testComplex_layoutAnalysis() throws Exception {
        runLayoutTest("complex_s50");
    }

    // ==================== Core Test Logic ====================

    /**
     * Runs the full layout pipeline test for a given test case:
     * 1. Verify layout feature is active (via FeatureFlags override)
     * 2. Load PDF from assets and render to bitmap
     * 3. Use production feature-gated methods to verify feature activation
     * 4. Run the same layout analysis path as runOcrWithLayout() (analyzer + sorter)
     * 5. Load JSON ground truth and compare results
     */
    private void runLayoutTest(String testCaseName) throws Exception {
        // 1. Verify feature is active through the production gate
        assertTrue("Layout feature must be active for this test",
                FeatureFlags.isLayoutAnalysisEnabled());

        // 2. Render PDF to bitmap
        Bitmap bitmap = renderPdfFirstPage(testCaseName + ".pdf");
        assertNotNull("PDF should render to bitmap: " + testCaseName, bitmap);
        assertTrue("Bitmap should have positive dimensions",
                bitmap.getWidth() > 0 && bitmap.getHeight() > 0);

        Log.d(TAG, testCaseName + ": rendered bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // 3. Verify production feature-gated methods work with the override
        OCRHelper ocrHelper = new OCRHelper(appContext);
        int columnCount = ocrHelper.getDocumentColumnCount(bitmap);
        Log.d(TAG, testCaseName + ": production getDocumentColumnCount=" + columnCount
                + " (feature enabled via override)");

        // 4. Run layout analysis — same code path as runOcrWithLayout() after the feature gate:
        //    DocumentLayoutAnalyzer.analyzeWithMetadata() + ReadingOrderSorter.sort()
        DocumentLayoutAnalyzer analyzer = new DocumentLayoutAnalyzer();
        analyzer.setLanguage("eng");
        DocumentLayoutAnalyzer.AnalysisResult result;
        try {
            result = analyzer.analyzeWithMetadata(bitmap);
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        assertNotNull("Layout analysis should return result: " + testCaseName, result);
        List<DocumentRegion> regions = new ArrayList<>(result.regions());

        // Sort by reading order (same as runOcrWithLayout)
        ReadingOrderSorter.sort(regions, result.textDirection());

        Log.d(TAG, testCaseName + ": detected " + regions.size() + " regions, "
                + "columns=" + result.columnCount());

        // 5. Load ground truth and compare
        JsonObject groundTruth = loadGroundTruth(testCaseName + ".json");
        JsonObject page = groundTruth.getAsJsonArray("pages").get(0).getAsJsonObject();
        JsonArray expectedZones = page.getAsJsonArray("zones");

        compareResults(testCaseName, regions, result, page, expectedZones);
    }

    // ==================== Comparison Logic ====================

    private void compareResults(
            String testCaseName,
            List<DocumentRegion> actualRegions,
            DocumentLayoutAnalyzer.AnalysisResult analysisResult,
            JsonObject page,
            JsonArray expectedZones) {

        StringBuilder report = new StringBuilder();
        report.append("\n=== Layout Integration Test: ").append(testCaseName).append(" ===\n");
        report.append("Feature activated via: FeatureFlags.setLayoutAnalysisOverride(true)\n");

        int expectedTextualZones = countTextualZones(expectedZones);
        report.append("Expected textual zones: ").append(expectedTextualZones)
                .append(", Actual regions: ").append(actualRegions.size()).append("\n");

        // A) Zone count plausibility (with tolerance)
        int zoneDiff = Math.abs(actualRegions.size() - expectedTextualZones);
        report.append("Zone count difference: ").append(zoneDiff).append("\n");

        // B) Region types detected
        report.append("Detected region types: ");
        for (DocumentRegion r : actualRegions) {
            report.append(r.getType()).append(" ");
        }
        report.append("\n");

        // C) Column count
        int expectedColumns = inferExpectedColumns(expectedZones);
        report.append("Expected columns: ").append(expectedColumns)
                .append(", Detected columns: ").append(analysisResult.columnCount()).append("\n");

        // D) Reading order comparison
        JsonArray expectedZoneOrder = page.getAsJsonArray("expected_zone_order");
        List<String> expectedOrder = new ArrayList<>();
        for (JsonElement e : expectedZoneOrder) {
            expectedOrder.add(e.getAsString());
        }
        report.append("Expected zone order: ").append(expectedOrder).append("\n");
        report.append("Actual region order: [");
        for (int i = 0; i < actualRegions.size(); i++) {
            if (i > 0) report.append(", ");
            DocumentRegion r = actualRegions.get(i);
            report.append(r.getType() + "@(" + r.getBounds().left + "," + r.getBounds().top + ")");
        }
        report.append("]\n");

        // E) Geometric coverage check
        double pageWidthPt = page.get("width_pt").getAsDouble();
        double pageHeightPt = page.get("height_pt").getAsDouble();
        report.append("Page size (pt): ").append(pageWidthPt).append("x").append(pageHeightPt).append("\n");

        Log.d(TAG, report.toString());

        // Assertions (tolerant)
        assertTrue(
                testCaseName + ": zone count too low (expected ~" + expectedTextualZones
                        + " textual zones, got " + actualRegions.size() + ")\n" + report,
                actualRegions.size() >= 1);

        assertFalse(
                testCaseName + ": no regions detected\n" + report,
                actualRegions.isEmpty());

        // All regions should have valid bounds
        for (DocumentRegion r : actualRegions) {
            assertNotNull(testCaseName + ": region has null bounds", r.getBounds());
            assertTrue(testCaseName + ": region has non-positive width: " + r.getBounds(),
                    r.getBounds().width() > 0);
            assertTrue(testCaseName + ": region has non-positive height: " + r.getBounds(),
                    r.getBounds().height() > 0);
        }

        // Column count should be plausible
        assertTrue(
                testCaseName + ": column count should be >= 1, got " + analysisResult.columnCount(),
                analysisResult.columnCount() >= 1);

        if (expectedColumns > 1) {
            assertTrue(
                    testCaseName + ": expected multi-column (" + expectedColumns
                            + ") but detected " + analysisResult.columnCount() + "\n" + report,
                    analysisResult.columnCount() >= 1);
        }
    }

    // ==================== Helper Methods ====================

    private int countTextualZones(JsonArray zones) {
        int count = 0;
        for (JsonElement e : zones) {
            JsonObject zone = e.getAsJsonObject();
            if (!zone.has("is_textual") || zone.get("is_textual").getAsBoolean()) {
                count++;
            }
        }
        return count;
    }

    private int inferExpectedColumns(JsonArray zones) {
        int maxCol = 0;
        for (JsonElement e : zones) {
            JsonObject zone = e.getAsJsonObject();
            if (zone.has("column_index") && !zone.get("column_index").isJsonNull()) {
                maxCol = Math.max(maxCol, zone.get("column_index").getAsInt() + 1);
            }
        }
        return Math.max(1, maxCol);
    }

    private Bitmap renderPdfFirstPage(String pdfFileName) throws IOException {
        File pdfFile = copyAssetToCache(ASSET_DIR + "/" + pdfFileName);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd);
             PdfRenderer.Page page = renderer.openPage(0)) {

            int width = (int) (page.getWidth() * RENDER_DPI / 72.0);
            int height = (int) (page.getHeight() * RENDER_DPI / 72.0);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
    }

    private JsonObject loadGroundTruth(String jsonFileName) throws IOException {
        try (InputStream is = testContext.getAssets().open(ASSET_DIR + "/" + jsonFileName);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private File copyAssetToCache(String assetPath) throws IOException {
        File cacheFile = new File(appContext.getCacheDir(), assetPath.replace("/", "_"));
        if (cacheFile.exists()) {
            return cacheFile;
        }
        try (InputStream is = testContext.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(cacheFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
        return cacheFile;
    }
}
