package de.schliweb.makeacopy.a11y;

import static org.junit.Assert.*;

import android.os.Build;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.ui.camera.CameraFragment;
import de.schliweb.makeacopy.ui.export.ExportFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeadingsAndLiveRegionsTest {

  @Test
  public void camera_heading_and_liveRegion_and_preview_not_accessible() {
    HiltFragmentScenario<CameraFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            CameraFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    scenario.onFragment(
        fragment -> {
          View root = fragment.requireView();
          TextView heading = root.findViewById(R.id.text_camera);
          assertNotNull(heading);
          // accessibilityHeading attribute is available API 28+; below falls back to true if set in
          // XML
          if (Build.VERSION.SDK_INT >= 28) {
            assertTrue("text_camera should be marked as heading", heading.isAccessibilityHeading());
          }
          // Live region polite
          assertEquals(
              "Live region should be POLITE",
              View.ACCESSIBILITY_LIVE_REGION_POLITE,
              heading.getAccessibilityLiveRegion());

          // Preview not accessible
          View preview = root.findViewById(R.id.view_finder);
          assertNotNull(preview);
          assertFalse(preview.isFocusable());
          assertFalse(preview.isClickable());
          int importance = preview.getImportantForAccessibility();
          assertTrue(
              importance == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                  || importance == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        });
  }

  @Test
  public void export_heading_and_liveRegion_present() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    scenario.onFragment(
        fragment -> {
          TextView heading = fragment.requireView().findViewById(R.id.text_export);
          assertNotNull(heading);
          if (Build.VERSION.SDK_INT >= 28) {
            assertTrue("text_export should be marked as heading", heading.isAccessibilityHeading());
          }
          assertEquals(
              "Live region should be POLITE",
              View.ACCESSIBILITY_LIVE_REGION_POLITE,
              heading.getAccessibilityLiveRegion());
        });
  }
}
