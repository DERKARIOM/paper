/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.export;

import android.view.View;
import androidx.lifecycle.LifecycleOwner;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.databinding.FragmentExportBinding;
import de.schliweb.makeacopy.ui.export.ExportViewModel;
import lombok.experimental.UtilityClass;

/**
 * Binds export progress-related LiveData from ExportViewModel to the FragmentExportBinding so UI
 * classes can delegate this boilerplate and reduce complexity.
 */
@UtilityClass
public final class ExportUiBindings {

  /**
   * Temporarily sets the enabled state of a given view. If the `disable` parameter is true, the
   * method disables the view and caches its previous enabled state. If the `disable` parameter is
   * false, the view is restored to its cached previous enabled state.
   *
   * @param v The view whose enabled state is to be modified. If null, the method does nothing.
   * @param disable A boolean indicating whether the view should be disabled (true) or restored to
   *     its previous enabled state (false).
   */
  private static void setEnabledTemporarily(View v, boolean disable) {
    if (v == null) return;
    if (disable) {
      // Cache previous enabled state once
      Object prev = v.getTag(R.id.tag_previous_enabled);
      if (prev == null) {
        v.setTag(R.id.tag_previous_enabled, v.isEnabled());
      }
      v.setEnabled(false);
    } else {
      Object prev = v.getTag(R.id.tag_previous_enabled);
      if (prev instanceof Boolean) {
        v.setEnabled((Boolean) prev);
      }
      v.setTag(R.id.tag_previous_enabled, null);
    }
  }

  /**
   * Binds the export progress-related LiveData from the ExportViewModel to the provided
   * FragmentExportBinding. This ensures updates to export progress, progress visibility, and
   * progress state are reflected in the UI components.
   *
   * @param binding The binding object for accessing views in the fragment.
   * @param owner The LifecycleOwner for observing LiveData changes.
   * @param vm The ExportViewModel containing LiveData for export progress and state.
   */
  public static void bindExportProgress(
      FragmentExportBinding binding, LifecycleOwner owner, ExportViewModel vm) {
    if (binding == null || owner == null || vm == null) return;
    vm.isExporting()
        .observe(
            owner,
            exporting -> {
              boolean isExporting = exporting != null && exporting;
              if (isExporting) {
                binding.exportProgress.setVisibility(View.VISIBLE);
              } else {
                binding.exportProgress.setVisibility(View.GONE);
              }
              // During export: disable all actionable controls. After export: restore to previous
              // state.
              setEnabledTemporarily(binding.buttonBack, isExporting);
              setEnabledTemporarily(binding.buttonAddScan, isExporting);
              setEnabledTemporarily(binding.buttonExport, isExporting);
              setEnabledTemporarily(binding.buttonOptions, isExporting);
              setEnabledTemporarily(binding.buttonAddPage, isExporting);
              setEnabledTemporarily(binding.buttonClearPages, isExporting);
              // Special handling for Share icon: only force-disable during export; do not restore
              // here
              if (isExporting) {
                binding.buttonShareSmall.setEnabled(false);
              }
              // When export finishes, ExportFragment will decide whether to enable it

              if (!isExporting) {
                // Ensure Export button respects document readiness after restoring
                Boolean ready = vm.isDocumentReady().getValue();
                binding.buttonExport.setEnabled(Boolean.TRUE.equals(ready));
                // Do not change Share here; it will be enabled explicitly on successful export
              }
            });
    vm.getExportProgressMax()
        .observe(
            owner,
            max -> {
              Integer m = (max == null) ? 0 : max;
              binding.exportProgress.setMax((m <= 0) ? 100 : m);
              boolean indeterminate = m <= 0;
              if (binding.exportProgress.isIndeterminate() != indeterminate) {
                // Material progress indicators only allow switching the mode while not visible.
                int previousVisibility = binding.exportProgress.getVisibility();
                if (previousVisibility == android.view.View.VISIBLE) {
                  binding.exportProgress.setVisibility(android.view.View.INVISIBLE);
                }
                binding.exportProgress.setIndeterminate(indeterminate);
                binding.exportProgress.setVisibility(previousVisibility);
              }
            });
    vm.getExportProgress()
        .observe(
            owner,
            value -> {
              if (value != null) binding.exportProgress.setProgress(value);
            });
  }
}
