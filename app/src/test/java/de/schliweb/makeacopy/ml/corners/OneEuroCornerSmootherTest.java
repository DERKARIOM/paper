package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** JVM-Unit-Tests für {@link OneEuroCornerSmoother}. Pure JVM, keine Android-APIs. */
public class OneEuroCornerSmootherTest {

  private static double[][] quad(double tlx, double tly, double w, double h) {
    return new double[][] {
      {tlx, tly}, {tlx + w, tly}, {tlx + w, tly + h}, {tlx, tly + h},
    };
  }

  private static double maxAbsDiff(double[][] a, double[][] b) {
    double m = 0.0;
    for (int i = 0; i < 4; i++) {
      for (int k = 0; k < 2; k++) {
        m = Math.max(m, Math.abs(a[i][k] - b[i][k]));
      }
    }
    return m;
  }

  @Test
  public void firstCallReturnsRawInputAndInitializesState() {
    OneEuroCornerSmoother s = OneEuroCornerSmoother.withDefaults();
    double[][] raw = quad(10, 20, 100, 200);
    double[][] out = s.apply(raw, 1000L, 1920, 1080);
    for (int i = 0; i < 4; i++) {
      assertArrayEquals(raw[i], out[i], 0.0);
    }
  }

  @Test
  public void steadyJitterIsAttenuated() {
    OneEuroCornerSmoother s = OneEuroCornerSmoother.withDefaults();
    int W = 1920, H = 1080;
    double[][] base = quad(500, 500, 800, 600);
    long t = 1000L;
    s.apply(base, t, W, H);

    // 30 Frames mit kleinem Jitter (deterministisch über sin).
    double rawAvgDist = 0.0;
    double smoothAvgDist = 0.0;
    int n = 30;
    for (int k = 1; k <= n; k++) {
      t += 250L; // 4 Hz
      double j = 2.0 * Math.sin(k * 0.7); // ±2 px Amplitude
      double[][] noisy = new double[4][2];
      for (int i = 0; i < 4; i++) {
        noisy[i][0] = base[i][0] + j;
        noisy[i][1] = base[i][1] - j;
      }
      double[][] sm = s.apply(noisy, t, W, H);
      rawAvgDist += maxAbsDiff(noisy, base);
      smoothAvgDist += maxAbsDiff(sm, base);
    }
    rawAvgDist /= n;
    smoothAvgDist /= n;
    assertTrue(
        "Smoothed jitter must be smaller than raw jitter: raw="
            + rawAvgDist
            + " sm="
            + smoothAvgDist,
        smoothAvgDist < rawAvgDist * 0.95);
  }

  @Test
  public void largeJumpTriggersResetAndFollowsImmediately() {
    OneEuroCornerSmoother s = OneEuroCornerSmoother.withDefaults();
    int W = 1920, H = 1080;
    double[][] q1 = quad(100, 100, 400, 300);
    double[][] q2 = quad(1200, 700, 400, 300); // weit entfernt → > 25 % Diagonale

    s.apply(q1, 1000L, W, H);
    // Stabilisieren
    for (int k = 0; k < 5; k++) {
      s.apply(q1, 1000L + (k + 1) * 250L, W, H);
    }
    double[][] out = s.apply(q2, 1000L + 6 * 250L, W, H);
    // Nach Reset muss out exakt q2 sein.
    for (int i = 0; i < 4; i++) {
      assertArrayEquals("corner " + i, q2[i], out[i], 0.0);
    }
  }

  @Test
  public void deterministicForIdenticalInputs() {
    OneEuroCornerSmoother a = OneEuroCornerSmoother.withDefaults();
    OneEuroCornerSmoother b = OneEuroCornerSmoother.withDefaults();
    int W = 1920, H = 1080;
    long t = 1000L;
    double[][] base = quad(300, 300, 500, 400);
    double[][] outA = null;
    double[][] outB = null;
    for (int k = 0; k < 20; k++) {
      double j = 1.5 * Math.sin(k * 0.9);
      double[][] r = new double[4][2];
      for (int i = 0; i < 4; i++) {
        r[i][0] = base[i][0] + j;
        r[i][1] = base[i][1] + j;
      }
      outA = a.apply(r, t, W, H);
      outB = b.apply(r, t, W, H);
      t += 250L;
    }
    for (int i = 0; i < 4; i++) {
      assertEquals(outA[i][0], outB[i][0], 0.0);
      assertEquals(outA[i][1], outB[i][1], 0.0);
    }
  }

  @Test
  public void nonMonotonicTimestampHoldsLastSmoothedValue() {
    OneEuroCornerSmoother s = OneEuroCornerSmoother.withDefaults();
    int W = 1920, H = 1080;
    double[][] q = quad(200, 200, 300, 300);
    s.apply(q, 1000L, W, H);
    double[][] last = s.apply(q, 1250L, W, H);
    // Identische Zeit (dt=0): muss letzten geglätteten Zustand zurückgeben, nicht crashen.
    double[][] out = s.apply(quad(220, 220, 300, 300), 1250L, W, H);
    for (int i = 0; i < 4; i++) {
      assertArrayEquals("corner " + i, last[i], out[i], 0.0);
    }
  }

  @Test
  public void invalidInputsRejected() {
    OneEuroCornerSmoother s = OneEuroCornerSmoother.withDefaults();
    try {
      s.apply(null, 0, 100, 100);
      fail("expected IAE");
    } catch (IllegalArgumentException expected) {
      // ok
    }
    try {
      s.apply(new double[3][2], 0, 100, 100);
      fail("expected IAE");
    } catch (IllegalArgumentException expected) {
      // ok
    }
    try {
      new OneEuroCornerSmoother(0.0, 0.0, 1.0, 0.25);
      fail("expected IAE");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }
}
