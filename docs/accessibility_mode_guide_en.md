# MakeACopy – Accessibility Mode

This guide explains step by step how to use Accessibility Mode in MakeACopy. The mode is designed to operate the camera without visual cues — using clear spoken announcements and gentle haptic feedback instead.

Note: When this guide mentions “tap”, “double tap”, or “button”, it refers to typical screen reader usage such as TalkBack.

---

Contents
- What does Accessibility Mode do?
- Turn on Accessibility Mode
- Operating the camera (flow)
- Feedback in detail
- Using the volume keys to capture
- Behavior in low light (flashlight)
- Success and error messages after capture
- Tips for good results
- Privacy and offline operation
- Frequently Asked Questions (FAQ)

---

What does Accessibility Mode do?
- Focuses on audio and haptic feedback. The visual corner overlay follows the "Preview corner detection" option.
- Speaks important states, e.g., “Camera ready”, “Low light”, “Document detected – ready to capture.”
- Provides alignment guidance when needed, e.g., “Move left”, “Move up”, “Move closer”, “Tilt forward”.
- Allows capturing using the hardware volume keys so you don’t have to aim for a small on‑screen button.
- Works fully offline — no data connection, no upload.

Turn on Accessibility Mode
1) In the camera screen, open the “Options” button at the bottom.
2) Enable the checkbox “Accessibility Mode”.
3) Confirm with “Confirm”.

From now on:
- You will receive spoken hints and short haptic feedback. If you enable "Preview corner detection" in Options, the corner overlay is shown; otherwise it stays hidden.

Operating the camera (flow)
1) Open the camera: After initialization you’ll hear “Camera ready. Double tap Scan to capture.”
2) Align: Move the device over the document. The system continuously analyzes the camera image.
3) Good framing detected: As soon as the document is detected well and stably, you’ll hear a short confirmation tone, a light vibration, and the announcement “Document detected – ready to capture.”
4) Capture:
   - Double tap the “Scan” button OR
   - Press one of the two volume keys (see section below).

Feedback in detail
- Camera ready: Spoken as soon as the camera starts.
- Document detected (stable):
  - Short tone
  - Light haptic feedback
  - Spoken announcement “Document detected – ready to capture.”
  - Note: These signals are rate‑limited so they don’t repeat constantly.
- Low light: Instead of a pop‑up dialog, you’ll hear “Low light detected. Double tap Flash to enable.”
- Toggle flashlight: When switching, you’ll hear “Flashlight on/off”.

 Alignment guidance (examples)
 - Movement:
   - “Move left” / “Move right”
   - “Move up” / “Move down”
 - Distance:
   - “Move closer” / “Move back”
   - "Move closer. Document is too far away." (when the document is detected but too small in the frame)
 - Tilt:
   - “Tilt left” / “Tilt right”
   - “Tilt forward” / “Tilt back”
 - Alignment OK: “Document aligned”

 Frequency notes:
 - Announcements are intentionally calm: speech occurs only after brief stability (hysteresis) and at most about every 1–1.5 seconds (rate limit).
 - “OK” (aligned) is typically only spoken when entering a good state or after a longer quiet period to avoid repetition.

Using the volume keys to capture
- When Accessibility Mode is active, you can capture with Volume Up or Volume Down.
- The app suppresses the actual volume change and triggers capture instead.
- A short vibration is provided on key press.
- Inputs are debounced: Pressing repeatedly within about 0.8 seconds won’t trigger multiple captures.
- While an image is being processed, key presses are ignored to avoid conflicts.

Behavior in low light (flashlight)
- If the app detects low light, it does not show a blocking prompt in Accessibility Mode.
- Instead, you’ll hear a spoken recommendation to enable the flashlight.
- Double tap the “Flash” button to enable/disable it. You will hear “Flashlight on” or “Flashlight off”.


Success and error messages after capture
- Saved successfully: “Image captured.” plus a short vibration.
- Capture failed: “Capture failed.” — you can trigger capture again afterwards.


Tips for good results
- Distance: Hold the device so the document fully fits in the frame (typically 20–40 cm / 8–16 in above the page).
- Stability: A brief moment of steady holding helps detection.
- Alignment: If possible, keep the device parallel to the document surface.
- Light: Turn on the flashlight in shadows or very low light.

Privacy and offline operation
- MakeACopy processes images locally on your device.
- No upload or sharing takes place in Accessibility Mode. No internet connection required.

Frequently Asked Questions (FAQ)
See also: Website FAQ → Scanning (Camera): docs/index.html#faq-scanning
Q: I hear “Document detected” very often.
A: The app already limits repetitions. Move the device slightly away and realign. When the detection is stable you’ll get a tone + vibration + announcement.

Q: The volume keys still change the volume.
A: This should not happen in Accessibility Mode while the camera preview is visible. Ensure Accessibility Mode is enabled and the camera is on screen. While processing an image, key presses are ignored.

Q: It’s very dark and detection doesn’t work.
A: Turn on the flashlight. Try to illuminate the document as evenly and shadow‑free as possible.


Q: Do I need to enable the visual “Preview corner detection” (Analysis) for Accessibility Mode to work?
A: No. Accessibility Mode runs the required analysis internally even if the visual analysis option is turned off. The app still analyzes frames to provide audio/haptic feedback.

Q: If both “Preview corner detection” (Live analysis) and Accessibility Mode are enabled, is the camera preview visible?
A: Yes. The normal camera preview remains visible. If "Preview corner detection" is enabled, the visual corner overlay is also shown even in Accessibility Mode. Analysis runs in the background regardless, to power guidance and readiness feedback.

In Accessibility Mode, the app does not rely on a single “model score”. Instead it uses the detected document shape (a quadrilateral) plus stability over multiple frames to decide when guidance is useful and when the document is “ready”.

Q: Does Accessibility Mode work without TalkBack?
A: Partly. Spoken output requires an active screen reader (e.g., TalkBack or Select‑to‑Speak). Without a screen reader, you still get tones and gentle haptics, but no speech.

<a id="guide-en-directional-hints-landscape"></a>
Q: What do “left/right/up/down” mean if I hold the phone in landscape?
A: Note about holding the phone (landscape): the camera screen stays in portrait orientation. The directional hints (“left/right/up/down”) refer to the upright-aligned preview.

If you hold the phone sideways, depending on your device/Android version,
- the hints may still behave like in portrait (because the UI does not switch to landscape), or
- the internal analysis axes may follow the display’s rotation.

If “left/right/up/down” feels confusing, return to portrait or rotate the phone by 180° and check whether the hints make more sense.

<a id="guide-en-orientation-tip"></a>
Q: Does the app suggest portrait vs. landscape?
A: Yes. In Accessibility Mode, the app can suggest whether portrait or landscape seems more appropriate for the current page.

The tip is only given when
- the estimate is sufficiently confident (confidence ≥ 0.30), and
- no plausible document is currently detected (so it does not override the normal guidance).

To stay calm, the tip goes through the same guidance logic as other announcements (brief stability over multiple frames) and is rate-limited. You may hear, for example, “This looks like portrait …” or “… like landscape …”. Normal directional guidance stays the same.

Q: How does the app decide when a document is “ready to capture”?
A: The app looks for a plausible document shape (a quadrilateral) and checks whether it stays stable for a brief moment. Only then it plays a short tone + light vibration and announces “Document detected – ready to capture.”

If the detection is unstable (e.g., because you are moving, the page is partially out of frame, or the lighting is difficult), you may hear directional hints instead.

Q: How can I improve detection and guidance?
A: Use even, bright lighting and avoid glare; hold the phone parallel to the page; keep all four corners in view with a small margin; if you’re too close, step back a little and crop later; place the paper on a high‑contrast, matte background; keep still briefly so detection can stabilize; match orientation (A4/Letter: portrait usually fits best).

Tip: A more detailed explanation with examples is available on the website: docs/index.html → FAQ → “Scanning (Camera)”.

<a id="guide-en-move-back"></a>
Q: I keep hearing “Move back” all the time.
A: Distance prompts are suppressed without a plausible document and rate‑limited. Improve lighting, include the whole page, and hold still briefly.

—

Technical note (for contributors)

For a detailed explanation of the underlying “plausibility + framing metrics + stability” concept (including a spec sheet and QA scenarios), see: `docs/accessibility_mode_docquad_concept.md`.

—

Contact
If anything is unclear or you have suggestions to improve accessibility, we welcome feedback in app store reviews or in the project repository.
