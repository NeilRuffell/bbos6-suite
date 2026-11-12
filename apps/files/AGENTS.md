# BBOS6 Files — development instructions

This is the standalone Files Android application under the `bbos6-suite`
monorepo. Consult `../../AGENTS.md`, `../../shared/AGENTS.md`,
`../../shared/android/bbos6-ui/COMPONENT_CATALOG.md`, and this app's
`README.md` before changing shared UI or file operations.

- Kotlin + Jetpack Compose; application ID: `dev.q25.bbfiles`.
- `assets/bbos6` is a **monorepo symlink** to one canonical `shared/`, not
  a Git submodule. All reusable BBOS6 components, icon index and assets
  must be modified there; never maintain app-specific copies.
- `Bbos6PageList` is the common full-page trackpad controller. Shared
  onLongPress and onMenu callbacks own input routing; Files owns commands.
- Canonical shared context menu, popup, text input, header, row and loading
  helpers are used directly.
- Android storage access is explicitly user granted; protected/private
  app storage remains restricted. All file operations validate canonical
  volume boundaries, refuse overwrite, and run off the UI thread.
- App-specific file browsing, operations and Android FileProvider integration
  belong only to Files. Never use file:// intent URIs.
- Delete always prompts with Cancel selected and has no undo.
- Keep trackpad, physical keyboard, DPAD, Menu and touch parity.

Build/test from monorepo root with JDK 17:
```bash
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```
Never uninstall/clear user data as a routine build. No merge until explicit
approval and Q25 verification.
