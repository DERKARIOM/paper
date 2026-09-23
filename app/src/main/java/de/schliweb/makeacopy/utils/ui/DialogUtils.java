/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import de.schliweb.makeacopy.R;
import lombok.experimental.UtilityClass;

/** Small dialog-related helpers to reduce UI code duplication. */
@UtilityClass
public final class DialogUtils {

  /**
   * In dark mode, some AlertDialog button colors can be low contrast depending on theme. This
   * method adjusts button text colors to white to improve readability. Safe to call on dialog's
   * onShow.
   */
  public static void improveAlertDialogButtonContrastForNight(
      androidx.appcompat.app.AlertDialog dialog, Context ctx) {
    if (dialog == null || ctx == null) return;
    try {
      int nightModeFlags =
          ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
      if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
        try {
          int white = androidx.core.content.ContextCompat.getColor(ctx, android.R.color.white);
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(white);
          }
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(white);
          }
          if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(white);
          }
        } catch (Exception ignored) {
          // Best-effort; failure is non-critical
        }
      }
    } catch (Throwable ignored) {
      // Best-effort; failure is non-critical
    }
  }

  /**
   * Builds a Material 3 bottom sheet hosting an options view with a title and a Cancel/Confirm
   * button row. Used by the camera/export option dialogs instead of AlertDialogs so options appear
   * as bottom sheets (reachable, M3-styled via the app-wide {@code bottomSheetDialogTheme}).
   *
   * @param ctx the context
   * @param title the sheet title
   * @param content the options content view (typically an inflated ScrollView)
   * @param onConfirm invoked when the user taps Confirm; the sheet is dismissed afterwards
   * @return the configured, not-yet-shown bottom sheet dialog
   */
  public static com.google.android.material.bottomsheet.BottomSheetDialog createOptionsBottomSheet(
      Context ctx, CharSequence title, android.view.View content, Runnable onConfirm) {
    com.google.android.material.bottomsheet.BottomSheetDialog dialog =
        new com.google.android.material.bottomsheet.BottomSheetDialog(ctx);
    float density = ctx.getResources().getDisplayMetrics().density;
    int pad = (int) (16 * density);

    LinearLayout container = new LinearLayout(ctx);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(pad, pad, pad, pad);

    TextView titleView = new TextView(ctx);
    titleView.setText(title);
    titleView.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
    titleView.setPadding(0, 0, 0, (int) (8 * density));
    androidx.core.view.ViewCompat.setAccessibilityHeading(titleView, true);
    container.addView(titleView);

    container.addView(
        content,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f /* weight: scrollable content */));

    LinearLayout buttonRow = new LinearLayout(ctx);
    buttonRow.setOrientation(LinearLayout.HORIZONTAL);
    buttonRow.setGravity(android.view.Gravity.END);
    buttonRow.setPadding(0, (int) (8 * density), 0, 0);

    com.google.android.material.button.MaterialButton cancelButton =
        new com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    cancelButton.setText(R.string.cancel);
    cancelButton.setOnClickListener(v -> dialog.dismiss());
    buttonRow.addView(cancelButton);

    com.google.android.material.button.MaterialButton confirmButton =
        new com.google.android.material.button.MaterialButton(ctx);
    confirmButton.setText(R.string.confirm);
    LinearLayout.LayoutParams confirmLp =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    confirmLp.setMarginStart((int) (8 * density));
    confirmButton.setLayoutParams(confirmLp);
    confirmButton.setOnClickListener(
        v -> {
          try {
            if (onConfirm != null) onConfirm.run();
          } finally {
            dialog.dismiss();
          }
        });
    buttonRow.addView(confirmButton);

    container.addView(buttonRow);
    dialog.setContentView(container);

    // Long option lists: open fully expanded so the action row is visible right away.
    // The sheet height is capped by the screen; the weighted content view scrolls while
    // title and buttons stay pinned. Skip the collapsed state entirely — a half-open
    // sheet hides the Cancel/Confirm row and only confuses.
    com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = dialog.getBehavior();
    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    behavior.setSkipCollapsed(true);
    return dialog;
  }

  /**
   * Shows the cleanup settings dialog for completed scans. Reads and writes policy configuration
   * from/to SharedPreferences ({@code cache_cleanup_prefs}).
   *
   * @param ctx the context (typically from a Fragment's {@code requireContext()})
   */
  public static void showCleanupSettingsDialog(Context ctx) {
    if (ctx == null) return;
    SharedPreferences prefs = ctx.getSharedPreferences("cache_cleanup_prefs", Context.MODE_PRIVATE);

    String[] policyKeys = {"NONE", "MAX_AGE", "MAX_COUNT", "MAX_STORAGE", "COMBINED"};
    String[] policyLabels = {
      ctx.getString(R.string.cleanup_policy_none),
      ctx.getString(R.string.cleanup_policy_max_age),
      ctx.getString(R.string.cleanup_policy_max_count),
      ctx.getString(R.string.cleanup_policy_max_storage),
      ctx.getString(R.string.cleanup_policy_combined)
    };

    String currentPolicy = prefs.getString("completed_scans_cleanup_policy", "NONE");
    int currentIdx = 0;
    for (int i = 0; i < policyKeys.length; i++) {
      if (policyKeys[i].equals(currentPolicy)) {
        currentIdx = i;
        break;
      }
    }

    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
    layout.setPadding(pad, pad, pad, pad);

    ScrollView scrollView = new ScrollView(ctx);
    scrollView.addView(
        layout,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

    TextView labelPolicy = new TextView(ctx);
    labelPolicy.setText(R.string.cleanup_policy);
    layout.addView(labelPolicy);

    Spinner spinner = new Spinner(ctx);
    ArrayAdapter<String> spinnerAdapter =
        new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, policyLabels);
    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(spinnerAdapter);
    spinner.setSelection(currentIdx);
    layout.addView(spinner);

    TextView labelAge = new TextView(ctx);
    labelAge.setText(R.string.cleanup_max_age_days);
    layout.addView(labelAge);
    EditText inputAge = new EditText(ctx);
    inputAge.setInputType(InputType.TYPE_CLASS_NUMBER);
    inputAge.setText(String.valueOf(prefs.getInt("completed_scans_max_age_days", 30)));
    layout.addView(inputAge);

    TextView labelCount = new TextView(ctx);
    labelCount.setText(R.string.cleanup_max_count);
    layout.addView(labelCount);
    EditText inputCount = new EditText(ctx);
    inputCount.setInputType(InputType.TYPE_CLASS_NUMBER);
    inputCount.setText(String.valueOf(prefs.getInt("completed_scans_max_count", 100)));
    layout.addView(inputCount);

    TextView labelStorage = new TextView(ctx);
    labelStorage.setText(R.string.cleanup_max_storage_mb);
    layout.addView(labelStorage);
    EditText inputStorage = new EditText(ctx);
    inputStorage.setInputType(InputType.TYPE_CLASS_NUMBER);
    inputStorage.setText(String.valueOf(prefs.getInt("completed_scans_max_storage_mb", 500)));
    layout.addView(inputStorage);

    final androidx.appcompat.app.AlertDialog dialog =
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.scan_cleanup_settings)
            .setView(scrollView)
            .setPositiveButton(
                R.string.ok,
                (d, w) -> {
                  int selectedIdx = spinner.getSelectedItemPosition();
                  String selectedPolicy = policyKeys[selectedIdx];
                  SharedPreferences.Editor editor = prefs.edit();
                  editor.putString("completed_scans_cleanup_policy", selectedPolicy);
                  try {
                    editor.putInt(
                        "completed_scans_max_age_days",
                        Integer.parseInt(inputAge.getText().toString().trim()));
                  } catch (NumberFormatException ignore) {
                    // keep existing
                  }
                  try {
                    editor.putInt(
                        "completed_scans_max_count",
                        Integer.parseInt(inputCount.getText().toString().trim()));
                  } catch (NumberFormatException ignore) {
                    // keep existing
                  }
                  try {
                    editor.putInt(
                        "completed_scans_max_storage_mb",
                        Integer.parseInt(inputStorage.getText().toString().trim()));
                  } catch (NumberFormatException ignore) {
                    // keep existing
                  }
                  editor.apply();
                  UIUtils.showToast(ctx, R.string.cleanup_settings_saved, Toast.LENGTH_SHORT);
                })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    dialog.setOnShowListener(
        d -> {
          try {
            improveAlertDialogButtonContrastForNight(dialog, ctx);
          } catch (Throwable ignore) {
            // Best-effort; failure is non-critical
          }
        });
    dialog.show();
  }
}
