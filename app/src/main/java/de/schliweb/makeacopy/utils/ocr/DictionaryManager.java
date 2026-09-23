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
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Manages dictionaries for OCR post-processing correction. Dictionaries are loaded lazily from
 * assets and cached in memory.
 *
 * <p>Supported languages correspond to Tesseract language codes: deu, eng, fra, spa, ita, por, nld,
 * pol, ces, slk, hun, ron, dan, nor, swe, rus, chi_sim, chi_tra, tha
 */
public class DictionaryManager {

  private static final String TAG = "DictionaryManager";
  private static final String DICT_DIR = "dictionaries";
  private static final String DICT_EXTENSION_GZ = ".txt.gz";
  private static final String DICT_EXTENSION_TXT = ".txt";
  private static final Pattern PLUS_SPLITTER = Pattern.compile("\\+");
  // Languages that don't use word-based dictionaries (CJK, Thai)
  private static final Set<String> NON_WORD_BASED_LANGUAGES = new HashSet<>();
  private static DictionaryManager instance;

  static {
    NON_WORD_BASED_LANGUAGES.add("chi_sim");
    NON_WORD_BASED_LANGUAGES.add("chi_tra");
    NON_WORD_BASED_LANGUAGES.add("tha");
  }

  private final Context context;
  private final Map<String, Set<String>> loadedDictionaries = new HashMap<>();

  private DictionaryManager(Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Gets the singleton instance of DictionaryManager.
   *
   * @param context Application context
   * @return DictionaryManager instance
   */
  public static synchronized DictionaryManager getInstance(Context context) {
    if (instance == null) {
      instance = new DictionaryManager(context);
    }
    return instance;
  }

  /**
   * Checks if the language uses word-based dictionary correction. CJK and Thai languages don't use
   * word boundaries like Western languages. For multi-language specs (e.g., "fas+eng"), returns
   * true if ANY language is word-based.
   *
   * @param language Tesseract language code (may contain multiple languages separated by "+")
   * @return true if word-based correction is applicable for at least one language
   */
  public static boolean isWordBasedLanguage(String language) {
    if (language == null) {
      return false;
    }
    // Check all languages in spec - if ANY is word-based, return true
    for (String lang : PLUS_SPLITTER.split(language, -1)) {
      String trimmed = lang.trim();
      if (!trimmed.isEmpty() && !NON_WORD_BASED_LANGUAGES.contains(trimmed)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if a word exists in the dictionary for the given language.
   *
   * @param word The word to check (case-insensitive)
   * @param language Tesseract language code (e.g., "deu", "eng")
   * @return true if the word is in the dictionary, false otherwise
   */
  public boolean isValidWord(String word, String language) {
    if (word == null || word.isEmpty() || language == null) {
      return false;
    }

    // Skip dictionary check for non-word-based languages
    if (NON_WORD_BASED_LANGUAGES.contains(language)) {
      return true;
    }

    Set<String> dictionary = getDictionary(language);
    if (dictionary.isEmpty()) {
      return true; // No dictionary available, assume valid
    }

    return dictionary.contains(word.toLowerCase(Locale.ROOT));
  }

  /**
   * Gets the dictionary for a language, loading it if necessary. For multi-language specs (e.g.,
   * "fas+eng"), combines dictionaries from all languages.
   *
   * @param language Tesseract language code (may contain multiple languages separated by "+")
   * @return Set of words in the dictionary (lowercase), or empty set if not available
   */
  public Set<String> getDictionary(String language) {
    if (language == null) {
      return Collections.emptySet();
    }

    // Handle multi-language specs (e.g., "fas+eng")
    if (language.contains("+")) {
      // Create combined dictionary from all languages
      Set<String> combined = new HashSet<>();
      int totalWords = 0;
      for (String lang : PLUS_SPLITTER.split(language, -1)) {
        String trimmed = lang.trim();
        if (!trimmed.isEmpty()) {
          Set<String> langDict = getDictionarySingle(trimmed);
          combined.addAll(langDict);
          totalWords += langDict.size();
        }
      }
      Log.i(
          TAG,
          "Combined dictionary for "
              + language
              + ": "
              + combined.size()
              + " unique words (from "
              + totalWords
              + " total)");
      return combined;
    }

    return getDictionarySingle(language);
  }

  /**
   * Gets the dictionary for a single language, loading it if necessary.
   *
   * @param language Single Tesseract language code (without "+")
   * @return Set of words in the dictionary (lowercase), or empty set if not available
   */
  private Set<String> getDictionarySingle(String language) {
    // Check cache
    if (loadedDictionaries.containsKey(language)) {
      return loadedDictionaries.get(language);
    }

    // Load dictionary
    Set<String> dictionary = loadDictionary(language);
    loadedDictionaries.put(language, dictionary);
    return dictionary;
  }

  /**
   * Loads a dictionary from assets. Tries uncompressed .txt first (Android may decompress .gz
   * during APK packaging), then falls back to .txt.gz if not found.
   *
   * @param language Tesseract language code
   * @return Set of words (lowercase)
   */
  private Set<String> loadDictionary(String language) {
    Set<String> words = new HashSet<>();

    // Try uncompressed .txt first (Android decompresses .gz files during APK packaging)
    String filenameTxt = DICT_DIR + "/" + language + DICT_EXTENSION_TXT;
    try (InputStream is = context.getAssets().open(filenameTxt);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

      String line;
      while ((line = br.readLine()) != null) {
        String word = line.trim().toLowerCase(Locale.ROOT);
        if (!word.isEmpty() && !word.startsWith("#")) {
          words.add(word);
        }
      }

      Log.i(
          TAG, "Loaded dictionary for " + language + ": " + words.size() + " words (uncompressed)");
      return words;

    } catch (IOException e) {
      // Try compressed .gz version as fallback
      Log.d(TAG, "Uncompressed dictionary not found for " + language + ", trying .gz");
    }

    // Fallback: try .txt.gz (original compressed format)
    String filenameGz = DICT_DIR + "/" + language + DICT_EXTENSION_GZ;
    try (InputStream is = context.getAssets().open(filenameGz);
        GZIPInputStream gis = new GZIPInputStream(is);
        BufferedReader br =
            new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {

      String line;
      while ((line = br.readLine()) != null) {
        String word = line.trim().toLowerCase(Locale.ROOT);
        if (!word.isEmpty() && !word.startsWith("#")) {
          words.add(word);
        }
      }

      Log.i(TAG, "Loaded dictionary for " + language + ": " + words.size() + " words (compressed)");

    } catch (IOException e) {
      Log.w(TAG, "Dictionary not available for " + language + ": " + e.getMessage());
      // Return empty set - dictionary not available
    }

    return words;
  }

  /**
   * Checks if a dictionary is available for the given language. Checks for both uncompressed .txt
   * and compressed .txt.gz files. For multi-language specs (e.g., "fas+eng"), returns true if ANY
   * language has a dictionary.
   *
   * @param language Tesseract language code (may contain multiple languages separated by "+")
   * @return true if dictionary exists for at least one language
   */
  public boolean hasDictionary(String language) {
    if (language == null) {
      return false;
    }

    try {
      String[] files = context.getAssets().list(DICT_DIR);
      if (files == null) {
        return false;
      }
      Set<String> fileSet = new HashSet<>(Arrays.asList(files));

      // Check all languages in spec
      for (String lang : PLUS_SPLITTER.split(language, -1)) {
        String trimmed = lang.trim();
        if (!trimmed.isEmpty()) {
          String targetFileTxt = trimmed + DICT_EXTENSION_TXT;
          String targetFileGz = trimmed + DICT_EXTENSION_GZ;
          if (fileSet.contains(targetFileTxt) || fileSet.contains(targetFileGz)) {
            return true;
          }
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Error checking dictionary availability", e);
    }

    return false;
  }

  /**
   * Preloads dictionaries for the specified languages. Call this in background to avoid loading
   * delay during OCR.
   *
   * @param languages Array of Tesseract language codes
   */
  public void preloadDictionaries(String... languages) {
    for (String lang : languages) {
      getDictionary(lang);
    }
  }

  /** Clears all cached dictionaries to free memory. */
  public void clearCache() {
    loadedDictionaries.clear();
    Log.i(TAG, "Dictionary cache cleared");
  }

  /**
   * Gets the number of words in the dictionary for a language.
   *
   * @param language Tesseract language code
   * @return Number of words, or 0 if not loaded/available
   */
  public int getDictionarySize(String language) {
    return getDictionary(language).size();
  }
}
