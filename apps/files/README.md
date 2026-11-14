# BBOS6 Files

Standalone Kotlin + Jetpack Compose file manager for the Zinwa Q25.
This application lives in `apps/files/` of the `bbos6-suite` monorepo.

## Implemented

- Real Internal Storage and mounted SD Card folder browsing with folders-first sorting.
- Android launcher icon uses original `shared/icons/211.png`, mapped to
  `bbos6_files_app_icon` in the canonical shared icon index. The original
  bytes remain untouched; the shared drawable is a symlink to that source.
  A Files-owned adaptive launcher XML uses that shared drawable as foreground.
- The Files root applies Android Compose `statusBarsPadding()` before the
  shared Files/path header, keeping it below the clock and notification icons
  on every page without per-page offsets. Files selects the shared
  `Bbos6SectionHeaderStyle.FLAT_BLACK_COMPACT` appearance: a 28dp solid-black
  bar with no gradient or border, 2dp horizontal text inset and 15sp regular
  white text, based on the supplied original BlackBerry File Folders image.
  Other consumers retain the original beveled section-header appearance.
  The shared header's optional trailing slot displays a passive Files-owned
  pending Copy/Cut badge with item count on the far right. Long directory
  paths **start-ellipsize** (dots on the left, current/rightmost folder
  remains visible) before the badge, whether or not a transfer is staged.
  The header uses the shared `titleOverflow` option, not a Files-specific
  text renderer. The 28dp flat-black header, trackpad focus and badge
  geometry remain unchanged. The badge stays visible while navigating
  Files pages and disappears when nothing is staged.
- Home navigation: Internal Storage, SD Card (when mounted), Recent Files,
  **Favourites**, Images, Downloads, Audio, Documents, Videos and **Settings**
  (final row). Settings opens a dedicated Files page using the existing
  flat-black section header and shared `Bbos6ThemeSelector`. Appearance
  offers Dark (default), Light and System. The selected preference persists
  for Files only. The Files screen is wrapped in `Bbos6ThemeSurface` so
  the shared `Bbos6PageList`, its `Bbos6MediaListRow` entries and
  `Bbos6EmptyState` respond immediately to the selected appearance.
  The flat-black path bar and existing BBOS6 popup/context menus intentionally
  retain their original styling until those helpers opt into themes.
  Settings requires no storage permission; Back returns to the home list.
- Home-only shortcut artwork uses original assets: Recent Files **129.png**,
  Images **2219.png**, Videos **2216.png**, Audio **1579.png**, and
  Favourites **950.png**.
  All Files lists use the same shared `Bbos6PageList` icon sizing:
  36dp slot with a default 2dp inset (32dp artwork viewport) and 52dp rows.
  No per-screen icon-size overrides are used. The home list reserves empty
  icon slots to align labels; that does not change icon size. Music's
  independent media-row layout remains unchanged.
  The original PNGs remain in `shared/icons/`; canonical shared resource
  symlinks supply them to Android. Audio uses its dedicated home shortcut
  mapping `bbos6_files_home_audio`; the original Genres icon **348.png**
  remains unchanged. Folder icons inside lists remain **112.png**, and
  MediaStore list rows remain unchanged.
- A folder marked as a favourite displays the original **1338.png**
  favourited-folder icon in both its normal directory list and the dedicated
  Favourites list. Removing the bookmark restores the normal **112.png**
  folder icon without altering the folder or its contents. File favourites
  keep their existing text-only row presentation.
- **Favourites** is one openable home row, with a dedicated list of pinned
  files/folders. Long-press or press the BB Menu key on a file/folder and
  choose **Add to Favorites**. Pinned items keep insertion order and survive
  app restarts in private SharedPreferences; no file contents are duplicated.
- Selecting a favorite opens the original folder (Back returns to Favourites)
  or opens the original file using the native Android viewer. Long-press a
  pinned entry to **Remove from Favorites** without deleting the original.
- Images, Audio, Videos and **Documents** use Android's existing `MediaStore`
  index across mounted shared-storage volumes, **not** an app-maintained scanner.
  Indexed items can be opened and shared using their system content URIs.
  The Documents category queries `MediaStore.Files` using MIME-type filters
  for recognized document formats, including PDFs, text, Office, OpenDocument,
  RTF and e-books. Its results are independent of directory location; files
  with unknown or non-document MIME types may not appear.
  File management operations and Add to Favorites remain available through
  normal folder browsing, not the indexed category lists.
- **Downloads only** opens the primary shared-storage `Download/` folder
  via the existing `FileBrowser`. Back from that shortcut root goes directly
  to the home page. The app does not create public directories on entry.
- Files/folders already pinned show **Remove from Favorites** in their
  original context menu, preventing duplicate pins.
- Missing, renamed or inaccessible favorites display an explanatory popup
  rather than inventing a location or deleting the original file. Removable
  storage shortcuts require their recorded volume root to be available.
- Home and directory lists retain their original 52dp vertical row height.
  The BB 9900 Files reference informs tighter **horizontal** layout only:
  a 36dp icon slot and 4dp label inset reduce the gap between icon and text.
- File rows in folder browsing, Favourites and indexed Images, Videos,
  Audio and Documents display platform-formatted sizes on the far right,
  without another row component. Folders show nothing. Folder browsing
  obtains bytes in `FileBrowser.list` on `Dispatchers.IO`; Favourites
  resolve valid bookmarked files and read sizes in a background effect;
  indexed categories query `MediaStore.MediaColumns.SIZE`. If a value is
  unavailable, no size is shown. The shared page list's optional
  `trailingTextForId` displays each size before the right-edge
  multi-selection checkbox, automatically shifting it left in selection
  mode. Home, Settings and Music retain their existing layouts.
  These dimensions are a Q25-tunable approximation, not historical SDK values.
- Folder rows and storage-root entries display the original BBOS6 **112.png**
  Folders icon, mapped as `bbos6_menu_folders` in the shared index.
  Folder names are displayed exactly as provided by the filesystem, without
  appending `/`; folders remain identified internally by `isDirectory`.
  Ordinary files remain text-only until their icon mapping is defined.
- **Opt-in directory multi-selection:** In a folder, long-press a file or
  folder and choose **Select**. With selection mode active, trackpad
  click/Enter and touch taps toggle selected items, leaving the original
  open-file/open-folder behavior unchanged outside that mode. The path bar
  shows the selection count. Every row shows the shared `Bbos6Checkbox` at
  the far right, checked only for selected items; the row remains the sole
  touch/trackpad focus target. The checkbox disappears outside multi-select
  mode. Press BB Menu or long-press to open **Select All**, **Deselect All**,
  **Copy Selected**, **Cut Selected**, **Delete Selected**, or **Cancel
  Selection**. Back exits selection mode before navigating up. Bulk deletion
  requires explicit confirmation with Cancel preselected. Bulk paste uses the
  existing validated, no-overwrite `FileOperations.paste` for each item,
  reports partial failures, and runs on `Dispatchers.IO`. Home, Favourites,
  indexed MediaStore categories, Settings and Music do not enable this mode.
  Multi-selection state resets when leaving the current directory.
- Shared `Bbos6PageList` DPAD, trackpad click/hold, touch tap/hold and BB Menu key navigation.
- Back navigates to parent; headers display the relative folder path.
  Returning from a nested directory selects its entry in the parent,
  keeping the previously entered folder highlighted and visible instead
  of resetting to the first row. Files passes a stable child path to the
  existing shared `Bbos6PageList(initialSelectedId)`; the list owns the
  viewport scrolling. Missing entries fall back to the first row.
  Nested Back navigation updates the target at every level.
- Regular file tap: native Android `ACTION_VIEW` with a temporary, read-only
  `FileProvider` URI grant. Files opens in a compatible installed Android app.
- Long-press a file/folder or press BB Menu: shared `Bbos6ContextMenuDialog`.
  With no pending clipboard, the first nine tiles use a fixed 3×3 order:
  `Cut | Copy | Rename`, `Properties | Open | Delete`,
  `Select | Share | New Folder`. Share is visibly disabled for directories,
  preserving tile positions. Optional Add/Remove from Favorites appears in
  a second group. **While Copy or Cut is pending**, all directory item and
  blank-folder context menus show only `Paste | New Folder | Cancel Copy/Cut`
  (in that order). Paste and New Folder operate in the **current folder**,
  not inside a long-pressed directory. Folder navigation by normal tap or
  trackpad click still works, and making a folder does not discard the
  clipboard. On Paste success or cancellation, the original 3×3 menu
  returns; failed transfers keep the simplified menu for retry.
  Existing Favourites, MediaStore and multi-selection menus remain independent,
  since those pages are not direct directory paste destinations.
- **Properties** opens the existing shared `Bbos6PropertiesDialog` for files
  and folders. Files reads authorized filesystem metadata on `Dispatchers.IO`
  and displays name, containing location, type, available modification date,
  and byte-derived formatted size for **files only**. Folder size is omitted,
  rather than showing a misleading directory-entry size or running a recursive
  scan. Missing or inaccessible paths show an error; no custom Properties
  presentation or storage access is added.
- Directory lists contain real filesystem entries only, never New Folder or
  Paste action rows. In an empty folder, press BB Menu, **long-press the
  hardware trackpad/DPAD centre or Enter**, or long-touch the blank list area
  to open the directory menu (New Folder, Refresh) without a staged clipboard,
  or (Paste, New Folder, Cancel Copy/Cut) while staging. This shared
  `Bbos6PageList` hardware fallback also handles a missing selected row
  and does not change row-long-press behavior on populated lists.
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

## Android system picker integration (Storage Access Framework)

Files registers an Android `DocumentsProvider` at
`dev.q25.bbfiles.documents`. Android's native DocumentsUI (not a second
Files-owned picker activity) discovers it through
`android.content.action.DOCUMENTS_PROVIDER` and displays **Internal Storage**
and mounted **SD Cards with stable volume UUIDs** as local roots.
Other apps can use standard
`ACTION_OPEN_DOCUMENT`, `ACTION_GET_CONTENT` (via Android's picker),
`ACTION_CREATE_DOCUMENT`, and `ACTION_OPEN_DOCUMENT_TREE` to browse
authorized directories and select documents. This does **not** make the Files
activity a direct handler for those intents.

- Grant **All files access** to Files in Android Settings first. Until the
  user grants it, the provider exposes **no roots**, rather than bypassing
  the app's normal permission model. Revoking it immediately blocks document
  lookups, reads, writes and listings. Storage-root changes are notified on
  Files activity resume; existing open pickers may need to be reopened.
- Only the app's current public shared-storage root and mounted removable
  volumes with stable UUIDs are exposed, using volume-scoped
  **opaque relative document IDs**
  validated against canonical storage paths. `Android/data`, `Android/obb`
  and other Android-protected paths remain subject to platform restrictions.
  Files does not expose its private data or other arbitrary filesystem paths.
  Symlink aliases are omitted from picker listings; removable volumes without
  a stable UUID are not exported.
- The exported provider is protected by system-only
  `android.permission.MANAGE_DOCUMENTS`, with narrow user-selected
  `content://` URI grants from Android. This is separate from the existing,
  **non-exported** file-sharing `FileProvider`.
- Supports native picker directory navigation, read/write streams for
  writable files, document/folder creation, rename and delete. Directory
  deletion is recursive through the existing bounded FileOperations policy.
  No overwrites are permitted when creating or renaming. Read-only volumes
  do not advertise write or create operations; removable media disappearance
  returns an unavailable-document error. Renamed documents receive updated
  document IDs. Deletion from a system picker is subject to that picker's
  own confirmation UI, not the Files app's BBOS6 delete dialog.
- As a deliberate minimal first release, the provider does not advertise
  search, recents, thumbnail or cross-folder move/copy APIs. The existing
  Files UI and its native FileProvider share/open actions are unchanged.

### Q25 picker integration checks

1. Open Files and grant All files access in Android Settings. Launch an
   external app's native **Open** picker; select **Browse**, then choose
   the Files provider. Confirm Internal Storage and mounted SD Card appear
   and can be navigated, and selecting a file returns a readable
   `content://dev.q25.bbfiles.documents/...` URI.
2. Test an external app's **Save as** / `ACTION_CREATE_DOCUMENT`: create a
   new file in a folder, write it, reopen it and verify bytes. Test new
   directories and an existing-name collision (must not overwrite).
3. Verify rename and delete of disposable files/folders via a system picker,
   including that changing a folder's name doesn't expose a stale path.
   Confirm the existing Files app delete-confirmation workflow is unchanged.
4. Test `ACTION_OPEN_DOCUMENT_TREE` for a nested directory; validate that
   the requesting app can access **only** the selected tree. Check ordinary
   document selections cannot escape into sibling volume roots.
5. Revoke All files access: provider roots and data become unavailable.
   Regrant it and reopen the picker. Also remove and reinsert an SD Card;
   stale document IDs must fail rather than redirect to another volume.
6. Confirm Files' normal launcher, BBOS6 navigation, favorites, and
   open/share actions remain unchanged. Verify no new default file picker
   chooser activity was registered.

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
Copy and Cut replace the previous staged selection. Both modes clear after a
fully successful Paste. If only some items transfer, the badge counts only
remaining failed items and those items alone are available for retry; an
error popup reports the failures. Folder and item context menus expose
the simplified Paste / New Folder / Cancel Copy or Cut action set while
staging; Cancel discards the staged operation without changing any files.
Storage permission revocation or source-volume removal also clears it.
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
git switch master
git pull --ff-only origin master
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```

## Q25 device checks

**Settings smoke check:** Confirm Settings is the final home entry with the
same list-row spacing/selection as the other shortcuts. Open using touch and
Q25 trackpad; verify the Settings and Appearance headers and all three modes.
Select Light, leave and reopen Settings: Light remains selected. Force-stop
and reopen Files: Light remains selected. Select System and verify its saved
selection. Back returns to Files home. Repeat with all-files permission denied:
Settings must open directly without Android's storage-access prompt. Existing
Files page-list backgrounds, labels and empty states must switch between
black/white immediately. Verify selection gradient, icons, scroll and
trackpad behavior are unchanged, and the flat-black header remains black.
Existing file operations must remain unchanged.

1. Confirm Favourites is a single home-page entry, between Recent Files
   and Images; category rows follow in Images, Downloads, Audio, Documents,
   Videos order. No individual favorites are pinned directly to home.
2. Pin a file and folder using the original-item context menu. Open
   Favourites, confirm ordered entries and no duplicates. Enter a favorite
   folder and verify Back returns to Favourites.
3. Force-stop/reopen Files; verify the favorites remain.
4. Remove a favorite using its Favourites-page long-press/Menu context menu;
   confirm the underlying file still exists.
5. Open Images, Audio, Videos and Documents; verify MediaStore lists indexed
   matches from mounted shared-storage volumes and that Back returns to Files
   home. Tap to open; long-press for Open or Share.
6. Check Documents includes indexed PDFs, text, Word/Excel/PowerPoint and
   OpenDocument files in locations *outside* `Documents/`, including SD Card
   when mounted. Non-document media and unrelated files should not appear.
   The category may omit files Android has not indexed or whose MIME types
   it has not recognized.
7. Open Downloads as the standard directory shortcut. Navigate a nested
   folder, then use Back to return to Downloads and Files home. Empty or
   unavailable directories must not crash or be created silently.
5. Pin a disposable file, rename or delete the original, then open the stale
   favorite: expect an unavailable message and ability to remove the shortcut.
6. Revoke storage permission and verify favorites request native Android
   access rather than bypassing storage restrictions. If present, test SD card
   removal and remount behavior.

### Directory Back-selection checks

1. Enter a folder that is far down a long parent directory list, then press
   Back. The highlighted row in the parent must be the folder just exited
   and should be scrolled into view, not the top row.
2. Repeat with trackpad click, touchscreen row tap, and context-menu Open.
   At each nested level, Back should highlight the directory you just left.
3. Rename/delete a directory before a listing refresh, or otherwise have
   a missing saved row: the list must fall back safely without crashing.
4. Verify Home, Favourites, indexed media and Music navigation still work.
   Check that multi-select, scroll-edge alignment and long-press are intact.

### File size row checks

1. Open a folder containing an empty file, a non-empty file, a long-named
   file, and a folder: sizes should appear for the files only (including
   zero-byte files), on the right edge.
2. Enter multi-selection: checkboxes must occupy the far-right slot and
   push the sizes left without obscuring long, ellipsized file names.
3. Toggle selection with trackpad/DPAD and touch; row focus, highlight,
   scroll-edge behaviour and clipboard actions must remain unchanged.
4. Verify Favourites and all four indexed media categories display sizes
   for valid files, omit unavailable sizes, and show no folder size.
5. Verify Home, Settings and Music retain their existing row layouts;
   no navigation or selection behavior changes in Favourites/indexed media.

### File context menu and Properties checks

1. Long-press or BB Menu on a file and confirm the three rows read
   Cut/Copy/Rename; Properties/Open/Delete; Select/Share/New Folder.
2. Repeat on a directory: Share stays in place but is disabled.
3. Confirm Add/Remove from Favorites remains in a separate group in normal
   mode. Stage Copy/Cut and confirm only Paste/New Folder/Cancel Cut or Copy
   is shown, both on a selected item and via an empty-folder context menu.
4. Open Properties on a file: validate its name, parent location, MIME type
   (or File), size, and modification time. Open on a directory: validate
   Folder type and no calculated size. Use Back to dismiss.
5. Confirm missing/revoked paths fail safely, the directory listing remains
   responsive, and Music's Properties implementation is unchanged.

### Hardware empty-folder context menu checks

1. In an empty folder with nothing staged, hold the Q25 trackpad/DPAD centre
   (or Enter). Confirm the directory menu appears with New Folder/Refresh;
   release the key and verify it does not also activate a row or reopen.
2. Repeat with the touchscreen long-press and the hardware BB Menu key.
   All three input routes should expose the same directory commands.
3. Stage Copy and Cut in turn, navigate into an empty destination folder,
   and hold the trackpad. Confirm Paste/New Folder/Cancel Copy or Cut appears.
4. In a populated folder, long-press the trackpad on a selected row and
   confirm the row-specific menu still opens once, rather than the blank
   folder menu. Verify ordinary short-click activation remains unchanged.

### Directory path truncation checks

1. Navigate into nested folders until the complete breadcrumb exceeds the
   Q25 width. Confirm the path begins with an ellipsis and preserves the
   rightmost/current folder name; a short path displays normally.
2. Stage Copy or Cut: the badge remains at the far right, and the reduced
   available width truncates **older path segments on the left**, never the
   current folder suffix when it fits.
3. Repeat with multi-selection active, confirm the selected-count suffix
   remains on the right, and verify Files home/Settings and Music keep their
   existing end-ellipsis behaviour.

### Clipboard indicator checks

1. Stage one file with Copy, then several with Cut Selected. Verify the
   flat-black path bar shows the correct icon, mode and count, on home and
   across directory changes; long paths must ellipsize before the badge.
2. Stage Copy or Cut, then open context menus on a file, folder, and blank
   folder area. Each must show only Paste, New Folder and Cancel Copy/Cut.
   Create and enter a new folder; verify staging remains, then Paste there.
   Confirm Paste uses the currently open directory, not the selected row.
3. Cancel Copy or Cut from the reduced folder/item context menu. Verify the
   badge disappears and the regular 3x3 menu returns without file changes.
4. Paste successfully in both modes and verify the badge disappears. Copy
   must preserve the source; Cut must move the source.
5. Cause a multi-item collision in the destination. Verify the popup
   reports failures, the badge count reflects only failed items and retry
   processes only them via the simplified menu. Existing destinations
   must not be overwritten.
6. Verify multi-selection counts, Back/trackpad, Settings and Music headers
   remain unaffected, including while a clipboard operation is pending.

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
