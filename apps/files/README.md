# BBOS6 Files

A standalone Kotlin + Jetpack Compose file manager for the Zinwa Q25,
maintained in `apps/files/` of the `bbos6-suite` monorepo.

## Current functionality

- Home: Internal Storage, SD Card (when mounted), Recent Files placeholder.
- Internal Storage and mounted SD Card: native, read-only folder browsing with
  child directories and files sorted folders-first, case-insensitively.
- Trackpad/DPAD Up/Down, click/Enter and touch use the shared
  `Bbos6PageList` helper; hardware Back navigates up to the storage root,
  then returns to Files home.
- Header displays the current folder path relative to the selected volume.
- An empty or inaccessible directory uses the shared `Bbos6EmptyState`.
- Folder enumeration happens off the UI thread; the existing shared
  `Bbos6BackgroundLoadingIndicator` appears while loading.
- Activating an ordinary file shows a truthful message: file opening is not
  implemented. Recent Files also remains a placeholder. No file operations
  or modification are implemented yet.

## Storage access and restrictions

On Android 11+, full shared-storage file browsing uses the native
`MANAGE_EXTERNAL_STORAGE` special app access. The app checks
`Environment.isExternalStorageManager()` and offers the Android Settings
grant screen only when the user selects a storage volume. There is no silent
grant, root privilege, or automatic access to other apps' private files.
Access can be revoked in Android Settings. Android continues to restrict
certain `Android/data` and `Android/obb` locations.

Directory navigation is constrained by canonical paths to the selected
storage root. Inaccessible folders report errors without inventing file
entries. File operations are explicitly out of scope for this stage.

## Canonical shared UI

`apps/files/assets/bbos6` is a monorepo symlink to `shared/`, **not** a
Git submodule. Reusable components, verified original assets and
`shared/icon-index.csv` must be modified only in `shared/`.
Files-specific storage queries and path rules belong in this application.

## Build and install (monorepo)

```bash
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/files-folder-browsing
git pull --ff-only origin feature/files-folder-browsing
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```

Requires JDK 17 and Android SDK. In-place installation preserves existing
app data. Local Release uses the existing signing configuration.

## Q25 acceptance tests

1. Files home appears and highlights only the selected row.
2. Choose Internal Storage; follow the explicit system Settings request and
   grant All Files Access. Return and confirm the root folder opens.
3. Confirm folders are first, file names are real, directory entry works,
   and hardware Back moves to parent, then Files home.
4. Confirm trackpad/DPAD/Enter and touch navigation throughout; no Android
   light focus overlay and no duplicate activations.
5. Confirm empty folders, unreadable Android protected folders and revoked
   access behave without crashes.
6. Verify mounted SD Card if present and a folder containing many entries.
7. Confirm a non-folder file shows the not-yet-implemented message.

No build or device verification is claimed until actually performed.
Do not merge this feature branch without approval.
