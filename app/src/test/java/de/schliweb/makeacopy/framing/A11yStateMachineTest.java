package de.schliweb.makeacopy.framing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class A11yStateMachineTest {

  // ── State enum ────────────────────────────────────────────────────────────

  @Test
  public void state_hasFourValues() {
    assertEquals(4, A11yStateMachine.State.values().length);
  }

  @Test
  public void state_valuesInOrder() {
    A11yStateMachine.State[] vals = A11yStateMachine.State.values();
    assertEquals(A11yStateMachine.State.NO_DOC, vals[0]);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, vals[1]);
    assertEquals(A11yStateMachine.State.DOC_ALIGNING, vals[2]);
    assertEquals(A11yStateMachine.State.READY, vals[3]);
  }

  // ── Event enum ────────────────────────────────────────────────────────────

  @Test
  public void event_hasExpectedCount() {
    // 4 system + 1 ready + 1 hold + 4 tilt + 4 move + 2 distance + 1 too_far + 2 orientation + 1 ok
    // = 20
    assertEquals(20, A11yStateMachine.Event.values().length);
  }

  @Test
  public void event_containsKeyValues() {
    assertEquals(
        A11yStateMachine.Event.CAMERA_READY, A11yStateMachine.Event.valueOf("CAMERA_READY"));
    assertEquals(A11yStateMachine.Event.READY_ENTER, A11yStateMachine.Event.valueOf("READY_ENTER"));
    assertEquals(A11yStateMachine.Event.HOLD_STILL, A11yStateMachine.Event.valueOf("HOLD_STILL"));
    assertEquals(A11yStateMachine.Event.MOVE_LEFT, A11yStateMachine.Event.valueOf("MOVE_LEFT"));
    assertEquals(A11yStateMachine.Event.MOVE_CLOSER, A11yStateMachine.Event.valueOf("MOVE_CLOSER"));
    assertEquals(A11yStateMachine.Event.TOO_FAR, A11yStateMachine.Event.valueOf("TOO_FAR"));
    assertEquals(A11yStateMachine.Event.OK, A11yStateMachine.Event.valueOf("OK"));
  }

  // ── Constants ─────────────────────────────────────────────────────────────

  @Test
  public void constants_haveExpectedValues() {
    assertEquals(10, A11yStateMachine.N_WINDOW);
    assertEquals(2, A11yStateMachine.MIN_STABLE_FRAMES_HINT);
    assertEquals(8, A11yStateMachine.MIN_STABLE_FRAMES_READY);
    assertEquals(2, A11yStateMachine.K_DOC_ENTER);
    assertEquals(3, A11yStateMachine.K_READY_EXIT);
    assertEquals(1200L, A11yStateMachine.RATE_LIMIT_MS);
    assertEquals(5000L, A11yStateMachine.OK_REPEAT_MS);
    assertEquals(0.80f, A11yStateMachine.READY_QGEOM_MIN, 1e-6f);
    assertEquals(1.0f, A11yStateMachine.TOO_FAR_FOCUS_THRESHOLD_DIOPTERS, 1e-6f);
  }

  // ── Constructor and reset ─────────────────────────────────────────────────

  @Test
  public void newInstance_startsInNoDoc() {
    A11yStateMachine sm = new A11yStateMachine();
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  @Test
  public void reset_returnsToNoDoc() {
    A11yStateMachine sm = new A11yStateMachine();
    sm.reset();
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  // ── DebugInfo ─────────────────────────────────────────────────────────────

  @Test
  public void debugInfo_afterConstruction() {
    A11yStateMachine sm = new A11yStateMachine();
    A11yStateMachine.DebugInfo info = sm.getDebugInfo();
    assertNotNull(info);
    assertEquals(A11yStateMachine.State.NO_DOC, info.state);
    assertNull(info.lastEvent);
    assertEquals(0, info.plausibleDocCounter);
    assertEquals(0, info.badFrameCounter);
    assertEquals(0, info.hintStableCount);
    assertNull(info.lastStableHint);
  }
}
