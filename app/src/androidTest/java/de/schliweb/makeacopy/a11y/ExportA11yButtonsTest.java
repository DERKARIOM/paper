package de.schliweb.makeacopy.a11y;

import androidx.lifecycle.Lifecycle;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.ViewAssertionsEx;
import de.schliweb.makeacopy.ui.export.ExportFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExportA11yButtonsTest {

  @Test
  public void export_icon_buttons_haveContentDescription_andMinTouchTarget() {
    HiltFragmentScenario.launchInHiltContainer(
        ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    int[] ids =
        new int[] {
          R.id.button_options,
          R.id.button_add_page,
          R.id.button_library_actions,
          R.id.button_clear_pages,
          R.id.button_share_small
        };
    for (int id : ids) {
      Espresso.onView(ViewMatchers.withId(id))
          .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
          .check(ViewAssertionsEx.minTouchTargetAndContentDescription(48));
    }
  }
}
