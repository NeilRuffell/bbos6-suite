# BBOS6 Files

Standalone Kotlin + Jetpack Compose file manager for the Zinwa Q25.
This application lives in `apps/files/` of the `bbos6-suite` monorepo.

## Implemented

- Real Internal Storage and mounted SD Card folder browsing with folders-first sorting.
- Android launcher icon uses original `shared/icons/211.png`, mapped to
  `bbos6_files_app_icon` in the canonical shared icon index. The original
  bytes remain untouched; the shared drawable is a symlink to that source.
  A Files-owned adaptive launcher XML uses that shared drawable as foreground.
- Home navigation: Internal Storage, SD Card (when mounted), Recent Files,
  **Favourites**, Images, Downloads, Audio, Documents and Videos.
- Home-only shortcut artwork uses original assets: Images **2219.png**,
  Videos **2216.png**, Audio **348.png**, and Favourites **950.png**.
  The home list retains 52dp row height and a compact 36dp icon column,
  but reduces icon inset from 8dp to 2dp (32dp artwork viewport). Empty
  icon slots are reserved, so rows such as Recent Files align their labels
  with the rest of the home list. Other screens retain their default icon
  insets and existing layout.
  The original PNGs remain in `shared/icons/`; canonical shared resource
  symlinks supply them to Android. Audio reuses the existing verified
  `bbos6_menu_genres` mapping for icon 348; folder icons inside lists
  remain **112.png** and MediaStore list rows remain unchanged.
- **Favourites** is one openable home row, with a dedicated list of pinned
  files/folders. Long-press or press the BB Menu key on a file/folder and
  choose **Add to Favorites**. Pinned items keep insertion order and survive
  app restarts in private SharedPreferences; no file contents are duplicated.
- Selecting a favorite opens the original folder (Back returns to Favourites)
  or opens the original file using the native Android viewer. Long-press a
  pinned entry to **Remove from Favorites** without deleting the original.
- Images, Audio and Videos use Android's existing `MediaStore` index across
  available shared-storage volumes, **not** an app-maintained scanner.
  Indexed items can be opened and shared using their system content URIs.
  File management operations and Add to Favorites remain available through
  normal folder browsing, not the indexed category lists.
- Downloads and Documents open the standard primary shared-storage
  `Download/` and `Documents/` folders via the existing `FileBrowser`.
  They are directory shortcuts, not all-device aggregated categories.
  Back from each shortcut root returns directly to the home page.
  The app does not create public directories on entry.
- Files/folders already pinned show **Remove from Favorites** in their
  original context menu, preventing duplicate pins.
- Missing, renamed or inaccessible favorites display an explanatory popup
  rather than inventing a location or deleting the original file. Removable
  storage shortcuts require their recorded volume root to be available.
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
`FileBrowser`, `FileOperations`, `FavoritePolicy`, `FavoritesStore`,
`MediaCategoryBrowser`, and Android storage policy belong here.

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

1. Confirm Favourites is a single home-page entry, between Recent Files
   and Images; category rows follow in Images, Downloads, Audio, Documents,
   Videos order. No individual favorites are pinned directly to home.
2. Pin a file and folder using the original-item context menu. Open
   Favourites, confirm ordered entries and no duplicates. Enter a favorite
   folder and verify Back returns to Favourites.
3. Force-stop/reopen Files; verify the favorites remain.
4. Remove a favorite using its Favourites-page long-press/Menu context menu;
   confirm the underlying file still exists.
5. Open Images, Audio and Videos and verify MediaStore lists items from the
   mounted shared-storage volumes; tap to open, long-press to Open or Share,
   and verify Back returns to the Files home page.
6. Open Downloads and Documents as directory shortcuts. Navigate a nested
   folder and use Back to return to the category root, then Files home.
   Empty/unavailable public directories must not crash or be created silently.
5. Pin a disposable file, rename or delete the original, then open the stale
   favorite: expect an unavailable message and ability to remove the shortcut.
6. Revoke storage permission and verify favorites request native Android
   access rather than bypassing storage restrictions. If present, test SD card
   removal and remount behavior.

### Existing functionality checks

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
