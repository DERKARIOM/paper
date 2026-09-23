package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AccessibilityGuidanceControllerStabilityTest {

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
  public void testMinStableFramesThreeDelaysAnnouncements() {
    long rate = 900L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 3, 5000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 2000L;

    // Two frames are not enough
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp);
    Assert.assertTrue(sp.spoken.isEmpty());

    // Third stable frame should trigger speech
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp);
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(0));
  }

  @Test
  public void testInstabilityResetsStableCount() {
    long rate = 800L;
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(rate, 3, 4000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 4000L;

    // Build up 2 stable frames
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp);
    ctrl.onResult(r(GuidanceHint.MOVE_LEFT), t += 100, sp);
    Assert.assertTrue(sp.spoken.isEmpty());

    // Introduce a different hint → should reset stability
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp);
    Assert.assertTrue(sp.spoken.isEmpty());

    // Need 3 total stable frames for the new hint before speaking.
    // We already had 1 (the switch frame). Add one more and still expect silence.
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp); // 2nd stable RIGHT
    Assert.assertTrue(sp.spoken.isEmpty());

    // Third stable RIGHT frame should trigger speaking
    ctrl.onResult(r(GuidanceHint.MOVE_RIGHT), t += 100, sp); // 3rd stable RIGHT
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.MOVE_RIGHT, sp.spoken.get(0));
  }
}
