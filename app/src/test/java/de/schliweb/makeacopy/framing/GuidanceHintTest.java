package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import org.junit.Test;

/** Unit tests for the {@link GuidanceHint} enum. */
public class GuidanceHintTest {

  @Test
  public void allValues_present() {
    GuidanceHint[] values = GuidanceHint.values();
    assertEquals(17, values.length);
  }

  @Test
  public void valueOf_directional() {
    assertEquals(GuidanceHint.MOVE_LEFT, GuidanceHint.valueOf("MOVE_LEFT"));
    assertEquals(GuidanceHint.MOVE_RIGHT, GuidanceHint.valueOf("MOVE_RIGHT"));
    assertEquals(GuidanceHint.MOVE_UP, GuidanceHint.valueOf("MOVE_UP"));
    assertEquals(GuidanceHint.MOVE_DOWN, GuidanceHint.valueOf("MOVE_DOWN"));
    assertEquals(GuidanceHint.MOVE_CLOSER, GuidanceHint.valueOf("MOVE_CLOSER"));
    assertEquals(GuidanceHint.MOVE_BACK, GuidanceHint.valueOf("MOVE_BACK"));
  }

  @Test
  public void valueOf_tilt() {
    assertEquals(GuidanceHint.TILT_LEFT, GuidanceHint.valueOf("TILT_LEFT"));
    assertEquals(GuidanceHint.TILT_RIGHT, GuidanceHint.valueOf("TILT_RIGHT"));
    assertEquals(GuidanceHint.TILT_FORWARD, GuidanceHint.valueOf("TILT_FORWARD"));
    assertEquals(GuidanceHint.TILT_BACK, GuidanceHint.valueOf("TILT_BACK"));
  }

  @Test
  public void valueOf_status() {
    assertEquals(GuidanceHint.OK, GuidanceHint.valueOf("OK"));
    assertEquals(GuidanceHint.NO_DOCUMENT_DETECTED, GuidanceHint.valueOf("NO_DOCUMENT_DETECTED"));
    assertEquals(GuidanceHint.HOLD_STILL, GuidanceHint.valueOf("HOLD_STILL"));
    assertEquals(GuidanceHint.READY_ENTER, GuidanceHint.valueOf("READY_ENTER"));
    assertEquals(GuidanceHint.TOO_FAR, GuidanceHint.valueOf("TOO_FAR"));
  }

  @Test
  public void valueOf_orientation() {
    assertEquals(
        GuidanceHint.ORIENTATION_PORTRAIT_TIP, GuidanceHint.valueOf("ORIENTATION_PORTRAIT_TIP"));
    assertEquals(
        GuidanceHint.ORIENTATION_LANDSCAPE_TIP, GuidanceHint.valueOf("ORIENTATION_LANDSCAPE_TIP"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void valueOf_invalid_throws() {
    GuidanceHint.valueOf("NONEXISTENT");
  }
}
