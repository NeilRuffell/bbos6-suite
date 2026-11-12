# BBOS6 Files — project instructions

Standalone Android file manager for Zinwa Q25. Kotlin and Jetpack Compose
only for application UI. App package and ID: `dev.q25.bbfiles`.
Active integration branch is `master`. Develop on feature branches, open
PRs and never merge without explicit owner approval.

Before code changes read this file, `README.md`, and
`assets/bbos6/AGENTS.md` plus
`assets/bbos6/android/bbos6-ui/COMPONENT_CATALOG.md`.

The `assets/bbos6` directory is the canonical `bbos6-shared` Git
submodule. Never copy shared UI, icon assets or the icon index into this
repository. For reusable UI, update `bbos6-shared` first on a feature
branch, commit and PR; pin the new gitlink here and open a separate PR.
No merges until explicitly approved and Q25 verified.

The first-stage Files app deliberately contains no filesystem browsing or
storage permissions. Any future permission grant must be explicit and use
native Android APIs; never claim access to protected app directories.
Keep permissions and file operations app-specific. Preserve DPAD, keyboard,
trackpad and touchscreen inputs, selected-state versus focused-state
separation, and prevent default Android focus overlays on managed BBOS6 UI.

Build with JDK 17, installed Android SDK and:
```
gradle :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```
Do not uninstall as part of normal updates. Report build/device tests only
when actually run and verified.
