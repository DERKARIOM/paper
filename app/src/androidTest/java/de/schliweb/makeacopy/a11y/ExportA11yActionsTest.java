package de.schliweb.makeacopy.a11y;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.testutil.HiltFragmentScenario;
import de.schliweb.makeacopy.testutil.LiveDataIdlingResource;
import de.schliweb.makeacopy.testutil.ViewDump;
import de.schliweb.makeacopy.ui.export.ExportFragment;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExportA11yActionsTest {

  private static CompletedScan makeScan(int w, int h) {
    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    return new CompletedScan(
        UUID.randomUUID().toString(),
        null,
        0,
        null,
        null,
        null,
        System.currentTimeMillis(),
        w,
        h,
        bmp,
        1,
        "metadata");
  }

  /** Seeds two pages in the ExportSessionViewModel and returns the RecyclerView + VM via refs. */
  private void seedTwoPages(
      HiltFragmentScenario<ExportFragment> scenario,
      AtomicReference<RecyclerView> rvRef,
      AtomicReference<de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel> vmRef,
      AtomicReference<androidx.lifecycle.LifecycleOwner> ownerRef) {
    // increase timeouts for slow devices
    IdlingPolicies.setIdlingResourceTimeout(30, TimeUnit.SECONDS);
    IdlingPolicies.setMasterPolicyTimeout(45, TimeUnit.SECONDS);

    scenario.onFragment(
        fragment -> {
          de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel vm =
              new androidx.lifecycle.ViewModelProvider(fragment.requireActivity())
                  .get(de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel.class);
          vm.setInitial(makeScan(120, 160));
          vm.add(makeScan(140, 180));
          vmRef.set(vm);
          ownerRef.set(fragment.getViewLifecycleOwner());

          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          assertNotNull(rv);
          rvRef.set(rv);
          if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
        });

    // Wait until pages >= 2 before proceeding
    LiveDataIdlingResource<List<CompletedScan>> idle =
        new LiveDataIdlingResource<>(
            vmRef.get().getPages(), ownerRef.get(), pages -> pages != null && pages.size() >= 2);
    try {
      IdlingRegistry.getInstance().register(idle);
    } finally {
      IdlingRegistry.getInstance().unregister(idle);
    }
  }

  @Test
  public void custom_actions_present_on_items() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    AtomicReference<RecyclerView> rvRef = new AtomicReference<>();
    AtomicReference<de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel> vmRef =
        new AtomicReference<>();
    AtomicReference<androidx.lifecycle.LifecycleOwner> ownerRef = new AtomicReference<>();
    seedTwoPages(scenario, rvRef, vmRef, ownerRef);

    scenario.onFragment(
        fragment -> {
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          assertNotNull(rv);
          // ensure first item is bound
          rv.scrollToPosition(0);
          RecyclerView.ViewHolder vh0 = rv.findViewHolderForAdapterPosition(0);
          if (vh0 == null) {
            fragment.requireView().post(() -> {}); // hint a frame
            vh0 = rv.findViewHolderForAdapterPosition(0);
          }
          assertNotNull("First ViewHolder should be bound", vh0);
          View item = vh0.itemView;
          AccessibilityNodeInfo info = item.createAccessibilityNodeInfo();
          AccessibilityNodeInfoCompat compat = AccessibilityNodeInfoCompat.wrap(info);
          boolean hasDelete = false, hasLeft = false, hasRight = false;
          for (AccessibilityNodeInfoCompat.AccessibilityActionCompat a : compat.getActionList()) {
            int id = a.getId();
            if (id == R.id.a11y_action_delete_page) hasDelete = true;
            if (id == R.id.a11y_action_move_left) hasLeft = true;
            if (id == R.id.a11y_action_move_right) hasRight = true;
          }
          assertTrue("Item should expose Delete custom action", hasDelete);
          // At position 0 (first item) left should be absent, right present
          assertFalse("First item should NOT expose Move left", hasLeft);
          assertTrue("First item should expose Move right", hasRight);
        });
  }

  @Test
  public void perform_delete_action_updates_count() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    AtomicReference<RecyclerView> rvRef = new AtomicReference<>();
    AtomicReference<de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel> vmRef =
        new AtomicReference<>();
    AtomicReference<androidx.lifecycle.LifecycleOwner> ownerRef = new AtomicReference<>();
    seedTwoPages(scenario, rvRef, vmRef, ownerRef);

    // Capture initial ids/count
    final AtomicReference<Integer> beforeCount = new AtomicReference<>(0);
    scenario.onFragment(
        fragment -> {
          List<CompletedScan> pages = vmRef.get().getPages().getValue();
          beforeCount.set(pages == null ? 0 : pages.size());
        });

    // Perform Delete on first item
    scenario.onFragment(
        fragment -> {
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          rv.scrollToPosition(0);
          RecyclerView.ViewHolder vh0 = rv.findViewHolderForAdapterPosition(0);
          assertNotNull(vh0);
          boolean ok = vh0.itemView.performAccessibilityAction(R.id.a11y_action_delete_page, null);
          assertTrue("performAccessibilityAction(delete) should return true", ok);
        });

    // Await pages decreased to 1
    LiveDataIdlingResource<List<CompletedScan>> idle =
        new LiveDataIdlingResource<>(
            vmRef.get().getPages(), ownerRef.get(), pages -> pages != null && pages.size() == 1);
    try {
      IdlingRegistry.getInstance().register(idle);
    } finally {
      IdlingRegistry.getInstance().unregister(idle);
    }

    // Assert count dropped by 1 and container visibility updated
    scenario.onFragment(
        fragment -> {
          List<CompletedScan> pages = vmRef.get().getPages().getValue();
          assertNotNull(pages);
          assertEquals("After delete, there should be exactly 1 page", 1, pages.size());
          View container = fragment.requireView().findViewById(R.id.pages_container);
          assertNotNull(container);
          assertEquals(
              "With one page, pages_container should be GONE",
              View.GONE,
              container.getVisibility());
        });
  }

  @Test
  public void perform_move_right_then_left_reorders_items() {
    HiltFragmentScenario<ExportFragment> scenario =
        HiltFragmentScenario.launchInHiltContainer(
            ExportFragment.class, null, R.style.Theme_MakeACopy, Lifecycle.State.RESUMED);

    AtomicReference<RecyclerView> rvRef = new AtomicReference<>();
    AtomicReference<de.schliweb.makeacopy.ui.export.session.ExportSessionViewModel> vmRef =
        new AtomicReference<>();
    AtomicReference<androidx.lifecycle.LifecycleOwner> ownerRef = new AtomicReference<>();
    seedTwoPages(scenario, rvRef, vmRef, ownerRef);

    final AtomicReference<String> firstIdBefore = new AtomicReference<>();
    final AtomicReference<String> secondIdBefore = new AtomicReference<>();
    scenario.onFragment(
        fragment -> {
          List<CompletedScan> pages = vmRef.get().getPages().getValue();
          assertNotNull(pages);
          assertTrue(pages.size() >= 2);
          firstIdBefore.set(pages.get(0).id());
          secondIdBefore.set(pages.get(1).id());
        });

    // Move right on position 0
    scenario.onFragment(
        fragment -> {
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          rv.scrollToPosition(0);
          RecyclerView.ViewHolder vh0 = rv.findViewHolderForAdapterPosition(0);
          assertNotNull(vh0);
          boolean ok = vh0.itemView.performAccessibilityAction(R.id.a11y_action_move_right, null);
          assertTrue("performAccessibilityAction(move_right) should return true", ok);
        });

    // Await order swap
    LiveDataIdlingResource<List<CompletedScan>> idle =
        new LiveDataIdlingResource<>(
            vmRef.get().getPages(),
            ownerRef.get(),
            pages ->
                pages != null
                    && pages.size() >= 2
                    && pages.get(0).id().equals(secondIdBefore.get()));
    try {
      IdlingRegistry.getInstance().register(idle);
    } finally {
      IdlingRegistry.getInstance().unregister(idle);
    }

    // Now move left back to original
    scenario.onFragment(
        fragment -> {
          RecyclerView rv = fragment.requireView().findViewById(R.id.pages_recycler);
          rv.scrollToPosition(1);
          RecyclerView.ViewHolder vh1 = rv.findViewHolderForAdapterPosition(1);
          if (vh1 == null) ViewDump.dumpTree(fragment.requireView());
          assertNotNull(vh1);
          boolean ok = vh1.itemView.performAccessibilityAction(R.id.a11y_action_move_left, null);
          assertTrue("performAccessibilityAction(move_left) should return true", ok);
        });

    // Await back to original order
    LiveDataIdlingResource<List<CompletedScan>> idle2 =
        new LiveDataIdlingResource<>(
            vmRef.get().getPages(),
            ownerRef.get(),
            pages ->
                pages != null
                    && pages.size() >= 2
                    && pages.get(0).id().equals(firstIdBefore.get()));
    try {
      IdlingRegistry.getInstance().register(idle2);
    } finally {
      IdlingRegistry.getInstance().unregister(idle2);
    }
  }
}
