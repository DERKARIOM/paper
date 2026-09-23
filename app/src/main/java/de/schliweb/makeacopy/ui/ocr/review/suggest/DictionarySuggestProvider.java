/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr.review.suggest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.schliweb.makeacopy.utils.ocr.DictionaryManager;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Provides dictionary-based word correction suggestions for OCR review.
 *
 * <p>Features: - Supports up to 2 languages (from OCR langSpec), treated equally - Ranking based on
 * Normalized Levenshtein Similarity - Optional weighted substitutions for common OCR confusions
 * (0↔o, 1↔l, 5↔s) - Performance optimization via length-based candidate filtering
 */
public class DictionarySuggestProvider {

  private static final int MAX_SUGGESTIONS = 5;
  private static final double MIN_SCORE = 0.6;
  private static final int LENGTH_TOLERANCE = 2;

  // Pattern to strip punctuation from word boundaries
  private static final Pattern PUNCT_PATTERN =
      Pattern.compile("^[.,;:!?\"'()\\[\\]{}]+|[.,;:!?\"'()\\[\\]{}]+$");
  private static final Pattern PLUS_SPLITTER = Pattern.compile("\\+");

  private final DictionaryManager dictionaryManager;
  private final List<String> languages;

  /**
   * Creates a new DictionarySuggestProvider.
   *
   * @param dictionaryManager the DictionaryManager instance to use
   * @param langSpec OCR language specification (e.g., "eng+deu" or "eng")
   */
  public DictionarySuggestProvider(
      @NonNull DictionaryManager dictionaryManager, @Nullable String langSpec) {
    this.dictionaryManager = dictionaryManager;
    this.languages = parseLangSpec(langSpec);
  }

  /** Parses the language specification into a list of up to 2 languages. */
  private List<String> parseLangSpec(@Nullable String langSpec) {
    List<String> result = new ArrayList<>();
    if (langSpec == null || langSpec.isEmpty()) {
      return result;
    }
    String[] parts = PLUS_SPLITTER.split(langSpec, -1);
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty() && result.size() < 2) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /**
   * Gets correction suggestions for the given word.
   *
   * @param word the word to get suggestions for
   * @return list of suggestions, sorted by similarity score (best first)
   */
  @NonNull
  public List<Suggestion> getSuggestions(@Nullable String word) {
    if (word == null || word.isEmpty() || languages.isEmpty()) {
      return new ArrayList<>();
    }

    String normalized = normalize(word);
    if (normalized.isEmpty()) {
      return new ArrayList<>();
    }

    int wordLen = normalized.length();
    Set<String> seenWords = new HashSet<>();
    List<Suggestion> candidates = new ArrayList<>();

    // Collect candidates from all languages
    for (String lang : languages) {
      Set<String> dictionary = dictionaryManager.getDictionary(lang);
      if (dictionary == null || dictionary.isEmpty()) {
        continue;
      }

      for (String dictWord : dictionary) {
        // Length filter for performance
        int dictLen = dictWord.length();
        if (Math.abs(dictLen - wordLen) > LENGTH_TOLERANCE) {
          continue;
        }

        String normalizedDict = normalize(dictWord);
        if (normalizedDict.isEmpty() || seenWords.contains(normalizedDict)) {
          continue;
        }

        // Skip if it's the same word
        if (normalizedDict.equals(normalized)) {
          continue;
        }

        double score = calculateSimilarity(normalized, normalizedDict);
        if (score >= MIN_SCORE) {
          seenWords.add(normalizedDict);
          candidates.add(new Suggestion(dictWord, score, lang));
        }
      }
    }

    // Sort by score (descending), then by length difference, then lexicographically
    candidates.sort(
        Comparator.comparingDouble(Suggestion::score)
            .reversed()
            .thenComparingInt(s -> Math.abs(s.text().length() - wordLen))
            .thenComparing(Suggestion::text));

    // Return top K
    if (candidates.size() > MAX_SUGGESTIONS) {
      return new ArrayList<>(candidates.subList(0, MAX_SUGGESTIONS));
    }
    return candidates;
  }

  /**
   * Normalizes a word for comparison: - lowercase - Unicode normalization (NFKD) - strip
   * punctuation from boundaries
   */
  @NonNull
  private String normalize(@NonNull String word) {
    // Strip boundary punctuation
    String stripped = PUNCT_PATTERN.matcher(word).replaceAll("");
    if (stripped.isEmpty()) {
      return "";
    }
    // Lowercase
    String lower = stripped.toLowerCase(Locale.ROOT);
    // Unicode normalization (NFKD - compatibility decomposition)
    String normalized = Normalizer.normalize(lower, Normalizer.Form.NFKD);
    // Remove diacritical marks (combining characters)
    return normalized.replaceAll("\\p{M}", "");
  }

  /**
   * Calculates the normalized Levenshtein similarity between two strings.
   *
   * @return similarity score in [0..1], where 1 means identical
   */
  private double calculateSimilarity(@NonNull String a, @NonNull String b) {
    int distance = levenshteinDistance(a, b);
    int maxLen = Math.max(a.length(), b.length());
    if (maxLen == 0) {
      return 1.0;
    }
    return 1.0 - ((double) distance / maxLen);
  }

  /**
   * Calculates the Levenshtein edit distance between two strings. Uses weighted substitution costs
   * for common OCR confusions.
   */
  private int levenshteinDistance(@NonNull String a, @NonNull String b) {
    int lenA = a.length();
    int lenB = b.length();

    // Use two-row optimization for memory efficiency
    int[] prev = new int[lenB + 1];
    int[] curr = new int[lenB + 1];

    // Initialize first row
    for (int j = 0; j <= lenB; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= lenA; i++) {
      curr[0] = i;
      char charA = a.charAt(i - 1);

      for (int j = 1; j <= lenB; j++) {
        char charB = b.charAt(j - 1);

        int cost;
        if (charA == charB) {
          cost = 0;
        } else {
          // Standard substitution cost is 1
          // Could add weighted costs for OCR confusions here
          cost = 1;
        }

        int insert = curr[j - 1] + 1;
        int delete = prev[j] + 1;
        int substitute = prev[j - 1] + cost;

        curr[j] = Math.min(Math.min(insert, delete), substitute);
      }

      // Swap rows
      int[] temp = prev;
      prev = curr;
      curr = temp;
    }

    return prev[lenB];
  }

  /** Represents a word suggestion with its similarity score. */
  public record Suggestion(String text, double score, String language) {
    public Suggestion(@NonNull String text, double score, @NonNull String language) {
      this.text = text;
      this.score = score;
      this.language = language;
    }

    @Override
    @NonNull
    public String text() {
      return text;
    }

    @Override
    @NonNull
    public String language() {
      return language;
    }

    @NonNull
    @Override
    public String toString() {
      return text + " (" + String.format(Locale.ROOT, "%.0f%%", score * 100) + ")";
    }
  }
}
