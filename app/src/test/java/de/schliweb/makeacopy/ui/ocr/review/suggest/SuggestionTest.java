package de.schliweb.makeacopy.ui.ocr.review.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SuggestionTest {

  @Test
  public void constructor_setsAllFields() {
    DictionarySuggestProvider.Suggestion s =
        new DictionarySuggestProvider.Suggestion("hello", 0.95, "eng");
    assertEquals("hello", s.text());
    assertEquals(0.95, s.score(), 1e-9);
    assertEquals("eng", s.language());
  }

  @Test
  public void score_zeroAndOne() {
    DictionarySuggestProvider.Suggestion zero =
        new DictionarySuggestProvider.Suggestion("a", 0.0, "eng");
    assertEquals(0.0, zero.score(), 1e-9);

    DictionarySuggestProvider.Suggestion one =
        new DictionarySuggestProvider.Suggestion("b", 1.0, "deu");
    assertEquals(1.0, one.score(), 1e-9);
  }

  @Test
  public void toString_containsTextAndPercentage() {
    DictionarySuggestProvider.Suggestion s =
        new DictionarySuggestProvider.Suggestion("Straße", 0.85, "deu");
    String str = s.toString();
    assertNotNull(str);
    assertTrue(str.contains("Straße"));
    assertTrue(str.contains("85%"));
  }

  @Test
  public void toString_zeroScore_shows0Percent() {
    DictionarySuggestProvider.Suggestion s =
        new DictionarySuggestProvider.Suggestion("x", 0.0, "eng");
    assertTrue(s.toString().contains("0%"));
  }

  @Test
  public void toString_fullScore_shows100Percent() {
    DictionarySuggestProvider.Suggestion s =
        new DictionarySuggestProvider.Suggestion("perfect", 1.0, "eng");
    assertTrue(s.toString().contains("100%"));
  }
}
