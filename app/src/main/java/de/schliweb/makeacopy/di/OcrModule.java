/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.di;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;

/** Hilt module that provides OCR-related dependencies. */
@Module
@InstallIn(SingletonComponent.class)
public class OcrModule {

  /**
   * Provides a new {@link OCRHelper} instance on each injection. OCRHelper holds mutable Tesseract
   * state and must not be shared as a singleton.
   */
  @Provides
  OCRHelper provideOCRHelper(@ApplicationContext Context context) {
    return new OCRHelper(context);
  }
}
