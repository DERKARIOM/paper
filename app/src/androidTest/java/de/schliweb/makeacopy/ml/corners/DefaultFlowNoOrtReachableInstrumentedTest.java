package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.utils.image.OpenCVUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration test for LegacyCornerDetector: - Verifies that LegacyCornerDetector (OpenCV-based)
 * works correctly - When OpenCV detection is disabled, it falls back to geometric fallback
 * (rectangle)
 *
 * <p>Note: For determinism/CI reliability, OpenCV is disabled in this test; the explicit geometric
 * fallback (rectangle) is expected.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultFlowNoOrtReachableInstrumentedTest {

  @Test
  public void legacyCornerDetectorUsesFallbackWhenOpenCVDisabled() {
    Context ctx = ApplicationProvider.getApplicationContext();

    boolean prevDisableOpenCv = OpenCVUtils.isOpenCVDetectionDisabled();
    try {
      // Avoid native OpenCV dependency in this test: force fallback rectangle.
      OpenCVUtils.setDisableOpenCVDetection(true);

      Bitmap bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
      DetectionResult r = new LegacyCornerDetector().detect(bmp, ctx);

      assertNotNull(r);
      assertTrue(r.success);
      assertNotNull(r.cornersOriginalTLTRBRBL);
      assertEquals(4, r.cornersOriginalTLTRBRBL.length);
      assertEquals(Source.FALLBACK, r.source);
    } finally {
      OpenCVUtils.setDisableOpenCVDetection(prevDisableOpenCv);
    }
  }
}
