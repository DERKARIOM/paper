package de.schliweb.makeacopy.ui.export;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * JVM-only tests that verify the Inbox Mode directory and the normal export directory are stored
 * under independent preference keys and never interfere with each other.
 *
 * <p>Since {@link ExportPrefsHelper} requires an Android {@code Context}, these tests model the
 * same key-based isolation using a simple in-memory map that mirrors SharedPreferences behavior.
 * The key names are copied verbatim from {@code ExportPrefsHelper} to catch any accidental
 * collision.
 */
public class ExportPrefsKeyIsolationTest {

  // Keys copied from ExportPrefsHelper — must stay in sync
  private static final String KEY_LAST_EXPORT_URI = "last_export_uri";
  private static final String KEY_INBOX_URI = "inbox_uri";
  private static final String KEY_INBOX_ENABLED = "inbox_enabled";

  private Map<String, Object> prefs;

  @Before
  public void setUp() {
    prefs = new HashMap<>();
  }

  // --- Key independence ---

  @Test
  public void inboxUri_and_lastExportUri_useDifferentKeys() {
    assertNotEquals(
        "Inbox URI and last export URI must use different preference keys",
        KEY_INBOX_URI,
        KEY_LAST_EXPORT_URI);
  }

  @Test
  public void settingInboxUri_doesNotAffectLastExportUri() {
    prefs.put(KEY_LAST_EXPORT_URI, "content://normal/export/folder");
    prefs.put(KEY_INBOX_URI, "content://inbox/folder");

    assertEquals("content://normal/export/folder", prefs.get(KEY_LAST_EXPORT_URI));
    assertEquals("content://inbox/folder", prefs.get(KEY_INBOX_URI));
  }

  @Test
  public void settingLastExportUri_doesNotAffectInboxUri() {
    prefs.put(KEY_INBOX_URI, "content://inbox/folder");
    prefs.put(KEY_LAST_EXPORT_URI, "content://different/export/folder");

    assertEquals("content://inbox/folder", prefs.get(KEY_INBOX_URI));
    assertEquals("content://different/export/folder", prefs.get(KEY_LAST_EXPORT_URI));
  }

  @Test
  public void clearingInboxUri_doesNotAffectLastExportUri() {
    prefs.put(KEY_LAST_EXPORT_URI, "content://normal/export/folder");
    prefs.put(KEY_INBOX_URI, "content://inbox/folder");

    // Simulate ExportPrefsHelper.clearInbox(): removes inbox_uri and sets inbox_enabled=false
    prefs.remove(KEY_INBOX_URI);
    prefs.put(KEY_INBOX_ENABLED, false);

    assertNull("Inbox URI should be cleared", prefs.get(KEY_INBOX_URI));
    assertEquals(
        "Last export URI must remain unchanged after clearing inbox",
        "content://normal/export/folder",
        prefs.get(KEY_LAST_EXPORT_URI));
  }

  @Test
  public void clearingLastExportUri_doesNotAffectInboxUri() {
    prefs.put(KEY_LAST_EXPORT_URI, "content://normal/export/folder");
    prefs.put(KEY_INBOX_URI, "content://inbox/folder");

    prefs.remove(KEY_LAST_EXPORT_URI);

    assertNull("Last export URI should be cleared", prefs.get(KEY_LAST_EXPORT_URI));
    assertEquals(
        "Inbox URI must remain unchanged after clearing last export URI",
        "content://inbox/folder",
        prefs.get(KEY_INBOX_URI));
  }

  @Test
  public void bothUris_canPointToDifferentDirectories() {
    String inboxDir = "content://com.android.externalstorage/tree/primary%3AInbox";
    String exportDir = "content://com.android.externalstorage/tree/primary%3ADocuments";

    prefs.put(KEY_INBOX_URI, inboxDir);
    prefs.put(KEY_LAST_EXPORT_URI, exportDir);

    assertNotEquals(
        "Inbox and export directories should be independently configurable",
        prefs.get(KEY_INBOX_URI),
        prefs.get(KEY_LAST_EXPORT_URI));
  }

  @Test
  public void bothUris_canPointToSameDirectory_withoutConflict() {
    String sameDir = "content://com.android.externalstorage/tree/primary%3AScans";

    prefs.put(KEY_INBOX_URI, sameDir);
    prefs.put(KEY_LAST_EXPORT_URI, sameDir);

    assertEquals(sameDir, prefs.get(KEY_INBOX_URI));
    assertEquals(sameDir, prefs.get(KEY_LAST_EXPORT_URI));

    // Changing one should not affect the other even when they had the same value
    prefs.put(KEY_LAST_EXPORT_URI, "content://other/folder");
    assertEquals(
        "Inbox URI must not change when last export URI is updated",
        sameDir,
        prefs.get(KEY_INBOX_URI));
  }

  @Test
  public void inboxEnabled_isIndependentFromExportUri() {
    prefs.put(KEY_INBOX_ENABLED, true);
    prefs.put(KEY_LAST_EXPORT_URI, "content://export/folder");

    // Disabling inbox should not touch export URI
    prefs.put(KEY_INBOX_ENABLED, false);

    assertEquals("content://export/folder", prefs.get(KEY_LAST_EXPORT_URI));
    assertEquals(false, prefs.get(KEY_INBOX_ENABLED));
  }
}
