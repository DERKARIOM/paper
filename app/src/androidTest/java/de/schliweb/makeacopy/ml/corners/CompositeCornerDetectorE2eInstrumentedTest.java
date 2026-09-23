package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.ml.docquad.DocQuadOrtRunner;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompositeCornerDetectorE2eInstrumentedTest {

  // Copied to generated debug assets by Gradle task (see app/build.gradle)
  private static final String TEST_IMAGE_ASSET =
      "instrumented_test_data/20251007_183138_cropped.jpg";

  @Test
  public void bitmap_to_detectionResult_docquad_success() throws Exception {
    Context ctx = ApplicationProvider.getApplicationContext();

    // Defensive init (legacy fallback uses OpenCV)
    OpenCVUtils.init(ctx);

    Bitmap bmp;
    try (InputStream is = ctx.getAssets().open(TEST_IMAGE_ASSET)) {
      bmp = BitmapFactory.decodeStream(is);
    }
    assertNotNull(bmp);
    assertTrue(bmp.getWidth() > 0);
    assertTrue(bmp.getHeight() > 0);

    DocQuadOrtRunner runner =
        DocQuadOrtRunner.getInstance(ctx, DocQuadDetector.DEFAULT_MODEL_ASSET_PATH);
    CornerDetector detector =
        new CompositeCornerDetector(new DocQuadDetector(runner), new LegacyCornerDetector());
    DetectionResult r = detector.detect(bmp, ctx);

    assertNotNull(r);
    assertTrue("Detection must succeed", r.success);
    assertEquals(Source.DOCQUAD, r.source);

    assertNotNull(r.cornersOriginalTLTRBRBL);
    assertEquals(4, r.cornersOriginalTLTRBRBL.length);
    for (int i = 0; i < 4; i++) {
      assertNotNull(r.cornersOriginalTLTRBRBL[i]);
      assertEquals(2, r.cornersOriginalTLTRBRBL[i].length);
      double x = r.cornersOriginalTLTRBRBL[i][0];
      double y = r.cornersOriginalTLTRBRBL[i][1];
      assertTrue(Double.isFinite(x));
      assertTrue(Double.isFinite(y));
      // Plausibility bounds (allow small overshoot)
      assertTrue(x >= -bmp.getWidth() * 0.25);
      assertTrue(x <= bmp.getWidth() * 1.25);
      assertTrue(y >= -bmp.getHeight() * 0.25);
      assertTrue(y <= bmp.getHeight() * 1.25);
    }
  }
}
