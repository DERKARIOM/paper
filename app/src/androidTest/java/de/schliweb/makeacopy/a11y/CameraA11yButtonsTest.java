package de.schliweb.makeacopy.a11y;

import android.Manifest;
import androidx.lifecycle.Lifecycle;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.ViewAssertionsEx;
import de.schliweb.makeacopy.ui.camera.CameraFragment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CameraA11yButtonsTest {

  @Rule public GrantPermissionRule camPerm = GrantPermissionRule.grant(Manifest.permission.CAMERA);

  @Test
  public void options_button_hasContentDescription_andMinTouchTarget() {
    HiltFragmentScenario.launchInHiltContainer(
        CameraFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    Espresso.onView(ViewMatchers.withId(R.id.button_camera_options))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        .check(ViewAssertionsEx.minTouchTargetAndContentDescription(48));
  }
}
