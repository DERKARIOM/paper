package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.ui.camera.CaptureStateMachine.State;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CaptureStateMachineTest {

  private CaptureStateMachine sm;
  private List<String> transitions;

  @Before
  public void setUp() {
    sm = new CaptureStateMachine();
    transitions = new ArrayList<>();
    sm.setListener((from, to) -> transitions.add(from + ">" + to));
  }

  @Test
  public void normalCapture() {
    assertEquals(State.READY, sm.getState());
    assertFalse(sm.isBusy());
    assertTrue(sm.tryStartCapture());
    assertTrue(sm.isBusy());
    sm.onExposureStarted();
    assertEquals(State.PROCESSING, sm.getState());
    sm.onCompleted();
    assertEquals(State.COMPLETED, sm.getState());
    assertTrue(sm.isBusy());
    assertEquals(
        List.of("READY>CAPTURING", "CAPTURING>PROCESSING", "PROCESSING>COMPLETED"), transitions);
  }

  @Test
  public void doubleTapStartsOnlyOneCapture() {
    assertTrue(sm.tryStartCapture());
    assertFalse(sm.tryStartCapture());
    sm.onExposureStarted();
    assertFalse(sm.tryStartCapture());
    sm.onCompleted();
    assertFalse(sm.tryStartCapture());
    assertEquals(3, transitions.size());
  }

  @Test
  public void errorThenReadyAgain() {
    sm.tryStartCapture();
    sm.onExposureStarted();
    sm.onError();
    assertEquals(State.ERROR, sm.getState());
    assertFalse(sm.isBusy());
    assertFalse(sm.tryStartCapture()); // must be reset first
    sm.reset();
    assertEquals(State.READY, sm.getState());
    assertTrue(sm.tryStartCapture());
  }

  @Test
  public void errorBeforeExposureAndCompletionWithoutExposureEvent() {
    sm.tryStartCapture();
    sm.onError();
    assertEquals(State.ERROR, sm.getState());
    sm.reset();
    sm.tryStartCapture();
    sm.onCompleted(); // device without onCaptureStarted
    assertEquals(State.COMPLETED, sm.getState());
  }

  @Test
  public void lateCallbacksAreIgnored() {
    sm.onExposureStarted();
    sm.onCompleted();
    sm.onError();
    assertEquals(State.READY, sm.getState());
    assertTrue(transitions.isEmpty());
    sm.tryStartCapture();
    sm.onCompleted();
    sm.onError(); // after completion: ignored
    assertEquals(State.COMPLETED, sm.getState());
    sm.reset();
    sm.reset(); // no-op
    assertEquals(State.READY, sm.getState());
  }
}
