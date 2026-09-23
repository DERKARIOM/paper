package de.schliweb.makeacopy.framing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FramingResultTest {

  @Test
  public void allArgsConstructor_setsAllFields() {
    FramingResult r =
        new FramingResult(0.95f, 0.1f, -0.2f, 1.5f, 3.0f, -1.0f, GuidanceHint.OK, true);

    assertEquals(0.95f, r.quality, 1e-6f);
    assertEquals(0.1f, r.dxNorm, 1e-6f);
    assertEquals(-0.2f, r.dyNorm, 1e-6f);
    assertEquals(1.5f, r.scaleRatio, 1e-6f);
    assertEquals(3.0f, r.tiltHorizontal, 1e-6f);
    assertEquals(-1.0f, r.tiltVertical, 1e-6f);
    assertEquals(GuidanceHint.OK, r.hint);
    assertTrue(r.hasDocument);
  }

  @Test
  public void allArgsConstructor_noDocument() {
    FramingResult r =
        new FramingResult(
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, GuidanceHint.NO_DOCUMENT_DETECTED, false);

    assertEquals(0.0f, r.quality, 1e-6f);
    assertFalse(r.hasDocument);
    assertEquals(GuidanceHint.NO_DOCUMENT_DETECTED, r.hint);
  }

  @Test
  public void nullHint_allowed() {
    FramingResult r = new FramingResult(0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, null, true);
    assertNull(r.hint);
  }

  @Test
  public void negativeValues_stored() {
    FramingResult r =
        new FramingResult(
            -1.0f, -0.5f, -0.5f, -1.0f, -10.0f, -10.0f, GuidanceHint.MOVE_LEFT, false);

    assertEquals(-1.0f, r.quality, 1e-6f);
    assertEquals(-0.5f, r.dxNorm, 1e-6f);
    assertEquals(-10.0f, r.tiltHorizontal, 1e-6f);
  }

  @Test
  public void toString_containsFieldValues() {
    FramingResult r = new FramingResult(0.8f, 0.1f, 0.2f, 1.0f, 0.0f, 0.0f, GuidanceHint.OK, true);

    String s = r.toString();
    assertNotNull(s);
    assertTrue(s.contains("0.8"));
    assertTrue(s.contains("OK"));
  }
}
