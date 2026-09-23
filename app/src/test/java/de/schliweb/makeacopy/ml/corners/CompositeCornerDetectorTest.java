package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.*;

import org.junit.Test;

public class CompositeCornerDetectorTest {

  private static final double[][] QUAD = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private final CornerDetector successDocQuad =
      (src, ctx) -> DetectionResult.success(Source.DOCQUAD, QUAD);
  private final CornerDetector failDocQuad = (src, ctx) -> DetectionResult.fail(Source.DOCQUAD);
  private final CornerDetector successLegacy =
      (src, ctx) -> DetectionResult.success(Source.OPENCV, QUAD);
  private final CornerDetector failLegacy = (src, ctx) -> DetectionResult.fail(Source.OPENCV);
  private final CornerDetector throwingDetector =
      (src, ctx) -> {
        throw new RuntimeException("boom");
      };

  @Test
  public void docQuadSuccess_returnsDocQuadResult() {
    CompositeCornerDetector det = new CompositeCornerDetector(successDocQuad, failLegacy);
    DetectionResult r = det.detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.DOCQUAD, r.source);
  }

  @Test
  public void docQuadFails_fallsBackToLegacy() {
    CompositeCornerDetector det = new CompositeCornerDetector(failDocQuad, successLegacy);
    DetectionResult r = det.detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.OPENCV, r.source);
  }

  @Test
  public void bothFail_returnsFallback() {
    CompositeCornerDetector det = new CompositeCornerDetector(failDocQuad, failLegacy);
    DetectionResult r = det.detect(null, null);
    assertFalse(r.success);
    assertEquals(Source.FALLBACK, r.source);
  }

  @Test
  public void docQuadThrows_fallsBackToLegacy() {
    CompositeCornerDetector det = new CompositeCornerDetector(throwingDetector, successLegacy);
    DetectionResult r = det.detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.OPENCV, r.source);
  }

  @Test
  public void bothThrow_returnsFallback() {
    CompositeCornerDetector det = new CompositeCornerDetector(throwingDetector, throwingDetector);
    DetectionResult r = det.detect(null, null);
    assertFalse(r.success);
    assertEquals(Source.FALLBACK, r.source);
  }

  @Test
  public void docQuadReturnsNull_fallsBackToLegacy() {
    CornerDetector nullDetector = (src, ctx) -> null;
    CompositeCornerDetector det = new CompositeCornerDetector(nullDetector, successLegacy);
    DetectionResult r = det.detect(null, null);
    assertTrue(r.success);
    assertEquals(Source.OPENCV, r.source);
  }

  @Test
  public void bothReturnNull_returnsFallback() {
    CornerDetector nullDetector = (src, ctx) -> null;
    CompositeCornerDetector det = new CompositeCornerDetector(nullDetector, nullDetector);
    DetectionResult r = det.detect(null, null);
    assertFalse(r.success);
    assertEquals(Source.FALLBACK, r.source);
  }
}
