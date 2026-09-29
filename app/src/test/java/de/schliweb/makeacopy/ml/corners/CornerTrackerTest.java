package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

/** Temporal stabilization of the live outline (pure JVM, injected time). */
public class CornerTrackerTest {
  private static final int W = 540, H = 720;
  private static final long FRAME = 150; // ms between analysed frames

  private static double[][] rect(double x, double y, double w, double h) {
    return new double[][] {{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}};
  }

  private static double[][] shifted(double[][] q, double dx, double dy) {
    double[][] o = QuadGeometry.copy(q);
    for (double[] p : o) {
      p[0] += dx;
      p[1] += dy;
    }
    return o;
  }

  @Test
  public void aSingleMediumConfidenceFrameIsNotShown() {
    CornerTracker t = new CornerTracker();
    CornerTracker.Output o = t.update(rect(100, 100, 300, 420), 0.6, 0, W, H);
    assertEquals(CornerTracker.State.NONE, o.state);
    assertNull(o.quad);
    o = t.update(rect(101, 100, 300, 420), 0.6, FRAME, W, H);
    assertEquals(CornerTracker.State.DETECTED, o.state);
    assertNotNull(o.quad);
  }

  @Test
  public void highConfidenceIsShownAtOnce() {
    CornerTracker t = new CornerTracker();
    assertEquals(
        CornerTracker.State.DETECTED, t.update(rect(100, 100, 300, 420), 0.9, 0, W, H).state);
  }

  @Test
  public void lowConfidenceNeverShowsAnOutline() {
    CornerTracker t = new CornerTracker();
    for (int i = 0; i < 10; i++) {
      assertNull(t.update(rect(100, 100, 300, 420), 0.5, i * FRAME, W, H).quad);
    }
  }

  @Test
  public void jitterOfOnePixelIsSuppressed() {
    CornerTracker t = new CornerTracker();
    double[][] base = rect(100, 100, 300, 420);
    Random r = new Random(1);
    double maxDev = 0;
    t.update(base, 0.9, 0, W, H);
    for (int i = 1; i < 60; i++) {
      double[][] noisy = QuadGeometry.copy(base);
      for (double[] p : noisy) {
        p[0] += r.nextGaussian();
        p[1] += r.nextGaussian();
      }
      CornerTracker.Output o = t.update(noisy, 0.9, i * FRAME, W, H);
      maxDev = Math.max(maxDev, QuadGeometry.maxCornerDistance(o.quad, base));
    }
    // raw input deviates up to ~4 px; the displayed outline stays within ~1 px
    assertTrue("max deviation " + maxDev, maxDev < 1.2);
  }

  @Test
  public void realMovementIsFollowedWithoutLag() {
    CornerTracker t = new CornerTracker();
    double[][] a = rect(100, 100, 300, 420);
    t.update(a, 0.9, 0, W, H);
    double[][] b = shifted(a, 30, 0); // phone moved: 30 px (3.3% of the diagonal)
    CornerTracker.Output o = t.update(b, 0.9, FRAME, W, H);
    assertTrue(QuadGeometry.maxCornerDistance(o.quad, b) < 0.01);
  }

  @Test
  public void smallMovementConvergesQuickly() {
    CornerTracker t = new CornerTracker();
    double[][] a = rect(100, 100, 300, 420);
    t.update(a, 0.9, 0, W, H);
    double[][] b = shifted(a, 8, 0);
    CornerTracker.Output o = null;
    for (int i = 1; i <= 3; i++) o = t.update(b, 0.9, i * FRAME, W, H);
    assertTrue(QuadGeometry.maxCornerDistance(o.quad, b) < 2.5);
  }

  @Test
  public void anIsolatedJumpIsIgnoredAndAConfirmedJumpIsFollowed() {
    CornerTracker t = new CornerTracker();
    double[][] a = rect(100, 100, 300, 420);
    t.update(a, 0.9, 0, W, H);
    double[][] far = rect(20, 30, 480, 640); // e.g. the table edge for one frame
    CornerTracker.Output o = t.update(far, 0.9, FRAME, W, H);
    assertTrue(QuadGeometry.maxCornerDistance(o.quad, a) < 0.01);
    o = t.update(a, 0.9, 2 * FRAME, W, H);
    assertEquals(CornerTracker.State.DETECTED, o.state);
    // the same far outline twice in a row: real change (new page) -> snap
    t.update(far, 0.9, 3 * FRAME, W, H);
    o = t.update(far, 0.9, 4 * FRAME, W, H);
    assertTrue(QuadGeometry.maxCornerDistance(o.quad, far) < 0.01);
  }

  @Test
  public void shortLossIsBridgedThenTheOutlineIsDropped() {
    CornerTracker t = new CornerTracker();
    double[][] a = rect(100, 100, 300, 420);
    t.update(a, 0.9, 0, W, H);
    CornerTracker.Output o = t.update(null, 0, FRAME, W, H);
    assertEquals(CornerTracker.State.HOLDING, o.state);
    assertNotNull(o.quad);
    o = t.update(null, 0, CornerTracker.HOLD_MS, W, H);
    assertEquals(CornerTracker.State.HOLDING, o.state);
    o = t.update(null, 0, CornerTracker.HOLD_MS + FRAME, W, H);
    assertEquals(CornerTracker.State.NONE, o.state);
    assertNull(o.quad);
  }

  @Test
  public void trackedOutlineIsKeptWithHysteresis() {
    CornerTracker t = new CornerTracker();
    double[][] a = rect(100, 100, 300, 420);
    t.update(a, 0.9, 0, W, H);
    // 0.45 would not show a new outline, but keeps a tracked one
    CornerTracker.Output o = t.update(a, 0.45, FRAME, W, H);
    assertEquals(CornerTracker.State.DETECTED, o.state);
  }
}
