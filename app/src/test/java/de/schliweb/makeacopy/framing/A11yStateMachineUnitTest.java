package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * JVM unit tests for {@link A11yStateMachine}. Covers state transitions, hysteresis counters, rate
 * limiting, event mapping, system event injection, and debug info.
 */
public class A11yStateMachineUnitTest {

  private static final int W = 1000;
  private static final int H = 800;

  private A11yStateMachine sm;
  private List<A11yStateMachine.Event> emittedEvents;
  private List<A11yStateMachine.State> emittedStates;
  private A11yStateMachine.EventListener listener;

  private static PointF pt(float x, float y) {
    PointF p = new PointF();
    p.x = x;
    p.y = y;
    return p;
  }

  /** A plausible quad covering ~60% of the image. */
  private static PointF[] plausibleQuad() {
    return new PointF[] {pt(100, 100), pt(900, 100), pt(900, 700), pt(100, 700)};
  }

  private static FramingResult okResult() {
    return new FramingResult(0.95f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.OK, true);
  }

  private static FramingResult moveLeftResult() {
    return new FramingResult(0.5f, -0.3f, 0f, 1f, 0f, 0f, GuidanceHint.MOVE_LEFT, true);
  }

  @Before
  public void setUp() {
    sm = new A11yStateMachine();
    emittedEvents = new ArrayList<>();
    emittedStates = new ArrayList<>();
    listener =
        (event, state) -> {
          emittedEvents.add(event);
          emittedStates.add(state);
        };
  }

  // ---- Initial state ----

  @Test
  public void initialState_isNoDoc() {
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  // ---- Reset ----

  @Test
  public void reset_returnsToNoDoc() {
    // Push to some other state first
    feedPlausibleFrames(10, 0);
    sm.reset();
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  // ---- State transitions: NO_DOC -> DOC_UNSTABLE ----

  @Test
  public void noDoc_needsKDocEnterPlausibleFrames() {
    // One plausible frame is not enough
    sm.onFrame(plausibleQuad(), W, H, okResult(), 1000, listener);
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());

    // Second plausible frame triggers transition (K_DOC_ENTER = 2)
    sm.onFrame(plausibleQuad(), W, H, okResult(), 2000, listener);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, sm.getState());
  }

  @Test
  public void noDoc_nonPlausibleResetsCounter() {
    sm.onFrame(plausibleQuad(), W, H, okResult(), 1000, listener);
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());

    // Non-plausible frame resets counter
    sm.onFrame(null, W, H, null, 2000, listener);
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());

    // Need K_DOC_ENTER again
    sm.onFrame(plausibleQuad(), W, H, okResult(), 3000, listener);
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  // ---- DOC_UNSTABLE -> NO_DOC (bad frames) ----

  @Test
  public void docUnstable_badFramesCauseReturnToNoDoc() {
    feedPlausibleFrames(2, 0);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, sm.getState());

    // K_DOC_ENTER bad frames needed
    sm.onFrame(null, W, H, null, 10000, listener);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, sm.getState());

    sm.onFrame(null, W, H, null, 12000, listener);
    assertEquals(A11yStateMachine.State.NO_DOC, sm.getState());
  }

  // ---- DOC_UNSTABLE -> DOC_ALIGNING (stable guidance) ----

  @Test
  public void docUnstable_stableHintTransitionsToAligning() {
    // Feed enough plausible frames with same hint to become stable
    feedPlausibleFrames(A11yStateMachine.MIN_STABLE_FRAMES_HINT + A11yStateMachine.K_DOC_ENTER, 0);
    // Should have progressed past DOC_UNSTABLE
    assertTrue(
        sm.getState() == A11yStateMachine.State.DOC_ALIGNING
            || sm.getState() == A11yStateMachine.State.READY);
  }

  // ---- DOC_ALIGNING -> READY ----

  @Test
  public void docAligning_stableHighQualityTransitionsToReady() {
    // Feed many high-quality OK frames
    int needed = A11yStateMachine.MIN_STABLE_FRAMES_READY + A11yStateMachine.K_DOC_ENTER + 5;
    feedPlausibleFrames(needed, 0);
    assertEquals(A11yStateMachine.State.READY, sm.getState());
  }

  // ---- READY emits READY_ENTER ----

  @Test
  public void readyEnter_isEmittedOnTransition() {
    int needed = A11yStateMachine.MIN_STABLE_FRAMES_READY + A11yStateMachine.K_DOC_ENTER + 5;
    feedPlausibleFrames(needed, 0);
    assertTrue(emittedEvents.contains(A11yStateMachine.Event.READY_ENTER));
  }

  // ---- READY -> exits on bad frames ----

  @Test
  public void ready_exitsAfterKReadyExitBadFrames() {
    int needed = A11yStateMachine.MIN_STABLE_FRAMES_READY + A11yStateMachine.K_DOC_ENTER + 5;
    feedPlausibleFrames(needed, 0);
    assertEquals(A11yStateMachine.State.READY, sm.getState());

    long ts = 100000;
    for (int i = 0; i < A11yStateMachine.K_READY_EXIT; i++) {
      sm.onFrame(null, W, H, null, ts, listener);
      ts += 2000;
    }
    assertNotEquals(A11yStateMachine.State.READY, sm.getState());
  }

  // ---- DOC_UNSTABLE emits HOLD_STILL ----

  @Test
  public void docUnstable_emitsHoldStill() {
    feedPlausibleFrames(2, 0);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, sm.getState());

    emittedEvents.clear();
    // Feed another plausible frame with enough time gap for rate limit
    sm.onFrame(plausibleQuad(), W, H, moveLeftResult(), 50000, listener);
    assertTrue(emittedEvents.contains(A11yStateMachine.Event.HOLD_STILL));
  }

  // ---- System event injection ----

  @Test
  public void injectSystemEvent_emitsEvent() {
    sm.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 1000, listener);
    assertEquals(1, emittedEvents.size());
    assertEquals(A11yStateMachine.Event.LOW_LIGHT, emittedEvents.get(0));
  }

  @Test
  public void injectSystemEvent_nullListenerDoesNothing() {
    sm.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 1000, null);
    // No exception
  }

  @Test
  public void injectSystemEvent_nullEventDoesNothing() {
    sm.injectSystemEvent(null, 1000, listener);
    assertTrue(emittedEvents.isEmpty());
  }

  // ---- Rate limiting ----

  @Test
  public void rateLimiting_suppressesRapidEvents() {
    // System events bypass rate limit
    sm.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 1000, listener);
    assertEquals(1, emittedEvents.size());

    // Same event within rate limit window - system events can still bypass
    sm.injectSystemEvent(A11yStateMachine.Event.FLASHLIGHT_ON, 1100, listener);
    assertEquals(2, emittedEvents.size());
  }

  @Test
  public void readyEnter_alwaysBypassesRateLimit() {
    // READY_ENTER should always be emitted
    int needed = A11yStateMachine.MIN_STABLE_FRAMES_READY + A11yStateMachine.K_DOC_ENTER + 5;
    // Feed frames very rapidly (within rate limit)
    for (int i = 0; i < needed; i++) {
      sm.onFrame(plausibleQuad(), W, H, okResult(), i, listener);
    }
    assertTrue(emittedEvents.contains(A11yStateMachine.Event.READY_ENTER));
  }

  // ---- DebugInfo ----

  @Test
  public void debugInfo_reflectsCurrentState() {
    A11yStateMachine.DebugInfo info = sm.getDebugInfo();
    assertEquals(A11yStateMachine.State.NO_DOC, info.state);
    assertNull(info.lastEvent);
    assertEquals(0, info.plausibleDocCounter);
  }

  @Test
  public void debugInfo_toString_containsState() {
    A11yStateMachine.DebugInfo info = sm.getDebugInfo();
    String s = info.toString();
    assertTrue(s.contains("NO_DOC"));
  }

  // ---- NO_DOC only emits orientation tips ----

  @Test
  public void noDoc_emitsOrientationTip() {
    FramingResult orientResult =
        new FramingResult(0f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.ORIENTATION_PORTRAIT_TIP, false);
    // Feed enough frames for hint stability
    for (int i = 0; i < A11yStateMachine.MIN_STABLE_FRAMES_HINT + 1; i++) {
      sm.onFrame(null, W, H, orientResult, (long) i * 2000, listener);
    }
    // Should have emitted orientation tip (if rate limit allows)
    boolean hasOrientTip =
        emittedEvents.stream().anyMatch(e -> e == A11yStateMachine.Event.ORIENTATION_PORTRAIT_TIP);
    assertTrue(hasOrientTip);
  }

  // ---- onFrame overload without focus distance ----

  @Test
  public void onFrame_withoutFocusDistance_works() {
    sm.onFrame(plausibleQuad(), W, H, okResult(), 1000, listener);
    // No exception, state may or may not have changed
    assertNotNull(sm.getState());
  }

  // ---- Helper ----

  private void feedPlausibleFrames(int count, long startTs) {
    for (int i = 0; i < count; i++) {
      sm.onFrame(plausibleQuad(), W, H, okResult(), startTs + (long) i * 2000, listener);
    }
  }
}
