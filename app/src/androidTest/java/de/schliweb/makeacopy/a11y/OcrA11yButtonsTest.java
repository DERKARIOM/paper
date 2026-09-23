package de.schliweb.makeacopy.a11y;

import androidx.lifecycle.Lifecycle;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.ViewAssertionsEx;
import de.schliweb.makeacopy.ui.ocr.OCRFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OcrA11yButtonsTest {

  @Test
  public void ocr_buttons_haveContentDescription_andMinTouchTarget() {
    HiltFragmentScenario.launchInHiltContainer(
        OCRFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    int[] ids = new int[] {R.id.button_ocr_options, R.id.button_ocr_review};
    for (int id : ids) {
      final boolean[] visible = {true};
      // Check visibility state on main without running Espresso there
      HiltFragmentScenario<OCRFragment> scenario =
          HiltFragmentScenario.launchInHiltContainer(
              OCRFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);
      scenario.onFragment(
          fragment -> {
            android.view.View v = fragment.requireView().findViewById(id);
            if (v == null || v.getVisibility() != android.view.View.VISIBLE) {
              visible[0] = false;
            }
          });
      if (!visible[0]) {
        // Skip this button if not visible in this variant
        continue;
      }
      Espresso.onView(ViewMatchers.withId(id))
          .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
          .check(ViewAssertionsEx.minTouchTargetAndContentDescription(48));
    }
  }
}
