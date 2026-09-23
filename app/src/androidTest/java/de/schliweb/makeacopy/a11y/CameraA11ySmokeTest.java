package de.schliweb.makeacopy.a11y;

import static org.junit.Assert.*;

import android.Manifest;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.ui.camera.CameraFragment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CameraA11ySmokeTest {

  @Rule public GrantPermissionRule camPerm = GrantPermissionRule.grant(Manifest.permission.CAMERA);

  @Test
  public void previewView_isNotFocusable_andNotImportantForA11y() {
    HiltFragmentScenario<CameraFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            CameraFragment.class,
            /* args= */ null,
            R.style.Theme_MakeACopy,
            Lifecycle.State.RESUMED);

    scenario.onFragment(
        fragment -> {
          View preview = fragment.requireView().findViewById(R.id.view_finder);
          assertNotNull(preview);
          assertFalse("PreviewView must not be focusable", preview.isFocusable());
          assertFalse("PreviewView must not be clickable", preview.isClickable());
          int importance = preview.getImportantForAccessibility();
          assertTrue(
              "PreviewView should be NOT important for accessibility",
              importance == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                  || importance == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        });
  }
}
