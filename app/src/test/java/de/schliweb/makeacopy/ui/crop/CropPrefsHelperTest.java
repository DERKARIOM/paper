/*
 * Copyright (c) 2024-2025 Christian Kierdorf
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

/**
 * JVM unit tests for the validation logic of {@link CropPrefsHelper}. The {@code Context}-bound
 * roundtrips are covered by instrumented tests; here we exercise the pure, side-effect-free
 * helpers. See docs/aspect_ratio_concept_v3.8.0.md (§8.1).
 */
public class CropPrefsHelperTest {

  @Test
  public void isValidCustom_acceptsCommonRatios() {
    // Symmetric and asymmetric, within 1:1..1:20.
    assertTrue(CropPrefsHelper.isValidCustom(1.0, 1.0));
    assertTrue(CropPrefsHelper.isValidCustom(2.0, 3.0));
    assertTrue(CropPrefsHelper.isValidCustom(3.0, 2.0));
    assertTrue(CropPrefsHelper.isValidCustom(8.5, 11.0));
    assertTrue(CropPrefsHelper.isValidCustom(210.0, 297.0));
    // Boundary at 1:20 must be inclusive.
    assertTrue(CropPrefsHelper.isValidCustom(1.0, 20.0));
    assertTrue(CropPrefsHelper.isValidCustom(20.0, 1.0));
  }

  @Test
  public void isValidCustom_rejectsZeroOrNegative() {
    assertFalse(CropPrefsHelper.isValidCustom(0.0, 1.0));
    assertFalse(CropPrefsHelper.isValidCustom(1.0, 0.0));
    assertFalse(CropPrefsHelper.isValidCustom(-1.0, 2.0));
    assertFalse(CropPrefsHelper.isValidCustom(2.0, -1.0));
  }

  @Test
  public void isValidCustom_rejectsExtremeRatios() {
    // Beyond 1:20 must be rejected.
    assertFalse(CropPrefsHelper.isValidCustom(1.0, 21.0));
    assertFalse(CropPrefsHelper.isValidCustom(21.0, 1.0));
    assertFalse(CropPrefsHelper.isValidCustom(1.0, 100.0));
  }

  @Test
  public void isValidCustom_rejectsNonFiniteValues() {
    assertFalse(CropPrefsHelper.isValidCustom(Double.NaN, 1.0));
    assertFalse(CropPrefsHelper.isValidCustom(1.0, Double.NaN));
    assertFalse(CropPrefsHelper.isValidCustom(Double.POSITIVE_INFINITY, 1.0));
    assertFalse(CropPrefsHelper.isValidCustom(1.0, Double.POSITIVE_INFINITY));
    assertFalse(CropPrefsHelper.isValidCustom(Double.NEGATIVE_INFINITY, 1.0));
  }
}
