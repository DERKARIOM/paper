package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThrottledDocQuadLiveDetectorThrottleInstrumentedTest {

  @Test
  public void withinInterval_reusesLastResult_andDoesNotReRunInference() throws Exception {
    Context ctx = ApplicationProvider.getApplicationContext();
    Bitmap bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

    AtomicInteger calls = new AtomicInteger(0);
    ThrottledDocQuadLiveDetector.TimeSource clock =
        new ThrottledDocQuadLiveDetector.TimeSource() {
          long now = 1000;

          @Override
          public long nowMs() {
            return now;
          }
        };

    ThrottledDocQuadLiveDetector.Inference inf =
        new ThrottledDocQuadLiveDetector.Inference() {
          @Override
          public DetectionResult run(Bitmap src, Context c) {
            calls.incrementAndGet();
            return DetectionResult.success(
                Source.DOCQUAD, new double[][] {{1, 1}, {6, 1}, {6, 6}, {1, 6}});
          }
        };

    ThrottledDocQuadLiveDetector d = new ThrottledDocQuadLiveDetector(ctx, clock, inf);

    DetectionResult r1 = d.detect(bmp, ctx);
    DetectionResult r2 = d.detect(bmp, ctx);

    assertNotNull(r1);
    assertNotNull(r2);
    assertEquals(1, calls.get());
    assertEquals(Source.DOCQUAD, r2.source);
  }
}
