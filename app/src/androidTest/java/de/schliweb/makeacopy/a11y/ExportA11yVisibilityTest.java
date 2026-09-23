package de.schliweb.makeacopy.a11y;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.ui.export.ExportFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExportA11yVisibilityTest {

  @Test
  public void pages_container_gone_for_single_page_and_visible_for_two_pages() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    // Seed single page
    scenario.onFragment(
        fragment -> {
          de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel vm =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity())
                  .get(de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel.class);
          Bitmap first = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888);
          de.schliweb.makeacopy.ui.export.session.CompletedScan one =
              new de.schliweb.makeacopy.ui.export.session.CompletedScan(
                  java.util.UUID.randomUUID().toString(),
                  null,
                  0,
                  null,
                  null,
                  null,
                  System.currentTimeMillis(),
                  first.getWidth(),
                  first.getHeight(),
                  first,
                  1,
                  "metadata");
          vm.setInitial(one);

          View container = fragment.requireView().findViewById(R.id.pages_container);
          assertNotNull(container);
          assertEquals(
              "With 1 page, container should be GONE", View.GONE, container.getVisibility());
        });

    // Add second page
    scenario.onFragment(
        fragment -> {
          de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel vm =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity())
                  .get(de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel.class);
          Bitmap second = Bitmap.createBitmap(140, 180, Bitmap.Config.ARGB_8888);
          de.schliweb.makeacopy.ui.export.session.CompletedScan two =
              new de.schliweb.makeacopy.ui.export.session.CompletedScan(
                  java.util.UUID.randomUUID().toString(),
                  null,
                  0,
                  null,
                  null,
                  null,
                  System.currentTimeMillis(),
                  second.getWidth(),
                  second.getHeight(),
                  second,
                  1,
                  "metadata");
          vm.add(two);

          // Nudge binding
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          if (rv != null) rv.scrollToPosition(0);
        });

    // Assert visible for two pages
    scenario.onFragment(
        fragment -> {
          View container = fragment.requireView().findViewById(R.id.pages_container);
          assertNotNull(container);
          assertEquals(
              "With ≥2 pages, container should be VISIBLE",
              View.VISIBLE,
              container.getVisibility());
          RecyclerView.Adapter<?> ad =
              ((RecyclerView) fragment.requireView().findViewById(R.id.pages_recycler))
                  .getAdapter();
          assertNotNull(ad);
          assertTrue("Adapter should have at least 2 items", ad.getItemCount() >= 2);
        });
  }
}
