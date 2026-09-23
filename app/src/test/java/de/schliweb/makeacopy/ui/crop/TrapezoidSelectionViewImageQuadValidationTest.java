/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.crop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opencv.core.Point;

public class TrapezoidSelectionViewImageQuadValidationTest {

  @Test
  public void validDocumentQuad_isAccepted() {
    Point[] quad =
        new Point[] {
          new Point(100, 120), new Point(900, 100), new Point(880, 920), new Point(120, 900)
        };

    assertTrue(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void nonFinitePoint_isRejected() {
    Point[] quad =
        new Point[] {
          new Point(100, 100),
          new Point(Double.NaN, 100),
          new Point(900, 900),
          new Point(100, 900)
        };

    assertFalse(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void outsideImagePoint_isRejected() {
    Point[] quad =
        new Point[] {
          new Point(100, 100), new Point(1100, 100), new Point(900, 900), new Point(100, 900)
        };

    assertFalse(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void tooSmallArea_isRejected() {
    Point[] quad =
        new Point[] {
          new Point(100, 100), new Point(150, 100), new Point(150, 150), new Point(100, 150)
        };

    assertFalse(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void selfIntersectingBowTie_isRejected() {
    Point[] quad =
        new Point[] {
          new Point(100, 100), new Point(900, 900), new Point(900, 100), new Point(100, 900)
        };

    assertFalse(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void concaveQuad_isRejected() {
    Point[] quad =
        new Point[] {
          new Point(100, 100), new Point(900, 100), new Point(500, 500), new Point(100, 900)
        };

    assertFalse(TrapezoidSelectionView.isValidImageQuad(quad, 1000, 1000));
  }

  @Test
  public void scoreImageQuad_prefersPlausibleDocumentOverTinyValidQuad() {
    Point[] plausible =
        new Point[] {
          new Point(100, 120), new Point(900, 100), new Point(880, 920), new Point(120, 900)
        };
    Point[] tinyButValid =
        new Point[] {
          new Point(100, 100), new Point(260, 100), new Point(260, 260), new Point(100, 260)
        };

    assertTrue(
        TrapezoidSelectionView.scoreImageQuad(plausible, 1000, 1000)
            > TrapezoidSelectionView.scoreImageQuad(tinyButValid, 1000, 1000));
  }

  @Test
  public void scoreImageQuad_prefersBalancedQuadOverStronglySkewedQuad() {
    Point[] balanced =
        new Point[] {
          new Point(100, 120), new Point(900, 100), new Point(880, 920), new Point(120, 900)
        };
    Point[] skewed =
        new Point[] {
          new Point(80, 100), new Point(940, 120), new Point(650, 900), new Point(260, 880)
        };

    assertTrue(
        TrapezoidSelectionView.scoreImageQuad(balanced, 1000, 1000)
            > TrapezoidSelectionView.scoreImageQuad(skewed, 1000, 1000));
  }
}