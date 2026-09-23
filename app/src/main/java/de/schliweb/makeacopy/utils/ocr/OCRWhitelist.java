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

/**
 * A utility class that provides predefined whitelists for characters based on specific languages,
 * commonly used in Optical Character Recognition (OCR) processes. This class helps restrict the
 * character set to improve OCR accuracy and reduce errors.
 *
 * <p>This class is not intended to be instantiated.
 */
public class OCRWhitelist {

  // Common base: digits, punctuation, and symbols shared across all Latin-script languages
  private static final String COMMON_BASE =
      "0123456789.,:;-?!()[]/\"' %\u20AC\u00A7+=<>&@#*_|\\{}~$\u00A3\u00A5\u00B0\u00A9\u00AE\u2122\u2020\u2021\u2022\u2026\u2013\u2014\u00AB\u00BB\u2039\u203A\u201E\u201C\u201D\u2018\u2019\u00BF\u00A1";

  // German
  public static final String DE =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜabcdefghijklmnopqrstuvwxyzäöüß" + COMMON_BASE;

  // English
  public static final String EN =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" + COMMON_BASE;

  // Spanish
  public static final String ES =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúüñÁÉÍÓÚÜÑ" + COMMON_BASE;

  // French
  public static final String FR =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzàâäçéèêëîïôöùûüÿœæÀÂÄÇÉÈÊËÎÏÔÖÙÛÜŸŒÆ"
          + COMMON_BASE;

  // Italian
  public static final String IT =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzàèéìíîòóùúÀÈÉÌÍÎÒÓÙÚ" + COMMON_BASE;

  // Portuguese
  public static final String PT =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáàâãçéêíóôõúüÁÀÂÃÇÉÊÍÓÔÕÚÜ"
          + COMMON_BASE;

  // Dutch
  public static final String NL =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÀÂÄÉÈÊËÍÌÎÏÓÒÔÖÚÙÛÜáàâäéèêëíìîïóòôöúùûüÿŸ"
          + COMMON_BASE;

  // Polish
  public static final String PL =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzĄĆĘŁŃÓŚŹŻąćęłńóśźż" + COMMON_BASE;

  // Czech
  public static final String CS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁČĎÉĚÍŇÓŘŠŤÚŮÝŽáčďéěíňóřšťúůýž"
          + COMMON_BASE;

  // Slovak
  public static final String SK =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÄČĎÉÍĹĽŇÓÔŔŠŤÚÝŽáäčďéíĺľňóôŕšťúýž"
          + COMMON_BASE;

  // Hungarian
  public static final String HU =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÉÍÓÖŐÚÜŰáéíóöőúüű" + COMMON_BASE;

  // Romanian
  public static final String RO =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZĂÂÎȘȚŞŢabcdefghijklmnopqrstuvwxyzăâîșțşţ" + COMMON_BASE;

  // Danish
  public static final String DA =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZÆØÅabcdefghijklmnopqrstuvwxyzæøå" + COMMON_BASE;

  // Norwegian
  public static final String NO =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZÆØÅabcdefghijklmnopqrstuvwxyzæøå" + COMMON_BASE;

  // Swedish
  public static final String SV =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZÅÄÖabcdefghijklmnopqrstuvwxyzåäö" + COMMON_BASE;

  // Turkish (includes İ/ı, ğ, ş, ç, ö, ü)
  public static final String TR =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÇĞİÖŞÜçğıöşü" + COMMON_BASE;

  // Russian (Cyrillic incl. Ё/ё)
  public static final String RU =
      "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя" + COMMON_BASE;

  // Hindi (Devanagari: vowels, consonants, dependent vowel signs, Anusvara, Visarga, Chandrabindu,
  // Nukta, Virama/Halant, common conjuncts, Devanagari digits, Danda/Double Danda)
  private static final String DEVANAGARI_BASE =
      "\u0901\u0902\u0903" // Chandrabindu, Anusvara, Visarga
          + "\u0905\u0906\u0907\u0908\u0909\u090A\u090B\u090C\u090D\u090E\u090F\u0910\u0911\u0912\u0913\u0914" // independent vowels
          + "\u0915\u0916\u0917\u0918\u0919\u091A\u091B\u091C\u091D\u091E" // consonants ka-nya
          + "\u091F\u0920\u0921\u0922\u0923\u0924\u0925\u0926\u0927\u0928" // consonants tta-na
          + "\u0929\u092A\u092B\u092C\u092D\u092E\u092F\u0930\u0931\u0932" // consonants nna-la
          + "\u0933\u0934\u0935\u0936\u0937\u0938\u0939" // consonants lla-ha
          + "\u093C" // Nukta
          + "\u093E\u093F\u0940\u0941\u0942\u0943\u0944\u0945\u0946\u0947\u0948\u0949\u094A\u094B\u094C" // dependent vowel signs
          + "\u094D" // Virama (Halant)
          + "\u0950" // OM
          + "\u0964\u0965" // Danda, Double Danda
          + "\u0966\u0967\u0968\u0969\u096A\u096B\u096C\u096D\u096E\u096F"; // Devanagari digits 0-9

  public static final String HI = DEVANAGARI_BASE + COMMON_BASE;

  // Default: Superset
  public static final String DEFAULT =
      (DE + EN + ES + FR + IT + PT + NL + PL + CS + SK + HU + RO + DA + NO + SV + TR + RU + HI);

  /**
   * Returns a predefined whitelist of allowed characters for a given language code. The whitelist
   * is used to improve processing accuracy by restricting the character set.
   *
   * @param languageCode The ISO language code (e.g., "deu" for German, "eng" for English). When
   *     null or an unsupported code is provided, a default whitelist is returned.
   * @return A string containing the whitelist of allowed characters for the specified language, or
   *     the default whitelist if the language code is null or unsupported.
   */
  public static String getWhitelistForLanguage(String languageCode) {
    if (languageCode == null) return DEFAULT;
    return switch (languageCode) {
      case "deu" -> DE;
      case "eng" -> EN;
      case "spa" -> ES;
      case "fra" -> FR;
      case "ita" -> IT;
      case "por" -> PT;
      case "nld" -> NL;
      case "pol" -> PL;
      case "ces" -> CS;
      case "slk" -> SK;
      case "hun" -> HU;
      case "ron" -> RO;
      case "dan" -> DA;
      case "nor" -> NO;
      case "swe" -> SV;
      case "tur" -> TR;
      case "rus" -> RU;
      case "hin" -> HI;
      default -> DEFAULT;
    };
  }

  /**
   * Generates a composite whitelist of allowed characters for a given language specification. The
   * language specification can consist of multiple language codes separated by a "+". Each language
   * code will correspond to a predefined whitelist, and their characters are combined into a single
   * whitelist, ensuring no duplicate characters are present.
   *
   * @param langSpec A string containing one or more ISO language codes separated by "+" (e.g.,
   *     "eng+deu+fra"). If null or empty, a default whitelist is returned.
   * @return A string containing the combined whitelist of allowed characters for the specified
   *     language specification, or the default whitelist if the input is null or empty.
   */
  public static String getWhitelistForLangSpec(String langSpec) {
    if (langSpec == null || langSpec.trim().isEmpty()) return DEFAULT;
    StringBuilder sb = new StringBuilder();
    for (String part : langSpec.split("\\+", -1)) {
      String lang = part.trim();
      if (!lang.isEmpty()) sb.append(getWhitelistForLanguage(lang));
    }
    // cleanup duplicates
    return sb.chars()
        .distinct()
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  /**
   * Filters the given text, removing any characters that are not in the provided whitelist.
   * Whitespace characters (spaces, tabs, newlines) are always preserved.
   *
   * @param text the text to filter
   * @param whitelist the allowed characters; if null or empty, the text is returned unchanged
   * @return the filtered text containing only whitelisted characters and whitespace
   */
  public static String filterByWhitelist(String text, String whitelist) {
    if (text == null || text.isEmpty()) return text;
    if (whitelist == null || whitelist.isEmpty()) return text;
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c) || whitelist.indexOf(c) >= 0) {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
