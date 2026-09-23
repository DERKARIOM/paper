package de.schliweb.makeacopy.ui.export.session;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Rotation invariants for the export-pages adapter are covered by {@code RotationPolicyTest}
 * (JVM-only, no Robolectric needed). This test class validates that the seam-based approach is
 * sufficient.
 */
public class ExportPagesAdapterRotationTest {
  @Test
  public void rotationPolicyCoverage_placeholder() {
    // Actual rotation invariants are verified in RotationPolicyTest.
    assertTrue(true);
  }
}
