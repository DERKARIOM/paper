/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.framing;

/**
 * Enumeration representing various guidance hints. This enum defines a set of directional or
 * positional guidance hints that can be used to indicate desired movements or adjustments.
 *
 * <p>The hints include cardinal direction movements, proximity adjustments, and tilting motions to
 * guide an entity to a specific state or position.
 */
public enum GuidanceHint {
  OK,
  MOVE_LEFT,
  MOVE_RIGHT,
  MOVE_UP,
  MOVE_DOWN,
  MOVE_CLOSER,
  MOVE_BACK,
  TILT_LEFT,
  TILT_RIGHT,
  TILT_FORWARD,
  TILT_BACK,
  NO_DOCUMENT_DETECTED,
  ORIENTATION_PORTRAIT_TIP,
  ORIENTATION_LANDSCAPE_TIP,
  /** Indicates the user should hold the device still for stability */
  HOLD_STILL,
  /** Indicates the document is detected and ready to capture */
  READY_ENTER,
  /** Indicates the document is too far away (detected but too small in frame) */
  TOO_FAR
}
