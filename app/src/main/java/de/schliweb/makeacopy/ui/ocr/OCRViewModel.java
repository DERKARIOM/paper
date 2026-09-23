/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.ui.ocr;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel class for managing OCR operations. Provides LiveData to observe the state and data of
 * the OCR process. Extends the functionality of BaseViewModel.
 */
public class OCRViewModel extends ViewModel {
  private static final String TAG = "OCRViewModel";

  /**
   * Retrieves the currently selected language from the application state. The returned language is
   * derived from the `mState` LiveData object and defaults to "eng" (English) if the language is
   * not set or if the state is null.
   *
   * @return A LiveData<String> representing the language code. Defaults to "eng" if unavailable.
   */
  public LiveData<String> getLanguage() {
    return androidx.lifecycle.Transformations.map(
        mState, s -> s != null && s.language() != null ? s.language() : "eng");
  }

  /**
   * Describes the coordinate transform from original image to target (e.g. A4) dimensions.
   *
   * @param offsetY letterboxing, else 0
   */
  // Transform Original → Target (for example A4)
  public record OcrTransform(
      int srcW,
      int srcH,
      int dstW,
      int dstH,
      float scaleX,
      float scaleY,
      int offsetX,
      int offsetY) {}

  /**
   * Immutable snapshot of the OCR UI state.
   *
   * @param durationMs optional
   * @param meanConfidence optional (0..100)
   * @param transform optional
   * @param reviewedText text after review (null if not reviewed)
   * @param reviewedWords words after review (null if not reviewed)
   * @param hasReviewEdits true if review has been applied
   */
  // UI-State
  public record OcrUiState(
      boolean processing,
      boolean imageProcessed,
      String language,
      String ocrText,
      List<RecognizedWord> words,
      Long durationMs,
      Integer meanConfidence,
      OcrTransform transform,
      String reviewedText,
      List<RecognizedWord> reviewedWords,
      boolean hasReviewEdits) {
    /** Creates a new OcrUiState. */
    public OcrUiState {}

    public OcrUiState withProcessing(boolean p) {
      return new OcrUiState(
          p,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withImageProcessed(boolean ip) {
      return new OcrUiState(
          processing,
          ip,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withText(String t) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          t,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withWords(List<RecognizedWord> w) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          w,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withLanguage(String lang) {
      return new OcrUiState(
          processing,
          imageProcessed,
          lang,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withDuration(Long ms) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          ms,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withMeanConfidence(Integer mc) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          mc,
          transform,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withTransform(OcrTransform tx) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          tx,
          reviewedText,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withReviewedText(String rt) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          rt,
          reviewedWords,
          hasReviewEdits);
    }

    public OcrUiState withReviewedWords(List<RecognizedWord> rw) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          rw,
          hasReviewEdits);
    }

    public OcrUiState withHasReviewEdits(boolean hre) {
      return new OcrUiState(
          processing,
          imageProcessed,
          language,
          ocrText,
          words,
          durationMs,
          meanConfidence,
          transform,
          reviewedText,
          reviewedWords,
          hre);
    }

    /**
     * Returns the effective text for display/export: reviewed text if available, otherwise OCR
     * text.
     */
    public String getEffectiveText() {
      return (reviewedText != null && hasReviewEdits) ? reviewedText : ocrText;
    }

    /**
     * Returns the effective words for display/export: reviewed words if available, otherwise OCR
     * words.
     */
    public List<RecognizedWord> getEffectiveWords() {
      return (reviewedWords != null && hasReviewEdits) ? reviewedWords : words;
    }
  }

  /** Event wrapper for LiveData. */
  public static final class Event<T> {
    private final T data;
    private boolean handled = false;

    public Event(T data) {
      this.data = data;
    }

    public T getContentIfNotHandled() {
      if (handled) return null;
      handled = true;
      return data;
    }

    public T peek() {
      return data;
    }
  }

  private final MutableLiveData<OcrUiState> mState =
      new MutableLiveData<>(
          new OcrUiState(
              false, false, "eng", "", new ArrayList<>(), null, null, null, null, null, false));
  private final MutableLiveData<Event<String>> mErrorEvents = new MutableLiveData<>();
  private final MutableLiveData<Event<Void>> mNavigateToExport = new MutableLiveData<>();

  public LiveData<OcrUiState> getState() {
    return mState;
  }

  public LiveData<Event<String>> getErrorEvents() {
    return mErrorEvents;
  }

  public LiveData<Event<Void>> getNavigateToExport() {
    return mNavigateToExport;
  }

  /** Resets the UI-State for a new image. Also clears any review edits. */
  public void resetForNewImage() {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(
        new OcrUiState(
            false, false, s.language, "", new ArrayList<>(), null, null, null, null, null, false));
    Log.d(TAG, "resetForNewImage");
  }

  /** Sets the language. */
  public void setLanguage(String lang) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(s.withLanguage(lang));
    Log.d(TAG, "setLanguage: " + lang);
  }

  /** Starts processing. Clears any previous review edits. */
  public void startProcessing() {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(
        new OcrUiState(
            true,
            false,
            s.language,
            "",
            new ArrayList<>(),
            null,
            null,
            s.transform,
            null,
            null,
            false));
    Log.d(TAG, "startProcessing");
  }

  /** Sets the OCR text and stops processing. Clears any previous review edits. */
  public void finishSuccess(
      String text, List<RecognizedWord> words, long durationMs, Integer meanConf, OcrTransform tx) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    if (words == null) words = new ArrayList<>();
    String safeText = text != null ? text : "";
    // Normalize confidence for empty results (no text, no words).
    // This prevents a “high-confidence but empty” result from being treated as successful content
    // in the state.

    boolean hasWords = !words.isEmpty();
    boolean hasText = safeText != null && !safeText.trim().isEmpty();
    boolean hasContent = hasWords || hasText;
    Integer normalizedConf = hasContent ? meanConf : 0;
    if (!hasContent) {
      safeText = "";
      words = new ArrayList<>();
    }

    mState.setValue(
        new OcrUiState(
            false,
            true,
            s.language,
            safeText,
            words,
            durationMs,
            normalizedConf,
            tx != null ? tx : s.transform,
            null,
            null,
            false));
    Log.d(
        TAG,
        "finishSuccess: "
            + Math.min(safeText.length(), 100)
            + " chars, words="
            + words.size()
            + ", ms="
            + durationMs
            + ", conf="
            + normalizedConf);
  }

  /** Sets an error message and stops processing. */
  public void finishError(String msg) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(s.withProcessing(false).withImageProcessed(false).withText(""));
    mErrorEvents.setValue(new Event<>(msg));
    Log.d(TAG, "finishError: " + msg);
  }

  public void requestNavigateToExport() {
    mNavigateToExport.setValue(new Event<>(null));
  }

  /** Sets the transform (for example A4) */
  public void setTransform(OcrTransform tx) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(s.withTransform(tx));
  }

  /** Sets the recognized words */
  public void setWords(List<RecognizedWord> words) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(s.withWords(words != null ? words : new ArrayList<>()));
  }

  /**
   * Applies the result from the OCR Review screen. This sets the reviewed text and words, marking
   * that review edits have been applied.
   *
   * @param reviewedText the text after review
   * @param reviewedWords the words after review (may be null)
   */
  public void applyReviewResult(String reviewedText, List<RecognizedWord> reviewedWords) {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(
        s.withReviewedText(reviewedText)
            .withReviewedWords(reviewedWords != null ? reviewedWords : new ArrayList<>())
            .withHasReviewEdits(true));
    Log.d(
        TAG,
        "applyReviewResult: "
            + (reviewedText != null ? Math.min(reviewedText.length(), 100) : 0)
            + " chars");
  }

  /** Clears any review edits, reverting to the original OCR result. */
  public void clearReviewEdits() {
    OcrUiState s = mState.getValue();
    if (s == null) return;
    mState.setValue(s.withReviewedText(null).withReviewedWords(null).withHasReviewEdits(false));
    Log.d(TAG, "clearReviewEdits");
  }
}
