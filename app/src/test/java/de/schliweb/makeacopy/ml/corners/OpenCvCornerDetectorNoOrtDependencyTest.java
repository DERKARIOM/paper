package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;

public class OpenCvCornerDetectorNoOrtDependencyTest {

  @Test
  public void openCvCornerDetector_hasNoOrtTypesInSignatures() {
    assertNoOrtTypes(OpenCvCornerDetector.class);
    assertNoOrtTypes(LegacyCornerDetector.class);
  }

  private static void assertNoOrtTypes(Class<?> c) {
    for (Constructor<?> ctor : c.getDeclaredConstructors()) {
      for (Class<?> p : ctor.getParameterTypes()) {
        assertFalse("ORT type in ctor signature: " + p.getName(), isOrtType(p));
      }
    }
    for (Method m : c.getDeclaredMethods()) {
      assertFalse("ORT type in return: " + m, isOrtType(m.getReturnType()));
      for (Class<?> p : m.getParameterTypes()) {
        assertFalse("ORT type in method signature: " + m + ": " + p.getName(), isOrtType(p));
      }
    }
    for (Field f : c.getDeclaredFields()) {
      assertFalse("ORT type in field: " + f, isOrtType(f.getType()));
    }
  }

  private static boolean isOrtType(Class<?> t) {
    return t != null && t.getName().startsWith("ai.onnxruntime.");
  }
}
