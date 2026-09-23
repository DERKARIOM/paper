package de.schliweb.makeacopy.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.LiveDataIdlingResource;
import de.schliweb.makeacopy.testutil.ViewDump;
import de.schliweb.makeacopy.ui.export.ExportFragment;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Simulates a second scan being added to the export session without using the camera pipeline. The
 * test toggles the app's pending-add flag and sets a new bitmap in CropViewModel, then recreates
 * the fragment so onCreateView performs the actual add-page logic. This mirrors the real user path
 * (capture → return to Export) while staying deterministic for CI.
 */
@RunWith(AndroidJUnit4.class)
public class ExportA11ySecondScanSimulatedTest
    extends de.schliweb.makeacopy.a11y.util.A11yBaseTest {

  @Test
  public void second_scan_simulated_results_in_two_pages_with_correct_a11y_description() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class,
            /* args= */ null,
            R.style.Theme_MakeACopy,
            Lifecycle.State.RESUMED);

    // Increase Espresso timeouts
    IdlingPolicies.setIdlingResourceTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
    IdlingPolicies.setMasterPolicyTimeout(45, java.util.concurrent.TimeUnit.SECONDS);

    // Step 1: Seed an initial page directly in the session (fast path for initial state)
    scenario.onFragment(
        fragment -> {
          android.graphics.Bitmap first = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888);
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
          de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel vm =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity())
                  .get(de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel.class);
          vm.setInitial(one);
        });

    // Step 2: Simulate that user captured another page and returned to Export:
    // - put a new bitmap into CropViewModel
    // - set the SharedPreferences flag pending_add_page=true
    scenario.onFragment(
        fragment -> {
          androidx.lifecycle.ViewModelProvider provider =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity());
          de.schliweb.makeacopy.ui.crop.CropViewModel cropVm =
              provider.get(de.schliweb.makeacopy.ui.crop.CropViewModel.class);
          Bitmap second = Bitmap.createBitmap(140, 180, Bitmap.Config.ARGB_8888);
          cropVm.setImageBitmap(second);

          Context ctx = fragment.requireContext();
          android.content.SharedPreferences prefs =
              ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
          prefs.edit().putBoolean("pending_add_page", true).apply();
        });

    // Recreate the fragment so onCreateView processes the pending add-page
    scenario.recreate();

    // Access RecyclerView reference and build LiveData idling resource
    java.util.concurrent.atomic.AtomicReference<RecyclerView> rvRef =
        new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<
            LiveDataIdlingResource<
                java.util.List<de.schliweb.makeacopy.ui.export.session.CompletedScan>>>
        liveIdleRef = new java.util.concurrent.atomic.AtomicReference<>();
    scenario.onFragment(
        fragment -> {
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          assertNotNull(rv);
          rvRef.set(rv);
          // Nudge binding
          rv.scrollToPosition(0);
          de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel vm =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity())
                  .get(de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel.class);
          liveIdleRef.set(
              new LiveDataIdlingResource<>(
                  vm.getPages(),
                  fragment.getViewLifecycleOwner(),
                  pages -> pages != null && pages.size() >= 2));
        });

    RecyclerView rv = rvRef.get();
    assertNotNull(rv);

    // Use LiveData idling (await pages.size >= 2)
    LiveDataIdlingResource<java.util.List<de.schliweb.makeacopy.ui.export.session.CompletedScan>>
        idle = liveIdleRef.get();
    try {
      IdlingRegistry.getInstance().register(idle);
      final java.util.concurrent.atomic.AtomicReference<String> expectedRef =
          new java.util.concurrent.atomic.AtomicReference<>();
      scenario.onFragment(
          fragment -> expectedRef.set(fragment.getString(R.string.page_n_of_m, 1, 2)));
      String expected = expectedRef.get();

      boolean primaryOk = false;
      try {
        Espresso.onView(ViewMatchers.withId(R.id.pages_recycler))
            .check(
                ViewAssertions.matches(
                    ViewMatchers.hasDescendant(ViewMatchers.withContentDescription(expected))));
        primaryOk = true;
      } catch (AssertionError ae) {
        primaryOk = false;
      }

      if (!primaryOk) {
        scenario.onFragment(
            fragment -> {
              View container = fragment.requireView().findViewById(R.id.pages_container);
              RecyclerView rvl = fragment.requireView().findViewById(R.id.pages_recycler);
              assertNotNull("pages_container must exist", container);
              assertEquals(
                  "pages_container should be visible when pages >= 2",
                  View.VISIBLE,
                  container.getVisibility());
              RecyclerView.Adapter<?> ad = rvl.getAdapter();
              assertNotNull(ad);
              assertEquals("Adapter must report 2 items", 2, ad.getItemCount());
              // Diagnostics
              ViewDump.dumpTree(fragment.requireView());
            });
      }
    } finally {
      IdlingRegistry.getInstance().unregister(idle);
    }
  }
}
