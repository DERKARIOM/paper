/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.export;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import de.schliweb.makeacopy.R;
import de.schliweb.makeacopy.utils.export.PageFormat;
import de.schliweb.makeacopy.utils.export.PdfCreator;
import de.schliweb.makeacopy.utils.export.PdfQualityPreset;
import de.schliweb.makeacopy.utils.export.jpeg.JpegExportOptions;
import de.schliweb.makeacopy.utils.image.DocumentCleanupMode;
import de.schliweb.makeacopy.utils.infra.FeatureFlags;
import de.schliweb.makeacopy.utils.ui.DialogUtils;

/**
 * A dialog fragment that displays export options for the user to configure. Options include
 * selecting whether to include OCR data, exporting as JPEG or PDF, enabling grayscale conversion,
 * and choosing specific PDF or JPEG settings.
 *
 * <p>This dialog allows users to modify their preferences for exporting content and persists these
 * settings for future use. Once the user confirms their choices, the selected options are sent back
 * via a result bundle.
 *
 * <p>Constants: - REQUEST_KEY: The key for retrieving the fragment result. - BUNDLE_INCLUDE_OCR:
 * Key for including or excluding OCR data in export. - BUNDLE_EXPORT_AS_JPEG: Key for exporting the
 * output as JPEG format. - BUNDLE_CONVERT_TO_GRAYSCALE: Key for converting the output to grayscale.
 * - BUNDLE_JPEG_MODE: Key for specifying the JPEG export mode, represented as an enum name. -
 * BUNDLE_PDF_PRESET: Key for defining the PDF export quality preset, also represented as an enum
 * name.
 *
 * <p>Overrides: - onCreateDialog(Bundle): Creates and initializes the dialog with its UI and logic.
 *
 * <p>Methods: - show(FragmentManager): Static method to show the dialog using the provided
 * FragmentManager. - updateGroups(boolean, View, View): Private helper method to toggle visibility
 * between PDF and JPEG option groups within the dialog.
 */
public class ExportOptionsDialogFragment extends DialogFragment {

  private ActivityResultLauncher<Uri> inboxFolderLauncher;
  private TextView inboxFolderLabel;
  private CheckBox cbInboxEnabled;

  public static final String REQUEST_KEY = "export_options";
  public static final String BUNDLE_INCLUDE_OCR = "include_ocr";
  public static final String BUNDLE_EXPORT_AS_JPEG = "export_as_jpeg";
  public static final String BUNDLE_JPEG_MODE = "jpeg_mode"; // enum name
  public static final String BUNDLE_PDF_PRESET = "pdf_preset"; // enum name
  public static final String BUNDLE_PAGE_FORMAT = "page_format"; // enum name
  public static final String BUNDLE_PDF_TEXT_LAYER_MODE = "pdf_text_layer_mode"; // enum name

  public static void show(@NonNull FragmentManager fm) {
    new ExportOptionsDialogFragment().show(fm, "ExportOptionsDialogFragment");
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inboxFolderLauncher =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
              if (uri != null && getContext() != null) {
                // Persist permission across reboots
                int flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                getContext().getContentResolver().takePersistableUriPermission(uri, flags);
                ExportPrefsHelper.setInboxUri(getContext(), uri.toString());
                ExportPrefsHelper.setInboxEnabled(getContext(), true);
                if (cbInboxEnabled != null) cbInboxEnabled.setChecked(true);
                updateInboxFolderLabel();
              }
            });
  }

  private void updateInboxFolderLabel() {
    if (inboxFolderLabel == null || getContext() == null) return;
    String uri = ExportPrefsHelper.getInboxUri(getContext());
    if (uri != null) {
      // Show last path segment for readability
      Uri parsed = Uri.parse(uri);
      String display = parsed.getLastPathSegment();
      if (display == null) display = uri;
      inboxFolderLabel.setText(getString(R.string.inbox_folder_set, display));
    } else {
      inboxFolderLabel.setText(R.string.inbox_folder_none);
    }
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    Context ctx = requireContext();
    View view = getLayoutInflater().inflate(R.layout.dialog_export_options, null);

    CheckBox cbIncludeOcr = view.findViewById(R.id.dialog_checkbox_include_ocr);
    RadioGroup cleanupModeGroup = view.findViewById(R.id.dialog_document_cleanup_group);
    RadioButton rbCleanupOriginal = view.findViewById(R.id.dialog_document_cleanup_original);
    RadioButton rbCleanupNatural = view.findViewById(R.id.dialog_document_cleanup_natural);
    RadioButton rbCleanupEnhanced = view.findViewById(R.id.dialog_document_cleanup_enhanced);
    RadioButton rbCleanupCleanText = view.findViewById(R.id.dialog_document_cleanup_clean_text);
    View pdfGroup = view.findViewById(R.id.dialog_pdf_group);
    RadioGroup pdfPresetGroup = view.findViewById(R.id.dialog_pdf_preset_group);
    RadioButton rbHigh = view.findViewById(R.id.dialog_radio_pdf_high);
    RadioButton rbStandard = view.findViewById(R.id.dialog_radio_pdf_standard);
    RadioButton rbSmall = view.findViewById(R.id.dialog_radio_pdf_small);
    RadioButton rbVerySmall = view.findViewById(R.id.dialog_radio_pdf_very_small);

    View jpegGroup = view.findViewById(R.id.dialog_jpeg_group);
    RadioGroup jpegModeGroup = view.findViewById(R.id.dialog_jpeg_mode_group);
    RadioButton rbJpegNone = view.findViewById(R.id.dialog_radio_jpeg_none);
    RadioButton rbJpegAuto = view.findViewById(R.id.dialog_radio_jpeg_auto);
    RadioButton rbJpegBw = view.findViewById(R.id.dialog_radio_jpeg_bw_text);

    RadioGroup pageFormatGroup = view.findViewById(R.id.dialog_page_format_group);
    RadioButton rbPageFit = view.findViewById(R.id.dialog_radio_page_fit);
    RadioButton rbPageA4 = view.findViewById(R.id.dialog_radio_page_a4);
    RadioButton rbPageLetter = view.findViewById(R.id.dialog_radio_page_letter);
    RadioButton rbPageLegal = view.findViewById(R.id.dialog_radio_page_legal);

    RadioGroup pdfTextLayerModeGroup = view.findViewById(R.id.dialog_pdf_text_layer_mode_group);
    RadioButton rbPdfTextLayerLineBased = view.findViewById(R.id.dialog_pdf_text_layer_line_based);
    RadioButton rbPdfTextLayerWordPositioned =
        view.findViewById(R.id.dialog_pdf_text_layer_word_positioned);

    RadioGroup pdfBwModeGroup = view.findViewById(R.id.dialog_pdf_bw_mode_group);
    RadioButton rbPdfBwNone = view.findViewById(R.id.dialog_pdf_bw_none);
    RadioButton rbPdfGray = view.findViewById(R.id.dialog_pdf_grayscale);
    RadioButton rbPdfBwRobust = view.findViewById(R.id.dialog_pdf_bw_robust);
    RadioButton rbPdfBwClassic = view.findViewById(R.id.dialog_pdf_bw_classic);

    SharedPreferences prefs = ctx.getSharedPreferences("export_options", Context.MODE_PRIVATE);
    boolean includeOcr = prefs.getBoolean("include_ocr", false);
    boolean exportAsJpeg = prefs.getBoolean("export_as_jpeg", false);
    // Legacy booleans removed; selection now driven solely by pdf_bw_mode
    String jpegModeSaved = prefs.getString("jpeg_mode", JpegExportOptions.Mode.NONE.name());
    boolean jpegOutputGrayscale = prefs.getBoolean("jpeg_output_grayscale", false);
    JpegExportOptions.Mode jpegMode;
    try {
      jpegMode = JpegExportOptions.Mode.valueOf(jpegModeSaved);
    } catch (Exception e) {
      jpegMode = JpegExportOptions.Mode.NONE;
    }
    String pdfBwModeSaved = prefs.getString("pdf_bw_mode", null);
    DocumentCleanupMode savedCleanupMode = ExportPrefsHelper.resolveCleanupMode(ctx);
    String presetSaved = prefs.getString("pdf_preset", null);
    String pageFormatSaved = prefs.getString("page_format", PageFormat.FIT_TO_IMAGE.name());
    PageFormat pageFormat = PageFormat.fromName(pageFormatSaved, PageFormat.FIT_TO_IMAGE);
    PdfCreator.TextLayerMode textLayerMode = ExportPrefsHelper.resolveTextLayerMode(ctx);

    cbIncludeOcr.setChecked(includeOcr);
    // Format (PDF/JPEG) is selected inline on the Export screen; the dialog only shows the
    // option groups matching the currently selected format.

    if (savedCleanupMode == DocumentCleanupMode.NATURAL) rbCleanupNatural.setChecked(true);
    else if (savedCleanupMode == DocumentCleanupMode.ENHANCED) rbCleanupEnhanced.setChecked(true);
    else if (savedCleanupMode == DocumentCleanupMode.CLEAN_TEXT)
      rbCleanupCleanText.setChecked(true);
    else rbCleanupOriginal.setChecked(true);

    // Initialize page format selection
    if (pageFormat == PageFormat.FIT_TO_IMAGE) rbPageFit.setChecked(true);
    else if (pageFormat == PageFormat.A4) rbPageA4.setChecked(true);
    else if (pageFormat == PageFormat.US_LETTER) rbPageLetter.setChecked(true);
    else if (pageFormat == PageFormat.LEGAL) rbPageLegal.setChecked(true);

    if (textLayerMode == PdfCreator.TextLayerMode.WORD_POSITIONED) {
      rbPdfTextLayerWordPositioned.setChecked(true);
    } else {
      rbPdfTextLayerLineBased.setChecked(true);
    }

    // pick default preset if none saved: High for single page, Standard for multi (ExportFragment
    // will compute page count; here fallback Standard)
    PdfQualityPreset preset =
        presetSaved != null
            ? PdfQualityPreset.fromName(presetSaved, PdfQualityPreset.STANDARD)
            : PdfQualityPreset.STANDARD;
    if (preset == PdfQualityPreset.HIGH) rbHigh.setChecked(true);
    else if (preset == PdfQualityPreset.STANDARD) rbStandard.setChecked(true);
    else if (preset == PdfQualityPreset.SMALL) rbSmall.setChecked(true);
    else if (preset == PdfQualityPreset.VERY_SMALL) rbVerySmall.setChecked(true);

    if (jpegMode == JpegExportOptions.Mode.BW_TEXT) {
      rbJpegBw.setChecked(true);
    } else if (jpegOutputGrayscale) {
      rbJpegAuto.setChecked(true);
    } else {
      rbJpegNone.setChecked(true);
    }

    // Initialize PDF mode radios ("none" selected if no saved value)
    if ("GRAYSCALE".equalsIgnoreCase(pdfBwModeSaved)) rbPdfGray.setChecked(true);
    else if ("CLASSIC".equalsIgnoreCase(pdfBwModeSaved)
        || "ROBUST".equalsIgnoreCase(pdfBwModeSaved)) rbPdfBwRobust.setChecked(true);
    else rbPdfBwNone.setChecked(true);

    // ── Inbox Mode UI ──
    View inboxGroup = view.findViewById(R.id.dialog_inbox_group);
    cbInboxEnabled = view.findViewById(R.id.dialog_checkbox_inbox_enabled);
    inboxFolderLabel = view.findViewById(R.id.dialog_inbox_folder_label);
    View btnInboxSelect = view.findViewById(R.id.dialog_button_inbox_select);
    View btnInboxClear = view.findViewById(R.id.dialog_button_inbox_clear);

    if (FeatureFlags.isInboxModeEnabled() && inboxGroup != null) {
      inboxGroup.setVisibility(View.VISIBLE);
      cbInboxEnabled.setChecked(ExportPrefsHelper.isInboxEnabled(ctx));
      updateInboxFolderLabel();

      cbInboxEnabled.setOnCheckedChangeListener(
          (buttonView, isChecked) -> {
            if (isChecked && ExportPrefsHelper.getInboxUri(ctx) == null) {
              buttonView.setChecked(false);
              android.widget.Toast.makeText(
                      ctx, R.string.inbox_no_folder_selected, android.widget.Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            ExportPrefsHelper.setInboxEnabled(ctx, isChecked);
          });

      if (btnInboxSelect != null) {
        btnInboxSelect.setOnClickListener(v2 -> inboxFolderLauncher.launch(null));
      }
      if (btnInboxClear != null) {
        btnInboxClear.setOnClickListener(
            v2 -> {
              ExportPrefsHelper.clearInbox(ctx);
              cbInboxEnabled.setChecked(false);
              updateInboxFolderLabel();
            });
      }

      // Filename template spinner
      android.widget.Spinner filenameSpinner =
          view.findViewById(R.id.dialog_inbox_filename_spinner);
      if (filenameSpinner != null) {
        String[] templateLabels = {
          getString(R.string.inbox_filename_date_scan),
          getString(R.string.inbox_filename_date_time_scan),
          getString(R.string.inbox_filename_date_only)
        };
        String[] templateValues = {"date_scan", "date_time_scan", "date_only"};
        android.widget.ArrayAdapter<String> adapter =
            new android.widget.ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item, templateLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filenameSpinner.setAdapter(adapter);

        String current = ExportPrefsHelper.getInboxFilenameTemplate(ctx);
        for (int i = 0; i < templateValues.length; i++) {
          if (templateValues[i].equals(current)) {
            filenameSpinner.setSelection(i);
            break;
          }
        }
        filenameSpinner.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
              @Override
              public void onItemSelected(
                  android.widget.AdapterView<?> parent, View v, int pos, long id) {
                ExportPrefsHelper.setInboxFilenameTemplate(ctx, templateValues[pos]);
              }

              @Override
              public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
      }

      // Auto new scan checkbox
      CheckBox cbAutoNewScan = view.findViewById(R.id.dialog_checkbox_inbox_auto_new_scan);
      if (cbAutoNewScan != null) {
        cbAutoNewScan.setChecked(ExportPrefsHelper.isInboxAutoNewScan(ctx));
        cbAutoNewScan.setOnCheckedChangeListener(
            (buttonView, isChecked) -> ExportPrefsHelper.setInboxAutoNewScan(ctx, isChecked));
      }
    }

    // Visibility toggle between PDF and JPEG groups based on the inline format selection
    updateGroups(exportAsJpeg, pdfGroup, jpegGroup);

    // PDF color mode uses RadioGroup; mutual exclusivity is handled by the group.

    // JPEG modes use RadioGroup; mutual exclusivity is handled by the group.

    return DialogUtils.createOptionsBottomSheet(
        ctx,
        getString(R.string.export_options_title),
        view,
        () -> {
          boolean incOcr = cbIncludeOcr.isChecked();
          boolean asJpeg = ExportPrefsHelper.isExportAsJpeg(ctx);

          // determine jpeg mode from RadioGroup
          JpegExportOptions.Mode mode = JpegExportOptions.Mode.NONE;
          int jpegCheckedId = jpegModeGroup.getCheckedRadioButtonId();
          boolean jpegGray = false;
          if (jpegCheckedId == rbJpegAuto.getId()) {
            jpegGray = true;
          } else if (jpegCheckedId == rbJpegBw.getId()) {
            mode = JpegExportOptions.Mode.BW_TEXT;
          }

          // determine PDF color mode from RadioGroup (null = none/original)
          String pdfBwMode = null;
          int pdfBwCheckedId = pdfBwModeGroup.getCheckedRadioButtonId();
          if (pdfBwCheckedId == rbPdfGray.getId()) pdfBwMode = "GRAYSCALE";
          else if (pdfBwCheckedId == rbPdfBwClassic.getId()) pdfBwMode = "CLASSIC";
          else if (pdfBwCheckedId == rbPdfBwRobust.getId()) pdfBwMode = "ROBUST";

          DocumentCleanupMode cleanupMode = DocumentCleanupMode.ORIGINAL;
          int cleanupCheckedId = cleanupModeGroup.getCheckedRadioButtonId();
          if (cleanupCheckedId == rbCleanupNatural.getId()) {
            cleanupMode = DocumentCleanupMode.NATURAL;
          } else if (cleanupCheckedId == rbCleanupEnhanced.getId()) {
            cleanupMode = DocumentCleanupMode.ENHANCED;
          } else if (cleanupCheckedId == rbCleanupCleanText.getId()) {
            cleanupMode = DocumentCleanupMode.CLEAN_TEXT;
          }

          // determine pdf preset
          PdfQualityPreset sel = PdfQualityPreset.STANDARD;
          int checkedId = pdfPresetGroup.getCheckedRadioButtonId();
          if (checkedId == rbHigh.getId()) sel = PdfQualityPreset.HIGH;
          else if (checkedId == rbSmall.getId()) sel = PdfQualityPreset.SMALL;
          else if (checkedId == rbVerySmall.getId()) sel = PdfQualityPreset.VERY_SMALL;

          // determine page format
          PageFormat selFormat = PageFormat.FIT_TO_IMAGE;
          int pageFormatCheckedId = pageFormatGroup.getCheckedRadioButtonId();
          if (pageFormatCheckedId == rbPageA4.getId()) selFormat = PageFormat.A4;
          else if (pageFormatCheckedId == rbPageLetter.getId()) selFormat = PageFormat.US_LETTER;
          else if (pageFormatCheckedId == rbPageLegal.getId()) selFormat = PageFormat.LEGAL;

          PdfCreator.TextLayerMode selTextLayerMode = PdfCreator.TextLayerMode.LINE_BASED;
          int textLayerCheckedId = pdfTextLayerModeGroup.getCheckedRadioButtonId();
          if (textLayerCheckedId == rbPdfTextLayerWordPositioned.getId()) {
            selTextLayerMode = PdfCreator.TextLayerMode.WORD_POSITIONED;
          }

          // persist
          SharedPreferences.Editor editor =
              prefs
                  .edit()
                  .putBoolean("include_ocr", incOcr)
                  .putBoolean("export_as_jpeg", asJpeg)
                  .putString("jpeg_mode", mode.name())
                  .putBoolean("jpeg_output_grayscale", jpegGray)
                  .putString("document_cleanup_mode", cleanupMode.name())
                  .putString("pdf_preset", sel.name())
                  .putString("page_format", selFormat.name())
                  .putString("pdf_text_layer_mode", selTextLayerMode.name());
          if (pdfBwMode != null) editor.putString("pdf_bw_mode", pdfBwMode);
          else editor.remove("pdf_bw_mode");
          editor.apply();

          Bundle result = new Bundle();
          result.putBoolean(BUNDLE_INCLUDE_OCR, incOcr);
          result.putBoolean(BUNDLE_EXPORT_AS_JPEG, asJpeg);
          result.putString(BUNDLE_JPEG_MODE, mode.name());
          result.putBoolean("jpeg_output_grayscale", jpegGray);
          result.putString("document_cleanup_mode", cleanupMode.name());
          if (pdfBwMode != null) result.putString("pdf_bw_mode", pdfBwMode);
          else result.remove("pdf_bw_mode");
          result.putString(BUNDLE_PDF_PRESET, sel.name());
          result.putString(BUNDLE_PAGE_FORMAT, selFormat.name());
          result.putString(BUNDLE_PDF_TEXT_LAYER_MODE, selTextLayerMode.name());
          getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        });
  }

  private void updateGroups(boolean exportJpeg, View pdfGroup, View jpegGroup) {
    pdfGroup.setVisibility(exportJpeg ? View.GONE : View.VISIBLE);
    jpegGroup.setVisibility(exportJpeg ? View.VISIBLE : View.GONE);
  }
}
