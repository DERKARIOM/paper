package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AccessibilityGuidanceControllerTest {

  private static class CapturingSpeaker implements AccessibilityGuidanceController.Speaker {
    final List<GuidanceHint> spoken = new ArrayList<>();

    @Override
    public void speak(GuidanceHint hint) {
      spoken.add(hint);
    }
  }

  private static FramingResult makeResult(GuidanceHint hint) {
    // values other than hint don't matter for controller tests
    return new FramingResult(1f, 0f, 0f, 1f, 0f, 0f, hint, true);
  }

  @Test
  public void testHysteresisRequiresStableFrames() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1200, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 2000L; // start sufficiently large to pass initial rate-limit gate

    // First frame MOVE_LEFT -> not enough stable frames yet
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 200, sp);
    Assert.assertTrue(sp.spoken.isEmpty());

    // Second consecutive MOVE_LEFT -> should speak
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 200, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_LEFT, sp.spoken.get(0));

    // Change to MOVE_RIGHT but only one frame -> no speak due to hysteresis
    ctrl.onResult(makeResult(GuidanceHint.MOVE_RIGHT), t += 200, sp);
    Assert.assertEquals(1, sp.spoken.size());
  }

  @Test
  public void testRateLimitingPreventsFrequentSpeaks() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1200, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 2000L; // start sufficiently large to pass initial rate-limit gate

    // Speak once after stable frames
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 100, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // Still within rateLimit -> should not speak even if stable and same hint
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 200, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 200, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After extended wait beyond 2x rate limit -> can speak same hint again
    t += (1200 * 2 + 100); // > 2x rateLimit
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t, sp);
    Assert.assertEquals(2, sp.spoken.size());
  }

  @Test
  public void testOkSuppressionAndPeriodicOk() {
    long rate = 800L;
    long okRepeat = 3000L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, okRepeat);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 5000L; // start sufficiently large to pass initial rate-limit gate

    // First time reaching OK after stable frames -> should speak OK
    ctrl.onResult(makeResult(GuidanceHint.OK), t += 100, sp);
    ctrl.onResult(makeResult(GuidanceHint.OK), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(0));

    // Subsequent OK frames within okRepeat window -> suppressed
    ctrl.onResult(makeResult(GuidanceHint.OK), t += 500, sp);
    ctrl.onResult(makeResult(GuidanceHint.OK), t += 500, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After okRepeat has passed and rate limit satisfied -> should repeat OK
    t += okRepeat; // advance beyond OK repeat window
    ctrl.onResult(makeResult(GuidanceHint.OK), t, sp);
    Assert.assertEquals(2, sp.spoken.size());

    // Transition to MOVE_BACK -> after stable frames and rate limit, should speak MOVE_BACK
    t += rate; // ensure rate satisfied
    ctrl.onResult(makeResult(GuidanceHint.MOVE_BACK), t += 100, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_BACK), t += 100, sp);
    Assert.assertEquals(3, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_BACK, sp.spoken.get(2));
  }

  @Test
  public void testOscillationPreventsSpeakDueToHysteresis() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1000, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 3000L;

    // Alternate hints so stableCount never reaches the threshold
    for (int i = 0; i < 10; i++) {
      GuidanceHint h = (i % 2 == 0) ? GuidanceHint.MOVE_LEFT : GuidanceHint.MOVE_RIGHT;
      ctrl.onResult(makeResult(h), t += 150, sp);
    }

    // Expect no speaks because we never had 2 consecutive identical hints
    Assert.assertTrue(sp.spoken.isEmpty());
  }

  @Test
  public void testStateChangeRequiresRateLimitEvenWhenStable() {
    long rate = 1200L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 5000L;

    // First stable emission: MOVE_LEFT
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 50, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_LEFT), t += 50, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_LEFT, sp.spoken.get(0));

    // Attempt to switch to MOVE_RIGHT with enough stable frames,
    // but still within rate limit => must not speak yet
    ctrl.onResult(makeResult(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After rate limit elapsed, provide stable frames again => should speak
    t += rate;
    ctrl.onResult(makeResult(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    ctrl.onResult(makeResult(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(1));
  }
}
