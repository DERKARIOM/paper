package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AccessibilityGuidanceControllerEdgeTest {

  private static class CapturingSpeaker implements AccessibilityGuidanceController.Speaker {
    final List<GuidanceHint> spoken = new ArrayList<>();

    @Override
    public void speak(GuidanceHint hint) {
      spoken.add(hint);
    }
  }

  private static FramingResult r(GuidanceHint h) {
    return new FramingResult(1f, 0f, 0f, 1f, 0f, 0f, h, true);
  }

  @Test
  public void testNullInputsAreSafelyIgnored() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1000, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();

    // Null result
    ctrl.onResult(null, 1000L, sp);
    // Null speaker
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), 1200L, null);

    Assert.assertTrue(sp.spoken.isEmpty());
  }

  @Test
  public void testMinStableFramesOneSpeaksImmediatelyButRespectsRate() {
    long rate = 500L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 1, 4000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 2000L;

    // With minStableFrames=1, first stable frame can speak immediately
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_LEFT, sp.spoken.get(0));

    // Within rate limit, even with continuing same hint, no extra speak
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t + 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t + 200, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After 2x rate, same hint can be repeated (controller uses a 2x-rate suppression for repeats)
    t += (rate * 2 + 10);
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t, sp);
    Assert.assertEquals(2, sp.spoken.size());
  }

  @Test
  public void testOkSuppressionWithRateInteraction() {
    long rate = 700L;
    long okRepeat = 2500L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, okRepeat);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 5000L;

    // Speak a non-OK first after stability
    ctrl.onResult(r(GuidanceHint.MOVE_BACK), t += 50, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_BACK), t += 50, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_BACK, sp.spoken.get(0));

    // Transition to OK: requires stability and rate; should speak OK once
    t += rate; // ensure rate limit satisfied
    ctrl.onResult(r(GuidanceHint.OK), t += 50, sp);
    ctrl.onResult(r(GuidanceHint.OK), t += 50, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(1));

    // Continuous OK within okRepeat should be suppressed
    ctrl.onResult(r(GuidanceHint.OK), t + 200, sp);
    ctrl.onResult(r(GuidanceHint.OK), t + 400, sp);
    Assert.assertEquals(2, sp.spoken.size());

    // After okRepeat and rate, OK may be repeated
    t += okRepeat + rate;
    ctrl.onResult(r(GuidanceHint.OK), t, sp);
    Assert.assertEquals(3, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(2));
  }
}
