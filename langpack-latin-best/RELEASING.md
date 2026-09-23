# Releasing: MakeACopy OCR Latin (Best)

## 1) Tagging convention

- **Prefix:** `langpack-latin-best-`
- **Pattern:** `vMAJOR.MINOR.PATCH`
- **Examples:**
  - `langpack-latin-best-v1.0.0`
  - `langpack-latin-best-v1.0.1`

> **Why?**  
> The separate prefix ensures that only the Langpack workflow is triggered, not the main app workflow.

---

## 2) Versioning in Gradle & Fastlane

- In `langpack-latin-best/build.gradle`:
  - `versionName "1.0.0"`
  - `versionCode 1` (must be increased for every release)

- Changelog per release:
  - File: `langpack-latin-best/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
  - Example: For `versionCode 2` → `langpack-latin-best/fastlane/metadata/android/en-US/changelogs/2.txt`

---

## 3) Release process

1. **Update** `versionName` & `versionCode` in the Langpack module.
2. **Write** changelog in `fastlane/.../<versionCode>.txt`.
3. **Commit & push** to `main`.
4. **Tag the release** (example for v1.0.0):
   ```bash
   git tag langpack-latin-best-v1.0.0
   git push origin langpack-latin-best-v1.0.0
   ```
5. GitHub Actions will build automatically:
   - **APK:** `MakeACopy-Langpack-Latin-Best-v1.0.0-release.apk`
   - **AAB:** `MakeACopy-Langpack-Latin-Best-v1.0.0-release.aab`
   - **Checksums:** `.sha256` files
   - A **GitHub Release** with artifacts & changelog

---

## 4) Signing (CI secrets)

For signed artifacts on tags, set these secrets in your GitHub repo:

- `KEYSTORE_BASE64` – Base64-encoded `keystore.jks`
- `SIGNING_KEY_ALIAS` – Key alias
- `SIGNING_KEY_PASSWORD` – Key password
- `SIGNING_STORE_PASSWORD` – Keystore password

> If secrets are missing → the workflow still builds, but artifacts are **unsigned**.  
> Unsigned builds are fine for local tests and F-Droid, but **not** for Google Play.

---

## 5) Upload to Google Play Console

1. Go to **All apps → MakeACopy OCR Latin (Best)** (separate listing from the main app).
2. Navigate to **Production** (or Internal testing) → **Create new release**.
3. Upload the AAB:  
   ```
   langpack-latin-best/build/outputs/bundle/release/MakeACopy-Langpack-Latin-Best-vX.Y.Z-release.aab
   ```
4. Add release notes (you can copy from the GitHub Release).
5. **Review → Rollout**.

**Important notes:**
- Google Play requires strictly increasing **`versionCode`** for every release.
- `versionName` is cosmetic and can follow semantic versioning (`1.0.0`, `1.0.1`, etc.).

---

## 6) Relation to F-Droid

- Langpack has its **own subdir** and **own metadata YAML** in the F-Droid repo.
- In the metadata, use:
  ```yaml
  AutoUpdateMode: None
  ```
  (manual releases only, because models rarely change).
- Langpack tags do **not** affect the main MakeACopy workflow.

---

## 7) Checklist before tagging

- [ ] `build.gradle` updated (`versionName`, `versionCode`)
- [ ] Changelog file exists in `fastlane/.../<versionCode>.txt`
- [ ] App label and `applicationId` correct (`de.schliweb.makeacopy.lang.latin.best`)
- [ ] `.traineddata` files present in `assets/tessdata/`
- [ ] CI secrets set for signing (if publishing to Play Store)
- [ ] Local install of APK tested successfully

---

## Example workflow run

- Commit message:  
  ```
  Release: langpack-latin-best v1.0.0
  ```
- Tag:
  ```
  git tag langpack-latin-best-v1.0.0
  git push origin langpack-latin-best-v1.0.0
  ```
- CI will:
  - Build APK + AAB
  - Sign (if secrets present)
  - Verify with `apksigner`
  - Generate checksums
  - Publish GitHub Release with artifacts

---
