package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import org.junit.Test;

/**
 * JVM-Test für deterministische Policy (kein Parallel-Racing): DocQuad wird zuerst versucht, danach
 * Legacy.
 */
public class CompositeCornerDetectorPolicyTest {

  @Test
  public void docquad_success_shortCircuits() {
    CornerDetector docquad =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.success(
                Source.DOCQUAD, new double[][] {{1, 1}, {2, 1}, {2, 2}, {1, 2}});
          }
        };
    CornerDetector legacy =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.success(
                Source.FALLBACK, new double[][] {{9, 9}, {10, 9}, {10, 10}, {9, 10}});
          }
        };

    DetectionResult r = new CompositeCornerDetector(docquad, legacy).detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.DOCQUAD, r.source);
    assertEquals(1.0, r.cornersOriginalTLTRBRBL[0][0], 0.0);
  }

  @Test
  public void docquad_fail_fallsBack_to_legacy() {
    CornerDetector docquad =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.fail(Source.DOCQUAD);
          }
        };
    CornerDetector legacy =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.success(
                Source.FALLBACK, new double[][] {{9, 9}, {10, 9}, {10, 10}, {9, 10}});
          }
        };

    DetectionResult r = new CompositeCornerDetector(docquad, legacy).detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.FALLBACK, r.source);
  }

  @Test
  public void docquad_throw_fallsBack_to_legacy() {
    CornerDetector docquad =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            throw new RuntimeException("boom");
          }
        };
    CornerDetector legacy =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.success(
                Source.OPENCV, new double[][] {{9, 9}, {10, 9}, {10, 10}, {9, 10}});
          }
        };

    DetectionResult r = new CompositeCornerDetector(docquad, legacy).detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.OPENCV, r.source);
  }

  @Test
  public void docquad_invalidResult_fallsBack_to_legacy() {
    CornerDetector docquad =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            // success=false to simulate invalid/filtered result
            return DetectionResult.fail(Source.DOCQUAD);
          }
        };
    CornerDetector legacy =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.success(
                Source.OPENCV, new double[][] {{9, 9}, {10, 9}, {10, 10}, {9, 10}});
          }
        };

    DetectionResult r = new CompositeCornerDetector(docquad, legacy).detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.OPENCV, r.source);
  }

  @Test
  public void both_fail_returns_fail() {
    CornerDetector docquad =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.fail(Source.DOCQUAD);
          }
        };
    CornerDetector legacy =
        new CornerDetector() {
          @Override
          public DetectionResult detect(Bitmap src, Context ctx) {
            return DetectionResult.fail(Source.FALLBACK);
          }
        };

    DetectionResult r = new CompositeCornerDetector(docquad, legacy).detect(null, null);
    assertFalse(r.success);
    assertEquals(Source.FALLBACK, r.source);
  }
}
