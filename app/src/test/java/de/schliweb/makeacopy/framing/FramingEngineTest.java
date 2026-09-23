package de.schliweb.makeacopy.framing;

import org.junit.Assert;
import org.junit.Test;

/**
 * JVM unit tests for FramingEngine that do not rely on android.* classes. These focus on
 * null/absent-quad paths which are meaningful for behavior when no document is detected yet.
 */
public class FramingEngineTest {

  @Test
  public void evaluate_nullInput_returnsOkNoDocument() {
    FramingEngine engine = new FramingEngine();
    FramingResult r = engine.evaluate(null);

    Assert.assertNotNull(r);
    Assert.assertEquals(GuidanceHint.OK, r.hint);
    Assert.assertFalse(r.hasDocument);
    // Neutral-ish metrics
    Assert.assertEquals(0f, r.dxNorm, 1e-6);
    Assert.assertEquals(0f, r.dyNorm, 1e-6);
    Assert.assertEquals(1f, r.scaleRatio, 1e-6);
  }

  @Test
  public void evaluate_noQuad_returnsMoveCloserNoDocument() {
    FramingEngine engine = new FramingEngine();
    // Provide input with image size but no quad/fallback (allowed)
    FramingEngine.Input in = new FramingEngine.Input(1000, 800, null, null);
    FramingResult r = engine.evaluate(in);

    Assert.assertNotNull(r);
    Assert.assertEquals(GuidanceHint.MOVE_CLOSER, r.hint);
    Assert.assertFalse(r.hasDocument);
  }
}
