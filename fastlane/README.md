Fastlane Release Notes (Changelogs)

This folder contains the Play Store metadata (fastlane/metadata/android). Fastlane reads release notes from files named after the versionCode in the folder "metadata/android/<locale>/changelogs".

Important for ABI-specific builds
Since the switch to per-ABI versionCodes, multiple versionCodes are generated from one base code (e.g., 10200):

- 102001 (armeabi-v7a)
- 102002 (arm64-v8a)
- 102003 (x86)
- 102004 (x86_64)

ABI code mapping (suffix):
- armeabi-v7a = 1
- arm64-v8a = 2
- x86 = 3
- x86_64 = 4

Rule: versionCode = baseVersionCode * 10 + abiSuffix

Examples:
- 1.2.0 (base 10200) → 102001, 102002, 102003, 102004
- 1.2.1 (base 10201) → 102011, 102012, 102013, 102014

To display identical release notes for all APKs/Bundles in the Play Console, a separate changelog file must exist for each ABI-specific versionCode. Typically, you copy the content of the base notes into the ABI files.

Example structure (en-US):
- fastlane/metadata/android/en-US/changelogs/102001.txt
- fastlane/metadata/android/en-US/changelogs/102002.txt
- fastlane/metadata/android/en-US/changelogs/102003.txt
- fastlane/metadata/android/en-US/changelogs/102004.txt

Tips:
- If all ABIs share the same notes, keep one base file for reference (e.g., 10200.txt) and duplicate its content into the ABI-specific files.
- For additional languages, maintain the respective locale folders accordingly (e.g., de-DE, fr-FR). Play will fall back to the default locale if a specific locale file is missing.