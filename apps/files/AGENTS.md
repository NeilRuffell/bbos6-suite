# BBOS6 Files — development instructions

This is the standalone Files Android application under the `bbos6-suite`
monorepo. Consult `../../AGENTS.md`, `../../shared/AGENTS.md`,
`../../shared/android/bbos6-ui/COMPONENT_CATALOG.md`, and this app's
`README.md` before changing shared UI or file operations.

- Kotlin + Jetpack Compose; application ID: `dev.q25.bbfiles`.
- Launcher icon: user-selected original `shared/icons/211.png`, canonical
  index mapping `bbos6_files_app_icon`, exposed through shared UI resources.
  Files references it in its adaptive launcher XML; do not duplicate PNG data.
- `assets/bbos6` is a **monorepo symlink** to one canonical `shared/`, not
  a Git submodule. All reusable BBOS6 components, icon index and assets
  must be modified there; never maintain app-specific copies.
- Directory lists opt in to the shared `Bbos6PageList` multi-selection
  controller; it is disabled by default in every other page and app.
  Directory context menu **Select** enters the mode. Files owns the selected
  item count, Back-to-exit, BB Menu bulk commands, and each validated batch
  copy/cut/paste/delete. Shared only owns stable-ID selection, input routing
  and selection visuals. Do not add this mode to Music, home shortcuts,
  Favourites or indexed MediaStore views implicitly.
- `Bbos6PageList` is the common full-page trackpad controller. Shared
  onLongPress and onMenu callbacks own input routing, including an empty list;
  Files owns commands. A trackpad/DPAD-centre or Enter **long press with no
  selected row** must dispatch `onMenu(null)`, just like touching/long-pressing
  blank list space; with a row selected, preserve normal row long-press.
  New Folder and Paste live in the context menus, not artificial selectable
  directory rows.
- Folder list labels must use the raw `FileBrowser.Entry.name` without an
  appended `/`. The existing `isDirectory` flag controls artwork, navigation,
  and actions; do not encode directory identity in its displayed text.
- Files directory, indexed-media and Favourites file rows opt into shared
  `Bbos6PageList.trailingTextForId` for platform-formatted file sizes.
  Obtain folder-browsing byte lengths in `FileBrowser.list` on
  `Dispatchers.IO`, MediaStore lengths from the existing indexed query
  (`MediaColumns.SIZE`), and Favourites sizes from validated on-disk
  shortcuts in a background effect. Never query file sizes while rendering.
  Folders provide no size; unavailable values are omitted. The existing
  shared row places sizes immediately left of multi-selection checkboxes,
  keeping the checkbox on the far right and the row as the focus owner.
  Do not duplicate row UI or enable trailing sizes in Music.
- Canonical shared context menu, popup, text input, header, row and loading
  helpers are used directly. The Files root Compose Column owns
  `Modifier.statusBarsPadding()`, ensuring the shared header/path bar starts
  below the native Android status bar across all Files pages. Files selects
  `Bbos6SectionHeaderStyle.FLAT_BLACK_COMPACT` for its path bar (28dp flat
  black, no border, 2dp text inset). Other clients retain `BEVELED`.
  Do not add status-bar-specific padding inside `Bbos6SectionHeader`
  or individual pages. Its optional passive `trailingContent` slot reserves
  width at the right, ellipsizing long paths without changing header height.
  Files selects shared `titleOverflow = TextOverflow.StartEllipsis` only
  for directory paths, preserving the current folder at the right and moving
  truncation dots to the **left**. Default end-ellipsis remains for every
  other header/client, including Files home and Settings.
  Files owns its Copy/Cut indicator, clipboard state and cancel/paste actions;
  no other app gets an indicator by default.
- Android storage access is explicitly user granted; protected/private
  app storage remains restricted. All file operations validate canonical
  volume boundaries, refuse overwrite, and run off the UI thread.
- App-specific file browsing, operations, favorites and Android FileProvider
  integration belong only to Files. Never use file:// intent URIs.
- Favoured folders use original 1338.png (`bbos6_favourited_folder`) both
  in normal directory rows and the Favourites page; unpinned directories keep
  112.png. The original is mapped in `shared/icon-index.csv` and exposed
  through a shared resource symlink. File favorites are unchanged.
- `FavoritePolicy` and `FavoritesStore` persist location shortcuts, not
  file copies. Home has **one** Favourites navigation row, while actual pinned
  entries live in the dedicated Favourites page in saved insertion order.
  Long-press a favorite to remove only the shortcut. Validate saved paths
  and volume roots before opening. Do not duplicate shared list UI.
- The home-only artwork mappings are Recent Files=129, Images=2219,
  Videos=2216, Audio=1579, Favourites=950; retain original icon 112 for folder rows and other
  directory shortcuts. All Files lists use the shared `Bbos6PageList`
  default 2dp inset and 36dp artwork slot (32dp icon viewport) with 52dp
  rows; there must be no per-screen icon-size overrides. The home list
  reserves a blank icon column for text-only entries to align labels.
  Original artwork remains in canonical shared resources with no maintained
  Files copies. Audio uses `bbos6_files_home_audio`;
  the existing Genres mapping `bbos6_menu_genres` (348.png) is unaffected.
- `MediaCategoryBrowser` uses Android `MediaStore` for Images, Audio,
  Videos **and Documents** across shared volumes. Documents queries
  `MediaStore.Files` with `DocumentMimePolicy` MIME filters rather than
  resolving the literal `Documents/` directory. These indexed results use
  content URIs and currently support Open/Share; do not build custom
  media scanners. Unknown/non-document MIME types may be excluded.
  Only Downloads remains a native public-directory shortcut through
  `FileBrowser`. Preserve Back-to-home navigation and the current 52dp
  rows / 36dp icon slot / 4dp text inset.
- Files has a Settings home shortcut as the final list row, showing the shared
  `Bbos6ThemeSelector` and `Bbos6ThemeManager` with Dark/Light/System
  preferences. No all-files permission is required; Back returns to home.
  The preference persists per application. Files' root is wrapped in
  `Bbos6ThemeSurface`; shared `Bbos6PageList`, `Bbos6MediaListRow`
  and `Bbos6EmptyState` opt into its effective colors, preserving the
  existing flat-black path header and dark popups. Do not duplicate theme
  UI or add per-screen foreground overrides; future opt-in belongs in shared.
  Keep future Files settings business logic in this app, not shared UI.
- With no clipboard staged, Files owns the directory item's fixed 3x3
  context menu order: Cut/Copy/Rename, Properties/Open/Delete,
  Select/Share/New Folder. Keep Share present but disabled on folders so
  the grid remains stable. Bookmark commands follow in a second group.
  When Copy/Cut is pending, both item and blank-folder directory menus
  instead show only Paste, New Folder and Cancel Copy/Cut, supplied by the
  single Files-owned `FilesClipboardMenu` action policy. Paste and New
  Folder always target the **current** folder, never the long-pressed row.
  Normal row activation (including entering directories) remains enabled.
  New Folder must not clear the clipboard; cancelling or successful Paste
  restores the regular menu, while partial paste failures retain the
  reduced menu for retry. Do not replace Favourites, indexed media or
  active multi-selection menus: those are not directory paste destinations.
  Properties uses shared `Bbos6PropertiesDialog` and `Bbos6PropertyRow`;
  Files alone reads actual, authorized filesystem metadata off the UI
  thread using `FilePropertiesReader`. Do not derive folder size from
  `File.length()` or recursively scan folders for this dialog.
- Keep shared list controller and context menu app-neutral.
- Delete always prompts with Cancel selected and has no undo.
- Keep trackpad, physical keyboard, DPAD, Menu and touch parity.

Build/test from monorepo root with JDK 17:
```bash
gradle -p apps/files :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r apps/files/app/build/outputs/apk/release/app-release.apk
```
Never uninstall/clear user data as a routine build. No merge until explicit
approval and Q25 verification.
