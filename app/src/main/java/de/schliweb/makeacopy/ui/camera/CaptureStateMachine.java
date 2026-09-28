/*
 * Copyright 2026 The Paper authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * State of one shutter press on the scan screen.
 *
 * <pre>
 * READY ─► CAPTURING ─► PROCESSING ─► COMPLETED
 *              │             │
 *              └─────► ERROR ◄┘ ─► READY
 * </pre>
 *
 * <ul>
 *   <li>CAPTURING: the shutter was pressed and {@code takePicture} has been requested; the sensor
 *       exposure for this press has not been reported yet.
 *   <li>PROCESSING: the frame is exposed (it no longer depends on what the camera sees); the JPEG
 *       is being encoded and written.
 *   <li>COMPLETED: the file is saved and the flow moves on to the crop step.
 * </ul>
 *
 * <p>Only a press in READY starts a capture, so double taps and the volume-key shutter cannot
 * trigger a second capture. Main-thread only (all CameraX callbacks of the scan screen run on the
 * main executor).
 */
public final class CaptureStateMachine {

  public enum State {
    READY,
    CAPTURING,
    PROCESSING,
    COMPLETED,
    ERROR
  }

  /** Receives every state change (old state, new state). */
  public interface Listener {
    void onStateChanged(@NonNull State from, @NonNull State to);
  }

  @NonNull private State state = State.READY;
  @Nullable private Listener listener;

  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  @NonNull
  public State getState() {
    return state;
  }

  /** True while a capture is running: the shutter and incompatible actions must stay disabled. */
  public boolean isBusy() {
    return state == State.CAPTURING || state == State.PROCESSING || state == State.COMPLETED;
  }

  /**
   * Starts a capture. Returns {@code false} (nothing changes) unless the machine is READY, so the
   * caller must not call {@code takePicture} in that case.
   */
  public boolean tryStartCapture() {
    if (state != State.READY) return false;
    moveTo(State.CAPTURING);
    return true;
  }

  /** The sensor has exposed the frame of the current capture. */
  public void onExposureStarted() {
    if (state == State.CAPTURING) moveTo(State.PROCESSING);
  }

  /** The captured image is saved. Accepted from CAPTURING too, in case no exposure event came. */
  public void onCompleted() {
    if (state == State.CAPTURING || state == State.PROCESSING) moveTo(State.COMPLETED);
  }

  /** The capture failed. Ignored when no capture is running (late callback). */
  public void onError() {
    if (state == State.CAPTURING || state == State.PROCESSING) moveTo(State.ERROR);
  }

  /** Back to READY after an error has been shown, or when the scan screen is (re)displayed. */
  public void reset() {
    if (state != State.READY) moveTo(State.READY);
  }

  private void moveTo(@NonNull State next) {
    State prev = state;
    state = next;
    if (listener != null) listener.onStateChanged(prev, next);
  }
}
