package de.schliweb.makeacopy.ui.ocr.review.store;

import static org.junit.Assert.*;

import de.schliweb.makeacopy.ui.ocr.review.model.OcrDoc;
import java.io.File;
import org.junit.Test;

/**
 * Unit tests for {@link OcrJsonStore} edge cases. Round-trip tests require real org.json
 * implementation (instrumented tests) since android.jar stubs return defaults.
 */
public class OcrJsonStoreTest {

  @Test
  public void load_nullFile_returnsEmptyDoc() {
    OcrDoc doc = OcrJsonStore.load(null);
    assertNotNull(doc);
    assertTrue(doc.words.isEmpty());
  }

  @Test
  public void load_nonExistentFile_returnsEmptyDoc() {
    OcrDoc doc = OcrJsonStore.load(new File("/nonexistent/path.json"));
    assertNotNull(doc);
    assertTrue(doc.words.isEmpty());
  }

  @Test
  public void save_nullFile_returnsFalse() {
    assertFalse(OcrJsonStore.save(null, new OcrDoc()));
  }

  @Test
  public void save_nullDoc_returnsFalse() {
    assertFalse(OcrJsonStore.save(new File("/tmp/test.json"), null));
  }
}
