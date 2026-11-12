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
- `Bbos6PageList` is the common full-page trackpad controller. Shared
  onLongPress and onMenu callbacks own input routing, including an empty list;
  Files owns commands. New Folder and Paste live in the context menus, not
  artificial selectable directory rows.
- Canonical shared context menu, popup, text input, header, row and loading
  helpers are used directly.
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
- The home-only artwork mappings are Images=2219, Videos=2216, Audio=1579,
  Favourites=950; retain original icon 112 for folder rows and other
  directory shortcuts. Home artwork uses 36dp slot / 2dp inset (32dp icon
  viewport), reserves a blank icon column for text-only home entries and
  keeps 52dp row height. Other lists preserve the shared default 8dp inset. These are resource symlinks in canonical shared
  resources, not maintained Files copies. Audio uses `bbos6_files_home_audio`;
  the existing Genres mapping `bbos6_menu_genres` (348.png) is unaffected.
- `MediaCategoryBrowser` uses Android `MediaStore` for Images, Audio,
  Videos across shared volumes. These indexed results use content URIs and
  currently support Open/Share; do not build custom media scanners.
  Downloads and Documents are simple links to Android public directories
  via the existing `FileBrowser`. Preserve Back-to-home navigation and
  the current 52dp rows / 36dp icon slot / 4dp text inset.
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
