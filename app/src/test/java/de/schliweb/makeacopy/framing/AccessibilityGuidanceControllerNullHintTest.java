package de.schliweb.makeacopy.framing;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Additional edge-case tests for AccessibilityGuidanceController focusing on null-hint handling and
 * stability gating.
 */
public class AccessibilityGuidanceControllerNullHintTest {

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
  public void testNullHintDefaultsToOkPath() {
    AccessibilityGuidanceController ctrl = new AccessibilityGuidanceController(1000, 2, 4000);
    CapturingSpeaker sp = new CapturingSpeaker();
    long t = 10_000L;

    // Provide two stable frames with null hint (treated as OK)
    ctrl.onResult(r(null), t += 100, sp);
    ctrl.onResult(r(null), t += 100, sp);

    // First OK after stability should be spoken
    Assert.assertEquals(1, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(0));

    // Subsequent null/OK frames within okRepeat window should be suppressed
    ctrl.onResult(r(null), t + 200, sp);
    ctrl.onResult(r(null), t + 400, sp);
    Assert.assertEquals(1, sp.spoken.size());

    // After okRepeat + rate, OK may repeat
    t += 4000 + 1000;
    ctrl.onResult(r(null), t, sp);
    Assert.assertEquals(2, sp.spoken.size());
    Assert.assertEquals(GuidanceHint.OK, sp.spoken.get(1));
  }
}
