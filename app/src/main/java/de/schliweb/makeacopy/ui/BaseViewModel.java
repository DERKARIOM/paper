/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Abstract base class for ViewModel implementations that provides common functionality for managing
 * LiveData properties like text and image URI. Extends the Android ViewModel class to ensure
 * lifecycle-aware operations for UI components.
 */
public abstract class BaseViewModel extends ViewModel {

  private final MutableLiveData<String> mText;
  private final MutableLiveData<Uri> mImageUri;

  /**
   * Constructor for BaseViewModel
   *
   * @param defaultText The default text to display
   */
  protected BaseViewModel(String defaultText) {
    mText = new MutableLiveData<>();
    mText.setValue(defaultText);

    mImageUri = new MutableLiveData<>();
  }

  /**
   * Gets the text to display
   *
   * @return LiveData containing the text
   */
  public LiveData<String> getText() {
    return mText;
  }

  /**
   * Sets the text to display
   *
   * @param text The text to display
   */
  public void setText(String text) {
    mText.setValue(text);
  }

  /**
   * Gets the URI of the image
   *
   * @return LiveData containing the image URI
   */
  public LiveData<Uri> getImageUri() {
    return mImageUri;
  }

  /**
   * Sets the URI of the image
   *
   * @param uri The URI of the image
   */
  public void setImageUri(Uri uri) {
    mImageUri.setValue(uri);
  }
}
