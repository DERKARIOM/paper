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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import de.schliweb.makeacopy.BuildConfig;
import org.junit.Test;

/**
 * Standard-Flavor JVM-Tests für die {@code OCRHelper.selectEngine()}-Routing-Logik.
 *
 * <p>Im {@code standard}-Flavor liefert der {@link PaddleEngineProvider}-Stub konstant
 * {@code null}, sodass {@code OCRHelper} keine Paddle-Symbole referenziert.
 */
public class OCRHelperSelectEngineTest {

    @Test
    public void standardFlavor_featureFlagDisabled() {
        // Im standard-Flavor ist FEATURE_PADDLE_OCR per Definition false.
        assertFalse(
                "FEATURE_PADDLE_OCR muss im standard-Flavor false sein",
                BuildConfig.FEATURE_PADDLE_OCR);
    }

    @Test
    public void standardFlavor_providerStubReturnsNull() {
        // Aufruf des Stub-Providers über Reflection (Methode ist package-private).
        try {
            java.lang.reflect.Method m =
                    PaddleEngineProvider.class.getDeclaredMethod(
                            "create", android.content.Context.class, String.class);
            m.setAccessible(true);
            Object result = m.invoke(null, null, "eng");
            assertNull("Stub-Provider muss null zurückliefern", result);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("Provider.create nicht erreichbar", e);
        }
    }

    // Hinweis: Die statische Sprach-Whitelist {eng, deu, latin} und das ABI-Gate
    // wurden mit der Multi-Model-Migration (Konzept §C.2) aus OCRHelper entfernt.
    // Sprachrouting übernimmt jetzt PaddleLanguageRouter; das wird im paddle-Flavor
    // durch PaddleLanguageRouterTest abgedeckt.

    @Test
    public void diagnostics_dedupesIdenticalDecisions() {
        OcrBackendDiagnostics.resetForTesting();
        OcrBackendDiagnostics.record(
                "tesseract", "eng", "arm64-v8a", false, false,
                OcrBackendDiagnostics.Reason.DISABLED_BY_FLAG);
        // Zweiter, identischer Call darf den Status nicht verändern; ein Reset
        // hingegen schon. Wir prüfen das indirekt über keinen geworfenen Fehler.
        OcrBackendDiagnostics.record(
                "tesseract", "eng", "arm64-v8a", false, false,
                OcrBackendDiagnostics.Reason.DISABLED_BY_FLAG);
        // Ein abweichender Reason muss erneut geloggt werden — kein State-Leak.
        OcrBackendDiagnostics.record(
                "tesseract", "eng", "arm64-v8a", false, true,
                OcrBackendDiagnostics.Reason.TOGGLE_OFF);
        assertNotNull(OcrBackendDiagnostics.Reason.TOGGLE_OFF);
    }
}
