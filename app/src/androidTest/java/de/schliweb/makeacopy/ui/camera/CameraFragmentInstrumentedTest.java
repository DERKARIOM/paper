package de.schliweb.makeacopy.ui.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import androidx.camera.core.ImageCapture;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CameraFragmentInstrumentedTest {

  @Rule public GrantPermissionRule camPerm = GrantPermissionRule.grant(Manifest.permission.CAMERA);

  @Test
  public void imageCapture_isMaximizeQuality_afterBinding() {
    HiltFragmentScenario<CameraFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            CameraFragment.class,
            /* args= */ null,
            R.style.Theme_MakeACopy,
            Lifecycle.State.RESUMED);

    AtomicReference<ImageCapture> ref = new AtomicReference<>();

    // Wait until ImageCapture has been set (max 5s, polling every 100ms)
    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              scenario.onFragment(f -> ref.set(f.getImageCaptureForTest()));
              return ref.get() != null;
            });

    ImageCapture ic = ref.get();
    assertNotNull("ImageCapture should be initialized", ic);
    assertEquals(
        "Capture mode must be MAXIMIZE_QUALITY",
        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY,
        ic.getCaptureMode());
  }
}
