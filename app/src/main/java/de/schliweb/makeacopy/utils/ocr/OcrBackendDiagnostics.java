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

import android.util.Log;

/**
 * A utility class for logging and managing diagnostics related to OCR backend routing decisions.
 * The primary functionality includes recording routing decisions with relevant details and ensuring
 * deduplication of log entries during a single session.
 *
 * <p>This class is designed to centralize diagnostics for OCR backend handling and includes a
 * nested class, {@link Reason}, which defines predefined constants representing routing decision
 * reasons. The class is not intended to be instantiated.
 */
final class OcrBackendDiagnostics {
  private static final String TAG = "OCR_BACKEND";

  private static volatile String lastDecision;

  private OcrBackendDiagnostics() {}

  /**
   * Defines a set of constants representing various reasons for OCR backend routing decisions.
   * These reasons describe why certain engines or configurations may be enabled or disabled during
   * processing.
   *
   * <p>This class is a static utility and is not meant to be instantiated.
   */
  static final class Reason {
    static final String DISABLED_BY_FLAG = "disabled-by-flag";
    static final String UNSUPPORTED_ABI = "unsupported-abi";
    static final String UNSUPPORTED_LANG = "unsupported-lang";
    static final String TOGGLE_OFF = "toggle-off";
    static final String PADDLE_INIT_FAILED = "paddle-init-failed";
    static final String PADDLE_OK = "paddle-ok";

    private Reason() {}
  }

  /**
   * Records information about OCR backend routing decisions and logs the details. This method
   * avoids duplicating log entries by comparing the current routing decision with the last recorded
   * decision. If the current decision matches the previous one, logging is skipped to reduce
   * redundancy.
   *
   * @param engine the name of the OCR engine being used
   * @param lang the language involved in the routing decision
   * @param abi the application binary interface (ABI) of the current platform
   * @param featureFlag a boolean indicating if the feature is enabled via a feature flag
   * @param toggleEnabled a boolean specifying if the toggle is enabled
   * @param routingDecisionReason the reason for the particular routing decision
   */
  static void record(
      String engine,
      String lang,
      String abi,
      boolean featureFlag,
      boolean toggleEnabled,
      String routingDecisionReason) {
    String line =
        "engine="
            + engine
            + " lang="
            + lang
            + " abi="
            + abi
            + " featureFlag="
            + featureFlag
            + " toggleEnabled="
            + toggleEnabled
            + " routingDecisionReason="
            + routingDecisionReason;
    if (line.equals(lastDecision)) {
      return;
    }
    lastDecision = line;
    Log.i(TAG, line);
  }

  /**
   * Resets the internal state of the diagnostics utility for testing purposes. Specifically, this
   * method clears the last recorded routing decision to ensure that subsequent test cases start
   * with a clean state and can log decisions without interference from previous state.
   */
  static void resetForTesting() {
    lastDecision = null;
  }
}
