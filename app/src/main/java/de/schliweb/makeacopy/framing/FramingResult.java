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

import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Represents the result of a framing or alignment process. This class provides detailed information
 * about the framing status, including quality, positional adjustments, scale ratio, tilt angles,
 * guidance hints, and the presence of a document.
 */
@AllArgsConstructor
@ToString
public class FramingResult {
  public final float quality;
  public final float dxNorm;
  public final float dyNorm;
  public final float scaleRatio;
  public final float tiltHorizontal;
  public final float tiltVertical;
  public final GuidanceHint hint;
  public final boolean hasDocument;
}
