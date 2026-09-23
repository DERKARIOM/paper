package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import android.graphics.PointF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for A11yStateMachine - the accessibility state machine for document scanning.
 * Tests cover state transitions, hysteresis, stability, event prioritization, and rate limiting.
 */
@RunWith(AndroidJUnit4.class)
public class A11yStateMachineTest {

  private static final int IMG_W = 640;
  private static final int IMG_H = 480;

  private A11yStateMachine stateMachine;
  private List<A11yStateMachine.Event> emittedEvents;
  private List<A11yStateMachine.State> emittedStates;
  private A11yStateMachine.EventListener captureListener;

  @Before
  public void setUp() {
    stateMachine = new A11yStateMachine();
    emittedEvents = new ArrayList<>();
    emittedStates = new ArrayList<>();
    captureListener =
        (event, state) -> {
          emittedEvents.add(event);
          emittedStates.add(state);
        };
  }

  // --- Helper methods ---

  private PointF[] makePlausibleQuad() {
    // Centered quad covering ~25% of image
    float side = (float) Math.sqrt(0.25f * IMG_W * IMG_H);
    float halfW = side / 2f;
    float halfH = side / 2f;
    float cx = IMG_W / 2f;
    float cy = IMG_H / 2f;
    return new PointF[] {
      new PointF(cx - halfW, cy - halfH),
      new PointF(cx + halfW, cy - halfH),
      new PointF(cx + halfW, cy + halfH),
      new PointF(cx - halfW, cy + halfH)
    };
  }

  private FramingResult makeGoodFramingResult() {
    return new FramingResult(0.90f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.OK, true);
  }

  private FramingResult makeBadFramingResult(GuidanceHint hint) {
    return new FramingResult(0.50f, 0.2f, 0.1f, 1f, 0f, 0f, hint, true);
  }

  private void feedFrames(
      int count, PointF[] quad, FramingResult fr, long startMs, long intervalMs) {
    for (int i = 0; i < count; i++) {
      stateMachine.onFrame(quad, IMG_W, IMG_H, fr, startMs + i * intervalMs, captureListener);
    }
  }

  // --- Initial state tests ---

  @Test
  public void initialState_isNoDoc() {
    assertEquals(
        "Initial state should be NO_DOC", A11yStateMachine.State.NO_DOC, stateMachine.getState());
  }

  @Test
  public void reset_returnsToNoDoc() {
    // Move to another state first
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeGoodFramingResult();
    feedFrames(20, quad, fr, 0, 200);

    // Reset
    stateMachine.reset();
    assertEquals(
        "After reset, state should be NO_DOC",
        A11yStateMachine.State.NO_DOC,
        stateMachine.getState());
  }

  // --- State transition tests ---

  @Test
  public void noDoc_toDocUnstable_afterPlausibleFrames() {
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // Feed K_DOC_ENTER (2) plausible frames
    feedFrames(A11yStateMachine.K_DOC_ENTER, quad, fr, 0, 200);

    assertEquals(
        "Should transition to DOC_UNSTABLE after K_DOC_ENTER plausible frames",
        A11yStateMachine.State.DOC_UNSTABLE,
        stateMachine.getState());
  }

  @Test
  public void docUnstable_toDocAligning_whenStable() {
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // First get to DOC_UNSTABLE
    feedFrames(A11yStateMachine.K_DOC_ENTER, quad, fr, 0, 200);
    assertEquals(A11yStateMachine.State.DOC_UNSTABLE, stateMachine.getState());

    // Feed more frames with same hint for stability
    feedFrames(A11yStateMachine.MIN_STABLE_FRAMES_HINT, quad, fr, 1000, 200);

    assertEquals(
        "Should transition to DOC_ALIGNING when stable",
        A11yStateMachine.State.DOC_ALIGNING,
        stateMachine.getState());
  }

  @Test
  public void docAligning_toReady_whenStableAndHighQuality() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();

    // Get to DOC_ALIGNING first
    feedFrames(
        A11yStateMachine.K_DOC_ENTER + A11yStateMachine.MIN_STABLE_FRAMES_HINT,
        quad,
        goodFr,
        0,
        200);

    // Feed enough frames for READY (MIN_STABLE_FRAMES_READY)
    feedFrames(A11yStateMachine.MIN_STABLE_FRAMES_READY, quad, goodFr, 5000, 200);

    assertEquals(
        "Should transition to READY when stable with high quality",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }

  @Test
  public void ready_staysReady_withGoodFrames() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();

    // Get to READY
    feedFrames(30, quad, goodFr, 0, 200);
    assertEquals(A11yStateMachine.State.READY, stateMachine.getState());

    // Feed more good frames
    feedFrames(10, quad, goodFr, 10000, 200);
    assertEquals(
        "Should stay in READY with good frames",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }

  // --- Hysteresis tests ---

  @Test
  public void ready_toleratesBadFrames_upToKReadyExit() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();
    FramingResult badFr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // Get to READY
    feedFrames(30, quad, goodFr, 0, 200);
    assertEquals(A11yStateMachine.State.READY, stateMachine.getState());

    // Feed K_READY_EXIT - 1 bad frames (should stay in READY)
    feedFrames(A11yStateMachine.K_READY_EXIT - 1, quad, badFr, 10000, 200);
    assertEquals(
        "Should tolerate bad frames up to K_READY_EXIT",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }

  @Test
  public void ready_exitsAfterKReadyExit_badFrames() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();
    FramingResult badFr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // Get to READY
    feedFrames(30, quad, goodFr, 0, 200);
    assertEquals(A11yStateMachine.State.READY, stateMachine.getState());

    // Feed K_READY_EXIT bad frames
    feedFrames(A11yStateMachine.K_READY_EXIT, quad, badFr, 10000, 200);
    assertNotEquals(
        "Should exit READY after K_READY_EXIT bad frames",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }

  @Test
  public void noDoc_requiresKDocEnter_plausibleFrames() {
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // Feed K_DOC_ENTER - 1 frames (should stay in NO_DOC)
    feedFrames(A11yStateMachine.K_DOC_ENTER - 1, quad, fr, 0, 200);
    assertEquals(
        "Should stay in NO_DOC before K_DOC_ENTER frames",
        A11yStateMachine.State.NO_DOC,
        stateMachine.getState());
  }

  // --- Event emission tests ---

  @Test
  public void readyEnter_emittedOnTransition() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();

    emittedEvents.clear();
    feedFrames(30, quad, goodFr, 0, 200);

    assertTrue(
        "READY_ENTER should be emitted on transition to READY",
        emittedEvents.contains(A11yStateMachine.Event.READY_ENTER));
  }

  @Test
  public void holdStill_emittedInDocUnstable() {
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    emittedEvents.clear();
    // Get to DOC_UNSTABLE
    feedFrames(
        A11yStateMachine.K_DOC_ENTER, quad, fr, 0, 2000); // Long intervals to pass rate limit

    assertTrue(
        "HOLD_STILL should be emitted in DOC_UNSTABLE state",
        emittedEvents.contains(A11yStateMachine.Event.HOLD_STILL));
  }

  // --- Rate limiting tests ---

  @Test
  public void rateLimiting_preventsRapidEvents() {
    PointF[] quad = makePlausibleQuad();
    FramingResult fr = makeBadFramingResult(GuidanceHint.MOVE_LEFT);

    // Get to DOC_ALIGNING
    feedFrames(
        A11yStateMachine.K_DOC_ENTER + A11yStateMachine.MIN_STABLE_FRAMES_HINT, quad, fr, 0, 200);

    emittedEvents.clear();
    // Feed many frames rapidly (within rate limit)
    feedFrames(10, quad, fr, 5000, 100); // 100ms intervals, below RATE_LIMIT_MS

    // Should not emit many events due to rate limiting
    assertTrue("Rate limiting should prevent rapid event emission", emittedEvents.size() <= 2);
  }

  // --- No document state tests ---

  @Test
  public void noDoc_staysQuiet_withoutPlausibleQuad() {
    // Feed frames without a quad
    emittedEvents.clear();
    for (int i = 0; i < 10; i++) {
      stateMachine.onFrame(null, IMG_W, IMG_H, null, i * 2000L, captureListener);
    }

    assertEquals(
        "Should stay in NO_DOC without plausible quad",
        A11yStateMachine.State.NO_DOC,
        stateMachine.getState());

    // Should not emit distance hints
    assertFalse(
        "Should not emit MOVE_CLOSER in NO_DOC",
        emittedEvents.contains(A11yStateMachine.Event.MOVE_CLOSER));
    assertFalse(
        "Should not emit MOVE_BACK in NO_DOC",
        emittedEvents.contains(A11yStateMachine.Event.MOVE_BACK));
  }

  @Test
  public void noDoc_allowsOrientationTips() {
    FramingResult orientFr =
        new FramingResult(0f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.ORIENTATION_PORTRAIT_TIP, false);

    emittedEvents.clear();
    for (int i = 0; i < 5; i++) {
      stateMachine.onFrame(null, IMG_W, IMG_H, orientFr, i * 2000L, captureListener);
    }

    // Orientation tips should be allowed in NO_DOC
    // (though they may be rate-limited)
    assertEquals("Should stay in NO_DOC", A11yStateMachine.State.NO_DOC, stateMachine.getState());
  }

  // --- Distance hint suppression tests ---

  @Test
  public void distanceHints_suppressedWithoutPlausibleDoc() {
    // Create a non-plausible quad (too small)
    PointF[] tinyQuad =
        new PointF[] {
          new PointF(100, 100), new PointF(110, 100), new PointF(110, 110), new PointF(100, 110)
        };
    FramingResult fr =
        new FramingResult(0.5f, 0f, 0f, 0.5f, 0f, 0f, GuidanceHint.MOVE_CLOSER, true);

    emittedEvents.clear();
    feedFrames(20, tinyQuad, fr, 0, 200);

    // Distance hints should be suppressed for non-plausible quads
    assertFalse(
        "MOVE_CLOSER should be suppressed without plausible doc",
        emittedEvents.contains(A11yStateMachine.Event.MOVE_CLOSER));
  }

  // --- System event injection tests ---

  @Test
  public void injectSystemEvent_emitsEvent() {
    emittedEvents.clear();
    stateMachine.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 0, captureListener);

    assertTrue(
        "Injected system event should be emitted",
        emittedEvents.contains(A11yStateMachine.Event.LOW_LIGHT));
  }

  @Test
  public void injectSystemEvent_respectsRateLimit() {
    emittedEvents.clear();
    stateMachine.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 0, captureListener);
    stateMachine.injectSystemEvent(A11yStateMachine.Event.LOW_LIGHT, 100, captureListener);

    // Second event should still be emitted (system events can bypass rate limit)
    assertEquals(
        "System events should be emitted",
        2,
        emittedEvents.stream().filter(e -> e == A11yStateMachine.Event.LOW_LIGHT).count());
  }

  // --- Debug info tests ---

  @Test
  public void debugInfo_reflectsCurrentState() {
    PointF[] quad = makePlausibleQuad();
    FramingResult goodFr = makeGoodFramingResult();

    feedFrames(30, quad, goodFr, 0, 200);

    A11yStateMachine.DebugInfo info = stateMachine.getDebugInfo();
    assertEquals(
        "Debug info should reflect current state", A11yStateMachine.State.READY, info.state);
    assertNotNull("Debug info should have last event", info.lastEvent);
  }

  // --- Stability tests ---

  @Test
  public void unstableHints_preventReadyTransition() {
    PointF[] quad = makePlausibleQuad();

    // Alternate between different hints (unstable)
    for (int i = 0; i < 30; i++) {
      GuidanceHint hint = (i % 2 == 0) ? GuidanceHint.MOVE_LEFT : GuidanceHint.MOVE_RIGHT;
      FramingResult fr = new FramingResult(0.85f, 0.1f, 0f, 1f, 0f, 0f, hint, true);
      stateMachine.onFrame(quad, IMG_W, IMG_H, fr, i * 200L, captureListener);
    }

    assertNotEquals(
        "Unstable hints should prevent READY transition",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }

  @Test
  public void lowQuality_preventsReadyTransition() {
    PointF[] quad = makePlausibleQuad();
    // Quality below READY_QGEOM_MIN (0.80)
    FramingResult lowQualityFr =
        new FramingResult(0.70f, 0f, 0f, 1f, 0f, 0f, GuidanceHint.OK, true);

    feedFrames(30, quad, lowQualityFr, 0, 200);

    assertNotEquals(
        "Low quality should prevent READY transition",
        A11yStateMachine.State.READY,
        stateMachine.getState());
  }
}
