package de.schliweb.makeacopy.ml.docquad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DocQuadOrtRunnerOutputsTest {

  @Test
  public void constructor_setsFields() {
    float[][][][] mask = new float[1][1][64][64];
    float[][][][] corners = new float[1][4][64][64];
    DocQuadOrtRunner.Outputs out = new DocQuadOrtRunner.Outputs(mask, corners);
    assertSame(mask, out.maskLogits());
    assertSame(corners, out.cornerHeatmaps());
  }

  @Test
  public void constructor_nullFields_allowed() {
    DocQuadOrtRunner.Outputs out = new DocQuadOrtRunner.Outputs(null, null);
    assertNull(out.maskLogits());
    assertNull(out.cornerHeatmaps());
  }

  @Test
  public void record_equality() {
    float[][][][] mask = new float[1][1][64][64];
    float[][][][] corners = new float[1][4][64][64];
    DocQuadOrtRunner.Outputs a = new DocQuadOrtRunner.Outputs(mask, corners);
    DocQuadOrtRunner.Outputs b = new DocQuadOrtRunner.Outputs(mask, corners);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void constants_correctValues() {
    assertEquals(256, DocQuadOrtRunner.IN_H);
    assertEquals(256, DocQuadOrtRunner.IN_W);
    assertEquals(64, DocQuadOrtRunner.OUT_H);
    assertEquals(64, DocQuadOrtRunner.OUT_W);
  }
}
