package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Additional transition-focused tests for AccessibilityGuidanceController. Verifies that rapid
 * changes between different non-OK hints respect both hysteresis and rate limiting without spamming
 * announcements.
 */
public class AccessibilityGuidanceControllerTransitionTest {

  private static class CapturingSpeaker implements AccessibilityGuidanceController.Speaker {
    final List<GuidanceHint> spoken = new ArrayList<>();

    @Override
    public void speak(GuidanceHint hint) {
      spoken.add(hint);
    }
  }

  private static FramingResult r(GuidanceHint h) {
    // Other values are irrelevant for controller behavior
    return new FramingResult(1f, 0f, 0f, 1f, 0f, 0f, h, true);
  }

  @Test
  public void testTiltToTranslationTransitionRespectsRateLimit() {
    long rate = 1100L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, 5000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 7000L;

    // Speak a tilt hint after stability
    ctrl.onResult(r(GuidanceHint.TILT_LEFT), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.TILT_LEFT), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.TILT_LEFT, sp.spoken.get(0));

    // Transition towards MOVE_RIGHT but still within rate window → should not speak yet
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 200, sp); // 1st stable RIGHT frame
    ctrl.onResult(
        r(GuidanceHint.MOVE_RIGHT), t += 200, sp); // 2nd stable RIGHT frame (stable satisfied)
    Assert.assertEquals("No new speak expected within rate window", 1, sp.spoken.size());

    // After rate passes and stability maintained → speak MOVE_RIGHT
    t += rate;
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(1));
  }

  @Test
  public void testRapidDifferentHintsDoNotSpamDueToHysteresis() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1000, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 10_000L;

    // Provide a pattern of different hints but never allow two consecutive identical ones
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp); // 1st LEFT
    ctrl.onResult(r(GuidanceHint.MOVE_UP), t += 100, sp); // switch
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp); // switch
    ctrl.onResult(r(GuidanceHint.MOVE_DOWN), t += 100, sp); // switch

    // No announcement should be made because stability is never reached
    Assert.assertTrue(sp.spoken.isEmpty());
  }
}
