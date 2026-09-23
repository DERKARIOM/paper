package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AccessibilityGuidanceControllerMoreTest {

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
  public void testAlternatingHintsDoNotSpamDueToHysteresis() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1000, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 3000L; // start past initial gates

    // Alternate MOVE_LEFT and MOVE_RIGHT rapidly; hysteresis should block speaks
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp); // 1st LEFT
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp); // 1st RIGHT (breaks stability)
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp); // 1st LEFT again
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp); // 1st RIGHT again

    Assert.assertTrue(
        "No announcements expected when hints alternate without stability", sp.spoken.isEmpty());
  }

  @Test
  public void testTransitionRespectsRateLimit() {
    long rate = 1200L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, 6000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 5000L;

    // Speak LEFT after reaching stability
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_LEFT, sp.spoken.get(0));

    // Try to transition to RIGHT but within rate limit → should not speak yet
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 200, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 200, sp); // stable but time not enough
    Assert.assertEquals(1, sp.spoken.size());

    // After rate limit passes and stability achieved, should speak RIGHT
    t += rate; // ensure time gate satisfied
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    // Already stable from previous two frames; one more to be safe
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 50, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(1));
  }

  @Test
  public void testResetClearsState() {
    long rate = 1200L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 2, 4000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 8000L;

    // Speak once
    ctrl.onResult(r(GuidanceHint.MOVE_BACK), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_BACK), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // Reset controller and ensure previous timing/state does not block new speak after stability
    ctrl.reset();
    // Advance a bit but not necessary if reset clears timestamps
    t += 100;
    ctrl.onResult(r(GuidanceHint.MOVE_CLOSER), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_CLOSER), t += 100, sp);

    Assert.assertEquals(
        "After reset, a new stable hint should be spoken immediately (independent of previous rate)",
        2,
        sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_CLOSER, sp.spoken.get(1));
  }
}
