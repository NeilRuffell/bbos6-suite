# BBOS6 Files

A standalone Kotlin + Jetpack Compose file manager for the Zinwa Q25.

**Current state:** First-stage BBOS6 home-page prototype only. The home
shows Internal Storage, an SD Card entry when mounted removable storage is
detected, and Recent Files. Selecting an entry currently displays an explicit
not-yet-implemented message; **file browsing and file operations are not
implemented**. No all-files access or storage write permission is requested.

## Architecture

- Android app ID: `dev.q25.bbfiles`
- Independent repository: `NeilRuffell/bb-files`, active base `master`
- Canonical reusable UI: pinned `assets/bbos6` Git submodule pointing to
  `NeilRuffell/bbos6-shared`
- Full-page DPAD/trackpad/Enter/touch navigation: shared
  `Bbos6PageList` (feature branch until approved), using the canonical
  `Bbos6MediaListRow` selection face
- Title: shared `Bbos6SectionHeader`
- Notifications, storage access, SAF, file operations and recents: none yet

Never duplicate shared components or asset mappings in this app. Implement
future generic helpers in `bbos6-shared` first and advance this app's pinned
gitlink in a separate PR. Storage discovery and file operations belong here.

## Build and install

```bash
cd ~/Projects/bb-files
git fetch origin
git switch feature/initial-compose-home
git pull --ff-only origin feature/initial-compose-home
git submodule sync --recursive
git submodule update --init --recursive
gradle :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

Use JDK 17 and the existing Android SDK. The release APK is signed with the
existing local Android debug keystore, matching the BB Music personal-build
workflow; it is not a Play Store release signature.

## Device acceptance checklist

1. Open Files on Q25; verify the BBOS6 header, dark rows and blue highlight.
2. Trackpad/DPAD Up/Down changes the selected row without moving focus away.
3. Trackpad click / Enter shows the explanatory popup once per click.
4. Back closes the popup; Back on home exits the app.
5. Touch selects rows and dismisses the popup via OK.
6. Confirm mounted SD entry visibility on the actual device.
7. Confirm no white Android default focus overlay covers selected rows.

No build or on-device verification has been reported yet. Do not merge this
or the shared helper PR until approved after device testing.
