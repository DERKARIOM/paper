package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.makeacopy.ui.camera.AutoCaptureController.Frame;
import de.schliweb.makeacopy.ui.camera.AutoCaptureController.Phase;
import java.util.Random;
import org.junit.Test;

/** Automatic capture decisions (pure JVM, injected time). */
public class AutoCaptureControllerTest {
  private static final int W = 540, H = 720;
  private static final long FRAME = 125; // ~8 analysed frames per second (Galaxy S10)

  private static double[][] page(double dx, double dy) {
    return new double[][] {
      {110 + dx, 120 + dy}, {430 + dx, 125 + dy}, {435 + dx, 590 + dy}, {105 + dx, 585 + dy}
    };
  }

  private interface Maker {
    Frame make(long t);
  }

  private static Frame good(double[][] q, long t) {
    return new Frame(q, 0.85, 0.01, true, true, W, H, t);
  }

  /** Feeds frames from t0 every FRAME ms until a capture fires; returns the fire time or -1. */
  private static long runUntilFire(AutoCaptureController c, long t0, int maxFrames, Maker m) {
    for (int i = 0; i < maxFrames; i++) {
      long t = t0 + i * FRAME;
      if (c.update(m.make(t), true).fire) return t;
    }
    return -1;
  }

  private static AutoCaptureController enabledAt(long t) {
    AutoCaptureController c = new AutoCaptureController();
    c.setEnabled(true, t);
    return c;
  }

  @Test
  public void disabledByDefaultAndNeverFires() {
    AutoCaptureController c = new AutoCaptureController();
    assertFalse(c.isEnabled());
    for (int i = 0; i < 40; i++) {
      AutoCaptureController.Decision d = c.update(good(page(0, 0), 2000 + i * FRAME), true);
      assertFalse(d.fire);
      assertEquals(Phase.OFF, d.phase);
    }
  }

  @Test
  public void stableDocumentFiresOnceAfterTheHoldTime() {
    AutoCaptureController c = enabledAt(0);
    long start = 2000; // after the arm delay
    long fired = runUntilFire(c, start, 40, t -> good(page(0, 0), t));
    assertTrue("fired", fired >= 0);
    assertTrue("not before the hold time", fired - start >= AutoCaptureController.HOLD_MS);
    assertTrue("quickly after it", fired - start <= AutoCaptureController.HOLD_MS + 2 * FRAME);
    // locked: no second capture from the following frames
    for (int i = 1; i < 30; i++) {
      assertFalse(c.update(good(page(0, 0), fired + i * FRAME), true).fire);
    }
    assertEquals(Phase.FIRED, c.phase());
  }

  @Test
  public void aSingleDetectedFrameNeverFires() {
    AutoCaptureController c = enabledAt(0);
    for (int i = 0; i < 40; i++) {
      long t = 2000 + i * FRAME;
      // document only every other frame (flickering detection)
      Frame f = (i % 2 == 0) ? good(page(0, 0), t) : new Frame(null, 0, 1, false, false, W, H, t);
      assertFalse(c.update(f, true).fire);
    }
  }

  @Test
  public void handShakeBelowTheThresholdStillFires_strongMotionDoesNot() {
    Random r = new Random(3);
    AutoCaptureController calm = enabledAt(0);
    long fired =
        runUntilFire(
            calm, 2000, 40, t -> good(page(r.nextGaussian() * 1.5, r.nextGaussian() * 1.5), t));
    assertTrue("steady hand (±1.5 px) is accepted", fired >= 0);

    AutoCaptureController moving = enabledAt(0);
    long[] x = {0};
    long never = runUntilFire(moving, 2000, 60, t -> good(page(x[0] += 14, 0), t)); // panning
    assertEquals("moving phone never fires", -1, never);
  }

  @Test
  public void slowDriftBeyondTheWindowRestartsIt() {
    AutoCaptureController c = enabledAt(0);
    // 6 px per frame: below the per-frame step (10.8 px) but drifts 36 px within the hold time
    double[] dx = {0};
    long fired = runUntilFire(c, 2000, 12, t -> good(page(dx[0] += 6, 0), t));
    assertEquals(-1, fired);
  }

  @Test
  public void nonEligibleFramesDoNotFire() {
    // low confidence
    assertNeverFires(t -> new Frame(page(0, 0), 0.65, 0.01, true, true, W, H, t));
    // two documents in view: the mask spills out of the outline
    assertNeverFires(t -> new Frame(page(0, 0), 0.8, 0.45, true, true, W, H, t));
    // not in the framing guide / too small
    assertNeverFires(t -> new Frame(page(0, 0), 0.9, 0.0, true, false, W, H, t));
    // held after a loss, not currently detected
    assertNeverFires(t -> new Frame(page(0, 0), 0.9, 0.0, false, true, W, H, t));
    // page cut by the image border
    double[][] cut = {{-10, 120}, {430, 125}, {435, 590}, {-12, 585}};
    assertNeverFires(t -> good(cut, t));
  }

  private static void assertNeverFires(Maker m) {
    assertEquals(-1, runUntilFire(enabledAt(0), 2000, 30, m));
  }

  @Test
  public void aCornerLostForOneFrameRestartsTheWindow() {
    AutoCaptureController c = enabledAt(0);
    long t = 2000;
    for (int i = 0; i < 4; i++, t += FRAME) assertFalse(c.update(good(page(0, 0), t), true).fire);
    assertFalse(c.update(new Frame(null, 0, 1, false, true, W, H, t), true).fire); // lost
    t += FRAME;
    long fired = runUntilFire(c, t, 20, tt -> good(page(0, 0), tt));
    assertTrue(fired - t >= AutoCaptureController.HOLD_MS);
  }

  @Test
  public void neverFiresWhileACaptureIsRunning() {
    AutoCaptureController c = enabledAt(0);
    for (int i = 0; i < 30; i++) {
      assertFalse(c.update(good(page(0, 0), 2000 + i * FRAME), false).fire);
    }
  }

  @Test
  public void notRightAfterSwitchingOn() {
    AutoCaptureController c = enabledAt(1000);
    long fired = runUntilFire(c, 1000, 30, t -> good(page(0, 0), t));
    assertTrue(fired - 1000 >= AutoCaptureController.ARM_DELAY_MS);
  }

  @Test
  public void multiPage_samePageIsNotCapturedAgain_nextPageIs() {
    AutoCaptureController c = enabledAt(0);
    long fired = runUntilFire(c, 2000, 30, t -> good(page(0, 0), t));
    assertTrue(fired > 0);
    // back on the scan screen, page 1 still in view: no capture
    long back = fired + 5000;
    c.onScreenShown(back);
    assertEquals(-1, runUntilFire(c, back, 40, t -> good(page(0, 0), t)));
    // the user removes page 1 (a few frames without document) and puts page 2 at the same place
    long t = back + 40 * FRAME;
    for (int i = 0; i < 4; i++, t += FRAME) {
      c.update(new Frame(null, 0, 1, false, false, W, H, t), true);
    }
    assertTrue(runUntilFire(c, t, 30, tt -> good(page(0, 0), tt)) > 0);
  }

  @Test
  public void multiPage_movedPageIsCaptured() {
    AutoCaptureController c = enabledAt(0);
    long fired = runUntilFire(c, 2000, 30, t -> good(page(0, 0), t));
    // the same page nudged a little (4% of the diagonal): not a new page
    c.onScreenShown(fired + 3000);
    assertEquals(-1, runUntilFire(c, fired + 3000, 40, t -> good(page(0, 35), t)));
    // a page placed clearly elsewhere (11% of the diagonal): new page
    long t2 = fired + 3000 + 40 * FRAME;
    assertTrue(runUntilFire(c, t2, 40, t -> good(page(0, -100), t)) > 0);
  }

  @Test
  public void manualCaptureAlsoCountsAsCaptured() {
    AutoCaptureController c = enabledAt(0);
    long t = 2000;
    for (int i = 0; i < 3; i++, t += FRAME) c.update(good(page(0, 0), t), true);
    c.onCaptured(t); // shutter button pressed
    assertEquals(Phase.FIRED, c.phase());
    c.onScreenShown(t + 4000);
    assertEquals(-1, runUntilFire(c, t + 4000, 40, tt -> good(page(0, 0), tt)));
  }

  @Test
  public void failedCaptureRearmsForTheSamePage() {
    AutoCaptureController c = enabledAt(0);
    long fired = runUntilFire(c, 2000, 30, t -> good(page(0, 0), t));
    c.onCaptureFailed(fired + 200);
    assertTrue(runUntilFire(c, fired + 200, 40, t -> good(page(0, 0), t)) > 0);
  }

  @Test
  public void switchingOffStopsEverything() {
    AutoCaptureController c = enabledAt(0);
    for (int i = 0; i < 6; i++) c.update(good(page(0, 0), 2000 + i * FRAME), true);
    c.setEnabled(false, 2800);
    assertEquals(-1, runUntilFire(c, 2900, 30, t -> good(page(0, 0), t)));
  }

  @Test
  public void frameGapRestartsTheWindow() {
    AutoCaptureController c = enabledAt(0);
    long t = 2000;
    for (int i = 0; i < 4; i++, t += FRAME) c.update(good(page(0, 0), t), true);
    t += 800; // analysis paused (lens switch, rebind)
    AutoCaptureController.Decision d = c.update(good(page(0, 0), t), true);
    assertFalse(d.fire);
    assertTrue(d.progress < 0.05f);
  }
}
