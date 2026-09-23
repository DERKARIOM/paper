/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export.picker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.data.CompletedScansRegistry;
import de.schliweb.makeacopy.ui.export.session.CompletedScan;
import de.schliweb.makeacopy.utils.ui.DialogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment responsible for presenting a list of completed scan entries to the user and allowing
 * selection of specific entries. The selected entries can then be returned as a result.
 *
 * <p>This fragment includes a RecyclerView for displaying the completed scans, a ProgressBar to
 * indicate loading progress, and appropriate UI state management for empty data scenarios. The
 * pre-selected and disabled items are passed via fragment arguments, and user interactions are
 * handled through an adapter.
 *
 * <p>Implements {@link CompletedScansPickerAdapter.Callbacks} to handle user interactions like
 * selection changes, enabling/disabling items, and long-press actions for item-specific operations.
 */
public class CompletedScansPickerFragment extends Fragment
    implements CompletedScansPickerAdapter.Callbacks {

  public static final String RESULT_KEY = "pick_completed_scans";
  public static final String RESULT_IDS = "selected_ids";
  public static final String ARG_ALREADY_SELECTED_IDS = "already_selected_ids";

  private RecyclerView recyclerView;
  private ProgressBar progressBar;
  private TextView emptyView;
  private Button buttonBack;
  private Button buttonDelete;
  private Button buttonDone;
  private android.widget.ImageButton buttonSelectAll;
  private android.widget.ImageButton buttonSelectNone;
  private ImageButton buttonCleanupSettings;

  private final Set<String> selectedIds = new HashSet<>();
  private final Set<String> disabledIds = new HashSet<>();
  private final List<CompletedScan> currentItems = new ArrayList<>();
  private CompletedScansPickerAdapter adapter;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View root = inflater.inflate(R.layout.fragment_completed_scans_picker, container, false);
    recyclerView = root.findViewById(R.id.recycler);
    progressBar = root.findViewById(R.id.progress);
    emptyView = root.findViewById(R.id.empty);
    buttonBack = root.findViewById(R.id.button_back);
    buttonDelete = root.findViewById(R.id.button_delete);
    buttonDone = root.findViewById(R.id.button_done);
    buttonSelectAll = root.findViewById(R.id.button_select_all);
    buttonSelectNone = root.findViewById(R.id.button_select_none);
    buttonCleanupSettings = root.findViewById(R.id.button_cleanup_settings);

    // Edge-to-edge insets: pad title with status bar, pad bottom button container with nav bar
    final View titleView = root.findViewById(R.id.title);
    final View bottomContainer = root.findViewById(R.id.button_container);
    final int titleOrigLeft = titleView != null ? titleView.getPaddingLeft() : 0;
    final int titleOrigTop = titleView != null ? titleView.getPaddingTop() : 0;
    final int titleOrigRight = titleView != null ? titleView.getPaddingRight() : 0;
    final int titleOrigBottom = titleView != null ? titleView.getPaddingBottom() : 0;
    final int bottomOrigLeft = bottomContainer != null ? bottomContainer.getPaddingLeft() : 0;
    final int bottomOrigTop = bottomContainer != null ? bottomContainer.getPaddingTop() : 0;
    final int bottomOrigRight = bottomContainer != null ? bottomContainer.getPaddingRight() : 0;
    final int bottomOrigBottom = bottomContainer != null ? bottomContainer.getPaddingBottom() : 0;
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets sb =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          if (titleView != null) {
            titleView.setPadding(
                titleOrigLeft, titleOrigTop + sb.top, titleOrigRight, titleOrigBottom);
          }
          if (bottomContainer != null) {
            // Apply system bar inset as additional bottom MARGIN instead of padding to avoid
            // inflating the container's interior height. This keeps buttons visually centered.
            android.view.ViewGroup.LayoutParams lp = bottomContainer.getLayoutParams();
            if (lp instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp) {
              clp.bottomMargin = bottomOrigBottom + sb.bottom;
              bottomContainer.setLayoutParams(clp);
            } else if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
              mlp.bottomMargin = bottomOrigBottom + sb.bottom;
              bottomContainer.setLayoutParams(mlp);
            }
            // Keep original padding
            bottomContainer.setPadding(
                bottomOrigLeft, bottomOrigTop, bottomOrigRight, bottomOrigBottom);
          }
          return insets;
        });

    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    recyclerView.setHasFixedSize(true);
    recyclerView.setItemViewCacheSize(20);
    // Accept preselected/disabled IDs from args
    Bundle args = getArguments();
    if (args != null) {
      ArrayList<String> pre = args.getStringArrayList(ARG_ALREADY_SELECTED_IDS);
      if (pre != null) disabledIds.addAll(pre);
    }

    adapter = new CompletedScansPickerAdapter(this);
    recyclerView.setAdapter(adapter);

    buttonBack.setOnClickListener(v -> navigateBackWithoutResult());
    buttonDelete.setOnClickListener(v -> deleteSelectedAndRefresh());
    buttonDone.setOnClickListener(v -> returnResultAndClose());
    if (buttonSelectAll != null) buttonSelectAll.setOnClickListener(v -> selectAllEligible(true));
    if (buttonSelectNone != null)
      buttonSelectNone.setOnClickListener(v -> selectAllEligible(false));
    if (buttonCleanupSettings != null)
      buttonCleanupSettings.setOnClickListener(
          v -> DialogUtils.showCleanupSettingsDialog(requireContext()));

    loadItems();
    return root;
  }

  private void loadItems() {
    progressBar.setVisibility(View.VISIBLE);
    recyclerView.setVisibility(View.GONE);
    emptyView.setVisibility(View.GONE);

    // Opportunistic cleanup before listing
    try {
      de.schliweb.makeacopy.data.RegistryCleaner.cleanupOrphans(
          requireContext().getApplicationContext());
    } catch (Throwable ignore) {
      // Best-effort; failure is non-critical
    }
    // Load synchronously for v1 simplicity; list is small
    List<CompletedScan> items =
        CompletedScansRegistry.get(requireContext()).listAllOrderedByDateDesc();

    progressBar.setVisibility(View.GONE);
    currentItems.clear();
    if (items == null || items.isEmpty()) {
      emptyView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
      adapter.submitList(new ArrayList<>());
    } else {
      emptyView.setVisibility(View.GONE);
      recyclerView.setVisibility(View.VISIBLE);
      currentItems.addAll(items);
      adapter.submitList(items);
    }
    updateDoneEnabled();
  }

  private void updateDoneEnabled() {
    boolean hasSelection = !selectedIds.isEmpty();
    if (buttonDone != null) buttonDone.setEnabled(hasSelection);
    if (buttonDelete != null) buttonDelete.setEnabled(hasSelection);
  }

  private void returnResultAndClose() {
    ArrayList<String> ids = new ArrayList<>(selectedIds);
    Bundle result = new Bundle();
    result.putStringArrayList(RESULT_IDS, ids);
    getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    try {
      androidx.navigation.Navigation.findNavController(requireView()).popBackStack();
    } catch (Throwable t) {
      getParentFragmentManager().popBackStack();
    }
  }

  private void navigateBackWithoutResult() {
    // Do not pass any selection back; simply navigate back to Export
    try {
      androidx.navigation.Navigation.findNavController(requireView()).popBackStack();
    } catch (Throwable t) {
      getParentFragmentManager().popBackStack();
    }
  }

  private void deleteSelectedAndRefresh() {
    if (selectedIds.isEmpty()) return;
    // Copy to avoid ConcurrentModification
    ArrayList<String> toDelete = new ArrayList<>(selectedIds);
    int removed = 0;
    for (String id : toDelete) {
      try {
        de.schliweb.makeacopy.data.RegistryCleaner.removeEntryAndFiles(
            requireContext().getApplicationContext(), id);
        removed++;
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
    }
    selectedIds.clear();
    if (removed > 0) {
      try {
        android.widget.Toast.makeText(
                requireContext(),
                R.string.removed_from_registry_toast,
                android.widget.Toast.LENGTH_SHORT)
            .show();
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
    }
    // Refresh UI
    loadItems();
  }

  // Callbacks from adapter
  @Override
  public boolean isSelected(@NonNull String id) {
    return selectedIds.contains(id);
  }

  @Override
  public void onItemSelectionChanged(@NonNull String id, boolean selected) {
    if (selected) selectedIds.add(id);
    else selectedIds.remove(id);
    updateDoneEnabled();
  }

  @Override
  public boolean isDisabled(@NonNull String id) {
    return disabledIds.contains(id);
  }

  /**
   * Selects or deselects all eligible items in the current list based on the given parameter. If
   * selecting, only items that are not disabled and are not missing are added to the selection. If
   * deselecting, the selection is cleared entirely. The UI is updated accordingly.
   *
   * @param select A boolean indicating whether to select all eligible items (true) or deselect all
   *     (false).
   */
  private void selectAllEligible(boolean select) {
    if (currentItems.isEmpty()) return;
    if (!select) {
      // Deselect all: clear selection entirely
      selectedIds.clear();
      adapter.notifyDataSetChanged();
      updateDoneEnabled();
      return;
    }
    // Select all items that are not disabled and not missing
    for (CompletedScan s : currentItems) {
      String id = s.id();
      boolean hasThumb = s.thumbPath() != null && new java.io.File(s.thumbPath()).exists();
      boolean hasFile = s.filePath() != null && new java.io.File(s.filePath()).exists();
      boolean missing = !hasThumb && !hasFile;
      if (!disabledIds.contains(id) && !missing) {
        selectedIds.add(id);
      }
    }
    adapter.notifyDataSetChanged();
    updateDoneEnabled();
  }

  /**
   * Handles the long press action on an item in the list. This method displays a confirmation
   * dialog to remove the selected item from the registry and its associated files. If the removal
   * is confirmed, it updates the UI to reflect the changes and refreshes the list of items.
   *
   * @param id The unique identifier of the item that was long-pressed.
   */
  @Override
  public void onItemLongPress(@NonNull String id) {
    // Show confirmation dialog to remove from registry
    androidx.appcompat.app.AlertDialog dialog =
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_from_registry_title)
            .setMessage(R.string.remove_from_registry_message)
            .setPositiveButton(
                R.string.remove,
                (d, which) -> {
                  try {
                    de.schliweb.makeacopy.data.RegistryCleaner.removeEntryAndFiles(
                        requireContext().getApplicationContext(), id);
                    android.widget.Toast.makeText(
                            requireContext(),
                            R.string.removed_from_registry_toast,
                            android.widget.Toast.LENGTH_SHORT)
                        .show();
                  } catch (Throwable ignore) {
                    // Best-effort; failure is non-critical
                  }
                  // Refresh list after removal
                  loadItems();
                })
            .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
            .create();
    // Improve dark mode contrast for dialog buttons similar to other dialogs
    dialog.setOnShowListener(
        dlg -> DialogUtils.improveAlertDialogButtonContrastForNight(dialog, requireContext()));
    dialog.show();
  }
}
