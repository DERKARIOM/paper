/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DocumentCleanupProcessorTest {

  @Test
  public void originalOptionsAreConservativeByDefault() {
    DocumentCleanupOptions options = new DocumentCleanupOptions();

    assertEquals(DocumentCleanupMode.ORIGINAL, options.mode);
    assertEquals(DocumentCleanupOptions.STRENGTH_MEDIUM, options.strength);
    assertTrue(options.preserveColor);
    assertFalse(options.optimizeForOcr);
    assertEquals(0, options.targetLongEdgePx);
  }

  @Test
  public void cleanupPresetsAreExplicitAndIndependentFromExportFilters() {
    assertEquals(DocumentCleanupMode.NATURAL, DocumentCleanupOptions.natural().mode);
    assertEquals(DocumentCleanupMode.ENHANCED, DocumentCleanupOptions.enhanced(false).mode);
    assertFalse(DocumentCleanupOptions.enhanced(false).preserveColor);
    assertEquals(DocumentCleanupMode.ENHANCED, DocumentCleanupOptions.enhanced(true).mode);
    assertTrue(DocumentCleanupOptions.enhanced(true).preserveColor);
  }

  @Test
  public void cleanTextPresetOptimizesForOcr() {
    DocumentCleanupOptions options = DocumentCleanupOptions.cleanText();

    assertEquals(DocumentCleanupMode.CLEAN_TEXT, options.mode);
    assertEquals(DocumentCleanupOptions.STRENGTH_HIGH, options.strength);
    assertFalse(options.preserveColor);
    assertTrue(options.optimizeForOcr);
  }

  @Test
  public void explicitOptionsClampStrengthAndTargetSize() {
    DocumentCleanupOptions options =
        new DocumentCleanupOptions(DocumentCleanupMode.ENHANCED, 10, true, false, -1);

    assertEquals(DocumentCleanupMode.ENHANCED, options.mode);
    assertEquals(DocumentCleanupOptions.STRENGTH_HIGH, options.strength);
    assertEquals(0, options.targetLongEdgePx);
  }
}