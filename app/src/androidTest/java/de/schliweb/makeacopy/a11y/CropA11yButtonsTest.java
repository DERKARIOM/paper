package de.schliweb.makeacopy.a11y;

import androidx.lifecycle.Lifecycle;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.ViewAssertionsEx;
import de.schliweb.makeacopy.ui.crop.CropFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CropA11yButtonsTest {

  @Test
  public void rotate_buttons_haveContentDescription_andMinTouchTarget() {
    HiltFragmentScenario.launchInHiltContainer(
        CropFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    int[] ids = new int[] {R.id.button_rotate_left, R.id.button_rotate_right};
    for (int id : ids) {
      Espresso.onView(ViewMatchers.withId(id))
          .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
          .check(ViewAssertionsEx.minTouchTargetAndContentDescription(48));
    }
  }
}
