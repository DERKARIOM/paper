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

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * The ExportViewModel class is responsible for managing the state and data related to document
 * export functionality. It extends the ViewModel class to provide lifecycle-aware data management
 * and ensure that data survives configuration changes.
 *
 * <p>This ViewModel handles the following: - Document export settings such as export format,
 * inclusion of OCR, and grayscale conversion. - Management of the document's bitmap and OCR text. -
 * Management of selected export file location and TXT export URI. - Tracks export status and
 * document readiness.
 */
public class ExportViewModel extends ViewModel {
  private static final String TAG = "ExportViewModel";

  private final MutableLiveData<String> mText;
  private final MutableLiveData<Boolean> mDocumentReady;
  private final MutableLiveData<Boolean> mIsExporting;
  private final MutableLiveData<Integer> mExportProgress;
  private final MutableLiveData<Integer> mExportProgressMax;
  private final MutableLiveData<String> mExportFormat;
  private final MutableLiveData<Boolean> mIncludeOcr;
  private final MutableLiveData<Boolean> mConvertToGrayscale;

  // Selected file location
  private final MutableLiveData<Uri> mSelectedFileLocation;
  private final MutableLiveData<String> mSelectedFileLocationName;

  // Document bitmap and OCR text
  private final MutableLiveData<Bitmap> mDocumentBitmap;
  private final MutableLiveData<String> mOcrText;

  // TXT export URI for OCR text
  private final MutableLiveData<Uri> mTxtExportUri;

  public ExportViewModel() {
    mText = new MutableLiveData<>();
    mText.setValue("Export Fragment");

    mDocumentReady = new MutableLiveData<>();
    mDocumentReady.setValue(false);

    mIsExporting = new MutableLiveData<>();
    mIsExporting.setValue(false);

    mExportProgress = new MutableLiveData<>();
    mExportProgress.setValue(0);
    mExportProgressMax = new MutableLiveData<>();
    mExportProgressMax.setValue(0);

    mExportFormat = new MutableLiveData<>();
    mExportFormat.setValue("PDF");

    mIncludeOcr = new MutableLiveData<>();
    mIncludeOcr.setValue(
        false); // Default to false - only export OCR as TXT when explicitly requested

    mConvertToGrayscale = new MutableLiveData<>();
    mConvertToGrayscale.setValue(false); // Default to grayscale for better compression

    // Initialize selected file location
    mSelectedFileLocation = new MutableLiveData<>();
    mSelectedFileLocationName = new MutableLiveData<>();
    mSelectedFileLocationName.setValue("Default save location");

    // Initialize document bitmap and OCR text
    mDocumentBitmap = new MutableLiveData<>();
    mOcrText = new MutableLiveData<>();

    // Initialize TXT export URI
    mTxtExportUri = new MutableLiveData<>();
  }

  public LiveData<String> getText() {
    return mText;
  }

  public LiveData<Boolean> isDocumentReady() {
    return mDocumentReady;
  }

  public void setDocumentReady(boolean ready) {
    mDocumentReady.setValue(ready);
  }

  public LiveData<Boolean> isExporting() {
    return mIsExporting;
  }

  public void setExporting(boolean exporting) {
    mIsExporting.setValue(exporting);
  }

  // Export progress (0..max), only used for multi-page export operations
  public LiveData<Integer> getExportProgress() {
    return mExportProgress;
  }

  public LiveData<Integer> getExportProgressMax() {
    return mExportProgressMax;
  }

  public void setExportProgress(int value) {
    mExportProgress.setValue(Math.max(0, value));
  }

  public void setExportProgressMax(int max) {
    mExportProgressMax.setValue(Math.max(0, max));
  }

  public LiveData<String> getExportFormat() {
    return mExportFormat;
  }

  public void setExportFormat(String format) {
    String fmt = (format == null) ? "PDF" : format.trim().toUpperCase(java.util.Locale.ROOT);
    if (!"PDF".equals(fmt) && !"JPEG".equals(fmt)) {
      fmt = "PDF";
    }
    mExportFormat.setValue(fmt);
  }

  public LiveData<Boolean> isIncludeOcr() {
    return mIncludeOcr;
  }

  public void setIncludeOcr(boolean includeOcr) {
    mIncludeOcr.setValue(includeOcr);
  }

  /**
   * Gets whether to convert the image to grayscale
   *
   * @return LiveData containing whether to convert to grayscale
   */
  public LiveData<Boolean> isConvertToGrayscale() {
    return mConvertToGrayscale;
  }

  /**
   * Sets whether to convert the image to grayscale
   *
   * @param convertToGrayscale Whether to convert to grayscale
   */
  public void setConvertToGrayscale(boolean convertToGrayscale) {
    mConvertToGrayscale.setValue(convertToGrayscale);
  }

  /**
   * Gets the document bitmap
   *
   * @return LiveData containing the document bitmap
   */
  public LiveData<Bitmap> getDocumentBitmap() {
    return mDocumentBitmap;
  }

  /**
   * Sets the document bitmap
   *
   * @param bitmap The bitmap of the document
   */
  public void setDocumentBitmap(Bitmap bitmap) {
    Log.d(TAG, "Setting document bitmap: " + (bitmap != null ? "non-null" : "null"));
    mDocumentBitmap.setValue(bitmap);

    // Set document ready if we have a bitmap
    setDocumentReady(bitmap != null);
  }

  /**
   * Gets the OCR text
   *
   * @return LiveData containing the OCR text
   */
  public LiveData<String> getOcrText() {
    return mOcrText;
  }

  /**
   * Sets the OCR text
   *
   * @param text The OCR text
   */
  public void setOcrText(String text) {
    Log.d(TAG, "Setting OCR text: " + (text != null ? "non-null" : "null"));
    mOcrText.setValue(text);
  }

  /**
   * Gets the selected file location URI
   *
   * @return LiveData containing the selected file location URI
   */
  public LiveData<Uri> getSelectedFileLocation() {
    return mSelectedFileLocation;
  }

  /**
   * Sets the selected file location URI
   *
   * @param uri The URI of the selected file location
   */
  public void setSelectedFileLocation(Uri uri) {
    mSelectedFileLocation.setValue(uri);
  }

  /**
   * Gets the selected file location name (user-friendly display)
   *
   * @return LiveData containing the selected file location name
   */
  public LiveData<String> getSelectedFileLocationName() {
    return mSelectedFileLocationName;
  }

  /**
   * Sets the selected file location name (user-friendly display)
   *
   * @param name The name of the selected file location
   */
  public void setSelectedFileLocationName(String name) {
    mSelectedFileLocationName.setValue(name);
  }

  /**
   * Gets the TXT export URI for OCR text
   *
   * @return LiveData containing the TXT export URI
   */
  public LiveData<Uri> getTxtExportUri() {
    return mTxtExportUri;
  }

  /**
   * Sets the TXT export URI for OCR text
   *
   * @param uri The URI of the exported TXT file
   */
  public void setTxtExportUri(Uri uri) {
    mTxtExportUri.setValue(uri);
  }
}
