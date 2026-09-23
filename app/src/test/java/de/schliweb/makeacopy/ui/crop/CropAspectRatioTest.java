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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** JVM unit tests for {@link CropAspectRatio}. See docs/aspect_ratio_concept_v3.8.0.md (§8.1). */
public class CropAspectRatioTest {

  private static final double EPS = 1e-6;

  @Test
  public void shortOverLong_dinASeries_isOneOverSqrt2() {
    double expected = 1.0 / Math.sqrt(2.0);
    Double a3 = CropAspectRatio.A3.shortOverLong();
    Double a4 = CropAspectRatio.A4.shortOverLong();
    Double a5 = CropAspectRatio.A5.shortOverLong();
    assertNotNull(a3);
    assertNotNull(a4);
    assertNotNull(a5);
    assertEquals(expected, a3, EPS);
    assertEquals(expected, a4, EPS);
    assertEquals(expected, a5, EPS);
    // All three DIN-A entries must yield the exact same numeric ratio.
    assertEquals(a4, a3, 0.0);
    assertEquals(a4, a5, 0.0);
  }

  @Test
  public void shortOverLong_letterAndLegal() {
    assertEquals(8.5 / 11.0, CropAspectRatio.US_LETTER.shortOverLong(), EPS);
    assertEquals(8.5 / 14.0, CropAspectRatio.LEGAL.shortOverLong(), EPS);
  }

  @Test
  public void shortOverLong_autoOriginalCustom_returnNull() {
    assertNull(CropAspectRatio.AUTO.shortOverLong());
    assertNull(CropAspectRatio.ORIGINAL.shortOverLong());
    assertNull(CropAspectRatio.CUSTOM.shortOverLong());
  }

  @Test
  public void fromName_validNamesAreParsed() {
    for (CropAspectRatio v : CropAspectRatio.values()) {
      assertSame(v, CropAspectRatio.fromName(v.name(), CropAspectRatio.AUTO));
    }
  }

  @Test
  public void fromName_nullOrUnknown_returnsDefault() {
    assertSame(CropAspectRatio.AUTO, CropAspectRatio.fromName(null, CropAspectRatio.AUTO));
    assertSame(CropAspectRatio.AUTO, CropAspectRatio.fromName("garbage", CropAspectRatio.AUTO));
    assertSame(CropAspectRatio.A4, CropAspectRatio.fromName("garbage", CropAspectRatio.A4));
    // valueOf is case sensitive — lower-case names must not match
    assertSame(CropAspectRatio.AUTO, CropAspectRatio.fromName("a4", CropAspectRatio.AUTO));
  }

  @Test
  public void shortOverLong_isWithinUnitInterval() {
    for (CropAspectRatio v : CropAspectRatio.values()) {
      Double r = v.shortOverLong();
      if (r != null) {
        org.junit.Assert.assertTrue("ratio out of range for " + v + ": " + r, r > 0.0 && r <= 1.0);
      }
    }
  }
}
