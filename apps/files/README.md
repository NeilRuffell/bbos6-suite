# BBOS6 Files

Standalone Kotlin + Jetpack Compose file manager for the Zinwa Q25.
This application lives in `apps/files/` of the `bbos6-suite` monorepo.

## Implemented

- Real Internal Storage and mounted SD Card folder browsing with folders-first sorting.
- Home and directory lists retain their original 52dp vertical row height.
  The BB 9900 Files reference informs tighter **horizontal** layout only:
  a 36dp icon slot and 4dp label inset reduce the gap between icon and text.
  These dimensions are a Q25-tunable approximation, not historical SDK values.
- Folder rows and storage-root entries display the original BBOS6 **112.png**
  Folders icon, mapped as `bbos6_menu_folders` in the shared index.
  Ordinary files remain text-only until their icon mapping is defined.
- Shared `Bbos6PageList` DPAD, trackpad click/hold, touch tap/hold and BB Menu key navigation.
- Back navigates to parent; headers display the relative folder path.
- Regular file tap: native Android `ACTION_VIEW` with a temporary, read-only
  `FileProvider` URI grant. Files opens in a compatible installed Android app.
- Long-press a file/folder or press BB Menu: shared `Bbos6ContextMenuDialog`.
  Item actions: Open, Share (files only), Copy, Cut, Rename and Delete.
  The same menu also offers **New Folder** and Paste when available.
- Directory lists contain real filesystem entries only, never New Folder or
  Paste action rows. In an empty folder, press BB Menu or long-press the blank
  list area for the directory menu (New Folder, Paste, Refresh).
- Rename and New Folder use the shared Q25-proven native
  `Bbos6PopupButtonList` text-input helper.
- Delete requires explicit BBOS6 confirmation, initially selecting Cancel.
  Folders delete recursively only after that confirmation.
- Copy/Cut/Paste supports files and folders across selected storage volumes.
  Destinations with the same name are **never overwritten**. Cross-volume
  moves copy first and delete the original only after a complete copy.
  Partial failures are reported, not falsely labeled successful.
- Filesystem enumeration and file mutations execute on `Dispatchers.IO`,
  with the existing shared `Bbos6BackgroundLoadingIndicator`.
- Empty/inaccessible directories use shared empty-state presentation. The
  special `Recent Files` entry is still a placeholder.

## Storage and safety

All-files access is requested explicitly through Android Settings only when
browsing storage. It is not a silent permission. Android app-private storage
restrictions still apply (including protected `Android/data` and `Android/obb`).
The app doesn't request root, privileged permissions or shell tools.

File operations validate canonical paths within the selected source and
destination roots. They refuse collisions, invalid names, self-nesting folders
and symbolic-link copies. File sharing/opening uses a non-exported Android
`FileProvider` with per-URI read permission, never `file://`.
The clipboard is in-memory; nothing is changed until Paste is selected.
Deletion has no undo. Test with disposable files before trying valuable data.

## Source ownership

The monorepo symlink `apps/files/assets/bbos6` resolves to the one
canonical `shared/` directory. Shared list interaction, BBOS6 components,
assets and `icon-index.csv` are maintained **only** there.
`FileBrowser`, `FileOperations` and Android storage policy belong here.

## Build and install

```bash
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/files-actions-and-open
git pull --ff-only origin feature/files-actions-and-open
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```

## Q25 device checks

1. Verify normal trackpad tap opens directories and files; long-press opens
   context menu without also invoking the normal tap action. Confirm no
   synthetic New Folder row appears in the file listing.
2. Verify folder rows (including nested folders) and Internal Storage / SD Card
   show original 112.png, while ordinary files do not show that icon.
   Verify BB Menu key opens the menu for the highlighted item.
3. Verify New Folder appears in the context menu for selected files/folders,
   and in empty folders via BB Menu or blank-area long-press. Confirm menu
   navigation with trackpad/DPAD/Enter and Back; no light focus overlay.
4. Open a text/photo/audio file through Android's compatible installed apps;
   Share should grant access to exactly the chosen file.
5. Create a folder, rename a test file, copy/paste it, then cut/paste it.
6. Confirm collisions are rejected; verify copies across internal/SD if available.
7. Delete disposable file and nested test folder after verifying confirmation
   defaults to Cancel; verify Back cancels and existing app data is preserved.
8. Revoke all-files access and confirm no file operation proceeds.
9. Confirm UI remains responsive during a large copy and errors are reported.

No build or Q25 device verification is claimed without results.
Do not merge without explicit owner approval.
