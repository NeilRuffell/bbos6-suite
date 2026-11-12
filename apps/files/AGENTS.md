# BBOS6 Files — development instructions

This is the standalone Files Android application under the `bbos6-suite`
monorepo. Source-of-truth instructions are at `../../AGENTS.md`.
Use Kotlin and Jetpack Compose, app ID `dev.q25.bbfiles`, JDK 17,
and the Zinwa Q25 hardware serial `202512003630`.

The `assets/bbos6` link points to the **one canonical** monorepo
`shared/` directory; it is not a Git submodule. Read
`../../shared/AGENTS.md` and
`../../shared/android/bbos6-ui/COMPONENT_CATALOG.md` before editing
shared UI. Never copy a shared helper, original icon or index. Reusable
UI belongs in `shared/`, app-specific filesystem logic remains here.
Preserve hardware trackpad, DPAD, keyboard, Back and touchscreen.

Directory enumeration uses `FileBrowser` and is bounded to the
selected shared-storage root with canonical paths. Only explicitly granted
Android `MANAGE_EXTERNAL_STORAGE` is used; protected app-private
directories remain restricted. No root privileges, silent permission
grants or writes. The initial browser does not open files or implement
Recent Files.

Build from the monorepo root:
```bash
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```

Feature branches require PR and explicit approval before merging.
Never assert build or Q25 validation without results.
