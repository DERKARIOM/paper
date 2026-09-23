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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.ContextCompat;
import de.schliweb.makeacopy.utils.infra.FileUtils;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The OcrModelManager class is responsible for managing OCR (Optical Character Recognition) models,
 * including the discovery of add-on packages, importing, listing, and validation of .traineddata
 * files. The class includes utility functions for managing tessdata directories, validating package
 * signatures, and handling file operations atomically.
 */
public final class OcrModelManager {
  private static final String TAG = "OcrModelManager";

  // Public action that language packs should declare in an exported Activity
  public static final String ACTION_TESSDATA = "de.schliweb.makeacopy.ACTION_TESSDATA";

  // Max file size per model (50 MiB)
  public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

  // Allowed filenames (deu.traineddata, eng.traineddata, deu+eng.traineddata, ...)
  private static final Pattern TRAINEDDATA_NAME = Pattern.compile("^[a-z0-9_+]+\\.traineddata$");

  // Whitelist of accepted certificate fingerprints (SHA-256, lowercase, without colons/spaces)
  private static final String[] ACCEPTED_CERT_SHA256 =
      new String[] {
        // Upload key
        "ae322d3fb71afe21df4727e37a5c6803511d5a2fe1fc3135430cee0699fa1b34",
        // Google Play App Signing key
        "c0714439cb516232a447917a6fc2281e45faaadd37f830b1011fb485688e0d64"
      };

  // Locks per filename (prevents parallel import of the same file)
  private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

  private OcrModelManager() {
    // no instances
  }

  /**
   * Ensures the existence of the "tessdata" directory within the application's no-backup files
   * directory. If the directory does not exist, it attempts to create it. Also performs cleanup of
   * any leftover partial files caused by previous crashes within the directory.
   *
   * @param context The application context used to locate the no-backup files directory.
   * @return The File object representing the "tessdata" directory.
   * @throws IOException If the directory could not be created.
   */
  public static File getOrCreateTessdataDir(Context context) throws IOException {
    File base = ContextCompat.getNoBackupFilesDir(context);
    File dir = new File(base, "tessdata");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException("Failed to create tessdata dir: " + dir);
    }
    // Clean up any leftover .part files from previous crashes
    cleanupOrphanParts(dir);
    return dir;
  }

  /**
   * Discovers and retrieves a list of packages on the device that support the specified addon
   * functionality defined by the action constant {@code ACTION_TESSDATA}. Only packages with
   * accepted signatures will be included in the result.
   *
   * @param context The application context used to retrieve the package manager for querying
   *     installed applications.
   * @return A list of package names that provide compatible addon functionality. Returns an empty
   *     list if no such packages are found or in case of an error.
   */
  public static List<String> discoverAddonPackages(Context context) {
    try {
      PackageManager pm = context.getPackageManager();
      Intent query = new Intent(ACTION_TESSDATA);
      List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
      List<String> pkgs = new ArrayList<>();
      for (ResolveInfo ri : infos) {
        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
          String pkg = ri.activityInfo.packageName;
          if (isPackageSignatureAccepted(context, pm, pkg)) {
            pkgs.add(pkg);
          } else {
            Log.w(TAG, "Rejected package due to signature: " + pkg);
          }
        }
      }
      return pkgs;
    } catch (Throwable t) {
      Log.e(TAG, "discoverAddonPackages failed", t);
      return new ArrayList<>();
    }
  }

  /**
   * Retrieves a list of trained data files available in the specified package's assets directory
   * under the "tessdata" folder. It filters the files to include only those that match the expected
   * naming pattern for trained data files.
   *
   * @param context The application context used for creating a package context for the specified
   *     package.
   * @param packageName The name of the package whose "tessdata" folder contents should be listed.
   * @return A list of filenames matching the trained data pattern from the specified package's
   *     "tessdata" folder. Returns an empty list in case of an error or if no matching files are
   *     found.
   */
  public static List<String> listTrainedDataInPackage(Context context, String packageName) {
    try {
      Context pc = context.createPackageContext(packageName, 0);
      AssetManager am = pc.getAssets();
      String[] files = am.list("tessdata");
      List<String> out = new ArrayList<>();
      if (files != null) {
        for (String f : files) {
          if (TRAINEDDATA_NAME.matcher(f).matches()) out.add(f);
        }
      }
      return out;
    } catch (Throwable t) {
      Log.e(TAG, "listTrainedDataInPackage failed for " + packageName, t);
      return new ArrayList<>();
    }
  }

  /**
   * Detects whether a language currently uses an imported Best model instead of the bundled Fast
   * asset. For multi-language specs, the first language component determines the variant label.
   */
  public static boolean isUsingBestModel(Context context, String code) {
    if (context == null || code == null || code.isEmpty()) return false;
    String first = code;
    int plus = code.indexOf('+');
    if (plus > 0) first = code.substring(0, plus);
    try {
      File dir = getOrCreateTessdataDir(context);
      File local = new File(dir, first + ".traineddata");
      long localSize = local.exists() ? local.length() : -1L;

      long assetSize = -1L;
      try (InputStream in = context.getAssets().open("tessdata/" + first + ".traineddata")) {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) total += n;
        assetSize = total;
      } catch (Throwable ignore) {
        assetSize = -1L;
      }

      return localSize > 0 && (assetSize < 0 || localSize > assetSize + 1024);
    } catch (Throwable ignoreAll) {
      return false;
    }
  }

  public static String[] getAvailableLanguageCodes(Context context) {
    return null;
  }

  /**
   * Imports a trained data file from a specified package's assets directory into the application's
   * local tessdata directory. Validates the filename, accesses the package's tessdata folder, and
   * performs the file copy operation atomically.
   *
   * @param context The application context used to create a package context for accessing the
   *     package's assets.
   * @param packageName The name of the package from which the trained data file should be imported.
   * @param filename The name of the trained data file to be imported. Must match the required
   *     filename pattern.
   * @return {@code true} if the import operation was successful; {@code false} otherwise (e.g.,
   *     invalid filename, IO error).
   */
  public static boolean importFromPackage(Context context, String packageName, String filename) {
    if (!TRAINEDDATA_NAME.matcher(filename).matches()) {
      Log.e(TAG, "Invalid traineddata filename: " + filename);
      return false;
    }
    try {
      Context pc = context.createPackageContext(packageName, 0);
      AssetManager am = pc.getAssets();

      // Determine size reliably when possible (only available for uncompressed assets)
      long sizeHint = -1L;
      try (AssetFileDescriptor afd = am.openFd("tessdata/" + filename)) {
        if (afd != null) sizeHint = afd.getLength();
      } catch (IOException ignored) {
        // Asset might be compressed – sizeHint remains -1
      }

      try (InputStream in = am.open("tessdata/" + filename)) {
        return copyToTessdataAtomically(context, filename, in, sizeHint);
      }
    } catch (IOException e) {
      Log.e(TAG, "importFromPackage IO error", e);
      return false;
    } catch (Throwable t) {
      Log.e(TAG, "importFromPackage failed", t);
      return false;
    }
  }

  /**
   * Imports a trained data file from the given URI into the application's local tessdata directory.
   * Validates the filename, determines the size if available, and performs the file copy operation
   * atomically.
   *
   * @param context The application context used to access the content resolver and local tessdata
   *     directory.
   * @param uri The URI of the trained data file to be imported. The filename must match the
   *     required pattern for trained data files.
   * @return {@code true} if the import operation is successful; {@code false} otherwise (e.g.,
   *     invalid filename, IO error).
   */
  public static boolean importFromUri(Context context, Uri uri) {
    ContentResolver cr = context.getContentResolver();
    String name = FileUtils.getDisplayNameFromUri(context, uri); // your helper function
    if (name == null) name = "model.traineddata";
    if (!TRAINEDDATA_NAME.matcher(name).matches()) {
      Log.e(TAG, "Invalid traineddata filename from SAF: " + name);
      return false;
    }

    long sizeHint = -1L;
    // Get stat size if available
    try (android.os.ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r")) {
      if (pfd != null) {
        long s = pfd.getStatSize();
        if (s >= 0) sizeHint = s;
      }
    } catch (IOException ignored) {
      // Best-effort; failure is non-critical
    }

    try (InputStream in = cr.openInputStream(uri)) {
      if (in == null) return false;
      return copyToTessdataAtomically(context, name, in, sizeHint);
    } catch (IOException e) {
      Log.e(TAG, "importFromUri IO error", e);
      return false;
    }
  }

  /**
   * Copies a trained data file to the application's "tessdata" directory atomically. Ensures that
   * the filename matches the required pattern, copies the data to a temporary file, and replaces
   * the existing file only if certain conditions are met, such as file size comparisons. If the new
   * file is smaller or equal in size to the existing one, the existing file will be retained.
   *
   * <p>This operation is performed in a thread-safe manner using a per-file lock.
   *
   * @param context The application context used to locate the "tessdata" directory.
   * @param filename The name of the trained data file to be processed. Must match a specific
   *     pattern.
   * @param in The input stream providing the file contents to be copied.
   * @param sizeHint The expected size of the incoming file. If unknown, pass {@code 0}.
   * @return {@code true} if the operation was successful or unnecessary (e.g., a better model
   *     already exists).
   * @throws IOException If there is an error in file operations, such as reading or writing.
   */
  private static boolean copyToTessdataAtomically(
      Context context, String filename, InputStream in, long sizeHint) throws IOException {
    if (!TRAINEDDATA_NAME.matcher(filename).matches())
      throw new IOException("Invalid filename: " + filename);

    final Object lock = LOCKS.computeIfAbsent(filename, k -> new Object());
    synchronized (lock) {
      File dir = getOrCreateTessdataDir(context);
      File target = new File(dir, filename);

      // Policy: "Best automatically replaces Fast"
      // Heuristic: Larger traineddata implies "best" -> replace; otherwise keep existing.
      final long existingLen = (target.exists() ? target.length() : 0L);

      // 1) If we know the future size and it is NOT larger → abort early
      if (existingLen > 0 && sizeHint > 0 && sizeHint <= existingLen) {
        Log.i(
            TAG,
            "Keeping existing model (new sizeHint <= existing): "
                + filename
                + " (hint="
                + sizeHint
                + "B, existing="
                + existingLen
                + "B)");
        return true;
      }

      // 2) Copy to a temp file (if sizeHint is unknown or larger)
      File tmp = File.createTempFile("import_", ".part", dir);
      long total = 0L;
      byte[] buf = new byte[64 * 1024];
      try (OutputStream out = new FileOutputStream(tmp)) {
        int n;
        while ((n = in.read(buf)) != -1) {
          out.write(buf, 0, n);
          total += n;
          if (total > MAX_FILE_SIZE_BYTES) {
            throw new IOException("Exceeded max size (" + MAX_FILE_SIZE_BYTES + " bytes)");
          }
        }
        out.flush();
        try {
          ((FileOutputStream) out).getFD().sync();
        } catch (Throwable ignore) {
          // Best-effort; failure is non-critical
        }
      }

      // 3) After copying: Decide whether to replace
      if (existingLen > 0 && total <= existingLen) {
        // New file is not larger → assume that "best" is already present
        Log.i(
            TAG,
            "Keeping existing model (new <= existing): "
                + filename
                + " (new="
                + total
                + "B, existing="
                + existingLen
                + "B)");
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return true;
      }

      // 4) Finalize – remove existing file if present, then replace (rename with fallback)
      if (target.exists() && !target.delete()) {
        Log.w(TAG, "Could not delete existing model before replace: " + target);
      }

      boolean renamed = tmp.renameTo(target);
      if (!renamed) {
        try (InputStream rin = new FileInputStream(tmp);
            OutputStream rout = new FileOutputStream(target)) {
          int n;
          while ((n = rin.read(buf)) != -1) {
            rout.write(buf, 0, n);
          }
          rout.flush();
          try {
            ((FileOutputStream) rout).getFD().sync();
          } catch (Throwable ignore) {
            // Best-effort; failure is non-critical
          }
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
      }

      Log.i(
          TAG,
          "Imported/updated model: "
              + filename
              + " (new="
              + total
              + "B, prev="
              + existingLen
              + "B)");
      return true;
    }
  }

  /**
   * Deletes a locally imported ("Best") model for a given language code from the app's no-backup
   * tessdata directory. This removes only the local file (e.g., deu.traineddata) and does not
   * affect bundled assets. Returns true if the file existed and was deleted or did not exist
   * anymore; false on IO errors.
   */
  public static boolean deleteLocalModel(Context context, String langCode) {
    if (langCode == null || langCode.trim().isEmpty()) return false;
    String filename = langCode + ".traineddata";
    if (!TRAINEDDATA_NAME.matcher(filename).matches()) {
      Log.e(TAG, "deleteLocalModel: invalid lang code/filename: " + filename);
      return false;
    }
    final Object lock = LOCKS.computeIfAbsent(filename, k -> new Object());
    synchronized (lock) {
      try {
        File dir = getOrCreateTessdataDir(context);
        File target = new File(dir, filename);
        if (!target.exists()) {
          // Already gone → treat as success to simplify UX
          return true;
        }
        boolean ok = target.delete();
        if (!ok) {
          // Try rewrite empty then delete as a fallback
          try (FileOutputStream fos = new FileOutputStream(target, false)) {
            fos.getFD().sync();
          } catch (Throwable ignored) {
            // Best-effort; failure is non-critical
          }
          ok = target.delete();
        }
        if (!ok) Log.w(TAG, "deleteLocalModel: failed to delete " + target);
        return ok;
      } catch (Throwable t) {
        Log.e(TAG, "deleteLocalModel failed for code=" + langCode, t);
        return false;
      }
    }
  }

  /**
   * Cleans up orphaned ".part" files in the specified directory. A file is considered an orphan if
   * it is empty or its name starts with "import_". Such files are deleted to maintain a clean state
   * in the directory.
   *
   * @param dir The directory to search for orphan ".part" files. Must not be null.
   */
  private static void cleanupOrphanParts(File dir) {
    File[] parts = dir.listFiles((d, name) -> name != null && name.endsWith(".part"));
    if (parts == null) return;
    for (File f : parts) {
      //noinspection ResultOfMethodCallIgnored
      if (f.length() == 0 || f.getName().startsWith("import_")) {
        f.delete();
      }
    }
  }

  /**
   * Determines whether the signature of the specified package is accepted. The method verifies the
   * package's signature against a predefined whitelist of accepted signatures and optionally
   * against the host app's signature.
   *
   * @param context the context of the current application
   * @param pm the package manager used to retrieve package information
   * @param packageName the name of the package whose signature is being verified
   * @return true if the package's signature matches a predefined whitelist or the host app's
   *     signature, false otherwise
   */
  private static boolean isPackageSignatureAccepted(
      Context context, PackageManager pm, String packageName) {
    try {
      int flags = PackageManager.GET_SIGNING_CERTIFICATES;
      PackageInfo pi = pm.getPackageInfo(packageName, flags);
      if (pi == null) return false;

      String[] candidateDigests;

      if (pi.signingInfo != null) {
        // Consider key rotation: use certificate history instead of only current signers
        Signature[] sigs =
            pi.signingInfo.hasMultipleSigners()
                ? pi.signingInfo.getApkContentsSigners()
                : pi.signingInfo.getSigningCertificateHistory();
        candidateDigests = computeDigests(sigs);
      } else {
        candidateDigests = new String[0];
      }

      HashSet<String> whitelist = new HashSet<>();
      for (String s : ACCEPTED_CERT_SHA256) whitelist.add(normalizeHex(s));

      // 1) Check against whitelist
      for (String d : candidateDigests) {
        if (whitelist.contains(normalizeHex(d))) {
          Log.i(TAG, "Accepted package (signature whitelist): " + packageName);
          return true;
        }
      }

      // 2) Fallback: accept the same signature as the host app (useful for debug/side-load)
      try {
        PackageInfo selfPi = pm.getPackageInfo(context.getPackageName(), flags);
        String[] selfDigests;
        if (selfPi != null && selfPi.signingInfo != null) {
          Signature[] selfSigs =
              selfPi.signingInfo.hasMultipleSigners()
                  ? selfPi.signingInfo.getApkContentsSigners()
                  : selfPi.signingInfo.getSigningCertificateHistory();
          selfDigests = computeDigests(selfSigs);
        } else {
          selfDigests = new String[0];
        }
        HashSet<String> selfSet = new HashSet<>();
        for (String s : selfDigests) selfSet.add(normalizeHex(s));

        for (String d : candidateDigests) {
          if (selfSet.contains(normalizeHex(d))) {
            Log.i(TAG, "Accepted package (same signing cert as host): " + packageName);
            return true;
          }
        }
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }

      Log.w(TAG, "Rejected package (no matching signature): " + packageName);
      return false;
    } catch (Throwable t) {
      Log.e(TAG, "Signature check failed for " + packageName, t);
      return false;
    }
  }

  /**
   * Computes the SHA-256 digests for the provided array of signatures. Converts each signature into
   * a byte array and generates its corresponding SHA-256 hash in hexadecimal format. If the input
   * array is null or empty, an empty array is returned.
   *
   * @param sigs An array of {@link Signature} objects for which the digests are to be computed.
   *     Each signature's byte representation is processed to generate the SHA-256 hash.
   * @return An array of hexadecimal SHA-256 digest strings. The order of the digests corresponds to
   *     the order of the input signatures. Returns an empty array if the input array is null or
   *     empty.
   */
  private static String[] computeDigests(Signature[] sigs) {
    if (sigs == null || sigs.length == 0) return new String[0];
    String[] out = new String[sigs.length];
    for (int i = 0; i < sigs.length; i++) {
      byte[] certBytes = sigs[i].toByteArray();
      out[i] = sha256(certBytes);
    }
    return out;
  }

  /**
   * Normalizes a hexadecimal string by removing colons and whitespace, and converting it to
   * lowercase.
   *
   * @param s The input string to be normalized. It may contain colons, spaces, or uppercase
   *     letters. If the input is null, an empty string will be returned.
   * @return A normalized hexadecimal string without colons or spaces and in lowercase. If the input
   *     is null, returns an empty string.
   */
  private static String normalizeHex(String s) {
    if (s == null) return "";
    return s.replace(":", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Computes the SHA-256 hash of the given byte array and returns it as a hexadecimal string. If an
   * error occurs during the hash computation, an empty string is returned.
   *
   * @param data The byte array to be hashed. Must not be null.
   * @return A hexadecimal string representation of the SHA-256 hash of the input data. Returns an
   *     empty string if the hash computation fails.
   */
  private static String sha256(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(data);
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return "";
    }
  }

  /**
   * Creates and returns an intent to open a document, specifically targeting files that may
   * represent trained data or similar binary files. The returned intent is configured to include a
   * set of relevant MIME types, allowing the selection of files with various binary or generic file
   * formats.
   *
   * @return An {@link Intent} pre-configured to allow users to open and select files matching
   *     specified MIME type criteria, including binary and general-purpose file types.
   */
  public static Intent createOpenTraineddataIntent() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*"); // we validate the name later
    intent.putExtra(
        Intent.EXTRA_MIME_TYPES,
        new String[] {"application/octet-stream", "application/x-binary", "application/*", "*/*"});
    return intent;
  }
}
