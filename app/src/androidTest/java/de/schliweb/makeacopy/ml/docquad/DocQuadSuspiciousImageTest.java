package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.makeacopy.ml.corners.CompositeCornerDetector;
import de.schliweb.makeacopy.ml.corners.DetectionResult;
import de.schliweb.makeacopy.ml.corners.DocQuadDetector;
import de.schliweb.makeacopy.ml.corners.LegacyCornerDetector;
import de.schliweb.makeacopy.ml.corners.Source;
import java.io.InputStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test für Product Guardrails: Prüft das Postprocessing-Verhalten für ein bekanntes problematisches
 * Bild aus der Evaluierung.
 *
 * <p>Das Testbild {@code sample_20260124_174305_846_original.jpg} ist ein bekannter Outlier aus der
 * Evaluierung mit hohem MAE (568px) und niedrigem IoU (0.365). Dieser Test verifiziert, dass die
 * neuen Evidence-Based Guardrails (Rules D/E/F) dieses Bild als "suspicious" erkennen und somit
 * einen OpenCV-Fallback triggern würden.
 *
 * <p>Laut Evaluierungsbericht wird dieses Bild mit {@code suspicious_for_product: true} und {@code
 * suspicious_reason: "LOW_PEAK_MARGIN"} (Rule D) markiert.
 */
@RunWith(AndroidJUnit4.class)
public class DocQuadSuspiciousImageTest {

  private static final String TEST_IMAGE_ASSET =
      "instrumented_test_data/sample_20260124_174305_846_original.jpg";

  @Test
  public void suspiciousImage_triggersSuspiciousForProduct() throws Exception {
    // target context -> App-APK assets (ONNX model + test image)
    Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

    Assume.assumeTrue(
        "trained tests disabled (set RUN_TRAINED_TESTS=1 to enable)",
        TrainedTestConfig.trainedTestsEnabled());

    String modelAsset = TrainedTestConfig.resolveTrainedModelAsset(ctx);

    // 1) Load test image from assets
    Bitmap srcBitmap;
    try (InputStream is = ctx.getAssets().open(TEST_IMAGE_ASSET)) {
      srcBitmap = BitmapFactory.decodeStream(is);
    }
    assertNotNull("Test image could not be loaded: " + TEST_IMAGE_ASSET, srcBitmap);

    int srcW = srcBitmap.getWidth();
    int srcH = srcBitmap.getHeight();
    assertTrue("Image width must be positive", srcW > 0);
    assertTrue("Image height must be positive", srcH > 0);

    // 2) Create letterbox and render to 256x256
    DocQuadLetterbox lb =
        DocQuadLetterbox.create(srcW, srcH, DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H);
    Bitmap in256 = renderLetterbox256(srcBitmap, lb);

    // 3) Convert to NCHW float array
    float[] input = bitmapToNchwFloat01(in256);

    // 4) Run ONNX inference
    DocQuadOrtRunner.Outputs outputs;
    try (DocQuadOrtRunner runner = new DocQuadOrtRunner(ctx, modelAsset)) {
      outputs = runner.run(input);
    }
    assertNotNull("ONNX outputs must not be null", outputs);

    // 5) Run postprocessor with REFINE_3X3 (product mode)
    DocQuadPostprocessor.Result result =
        DocQuadPostprocessor.postprocess(outputs, lb, DocQuadPostprocessor.PeakMode.REFINE_3X3);
    assertNotNull("Postprocessor result must not be null", result);

    // 6) Log the result for diagnostic purposes
    android.util.Log.i(
        "DocQuadSuspiciousImageTest",
        "Outlier image result: chosenSource="
            + result.chosenSource()
            + ", penaltyCorners="
            + result.penaltyCorners()
            + ", penaltyMask="
            + result.penaltyMask()
            + ", suspiciousForProduct="
            + result.suspiciousForProduct()
            + ", suspiciousReason="
            + result.suspiciousReason());

    // 7) MAIN ASSERTION: This known outlier image MUST trigger suspiciousForProduct
    // The evaluation report shows this image is flagged with suspicious_reason: "LOW_PEAK_MARGIN"
    // (Rule D: Heatmap peak evidence - low margin indicates model uncertainty)
    assertTrue(
        "Expected suspiciousForProduct=true for image "
            + TEST_IMAGE_ASSET
            + " but got false. suspiciousReason="
            + result.suspiciousReason()
            + ", chosenSource="
            + result.chosenSource()
            + ", penaltyCorners="
            + result.penaltyCorners()
            + ", penaltyMask="
            + result.penaltyMask(),
        result.suspiciousForProduct());

    // 8) Verify that a valid suspiciousReason is set (one of Rules D/E/F)
    assertNotNull(
        "suspiciousReason must not be null when suspiciousForProduct is true",
        result.suspiciousReason());
    // Expected reasons from evidence-based guardrails: LOW_PEAK_MARGIN, MASK_DIFFUSE,
    // CHOSEN_MASK_INCONSISTENT
    // or from earlier rules: DISAGREE_64PX, MASK_FALLBACK_AND_PCORNER, GEOMETRY_IMPLAUSIBLE
    assertTrue(
        "suspiciousReason must be a known reason string",
        result.suspiciousReason().equals("LOW_PEAK_MARGIN")
            || result.suspiciousReason().equals("MASK_DIFFUSE")
            || result.suspiciousReason().equals("CHOSEN_MASK_INCONSISTENT")
            || result.suspiciousReason().equals("DISAGREE_64PX")
            || result.suspiciousReason().equals("MASK_FALLBACK_AND_PCORNER")
            || result.suspiciousReason().equals("GEOMETRY_IMPLAUSIBLE"));

    // Cleanup
    if (!srcBitmap.isRecycled()) srcBitmap.recycle();
    if (!in256.isRecycled()) in256.recycle();
  }

  /**
   * Test für den vollständigen Detektionspfad: Verifiziert, dass bei einem suspicious-Bild der
   * CompositeCornerDetector auf OpenCV zurückfällt.
   *
   * <p>Dieser Test prüft die End-to-End-Integration:
   *
   * <ol>
   *   <li>DocQuadDetector erkennt das Bild als suspicious (via Product Guardrails)
   *   <li>DocQuadDetector gibt fail() zurück
   *   <li>CompositeCornerDetector fällt auf LegacyCornerDetector (OpenCV) zurück
   *   <li>Das Ergebnis kommt von Source.OPENCV oder Source.FALLBACK (nicht DOCQUAD)
   * </ol>
   */
  @Test
  public void suspiciousImage_triggersOpenCVFallback() throws Exception {
    Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

    Assume.assumeTrue(
        "trained tests disabled (set RUN_TRAINED_TESTS=1 to enable)",
        TrainedTestConfig.trainedTestsEnabled());

    // 1) Load test image from assets
    Bitmap srcBitmap;
    try (InputStream is = ctx.getAssets().open(TEST_IMAGE_ASSET)) {
      srcBitmap = BitmapFactory.decodeStream(is);
    }
    assertNotNull("Test image could not be loaded: " + TEST_IMAGE_ASSET, srcBitmap);

    // 2) Create CompositeCornerDetector (DocQuad -> Legacy/OpenCV fallback)
    DocQuadOrtRunner runner =
        DocQuadOrtRunner.getInstance(ctx, DocQuadDetector.DEFAULT_MODEL_ASSET_PATH);
    DocQuadDetector docQuadDetector = new DocQuadDetector(runner);
    LegacyCornerDetector legacyDetector = new LegacyCornerDetector();
    CompositeCornerDetector compositeDetector =
        new CompositeCornerDetector(docQuadDetector, legacyDetector);

    // 3) Run detection through the full pipeline
    DetectionResult result = compositeDetector.detect(srcBitmap, ctx);

    // 4) Log the result for diagnostic purposes
    android.util.Log.i(
        "DocQuadSuspiciousImageTest",
        "CompositeDetector result: success="
            + result.success
            + ", source="
            + result.source
            + ", chosenSource="
            + result.chosenSource);

    // 5) MAIN ASSERTION: The result should NOT come from DOCQUAD
    // Because the image is suspicious, DocQuadDetector should fail and
    // CompositeCornerDetector should fall back to OpenCV.
    assertTrue("Expected detection to succeed (via OpenCV fallback)", result.success);

    // The source should be OPENCV or FALLBACK, but NOT DOCQUAD
    // (DOCQUAD would indicate the suspicious guardrails didn't trigger)
    assertTrue(
        "Expected source to be OPENCV or FALLBACK (not DOCQUAD) for suspicious image. "
            + "Actual source: "
            + result.source,
        result.source == Source.OPENCV || result.source == Source.FALLBACK);

    // 6) Verify corners are valid
    assertNotNull("Corners should not be null", result.cornersOriginalTLTRBRBL);
    assertEquals("Should have 4 corners", 4, result.cornersOriginalTLTRBRBL.length);

    // Cleanup
    if (!srcBitmap.isRecycled()) srcBitmap.recycle();
  }

  /** Preprocess exakt wie Training: RGB, 0..1, NCHW float32. */
  private static float[] bitmapToNchwFloat01(Bitmap bmp) {
    int w = bmp.getWidth();
    int h = bmp.getHeight();
    if (w != DocQuadOrtRunner.IN_W || h != DocQuadOrtRunner.IN_H) {
      throw new IllegalArgumentException("bitmap must be 256x256");
    }
    int hw = h * w;
    float[] out = new float[3 * hw];
    int[] px = new int[hw];
    bmp.getPixels(px, 0, w, 0, 0, w, h);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int c = px[y * w + x];
        float r = ((c >> 16) & 0xFF) / 255.0f;
        float g = ((c >> 8) & 0xFF) / 255.0f;
        float b = (c & 0xFF) / 255.0f;
        int idx = y * w + x;
        out[0 + idx] = r;
        out[hw + idx] = g;
        out[2 * hw + idx] = b;
      }
    }
    return out;
  }

  private static Bitmap renderLetterbox256(Bitmap src, DocQuadLetterbox lb) {
    Bitmap out =
        Bitmap.createBitmap(DocQuadOrtRunner.IN_W, DocQuadOrtRunner.IN_H, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    canvas.drawColor(android.graphics.Color.BLACK);

    float left = (float) lb.offsetX;
    float top = (float) lb.offsetY;
    float right = (float) (lb.offsetX + (double) lb.srcW * lb.scale);
    float bottom = (float) (lb.offsetY + (double) lb.srcH * lb.scale);
    RectF dst = new RectF(left, top, right, bottom);
    canvas.drawBitmap(src, null, dst, null);
    return out;
  }
}
