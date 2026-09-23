# Design note: Live focus-quality (sharpness) indicator

Status: **implemented, enabled by default and user-toggleable** (follow-up to GitHub issue #78 "Manual focus").

Implementation: `FocusQualityAnalyzer` (Laplacian variance, ThreadLocal Mats), `FocusQualityMeter`
(decaying rolling-max normalization + EMA + 5-segment mapping, JVM-unit-tested), and
`FocusQualityIndicatorView` (5-segment bar near the shutter button), wired into
`CameraFragment#analyzeFrameForCorners` behind `FeatureFlags.isFocusQualityIndicatorEnabled()`
(`FEATURE_FOCUS_QUALITY_INDICATOR`, default `true`; runtime override via
`FeatureFlags.setFocusQualityIndicatorOverride`). On top of the build flag, the user controls the
indicator via its own checkbox in the camera options dialog
(`CameraOptionsDialogFragment.BUNDLE_FOCUS_QUALITY_INDICATOR`); the setting is independent of the
live corner detection preference — when only the indicator is enabled, the analysis pipeline runs
without corner detection and the sharpness ROI falls back to the full frame.

## Goal

Give the user live feedback on document sharpness in the camera preview, so that

- manual focus can be adjusted by watching the indicator peak while dragging the slider, and
- blurry captures (and therefore poor OCR results) are avoided before pressing the shutter.

## Chosen method: Laplacian variance

Compared alternatives:

| Method                  | Cost                              | Suitability for documents |
|-------------------------|-----------------------------------|---------------------------|
| **Laplacian variance**  | 1 `Imgproc.Laplacian` + `Core.meanStdDev` | Best: text strokes are high-frequency; de-facto standard |
| Tenengrad (Sobel)       | 2 Sobel passes                    | Good, slightly more expensive |
| Brenner                 | Very cheap                        | Too crude (paper texture vs. blur) |
| Edge density (Canny)    | Expensive, threshold-sensitive    | Confounds "amount of text" with "sharpness" |

## Integration plan (CameraFragment)

1. Reuse the existing `ImageAnalysis` pipeline: `analyzeFrameForCorners()` already produces a
   small upright bitmap (`yuvToBitmapUprightSmall`) on a throttled analyzer thread — compute the
   metric there, **only on throttled frames** (reuse `lastAnalysisTs` gating), never per frame.
2. Region of interest: when document corners are detected, restrict the measurement to the
   bounding rect of the detected quad. This removes background-content bias, the main weakness
   of Laplacian variance.
3. Normalization: absolute values are scene-dependent. Track a rolling maximum for the current
   scene and display `current / rollingMax` as a relative score (reset the rolling max when the
   detected quad changes significantly or the camera rebinds).
4. UI: small 3–5 segment bar (or green/yellow/red dot) near the shutter button; not color-only
   (segment count conveys the level). Announce significant changes for accessibility only with
   rate limiting (reuse the `lastA11ySignalTs` pattern).
5. Feature flag: keep the whole feature behind `FeatureFlags` and disabled by default until the
   per-device performance and normalization behavior are validated.

## Risks

- Analyzer-thread cost on low-end devices → mitigate via existing downscaled bitmap, frame
  throttling, and reusable Mats (`ThreadLocal` pattern already in place).
- Scene-dependent thresholds → mitigated by the relative (rolling max) scale.
- On `LENS_INFO_FOCUS_DISTANCE_CALIBRATION = UNCALIBRATED` devices the indicator becomes the
  primary manual-focus aid, since the slider value has no physical meaning there (see the
  calibration logging in `CameraFragment#setupManualFocusControl`).
