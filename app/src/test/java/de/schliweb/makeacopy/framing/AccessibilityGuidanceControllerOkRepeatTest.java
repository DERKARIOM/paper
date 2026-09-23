package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Additional tests focused on OK repeat behavior after long silence and transitions in combination
 * with rate limiting and hysteresis.
 */
public class AccessibilityGuidanceControllerOkRepeatTest {

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
  public void testOkRepeatsAfterLongSilenceEvenWithoutStateChange() {
    long rate = 1000L;
    long okRepeat = 3500L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, okRepeat);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 10_000L;

    // Reach OK and speak once (requires stability)
    ctrl.onResult(r(GuidanceHint.OK), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.OK), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(0));

    // Keep feeding OK but within okRepeat window → suppressed
    ctrl.onResult(r(GuidanceHint.OK), t + 200, sp);
    ctrl.onResult(r(GuidanceHint.OK), t + 400, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After okRepeat and rate limit, OK should be repeated even without state change
    t += okRepeat + rate;
    ctrl.onResult(r(GuidanceHint.OK), t, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(1));
  }

  @Test
  public void testTransitionFromOkRespectsRateLimitBeforeSpeakingNewHint() {
    long rate = 900L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, 4000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 5000L;

    // Start with OK spoken
    ctrl.onResult(r(GuidanceHint.OK), t += 50, sp);
    ctrl.onResult(r(GuidanceHint.OK), t += 50, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(0));

    // Try to transition to MOVE_RIGHT but still within rate limit → no speak
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 200, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 200, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After rate passes and stability is maintained → speak new hint
    t += rate;
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(1));
  }
}
