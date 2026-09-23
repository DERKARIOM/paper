/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.infra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression test for the Nextcloud-style "extension stripped on create" issue. Uses {@link
 * StripExtensionDocumentsProvider} as a fake SAF provider that drops the file extension when a
 * document is created. Verifies that {@link FileUtils#ensureExtension(Context, Uri, String)}
 * recovers the missing extension via {@link DocumentsContract#renameDocument}.
 */
@RunWith(AndroidJUnit4.class)
public class FileUtilsEnsureExtensionInstrumentedTest {

  @Test
  public void ensureExtension_recoversStrippedPdfExtension() throws Exception {
    // Use the test APK's own context so we can access the test-only DocumentsProvider,
    // which is hosted in the test APK process (different UID than the app-under-test).
    Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
    ContentResolver cr = ctx.getContentResolver();

    // Build the URI for the (virtual) root directory provided by our test provider.
    Uri parent =
        DocumentsContract.buildDocumentUri(
            StripExtensionDocumentsProvider.AUTHORITY, StripExtensionDocumentsProvider.ROOT_ID);

    // Simulate the SAF "Create document" call as done by ACTION_CREATE_DOCUMENT.
    Uri created =
        DocumentsContract.createDocument(cr, parent, "application/pdf", "Scan_2026-04-22.pdf");
    assertNotNull("createDocument must not return null", created);

    // Sanity: the misbehaving provider stripped the extension.
    String createdName = FileUtils.getDisplayNameFromUri(ctx, created);
    assertEquals("Scan_2026-04-22", createdName);
    assertTrue(
        "ensureExtension precondition: name must be missing the .pdf extension",
        FileUtils.needsExtension(createdName, ".pdf"));

    // Act: ensure the extension is added.
    Uri fixed = FileUtils.ensureExtension(ctx, created, ".pdf");

    // Assert: name now has .pdf and the URI points at a real, openable document.
    assertNotNull(fixed);
    String fixedName = FileUtils.getDisplayNameFromUri(ctx, fixed);
    assertEquals("Scan_2026-04-22.pdf", fixedName);
    assertTrue("Fixed URI must be readable", FileUtils.isUriReadable(ctx, fixed));
  }

  @Test
  public void ensureExtension_noopWhenExtensionAlreadyPresent() throws Exception {
    Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
    ContentResolver cr = ctx.getContentResolver();

    Uri parent =
        DocumentsContract.buildDocumentUri(
            StripExtensionDocumentsProvider.AUTHORITY, StripExtensionDocumentsProvider.ROOT_ID);

    // Provider strips ".pdf" -> stored as "already_ok.pdf" only if no extension; here we deliberately
    // craft a name that the provider keeps intact (no dot).
    Uri created = DocumentsContract.createDocument(cr, parent, "text/plain", "no_dot_name");
    assertNotNull(created);

    // Manually rename it to a name with extension to simulate "already correct" state.
    Uri renamed = DocumentsContract.renameDocument(cr, created, "already_ok.txt");
    assertNotNull(renamed);
    assertEquals("already_ok.txt", FileUtils.getDisplayNameFromUri(ctx, renamed));

    Uri result = FileUtils.ensureExtension(ctx, renamed, ".txt");
    // No rename needed -> same URI returned.
    assertEquals(renamed, result);
    assertEquals("already_ok.txt", FileUtils.getDisplayNameFromUri(ctx, result));
  }
}
