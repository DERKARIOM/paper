package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.*;

import org.junit.Test;

/** Unit tests for the {@link Source} enum. */
public class SourceTest {

  @Test
  public void allValues_present() {
    Source[] values = Source.values();
    assertEquals(3, values.length);
    assertEquals(Source.DOCQUAD, Source.valueOf("DOCQUAD"));
    assertEquals(Source.OPENCV, Source.valueOf("OPENCV"));
    assertEquals(Source.FALLBACK, Source.valueOf("FALLBACK"));
  }

  @Test
  public void values_order() {
    Source[] values = Source.values();
    assertEquals(Source.DOCQUAD, values[0]);
    assertEquals(Source.OPENCV, values[1]);
    assertEquals(Source.FALLBACK, values[2]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void valueOf_invalid_throws() {
    Source.valueOf("INVALID");
  }
}
