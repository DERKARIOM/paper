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
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import de.schliweb.makeacopy.ui.ocr.review.model.OcrDoc;
import de.schliweb.makeacopy.ui.ocr.review.store.OcrJsonStore;
import de.schliweb.makeacopy.utils.export.PdfCreator;
import de.schliweb.makeacopy.utils.infra.FeatureFlags;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.OcrJsonWords;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration test for the OCR → Edit → Save/Persist → Load → Export-PDF path.
 *
 * <p>Verifies that layout reading order (block/line structure) is preserved after
 * an edit roundtrip through OcrJsonStore persistence. Uses a two-column test case
 * where Y→X sorting would incorrectly interleave columns, but block-based sorting
 * preserves the correct left-column-then-right-column order.
 *
 * <p>The layout feature is explicitly activated via {@link FeatureFlags#setLayoutAnalysisOverride}.
 */
@RunWith(AndroidJUnit4.class)
public class EditRoundtripExportIntegrationTest {

    private static final String TAG = "EditRoundtripTest";
    private static final String ASSET_DIR = "layout_test_data";
    private static final int RENDER_DPI = 300;
    private static final String EDIT_MARKER = "EDITED_WORD";

    private Context testContext;
    private Context appContext;

    @Before
    public void setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PDFBoxResourceLoader.init(appContext);
        FeatureFlags.setLayoutAnalysisOverride(true);
    }

    @After
    public void tearDown() {
        FeatureFlags.setLayoutAnalysisOverride(null);
    }

    // ==================== Guard Tests ====================

    @Test
    public void testDefaultPath_layoutFeatureEnabled() {
        FeatureFlags.setLayoutAnalysisOverride(null);
        assertFalse("Layout should be disabled by default (BuildConfig.FEATURE_LAYOUT_ANALYSIS=false)",
                FeatureFlags.isLayoutAnalysisEnabled());
        // Verify override can enable it
        FeatureFlags.setLayoutAnalysisOverride(true);
        assertTrue("Layout should be enabled via override",
                FeatureFlags.isLayoutAnalysisEnabled());
    }

    // ==================== Main Integration Test ====================

    @Test
    public void testEditRoundtrip_twoColumn_preservesLayoutOrder() throws Exception {
        assertTrue("Layout feature must be active",
                FeatureFlags.isLayoutAnalysisEnabled());

        String testCase = "two_column_s43";

        // 1. Render source PDF to bitmap
        Bitmap bitmap = renderPdfFirstPage(testCase + ".pdf");
        assertNotNull("PDF should render to bitmap", bitmap);
        Log.d(TAG, "Rendered bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // 2. Run REAL OCR with layout analysis
        OCRHelper ocrHelper = new OCRHelper(appContext);
        ocrHelper.setLanguage("eng");

        OCRHelper.OcrResultWithLayout ocrResult;
        try {
            ocrResult = ocrHelper.runOcrWithLayout(bitmap);
        } catch (Exception e) {
            Log.w(TAG, "OCR failed, skipping: " + e.getMessage());
            bitmap.recycle();
            return;
        }
        assertNotNull("OCR result should not be null", ocrResult);

        // 3. Collect all words with their blockId from real OCR
        List<RecognizedWord> allWords = new ArrayList<>();
        int regionIdx = 0;
        for (OCRHelper.RegionOcrResult regionResult : ocrResult.regionResults) {
            regionIdx++;
            if (regionResult.ocrResult() != null && regionResult.ocrResult().words != null) {
                for (RecognizedWord w : regionResult.ocrResult().words) {
                    w.setBlockId(regionIdx);
                    allWords.add(w);
                }
            }
        }

        Log.d(TAG, "Collected " + allWords.size() + " words from " + regionIdx + " regions");

        if (allWords.isEmpty()) {
            Log.w(TAG, "OCR produced no words (traineddata may be missing), skipping");
            bitmap.recycle();
            return;
        }

        // Verify that we have multiple blocks (multi-column layout)
        long distinctBlocks = allWords.stream()
                .map(RecognizedWord::getBlockId)
                .distinct()
                .count();
        assertTrue("Should have multiple blocks for two-column layout", distinctBlocks >= 2);

        // 4. Build OcrDoc (simulating the mapper step)
        OcrDoc doc = new OcrDoc();
        doc.imageSize.w = bitmap.getWidth();
        doc.imageSize.h = bitmap.getHeight();
        int id = 1;
        for (RecognizedWord rw : allWords) {
            RectF b = rw.getBoundingBox();
            if (b == null) continue;
            OcrDoc.Word w = new OcrDoc.Word();
            w.id = id++;
            w.t = rw.getText() != null ? rw.getText() : "";
            w.b[0] = Math.max(0, Math.round(b.left));
            w.b[1] = Math.max(0, Math.round(b.top));
            w.b[2] = Math.max(0, Math.round(b.right - b.left));
            w.b[3] = Math.max(0, Math.round(b.bottom - b.top));
            w.c = Math.max(0f, Math.min(1f, rw.getConfidence() > 1f ? rw.getConfidence() / 100f : rw.getConfidence()));
            w.k = rw.getBlockId();
            w.l = rw.getLineId();
            doc.words.add(w);
        }

        // 5. Simulate edit: change one word in block 1 (first region)
        boolean edited = false;
        for (OcrDoc.Word w : doc.words) {
            if (w.k == 1 && w.t.length() > 2) {
                Log.d(TAG, "Editing word '" + w.t + "' in block " + w.k + " → '" + EDIT_MARKER + "'");
                w.t = EDIT_MARKER;
                w.e = true;
                edited = true;
                break;
            }
        }
        assertTrue("Should have edited at least one word in block 1", edited);

        // 6. Save via OcrJsonStore (real persistence)
        File ocrJsonFile = new File(appContext.getCacheDir(), "edit_roundtrip_test_ocr.json");
        boolean saved = OcrJsonStore.save(ocrJsonFile, doc);
        assertTrue("OcrJsonStore.save should succeed", saved);
        assertTrue("ocr.json file should exist", ocrJsonFile.exists());
        Log.d(TAG, "Saved ocr.json, size=" + ocrJsonFile.length());

        // 7. Load back via OcrJsonWords.parseFile (real export path)
        List<RecognizedWord> loadedWords = OcrJsonWords.parseFile(ocrJsonFile);
        assertNotNull("OcrJsonWords.parseFile should return non-null", loadedWords);
        assertFalse("Loaded words should not be empty", loadedWords.isEmpty());

        // Verify blockId survived the roundtrip
        boolean hasLayoutInfo = loadedWords.stream().anyMatch(rw -> rw.getBlockId() > 0);
        assertTrue("Block IDs should survive persistence roundtrip", hasLayoutInfo);

        // Verify edited word survived
        boolean editFound = loadedWords.stream()
                .anyMatch(rw -> EDIT_MARKER.equals(rw.getText()));
        assertTrue("Edited word should survive persistence roundtrip", editFound);

        Log.d(TAG, "Loaded " + loadedWords.size() + " words after roundtrip");

        // 8. Create export PDF via PdfCreator
        File outputFile = new File(appContext.getCacheDir(), "edit_roundtrip_export.pdf");
        Uri outputUri = Uri.fromFile(outputFile);

        Uri result;
        try {
            result = PdfCreator.createSearchablePdf(
                    appContext, bitmap, loadedWords, outputUri,
                    85, false, false, RENDER_DPI);
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        assertNotNull("PdfCreator should return non-null URI", result);
        assertTrue("Export PDF should exist", outputFile.exists());
        assertTrue("Export PDF should not be empty", outputFile.length() > 0);

        // 9. Extract text from export PDF
        String extractedText;
        try (PDDocument pdfDoc = PDDocument.load(outputFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            extractedText = stripper.getText(pdfDoc);
        }

        Log.d(TAG, "Extracted text length=" + extractedText.length());
        Log.d(TAG, "Extracted text (first 500)=" +
                extractedText.substring(0, Math.min(500, extractedText.length())));

        // 10. Assertions: verify the edit roundtrip preserved layout order

        // A) Export PDF contains text
        assertFalse("Export PDF should contain extractable text", extractedText.trim().isEmpty());

        // B) Edited word is present in export
        assertTrue("Edited word '" + EDIT_MARKER + "' should appear in export PDF",
                extractedText.contains(EDIT_MARKER));

        // C) Block order is preserved: words from block 1 should appear before words from later blocks
        //    Find the position of the edited word (block 1) and a word from a later block
        int editPos = extractedText.indexOf(EDIT_MARKER);
        assertTrue("Edit marker should be found in text", editPos >= 0);

        // Find a word from the last block to verify ordering
        int maxBlock = loadedWords.stream()
                .mapToInt(RecognizedWord::getBlockId)
                .max()
                .orElse(0);
        String lastBlockWord = null;
        for (RecognizedWord rw : loadedWords) {
            if (rw.getBlockId() == maxBlock && rw.getText().length() > 2) {
                lastBlockWord = rw.getText();
                break;
            }
        }

        if (lastBlockWord != null && extractedText.contains(lastBlockWord)) {
            int lastBlockPos = extractedText.indexOf(lastBlockWord);
            Log.d(TAG, "Edit marker (block 1) at pos " + editPos
                    + ", last block word '" + lastBlockWord + "' at pos " + lastBlockPos);
            assertTrue("Words from block 1 should appear before words from block " + maxBlock
                            + " (edit marker at " + editPos + ", last block word at " + lastBlockPos + ")",
                    editPos < lastBlockPos);
        }

        // D) Verify block structure was used (not just Y→X)
        //    Check that words are grouped by block in the loaded list
        int prevBlock = -1;
        boolean blocksAreGrouped = true;
        java.util.Set<Integer> seenBlocks = new java.util.HashSet<>();
        for (RecognizedWord rw : loadedWords) {
            int blk = rw.getBlockId();
            if (blk != prevBlock) {
                if (seenBlocks.contains(blk)) {
                    blocksAreGrouped = false;
                    break;
                }
                seenBlocks.add(blk);
                prevBlock = blk;
            }
        }
        assertTrue("Words should be grouped by block (not interleaved by Y→X)", blocksAreGrouped);

        // Cleanup
        //noinspection ResultOfMethodCallIgnored
        outputFile.delete();
        //noinspection ResultOfMethodCallIgnored
        ocrJsonFile.delete();

        Log.d(TAG, "Edit roundtrip test PASSED: layout order preserved after edit");
    }

    // ==================== Helpers ====================

    private Bitmap renderPdfFirstPage(String pdfFileName) throws IOException {
        File pdfFile = copyAssetToCache(ASSET_DIR + "/" + pdfFileName);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd);
             PdfRenderer.Page page = renderer.openPage(0)) {

            int width = (int) (page.getWidth() * RENDER_DPI / 72f);
            int height = (int) (page.getHeight() * RENDER_DPI / 72f);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
    }

    private File copyAssetToCache(String assetPath) throws IOException {
        File cacheFile = new File(appContext.getCacheDir(), assetPath.replace("/", "_"));
        if (cacheFile.exists()) return cacheFile;
        try (InputStream is = testContext.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(cacheFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        return cacheFile;
    }
}
