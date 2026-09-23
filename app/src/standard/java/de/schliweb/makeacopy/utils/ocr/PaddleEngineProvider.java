/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

import android.content.Context;

/**
 * A utility class that provides methods to create and manage Paddle OCR engine instances.
 * This class is not intended to be instantiated.
 */
public final class PaddleEngineProvider {

    private PaddleEngineProvider() {}

    /**
     * Creates and initializes a new instance of the OCR engine configured for the specified language.
     *
     * @param context the application context used to access resources and file paths
     * @param language the language code for OCR processing, specifying the desired language model
     * @return an initialized instance of the {@code OcrEngine}, or {@code null} if initialization fails
     */
    static OcrEngine create(Context context, String language) {
        return null;
    }

    /**
     * Releases all resources and cleans up any internal state associated with the OCR engine instances.
     * This operation has no effect in the standard flavor.
     *
     * @param context the application context used for resource and file management
     */
    public static void releaseAll(Context context) {
        // no-op in standard flavor
    }
}
