# BB Music — standalone Android application

The installed application's display name is **Music** (Android manifest
label), and its Media3 browsable library root also advertises **Music**.
The package ID remains `dev.q25.bbmusic`, so this does not create a separate
installation or change the existing local signing/update workflow.

BB Music is an ordinary Android APK (`dev.q25.bbmusic`). It does not require
BB UI, Vector, LSPosed, root, another player, a custom ROM or SystemUI access.
Its Gradle project lives at the root of this repository. The canonical BBOS6 assets and reusable Android UI widgets are pinned through the `assets/bbos6` Git submodule.

## Standard local Release workflow (same as TV-DV)

Build and test the **release** APK on the Q25 before publishing:
```bash
git submodule update --init --recursive
gradle :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```
After Q25 approval, publish **the same APK** using GitHub CLI (no rebuild):
```bash
gh release create "bb-music-$(date -u +%Y%m%d-%H%M%S)" \
  "app/build/outputs/apk/release/app-release.apk#BBMusic.apk" \
  --repo NeilRuffell/bb-music --target "$(git rev-parse HEAD)" \
  --title "BB Music" --notes "Tested release APK for direct installation."
```
Gradle handles APK signing using the same local Android debug identity across
builds, like TV-DV's Release build; do not generate disposable keys. Keep
`~/.android/debug.keystore` safe. The old GitHub Actions job that signed
each APK with a *new* certificate was removed. Existing installations
signed by that job cannot be upgraded in-place using a different identity.


## UI and navigation

The BlackBerry OS 6-style home screen uses a native Android `ListView` and
`BaseAdapter`, with the same one-row-at-a-time hardware Up/Down navigation
and native touch scrolling model as the BB UI Quick Settings shade.
BB Music's library is always dark: its shared list background, outer
viewport and unfocused row backgrounds are pure black (`#000000`) in both
Android light and dark modes. Titles remain white, counts muted, and row
separators dark gray. **The original full-width, square-cornered blue focus
gradient is unchanged but has no outline or stroke**, including the color-selection
logic. The outer `FrameLayout.setFitsSystemWindows(true)` keeps the list
below system bars.

Home screen, in order:

1. **Now Playing...** — only while Media3 has a current track in
   ready/buffering state (playing or paused)
2. All Songs
3. Artists
4. Albums
5. Genres
6. **Playlists** — local, persistent, ordered playlists
7. **Folders** — browse directories chosen in Settings
8. Shuffle Songs (first-party shuffled playback)
9. Settings

**Playlists** uses the existing menu index 4 and original verified BBOS6
`337.png` icon. Folders keeps index 7, preserving the existing Shuffle
and Settings routing. Both native touch and Q25 trackpad controls work in
the existing list and context menus.

The **Now Playing...** item is the first row of the existing shared native
`MusicListView`, with the original BBOS6 `341.png` icon and identical
row sizing/gradient/trackpad handling. It is not shown before a song is
queued, and disappears if the session no longer has an active track.
Tapping or pressing Enter on this item reopens the existing Now Playing
screen for the **same** Media3 session; it does **not** restart, resume, or
requeue the song, so paused audio remains paused. The same row appears
when resuming the app if the live session still has a current track.
The original seven menu indices remain unchanged in code; Folders has index 7.
Updates modify
only `rootPage.rows` and preserve existing list selection/Back navigation;
the shortcut is not included in the home screen's song-search results.

**Settings** uses reusable non-selectable group headers. Under the
**Music library** group, **Music Folders** opens a submenu with **Add folders**
and a live list of saved folders, each with a **Remove** control. The group
also contains **Refresh library** (incremental), **Full rescan** (complete
metadata recheck) and **Auto refresh on launch** (On/Off, initially On).
Removing a folder does not delete the files on the device.

### Now Playing metadata plates

The Now Playing stage displays the plain, horizontally centred song title
(including wrapped titles) above separate, compact BBOS6-style raised labels
for artist and album. `MusicBeveledTextLabel` is the
shared native Android view for these metadata plates. Its unfocused background
uses three native drawable layers: a vertically shaded low-contrast outer rim,
an inset cool-charcoal face, and a narrow upper reflection. A tiny text shadow
and a dark native elevation shadow provide restrained depth, without bitmap
assets, custom drawing or third-party dependencies. The brighter upper edge
and darker lower edge deliberately replace the former uniform blue-grey stroke.
Labels size to their text, are centred in the metadata column, and ellipsize
long values to avoid overlapping the album artwork.
Missing artist metadata hides the artist label. Missing album metadata
is stored as an empty string and hides the album label; the exact, literal
tag `Unknown Album` remains visible and selectable. When playback is
restored from an older queue, Now Playing resolves its album through the
current indexed track so stale synthetic labels disappear after the catalog
loads. Playback logic and the unboxed track title are unchanged. The
metadata parent disables child clipping to preserve the native shadow.

Artist and album labels are interactive on touch and the Q25 trackpad.
On Now Playing, Up/Down visits Repeat/Shuffle, artist, album, seek and
transport in vertical order, skipping any missing artist/album labels. The
focused metadata label uses the vivid blue palette of the shared BBOS6
Music selection gradient regardless of Android night mode;
Left/Right remains reserved for Repeat/Shuffle, seek editing and transport.
Pressing Enter/trackpad on a metadata label opens the current song's existing
Artist or Album group page (including the existing album header).

Metadata navigation resolves the active Media3 item's exact SAF URI against
the currently indexed catalog, so identically named albums are not confused.
It neither requeues nor resumes playback. The browse breadcrumb is reset to
**Music → Artists/Albums → selected group**: Back returns to the Artists/Albums
list, then Music, **never to Now Playing**. The menu's selected category
and group rows are retained for normal Back behavior. If the catalog is still
loading, or the active track is no longer indexed, the view remains on
Now Playing with a short explanation rather than opening an incorrect group.

### Now Playing mode selection bounds

Repeat and Shuffle focus backgrounds use the shared
`MusicPlayerChrome.modeSelectionBackground()` drawable. It wraps the
existing `MusicSelectionGradient` in a vertical 1dp `InsetDrawable`,
leaving the mode header's original top highlight and bottom bevel exposed.
The buttons retain their full touch and trackpad target heights. This does
not alter library list selection gradients or the seek/transport panels.

### Folders browsing

**Home → Folders** lists the directories saved under **Settings → Music
Folders**, using their actual SAF display names. For example, selecting
`Internal Storage/music` shows a **music** row; opening it lists its immediate
child folders and files. Selecting a child folder continues the hierarchy to
any supported depth. Back returns to the previous folder or the Folders menu,
with the existing list's selection and scroll position preserved.

Folder location subtitles are formatted by the shared
`MusicFolders.displayLocation()` helper on both **Home → Folders** and
**Settings → Music Folders**. For example, Android's SAF document ID
`primary:Music/Albums` displays as `Music/Albums`; `primary:` alone
displays as `Internal storage`. Other storage volume identifiers remain
visible to distinguish devices. The original SAF document ID is retained
unchanged for directory queries, authorization and navigation.

Folder navigation uses the same `MusicListView` / hardware trackpad focus as
other library pages. Directories appear before files, in case-insensitive name
order. Only directories not beginning with `.` and verified indexed audio files
are shown. Hidden directories such as `.thumbnails`, cover artwork, playlists and
other non-audio files are omitted, including from configured-root entries
when a root's display name starts with `.`. This affects only Folders browsing:
Settings can still manage all configured roots and the library scanner's
existing selection remains unchanged.

The folder worker first identifies audio candidates with the same
MIME/extension/playlist exclusion policy as the library scanner, centralized
in `MusicFilePolicy`. Folder rows show those files **only** when their document
URI matches a verified song in the currently selected tree's catalog. A matching
extension or MIME type alone is insufficient; unindexed, unsupported or
unreadable audio-looking files are hidden rather than shown as unplayable
duplicates. Repeated provider rows for a document URI are displayed once.
New music requires **Refresh library** to become visible if it has not yet been
indexed. When the catalog is still loading, a folder containing only audio
candidates displays the loading message rather than an empty folder. Indexed
music shows artist, duration and artwork where available and plays through the
existing Media3 session. Previous/Next uses the playable tracks in the
displayed folder (or its current filtered results), not the entire library.

`MusicFolderBrowser` queries only the immediate children of a directory
through Android's Storage Access Framework, on a background worker. It reads
only saved roots with an active persisted read grant, uses no broad device
storage permissions, and does not mutate or delete device files. Revoked roots
remain visible in Folders as **Access unavailable** until permission is
restored from Settings. No independently maintained folder allowlist or
device-wide directory scan is introduced. Completing an existing music library
scan makes newly indexed files playable in previously opened browse pages
without discarding their navigation history.

The original BBOS6 `112.png` folder icon appears in the home **Folders**
entry, the saved folder rows in **Settings → Music Folders**, the configured
roots in **Folders**, and every recursively browsed subdirectory. Revoked
root entries use the same icon. All instances reuse the existing
`MusicListView` menu icon rendering/caching; no list component is duplicated.

### Persistent offline catalog (Room)

`MusicLibraryDatabase.java` uses AndroidX Room 2.8.5 (SQLite) to persist
the scanned song catalog in app-private storage. Tracks store only their SAF
`content://` URIs, source tree URI, tags, duration, track number and SAF
file size/last-modified/display-name fingerprints; audio
files remain in the user-selected folders. `LocalMusicSource` performs all
database operations off the UI thread. On startup it:

1. Determines the currently authorized trees using `MusicFolders.selectedTrees`.
2. Checks the saved scan marker, which includes the exact authorized tree set.
3. Loads tracks directly from Room when the marker matches, including a
   successfully indexed but empty collection. No directory enumeration or
   audio-tag extraction occurs on this fast path.
4. If the authorized folder set changes, incrementally enumerates selected
   trees, reusing already-verified unchanged metadata when possible.
5. With **Auto refresh on launch** enabled (default), displays cached tracks
   immediately, then asynchronously checks selected folders for changes.
   If the initial load already scanned, it does not scan twice.

**Refresh library** enumerates every selected directory to find new/missing
documents, but compares SAF file size, modification time and display name
before opening audio streams. Verified unchanged tracks reuse cached
metadata; new and changed tracks are re-inspected. Only added/changed/deleted
Room rows and the scan marker are committed in a single transaction.
A failed scan leaves the prior catalog intact and browseable.
**Full rescan** ignores fingerprints and rechecks all candidate audio files.
Individual rows are checked against the current authorized folder list
before display; stale rows from revoked/removed folder access cannot leak
into the library. A changed permission/selection set invalidates the
saved scan marker.

**Audio document validation:** Before indexing a purported audio file,
`LocalMusicSource` opens it through Android's `MediaExtractor` and requires
an actual `audio/*` track. An extension or document-provider MIME label alone
is insufficient. Only SAF entries explicitly identified as directories by
`DocumentsContract.Document.MIME_TYPE_DIR` are traversed. Playlist
documents (`.m3u`, `.m3u8`, `.pls`, `.xspf`) are excluded even if a provider
reports an audio MIME type; a successful child-query cursor does **not**
prove that a document is a folder. The scanner excludes unrecognized or
unsupported audio candidates rather than adding them as playable songs
with zero duration. `MediaExtractor` may also reject otherwise legitimate
formats such as WMA when no extractor is available on the device. A playable audio file with unreadable title tags still uses its filename
and Unknown Artist/Genre fallbacks. Missing album tags remain empty: they
are not given a synthetic `Unknown Album` value. Albums browsing and counts
include only tracks with a real, non-blank album tag. Such tracks remain
available under All Songs, Artists and Folders; an album literally tagged
`Unknown Album` remains a valid browsable album.

**Album metadata index upgrade (v3):** The scan marker is versioned without
changing the Room database schema. On first launch following the upgrade, the
app enumerates the authorized music folders once, re-examining only cached
entries whose album was exactly `Unknown Album` (because previous versions
could not distinguish missing tags from that literal value). Unchanged
other files still reuse metadata signatures; the scan preserves selected
folders, SAF grants and existing data. After the successful atomic scan,
the new marker restores fast cached startup. A failed scan retains the prior
catalog intact, as with existing refresh behavior.

**Room v1 to v2 migration:** Existing metadata, scan marker and selected-folder
SAF permissions remain intact. Previous tracks have unknown fingerprints;
the first incremental refresh rechecks them and saves size/time/name data.
Later scans reuse unchanged rows. SAF providers may report null file sizes or
timestamps; those files are rechecked every time rather than silently
classified as unchanged. Changes that preserve file size, timestamp and name
cannot be detected reliably, so **Full rescan** is available.

Auto refresh runs once when an Activity is created after its cached library
loads, not as continuous polling. The worker scans only selected trees and
logs added/rechecked/removed/reused counts. Manually triggered scans show a
completion message. `adb install -r` preserves catalog and settings;
uninstalling or clearing app data does not. Artwork caches are invalidated
after file changes or a full rescan.

### Original BBOS6 music menu icons

**Launcher icon:** The manifest references `@mipmap/ic_launcher`, a modern Android adaptive icon that scales the original BBOS6 `350.png` foreground (`@drawable/bbos6_menu_all_songs`) to the launcher viewport. The source is 65 × 65 pixels with only 39 × 46 pixels of visible artwork, which appeared undersized when assigned directly to `android:icon`. The adaptive resource fixes launcher sizing without replacing the original PNG or affecting the **All Songs** menu icon.

**Canonical icon reference:** [`assets/bbos6/icon-index.csv`](assets/bbos6/icon-index.csv)
in the pinned [`bbos6-shared`](https://github.com/NeilRuffell/bbos6-shared)
submodule. Read [`AGENTS.md`](AGENTS.md) and the shared
[`AGENTS.md`](assets/bbos6/AGENTS.md) before editing BBOS6 assets.
For every newly verified original icon, update the index **in the shared
repository**, push/review the shared commit, and advance this repository's
submodule pin. Never create a second index or guess identifications.

The first six home-menu rows now render the **original PNGs** from the
repository-wide `assets/bbos6/icons/` collection. They were matched
visually and by image comparison against screenshots of the BlackBerry
OS 6 Music home menu supplied for this project:

| Menu entry | Original asset | Android packaged resource |
| --- | --- | --- |
| Now Playing... (when active) | `341.png` | `bbos6_menu_now_playing.png` |
| All Songs | `350.png` | `bbos6_menu_all_songs.png` |
| Artists | `345.png` | `bbos6_menu_artists.png` |
| Albums | `351.png` | `bbos6_menu_albums.png` |
| Genres | `348.png` | `bbos6_menu_genres.png` |
| Playlists | `337.png` | `bbos6_menu_playlists.png` |
| Saved playlist rows (inside Playlists) | `337.png` | `bbos6_menu_playlists.png` |
| Add Playlist (inside Playlists) | `335.png` | `bbos6_menu_add_playlist.png` |
| Play all (inside a playlist) | `334.png` | `bbos6_menu_play_all.png` |
| Shuffle (inside a playlist) | `339.png` | `bbos6_menu_shuffle_songs.png` |
| Folders (home and directory rows) | `112.png` | `bbos6_menu_folders.png` |
| Shuffle Songs | `339.png` | `bbos6_menu_shuffle_songs.png` |
| Settings (Options wrench) | `3412.png` | `bbos6_menu_settings.png` |

Android requires referenced PNGs to be present in application resource
directories, so the exact original binary blobs are reused under
`app/src/main/res/drawable-nodpi/`. No artwork is redrawn or
modified. `MusicLibraryRowPolicy.menuIconForRow` owns the menu-to-resource mapping;
`MusicListView` uses its existing left image slot to draw the selected
resource, so it does not introduce a second adapter or custom scrolling.

**Shared icon normalization:** The renderer loads each original `drawable-nodpi`
PNG once, finds the bounding rectangle of pixels with alpha greater than 16,
and creates an in-memory cropped bitmap. `FIT_CENTER` then scales that
visible region proportionally into a centered **52 × 52dp** area inside
the existing **68 × 68dp** image slot. This normalizes the apparent size
despite the original 65px/75px source dimensions and differing transparent
margins. The original PNG files remain byte-for-byte unchanged. Cached
bitmaps are reused by the shared list adapter. When a recycled row changes
from a home-menu icon to album artwork, its temporary 8dp inset is cleared;
album covers retain their full edge-to-edge 68dp presentation.

The initial six icons were identified from the Music menu reference.
**Settings** now uses the original BBOS6 **Options** wrench from
`3412.png`, identified and confirmed against the supplied screenshot;
its PNG is packaged without modification. Album artwork on songs,
albums, artists and genres remains independent of these static home-menu icons.

**Missing album covers:** Original BBOS6 `543.png` is packaged as
`bbos6_album_no_art.png` and displayed when an **Albums** category entry,
an open album's information header, or the **Now Playing** artwork area
has no embedded cover. Real embedded album art replaces this fallback in
Now Playing when successfully decoded; each track change immediately
resets the artwork area to the original fallback until the new cover loads.
Other library artwork fallbacks and original audio files are unchanged.


### Shared list component

The list implementation is extracted into `MusicListView.java`. This class
extends Android's native `ListView` and owns one `BaseAdapter`, 68dp recycled
rows, the full-width square-cornered BBOS6 selection gradient, cached album
artwork binding, count/text presentation, and the same QS-derived Up/Down
trackpad behavior. Every menu and library page uses **the same instance**;
switching pages updates the adapter's data and preserves prior selection.

`MusicActivity.java` owns only page data, album-art *selection policy*
(which album's cover belongs to each entry), library counts, Back history,
permissions, Settings and playback routing. It supplies platform-neutral
`MusicListView.RowData` to the component through a small `DataSource`
interface. No second list, independent adapter, separate selection style or
custom touch scroll engine is introduced. The view owns its artwork cache
lifecycle and releases it when the Activity is destroyed.

The current standalone implementation still does not depend on BB UI,
Vector/LSPosed, a custom ROM, or another music app.

### Track Properties (long press)

Track rows in All Songs, Artists, Albums, Genres, Folders and search results
open the shared `Bbos6ContextMenu` on touchscreen or Q25 trackpad long press.
Their actions are **Properties** (existing `Bbos6PropertiesPopup`) and
**Delete** (native Storage Access Framework deletion). Artist, Album and Genre
group rows display only **Delete**: it operates on all songs in that specific
indexed group, never on album, artist, or directory containers. Folder entries
still have no long-press actions. Normal short presses still play/open tracks;
nothing on Now Playing, Home or Settings acquires long-press behavior.

`MusicTrackPropertiesReader` reads metadata asynchronously for the exact
selected song's `content://` SAF document, checking that its source folder
still has the user-selected persisted read grant both before and after the
query. Only this one authorized document is queried, not MediaStore or
unselected folders. The reader populates the shared, application-neutral
property items; it never changes the song, selection, queue or files.

Fields, when available:
- **Path:** a logical storage location only when the External Storage
  DocumentsProvider supplies a path-shaped document ID consistent with its
  display name. Opaque SAF IDs and guessed `/storage/emulated/0` paths are
  not presented as physical file paths.
- **Size:** provider-reported byte count, formatted using Android's native
  file-size formatter. Missing/invalid size is omitted.
- **Type:** provider-reported `audio/*` MIME type, or "Audio File" for an
  already-verified track when the provider has no usable audio MIME type.
- **Created:** omitted: Android's standard SAF document projection has no
  portable creation timestamp. Recorded/tagged dates are not repurposed.
- **Last Modified:** optional provider-reported timestamp, formatted using
  the system date/time settings. Missing or zero values are omitted.
- **Title, Album, Artist:** existing indexed song metadata. Missing album
  and synthetic "Unknown Artist" values are omitted.

Missing fields are omitted instead of displaying fabricated placeholders.
A failed provider query still permits known indexed track metadata.
Slow asynchronous results are discarded after navigation, a new request,
search changes or Activity stop. Back dismisses the context menu first;
when Properties is open, Back dismisses the Properties dialog. The underlying
list selection, scroll position and playback remain unchanged.

The context menu uses the shared builder with Android-provided information
and delete icons (`android.R.drawable.ic_menu_info_details` and
`android.R.drawable.ic_menu_delete`), not unverified BlackBerry assets.
Adding future actions or groups requires editing the Music caller's
menu definition, not the shared renderer.

### Shared button-only confirmation dialogs

Use the canonical `bbos6-shared` `Bbos6PopupButtonList` for simple app-owned
confirmation/choice dialogs, rather than native Android AlertDialog buttons.
`MusicActivity.showButtonConfirmation(...)` supplies the existing action-specific
title, full explanatory message, labels and callback; the shared helper owns
visuals and hardware/touch input. Cancel is the safe initial selection.

The popup sizing and long-press menu sizing are both owned by the
`bbos6-shared` implementation. The current integration pins the compact
Bold 9900-inspired helpers; Music does not add pixel offsets, density
overrides, layout-scale corrections, or separate row-size calculations.
The button popup specifically uses a screenshot-measured blue/silver
gradient, a thin native outline and **no opaque black underlay or per-button
elevation**. The former thick black frame and continuously darkening face
have been removed centrally in the shared renderer.
Review button/label sizes and three-column context menus directly on the
Q25 before approving the shared or Music PRs.

The same shared popup also handles the **Add Playlist** two-option
choice, and its optional native text-entry mode is used for both playlist
creation and renaming. Save/Cancel are horizontal with Cancel initially
highlighted. A single Music helper, `showButtonPopup(...)`, manages dialog
lifetime for confirmations and the playlist choice without duplicating
any widget drawing or navigation. The button actions are still
application-owned and do not change Room, SAF or Media3 logic.

The shared helper now handles **per-track and artist/album/genre file deletion**,
**playlist deletion**, **removing a selected music folder**, and
**Full rescan** confirmation. It changes no file-operation, playback,
playlist-database, or SAF permission behavior. The **Android document-tree
write-authorization picker stays native** (it cannot be replaced by a custom
popup). Text-entry dialogs retain native Android EditText, but
playlist selection uses the shared Compose `Bbos6ListSelectionComposeBridge`
and compact `Bbos6MediaListRow` faces instead of an Android AlertDialog.

### SAF track and group deletion

Delete permanently removes the **actual audio documents** from selected
music folders. App-owned confirmations use the shared
`Bbos6PopupButtonList`, with rounded silver/blue buttons, the original warning
text and **Cancel initially highlighted**. For a track, it asks
**"Are you sure you want to delete
<filename> permanently from storage?"** using the document provider's
`COLUMN_DISPLAY_NAME` (not the track-title metadata). If the provider does
not supply a filename, it says "this track" rather than inventing one.
For artist/album/genre groups, it asks the corresponding question naming
the group and the number of songs affected. Cancel changes nothing.

- A track row deletes that single track.
- An Artist, Album, or Genre group deletes all **indexed tracks** in that
  group. Document URIs are deduplicated. Other groups or folder contents
  not in the selection are untouched. Folder directories are **never** deleted.
- Existing SAF grants were intentionally persisted read-only. When a selected
  tree lacks persistent write access, Android's
  `Intent.ACTION_OPEN_DOCUMENT_TREE` picker is used to authorize the
  **same tree** (possibly more than once for a multi-root group).
  Choosing a different tree is rejected without adding it to the music
  library. A denied or unavailable grant cancels the operation. Write grants
  persist so the user does not need to reauthorize each subsequent deletion;
  removing a selected folder releases **both** read and write grants.
- `MusicTrackDeletion` performs file operations off the UI thread using
  `DocumentsContract.deleteDocument()`, after verifying the exact selected
  tree identity, an active persisted read/write grant, and the provider's
  `Document.FLAG_SUPPORTS_DELETE`. It refuses directory documents.
  It does not use `java.io.File`, rename/move files, or request broad storage
  permissions.
- Provider failures or unsupported documents are counted as failures; other
  selected songs can still be deleted. The UI reports successes and failures
  separately and never reports a failed file as removed.
- Before the first deletion the worker invalidates the persistent Room scan
  marker, allowing recovery from a process termination during a batch.
  Successfully deleted track rows are removed from the private catalog,
  visible library pages, album artwork caches, the active Media3 queue, and
  the saved playback-resumption data. Surviving files stay indexed and a
  normal incremental SAF refresh reconciles the library afterward.
- Deleting or removing a selected **folder configuration** in Settings is
  a different operation: it changes authorization and never deletes files.

**Testing:** On the Q25, confirm Cancel/Back/Escape do not delete files,
trackpad Up/Down changes only the highlighted popup button, Enter/trackpad
confirms exactly once, and the existing warning text is visible without
clipping. Preserve system SAF picker behavior when write permission is required.
Then test a track with a disposable audio file,
then test Artist/Album/Genre groups, cancellation, multi-folder write grants,
partial failures, active playback and restart retention. Run the existing
release APK build and install workflow; never uninstall or clear app data
as part of routine testing.

### Shared long-press input

BB Music uses the platform's long-press gesture handling for **music library
entries only**. Touch and physical Q25 trackpad holds share the same Activity
callback. Tracks open Properties/Delete; artist, album and genre groups
open Delete; folder holds currently have no assigned action.

- **Allowed rows:** playable songs/tracks; artist, album and genre group rows;
  and folder rows, configured roots and subdirectories (including entries
  opened via the Folders browser). These are checked centrally through
  `MusicActivity.supportsRowLongPress()`.
- **Excluded:** every home-menu shortcut, Now Playing (including its transport,
  seek, artist/album metadata controls and Repeat/Shuffle), Settings options,
  the folder Remove control, disabled entries, unavailable/unindexed files,
  and other non-library controls.
- **Touch:** the reusable `MusicListView` owns a native
  `OnItemLongClickListener`, forwarding only eligible rows through the
  existing `DataSource.longPress(position)` callback. Regular click behavior
  is unchanged; the folder Remove child is not a long-press target.
- **Trackpad:** the existing `TrackpadPressHandler` uses Android's
  `ViewConfiguration.getLongPressTimeout()` and checks the selected
  item's eligibility before dispatch. Ineligible controls still perform
  their normal short-click action on release even after a long hold.
  Eligible holds fire once and suppress the release click.
  Pending holds are canceled on other keys, touch, focus loss and Activity stop.
- **Actions:** tracks show Properties and Delete; artist, album and genre
  groups show Delete; folder holds remain unassigned. Hardware long
  presses request Android native haptic feedback.

Run `gradle -p music-app :app:testDebugUnitTest` for the
press-state gating regression tests, then verify touchscreen and Q25
trackpad behavior using a locally built APK before merging.

### Album detail header (BBOS6 Music)

Opening an entry on the **Albums** page displays an 88dp-tall black
album header above its song list, based on the supplied BBOS6 screenshot.

- Album artwork occupies an 88dp square, aligned exactly to the left and
  top of the content area; there is no extra padding against the Android
  status-bar inset or the left screen edge.
- A full-width **1dp separator in `#515251`** sits immediately below the
  88dp header content. The header's total height, including this separator,
  is 89dp. The separator is not an inset and does not overlap the artwork.
- To its right are the album title, artist, a genre line in parentheses
  (or `(Unknown)` if no genre tag was found), and the album's singular or
  plural song count.
- Art comes from **the same album-keyed, selected-folder-only thumbnail
  cache** used by regular Music list rows. Albums without embedded art show
  the existing placeholder.
- The header is displayed only on song lists reached from an album.
  Artists and Genres retain their existing list navigation.
- It sits above the `MusicListView`, not in its adapter: row indices,
  full-width blue selections, physical-keyboard search, trackpad behavior,
  playback queues and Back-stack selection remain unchanged.
- Only when an **album** is open, its song rows replace the left-side cover
  with their tagged track number. The 64dp-wide column is centered; tracks
  with no valid track-number tag show exactly `--` (two hyphens), matching
  the supplied BBOS6 reference. Regular All Songs, Artist and Genre song
  lists continue to display album artwork. Filtering an open album retains
  its number column.
- Back restores the parent album list; returning to the album restores
  its header. The header stays visible while filtering an album's songs.

The four-line layout reflects fields currently available in the
selected-folder metadata index. The third line explicitly represents
genre; the proprietary BBOS6 screenshot does not establish what its
`(Unknown)` label originally referred to.

### Physical keyboard instant search

While a music list or main menu is visible, **simply type** on the Q25's
physical keyboard. No search button, menu command or trackpad click is needed.
The first printable character reveals a **54dp BBOS6 search bar** above
the same existing `MusicListView`. Further typing filters results immediately.
Shift and ALT characters work through Android key mapping.

`MusicSearchBarView.java` now owns only the outer search panel; the canonical
shared `Bbos6TextField` owns the **same original beveled white input face**
in both Music search and the playlist New/Rename popup. Each extends Android's
native `EditText` behavior, retaining the existing input handler and cursor,
selection, keyboard, clipboard, and IME. This reuses one renderer rather than
duplicating a drawable in Music and the shared popup:

- **1dp top separator:** `#5A5D63` at the outer top edge.
- **Panel gradient:** vertical, `#4B4E53` (top) to `#292C31` (bottom).
- **Shared input field:** inset approximately 6dp horizontally and 6dp above,
  white with a 5dp rounded dark outer rim and a gray-to-white inset
  highlight/shadow that reaches pure white within the first ~5dp.
  Approximately 43dp tall; its face is drawn only by `Bbos6TextField`.
- **Typed text:** dark charcoal inside the white field, approximately
  12dp internal horizontal inset.
- No hint or `Search` description appears; the Q25 physical keyboard
  makes search discoverable without prompting. No on-screen keyboard opens.

This is purely visual: search matching, Back, trackpad focus, row
dimensions, album artwork and selected-folder restrictions do not change.

- **Music home:** Search the previously indexed **selected-folder songs**
  across song title, artist, album and genre. The results are playable songs;
  the original seven-entry home menu is unchanged when search is dismissed.
- **All Songs / nested song lists:** Filter that list's songs by song title,
  artist, album or genre.
- **Artists / Albums / Genres:** Filter only the currently displayed
  artist/album/genre entries by their visible name.
- **Settings / Music Folders:** Filter the current page's selectable entries
  by label. Non-selectable group headings are excluded from results.

**Back** closes the active search first and restores the previous unfiltered
selection and scroll position; a second Back performs normal page navigation.
Back from Now Playing returns to the filtered search results if playback
was started during a search. Backspace edits the query; an empty query
shows the original page entries again. Trackpad Up/Down still moves one
list row; Enter opens the highlighted result. Standard Android Ctrl/Meta
keyboard shortcuts are not intercepted.

When a non-empty query has no matches, the search results adapter remains
empty. A separate, non-focusable white text label reading
**No search results found** appears horizontally centered at the top of
the results area, beneath the search bar (and any album header). It is
not a ListView item: no row selection gradient, divider, or activation.
The label disappears as soon as results return, the query is cleared,
or search closes. The scan-in-progress message remains unchanged.

The search operates solely on the in-memory catalog returned from
`MusicFolders.selectedTrees`; typing never scans other folders,
queries MediaStore, uses the network, or requests additional permissions.
If a selected-folder scan is underway when search starts, the view
displays a scan-in-progress message and refreshes when it finishes.
The search field does not automatically invoke a touch keyboard.

### Artwork in music browse lists

The shared row height is **68dp**, increased from 64dp to add approximately
2dp of space above and below centered text relative to the separators.
A single
`MusicListView.ROW_HEIGHT_DP` constant sizes both the rows and square artwork,
keeping the image flush with the selection's left, top and bottom edges.

Songs, Artists, Albums and Genres display 68dp square album covers
flush with the left, top and bottom edges of the 68dp `ListView` selection
bounds, with zero artwork or row padding. Text labels keep their own internal
inset. The existing selected-row gradient stays full-width.
The home menu and selectable Settings rows use the same shared 68dp rows.
Reusable 36dp section headers are rendered as disabled ListView item types,
so they cannot receive selection or activate when pressing Enter.

**BBOS6 song-row metadata:** Every song entry in All Songs, artist/genre
track lists, album track lists and song-search results shows its **title on
the left**, the stored **track duration (M:SS, or H:MM:SS when needed) on
the right of the same line**, and its **artist on the second line**.
Unknown durations are not fabricated or displayed. The title truncates
before overlapping the right-aligned duration. Album track-number columns
and the shared 68dp artwork/selection bounds remain unchanged. Non-song
rows continue displaying their existing item-count or folder-location
subtitles, with no duration field.

The cover-selection rules are **BB Music project rules**, not a verified
claim about the proprietary BlackBerry OS 6 selection algorithm:

- **Songs:** Artwork for that song's album.
- **Artists:** Artwork of the artist's first album sorted alphabetically
  by album title (case-insensitive, deterministic ties).
- **Albums:** Artwork for the displayed album itself.
- **Genres:** Artwork of the first album sorted alphabetically within
  the genre (same deterministic tie-breaks).

All tracks in an album share the same cached cover. For an album, tracks are
checked in track-number/title order until an embedded cover is found;
if none has embedded art, the row shows a small music-note placeholder.
For Artists and Genres, **do not substitute another album** if the first
alphabetical album has no embedded artwork.

`MusicListArtworkLoader` reads embedded artwork using
`MediaMetadataRetriever`, accepts only `content://` song URIs returned
from the **user-selected SAF folder trees**, and does not query MediaStore,
scan unselected folders, access the network, or use Symfonium. Thumbnails
are decoded off the UI thread with two workers, bounded to approximately
160px per side, memory cached at 12 MiB and deduplicated by album.
When a row is recycled, its artwork binding key prevents earlier
asynchronous results from being displayed on the wrong entry.

**Current limitation:** External artwork files such as `folder.jpg` or
`cover.png` are not searched; the feature reads **embedded audio artwork**
only. The source repo and historical BlackBerry 6 documentation available
during implementation do not establish a verifiable proprietary selection
algorithm for artist/genre representative artwork.


**Child-item counters:** The main menu and browsable category entries use
two-line 68dp ListView rows, matching the BBOS6 Music example: a primary
label and a smaller muted count underneath. All Songs displays the number
of selected-folder audio tracks (e.g. `1 Song`, `2 Songs`); Artists, Albums
and Genres display distinct category entries (`1 Item`, `2 Items`).
Artist/album/genre groups display their direct number of songs when opened.
Settings has no child-count subtitle on the home menu, while retaining
its Add folders entry. The conditional Now Playing shortcut also has no
child-count subtitle. Playlists displays `0 Items` until the app supports
playlists. Shuffle Songs is an action and has no child count.

When the app opens, counts come from the saved Room catalog for the current
authorized folder set. With no folders selected the counters are zero **and
no storage scan occurs**. During initial database loading/indexing counters
read `Loading...`; failures show `Unavailable` instead of a false zero.
Adding a folder invalidates the previous snapshot and refreshes counts.
Count updates rebind visible rows
without adapter invalidation, preserving trackpad selection and scroll offset.

## Selected-folder-only device library

**Privacy/access contract:** BB Music must **not scan any music outside the
directories explicitly selected by the user**. Automatic startup scanning,
when enabled, is limited to previously selected and still-authorized trees.
With no selections there are no media queries.

- Settings > Music Folders > Add folders opens Android's trusted
  `Intent.ACTION_OPEN_DOCUMENT_TREE` folder picker.
- Each folder initially grants **read-only** access to its directory and
  subfolders using Android's Storage Access Framework (SAF). The separate
  user-confirmed Delete action can request a persisted **write grant for the
  same selected tree**, and only uses it for explicit file deletions.
- The app persists the returned tree URI in `MusicFolders.java`, together
  with Android's durable read grant. Selecting another folder adds it to the
  allowlist rather than replacing earlier selections.
- `LocalMusicSource.java` enumerates **only** these selected and currently
  authorized trees via `DocumentsContract` child-document queries. It never
  queries device-wide `MediaStore` tables, scans arbitrary storage roots,
  or reads files outside the selected subtrees.
- The manifest does **not** request `READ_MEDIA_AUDIO`,
  `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, network access,
  or root permissions for this feature.
- The scanner validates actual audio tracks and reads their metadata inside
  allowed trees on a background thread. Results are stored in an app-private
  Room database and presented
  through an in-memory snapshot. A newly selected folder invalidates the
  snapshot and scans the updated allowlist; unchanged libraries load from
  Room without rescanning.
- All Songs, Artists, Albums and Genres use the same recycled list rows.
  Album/artist/genre grouping uses file metadata; untagged audio remains
  visible by filename, with Unknown Artist/Genre fallbacks. Missing albums
  do not generate an Albums entry or a Now Playing album pill.
- Songs retain their `content://` document URIs for the first-party Media3
  player. Selecting a track opens Now Playing and starts playback.
- The original Symfonium adapter is preserved but never automatically
  connects; it is optional and not needed for this local library.

**Android picker restrictions:** Starting in Android 11, Android does not
allow choosing the internal storage root, certain SD card volume roots or
the `Download` directory itself. Choose a folder such as `Music`, or
individual subfolders. The selected folder includes its subfolders.
Android's DocumentsProvider must actually expose the folders for SAF access.

### Manage selected folders

The **Settings > Music Folders** submenu has **Add folders** as its first row.
Beneath it, one regular 68dp list row appears for each **saved** selected SAF folder,
with its document-provider display name, the tree document ID as a
distinguishing subtitle, and a right-side **Remove** button. Folders whose
Android read permission is no longer held remain listed with an
**Access unavailable** subtitle so they can be removed or re-authorized.
The submenu uses the existing shared `MusicListView`, selection gradient, touch
scrolling, physical keyboard and one-row trackpad Up/Down navigation.
Tapping Remove, tapping a folder row, or pressing Enter/trackpad on a
highlighted folder row opens a **Remove / Cancel confirmation**.

Confirming removal deletes only that tree's saved allowlist entry and
releases this app's persisted read/write grants. It **never deletes, moves or
modifies files on the device**. The removed folder disappears from Music Folders;
in-progress scans are invalidated, old library metadata and artwork caches
are cleared, and the remaining authorized folders are scanned again.
Removing the final folder leaves an empty selected-folder library. Back
retains normal page navigation, and the submenu refreshes after Add folders
returns from the Android folder picker. Re-selecting a saved folder that had
lost its read grant restores authorization and triggers a new library scan.
**Refresh library** updates the catalog incrementally, keeping the existing
snapshot browseable while it runs. **Full rescan** rechecks every candidate
when provider change metadata cannot be trusted. The scanner still reads
**only** the intersection of saved folders and live read grants. If a document provider cannot return a friendly folder name,
the UI derives a label from its tree document ID.


## Native playback and the BB UI Now Playing port

BB Music now includes its own `MusicPlaybackService` with AndroidX
Media3 1.9.4 ExoPlayer and a MediaLibraryService. The independent
`MusicPlaybackClient` controls it from the Activity. Neither Symfonium
nor the BB UI Vector module is needed for local playback.

Selecting a local track creates a queue from its current song list and
opens Now Playing. The queue's source URIs are the `content://` documents
obtained from **only the selected SAF folder trees**. Back returns to the
previous library page without stopping playback. The root **Shuffle Songs**
action starts a shuffled queue of selected-folder music.

**Local-file media source:** `MusicPlaybackService` supplies
`ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(this))` to
`ExoPlayer.Builder`. Selected-folder songs are regular SAF audio documents,
not HLS/DASH streams. Do not revert to the default auto-detecting media-source
factory without supporting its optional streaming modules. The device log on
2026-11-07 confirmed that auto-detection attempted to instantiate the missing
`androidx.media3.exoplayer.hls.HlsMediaSource$Factory`, resulting in an empty
Now Playing queue after `setMediaItems`. The progressive source avoids that
class lookup and uses Android's content URI resolver. This change does not
grant additional storage access and does not affect the Room catalog.

The Now Playing screen is a standalone presentation port of BB UI master
`TwoColumnPanel.java` and `UnifiedShadeHost.java`, preserving:

**Curve 9300 responsive height proportions:** The supplied 320×226 BBOS6
Now Playing reference measures approximately 22px for the top mode header,
20px for the seek strip and 30px for the lower transport controls.
`MusicPlayerChrome` defines one shared 48dp transport-bar baseline (maintains
a 48dp-tall transport touch target) and derives the other bars from those
reference proportions: mode header **35dp**, seek panel **32dp**, transport
**48dp**. These are density-independent dimensions, not absolute screenshot
pixels; the artwork/information area retains `layout_weight=1` and absorbs
all remaining available vertical space, including changes in phone size and
screen insets. The transport glyphs retain their 36dp intrinsic size, with
6dp vertical insets so none are clipped in the shorter button row. No change
to trackpad, touch or media playback logic.

- Proportional 35dp mode bar with icon-only Repeat on the left (replacing the "Media"
  heading), a centered read-only queue position (e.g. `17 of 637`), and
  icon-only Shuffle on the right. The counter uses the current Media3
  media-item index and playlist length (not the total library count), updates
  when the playback controller changes and shows `0 of 0` if disconnected
  or empty. It does not affect trackpad focus. Original BBOS6 PNG mapping:
  - **Repeat:** unavailable `485.png`, available/off `488.png`,
    Repeat All on `486.png`, Repeat One on `487.png`.
  - **Shuffle:** unavailable `509.png`, available/off `511.png`,
    on `510.png`.
  The icons are bundled as `bbos6_repeat_*.png` and
  `bbos6_shuffle_*.png` drawables and indexed in
  `assets/bbos6/icon-index.csv`. Both buttons have zero internal padding
  and a background only when focused, using the exact theme-aware list-row
  selection gradient through the shared `MusicSelectionGradient` helper.
  Repeat is flush with the left edge and Shuffle with the right; neither
  button nor the header has padding. Trackpad left/right navigation follows
  their displayed order (Repeat then Shuffle). Touch controls are unchanged.
  **Header background (2513.png reference):** The mode bar draws
  a smooth vertical charcoal gradient based on the main tones of the supplied
  358×40 BBOS6 header sample, rather than copying the sample's indexed-PNG
  palette steps. A short, broad centre-brightened bevel occupies the upper
  ~7dp. Its own 1dp top border runs from silver at the sides to white at the
  centre, and its 1dp bottom border is a very subtle, dark horizontal bevel.
  The old 2dp dark bottom rule is removed for this bar only. Header
  bevel, borders and dimensions are unchanged by the seekbar background
  update; original reference colour stops are used directly without
  screenshot brightness compensation. Repeat/shuffle focus backgrounds
  and the artwork stage are unaffected.
- Full-height half-radial artwork/metadata background and 50/50 art/text split.
- Proportional 32dp seek strip styled after BBOS6 `2486.png` (a 480×30 indexed
  bottom-panel reference), with its **own** smooth charcoal background,
  muted dark horizontally graduated top border, centre-brightened lower
  bevel and 1dp silver-to-white-to-silver bottom border. The seek strip
  supplies the separator above the four-button transport row; the transport
  background adds no duplicate top line. The bottom border and bevel come
  from the same `MusicPlayerChrome.seekBackground()` helper, not from the
  artwork stage or the transport controls. The dark background fades from
  `#212421` near the top into `#181818` below, with no literal PNG
  palette steps. Reference colours are used directly with no brightness
  compensation. Seek progress and trackpad playback behaviour remain
  unchanged.
  **Curve 9300 seek geometry:** The supplied 320×226 screen reference shows
  an approximately 30×9px pill, a thin ~2px rail and time readouts close
  to the left/right screen edges. The 32dp seek panel now uses a 42×13dp
  rounded thumb (same outer size for selected and unselected states) and
  3dp rail. The seek-row outer padding is 4dp per side; elapsed and
  remaining readouts use their measured content widths (minimum 34dp and
  42dp, respectively), so longer hour-formatted times cannot be clipped
  by fixed-width label slots. The remaining area belongs to the weighted
  SeekBar, with 2dp margins. Its horizontal end padding is derived from
  half the thumb width plus a 2dp guard, and the native thumb offset is
  explicitly half its width. The pill cannot overlap either time label
  at 0% or 100%. One full-width background drawable now renders BOTH
  the dark unplayed rail and white completed progress from the same starting
  coordinate, ending 2dp before the SeekBar allocation edges. Native progress
  drawing is suppressed, since its clipping to the padded thumb range caused
  the completed white line to start too far inward. The white endpoint is
  calculated as `left + (right - left) * progressFraction` while native
  thumb travel still uses the half-pill + 2dp guard. Consequently, the pill's
  left edge covers the rail start at 0% and its right edge covers the rail end
  at 100%; at intermediate values, the white endpoint sits under the pill.
  Playback seeking, trackpad navigation and touch dragging are unchanged.
  **Seek time labels:** The left label shows elapsed track time, while the
  right label shows time remaining prefixed with a minus sign, e.g.
  `0:09` and `-6:28` for a 6:37 track. Remaining time is computed as
  duration minus the current position and clamped to zero at the end.
  Both labels update during playback and while dragging the seek thumb;
  unknown durations continue to display `--:--` on the right.
- Proportional 48dp edge-to-edge four-button Previous / Play-Pause / Stop / Next toolbar;
  charcoal gradient, matching button symbols, double gradient separators
  and fixed dark-blue selected-button gradient.
  **Transport background reference matching (3246.png):**
  `MusicPlayerChrome.transportBackground()` now uses a smooth 10-stop
  charcoal gradient drawn by Android, preserving the strong upper-third
  fade and darker lower plateau of the 480×60 BBOS6 Sound Recorder
  reference. It deliberately does not reproduce the indexed PNG's small
  palette/quantization steps. The silver divider is drawn separately by
  the seek strip. Reference stop colours are used directly, without the
  obsolete 12% white-lift compensation (which was introduced for a Q25
  screen overlay that has since been disabled). Selection colours,
  transport separators, dimensions and playback controls are unchanged.
- **Stop** pauses the audio immediately, seeks the current track to `0:00`,
  and calls Media3 `stop()`, returning the player to idle without changing
  the selected track, playlist, or Now Playing screen. **Play** after Stop
  explicitly prepares the retained item and starts again at the beginning.
  This differs from Pause, which preserves its prior position.
- The Media3 `MusicPlaybackService` uses
  `SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER`, so its active media notification
  is removed in the idle/stopped state. The system may independently cache a
  media-resumption suggestion; Android System UI owns that presentation.
  Normal active playback and background notification remain unchanged.
- Trackpad navigation: toolbar Up -> seek -> Shuffle/Repeat; Down returns.
  Seek only changes with Left/Right **after** pressing Enter/trackpad to
  enter seek-edit mode; another press exits. Touch-dragging works directly.
- An independent `MusicArtworkLoader` reads embedded album artwork only
  from the selected track's existing document URI on a background thread.
  If decoding returns no cover, Now Playing displays the same original
  BBOS6 `543.png` album-art fallback as the album list/header, instead
  of an `Artwork unavailable` text label.

Media3 owns audio focus, noisy-headset handling and the media-session
notification, including background playback. The Activity disconnects its
controller on Stop, but does not stop the service. Standard Android media
buttons and system controls can operate the MediaSession.

**Implementation:** Playback retention and system resumption are included in the standalone Music source. Validate each release APK on Q25.
The shade's original code is unchanged; this is a copied presentation,
not a runtime dependency. Android's native playback resumption callback
is implemented, and no separate playback mini-bar is added in the library.

`SymfoniumSource.java` remains an unused, optional adapter. Direct OneDrive
Microsoft Graph authentication, cloud indexing and downloads are future
features and are not required for local playback.

## Publish an approved release APK

Build locally using the **release** task above, preserving
`~/.android/debug.keystore` and the installed signing identity.

After that exact APK passes Q25 testing and receives explicit approval,
increment `versionCode` and `versionName` for each newly published release,
then upload **the same file** to
[`bb-music` releases](https://github.com/NeilRuffell/bb-music/releases)
using the `gh release create` command above. Never automatically rebuild
or sign a different APK, create ephemeral keys, uninstall, or clear app data.

## Build and test on Q25

Requires the standalone Android SDK 35, JDK 17 and globally installed Gradle.

```sh
cd ~/Projects/bb-music
git switch master
git pull --ff-only origin master
git submodule update --init --recursive
gradle :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

1. Open BB Music > All Songs **before choosing any folders**. It must not
   enumerate device music; it displays the Add folders instruction.
2. Go Back > Settings > Music Folders > Add folders; pick a music-containing directory and
   grant read access through Android's picker.
3. Return to All Songs. Only audio under that directory and its descendants
   should be indexed. Confirm an unrelated music folder is not listed.
4. Return to Settings > Music Folders > Add folders; choose a **different** music folder.
5. Return to All Songs and verify the union of the two selected folder trees.
6. Browse Artists, Albums and Genres, including physical trackpad navigation
   and Back. The blue selection and status-bar boundaries should remain
   correct.
7. In Settings > Music Folders, confirm both folders appear with readable titles, location
   subtitles and separate Remove buttons. Touch a button, then Cancel:
   neither folder may disappear. Use trackpad Up/Down and Enter on a folder
   row; the same confirmation must open.
8. Confirm Remove on one folder. Only its row must disappear; the other
   remains available. Reopen All Songs: tracks from the removed folder must
   no longer be present, while remaining-folder tracks still appear. Files
   in the removed folder must remain untouched. Remove the last folder and
   verify the library is empty; Add folders remains available.
9. Re-add a removed folder and restart BB Music to confirm the remaining
   folder grants and Music Folders entries persist. If a saved folder loses its
   permission, Settings should still list it as Access unavailable. Re-add
   that folder to restore access or use Remove to discard the saved entry.
10. Confirm main-menu counters show the selected-folder track count, distinct
   artists/albums/genres and their singular/plural labels. Group rows show
   their direct number of child songs.
11. Confirm counts finish updating without resetting trackpad selection or
   changing touch scroll position.
12. Select a track: Now Playing opens and first-party audio starts.
13. Check Play/Pause, Previous/Next, duration, drag-to-seek and trackpad
    two-stage seek mode. Verify album art when embedded artwork exists,
    and the original BBOS6 `543.png` cover when it does not. Switch between
    tracks with and without embedded art and confirm the previous track's
    cover never remains visible or replaces the fallback late.
14. Check Shuffle, Repeat All, Repeat One and Off, using touch and trackpad.
15. Back should return to the same music list without pausing playback.
16. Background the app and use system notification/headset media controls.
17. Start Shuffle Songs from the home menu, and verify the shuffled queue.
18. Force-stop the app with `adb shell am force-stop dev.q25.bbmusic`,
    reopen it, and confirm that songs and counts load from Room rather than
    scanning all selected folders. Check the `BBMusic-Local` log tag for
    `Loaded persistent music index`.
19. Reboot the device and verify the library persists, including an empty
    indexed folder.
20. Add a track to an existing selected folder outside BB Music; verify
    **Settings > Refresh library** discovers it. Delete/retag a file and
    repeat to confirm the transaction replaces removed/changed entries.
21. Remove or revoke a selected tree grant and confirm its tracks do not
    reappear from the saved database. A failed refresh must not destroy the
    last successfully saved snapshot.
    After upgrading to the audio-validation build, verify one automatic
    reindex removes the bogus `PEI (Legacy)` folder entry while the songs
    inside valid selected subfolders remain available; then force-close and
    reopen to verify Room resumes fast cached startup.
18. Confirm tracks from unselected folders do not appear or enter a queue.
19. Verify song and album rows display their own album cover; artist and
    genre rows select the first album in alphabetical album-title order.
20. Scroll quickly with touch, then select rows using the Q25 trackpad:
    artwork must not be cross-assigned by recycled ListView rows.
    After the shared-list refactor, repeat this on the home menu, Settings,
    All Songs, Artists, Albums, Genres and their nested song pages; Back must
    still restore the previous selection and scroll position.
21. Confirm coverless albums show a placeholder and that folder selection,
    existing counts, previous navigation, status bar and Now Playing
    remain functional.
22. On the home menu type a song title, artist, album or genre directly
    using the Q25 keyboard: a search field must appear and show matching
    selected-folder songs with the correct artwork.
23. Type on the Artists, Albums, Genres and nested song lists. Verify
    only the current page is filtered; all search results retain the
    regular 68dp borderless blue selector and trackpad navigation.
24. Verify song rows in All Songs, album pages, artist/genre pages and
    search results show artist directly under the title and duration at
    the far right of the title line. Long titles must ellipsize without
    overlapping the duration. Confirm non-song rows still show counts.
25. Enter a query with no matches on the main and nested pages.
    Confirm the empty adapter leaves no selectable rows and shows
    horizontally centered `No search results found` below the search bar,
    without the blue selection gradient or a row divider. Backspace to
    recover matches or clear the query; the message must disappear.
    Press Back once to dismiss search and restore the original list
    selection/scroll; a second Back must navigate to the parent page.
25. Play a song from filtered results, press Back, and verify search
    results return intact. Ensure typing on Now Playing does not open
    the library search and the background player is unaffected.
    Compare the search field against the BBOS6 reference: full-width top
    `#5A5D63` rule, dark gray vertical panel gradient and rounded white
    inset. Verify the field begins empty (no placeholder) and typed
    characters remain readable on white.
26. Open Albums and select an album. Check the 88dp artwork touches the
    left screen edge and begins immediately beneath the status bar,
    without white space. Its title, artist, genre and song count should
    appear alongside the cover.
27. Enter the song list with trackpad Up/Down and Enter. Check selected
    tracks, playback queues, instant search and Back navigation behave
    unchanged. Artist, genre, home and Settings pages must not show an
    album header. Coverless albums must show the normal placeholder.
28. In an album song list, verify each row's left edge shows the tagged
    track number rather than album artwork. Missing/zero track tags show
    `--`. Verify all other song lists retain artwork, row selection
    gradients have no stroke, and rapid scrolling never mixes row types.

For relevant logs:

```sh
adb -s 202512003630 logcat -d -s 'BBMusic:I' 'BBMusic-Local:I' 'BBMusic-Local:W' '*:S'
```

### Incremental refresh regression checks

- From Settings > Music library, verify the header cannot be selected, and
  toggle **Auto refresh on launch** on/off with the trackpad; the displayed
  state must persist across application restarts.
- On a provider supplying size and modification timestamps, refresh twice:
  the second scan should log zero rechecked and reused unchanged tracks.
- Add a new track, retag one existing track and delete another outside the
  app; then **Refresh library**. The next catalog should add/recheck/remove
  only those items. Inspect `BBMusic-Local` logs for counts.
- Choose **Full rescan** to re-inspect every candidate regardless of cached
  signatures. Cancel its confirmation to verify no scan begins.
- Test providers with unavailable size/mtime, old v1 catalogs, revoked SAF
  grants, overlapping tree selections, and failed folder enumeration.
  Failures must retain the last good Room snapshot; no files are modified.

## Architecture

```text
BB Music APK (dev.q25.bbmusic)
  MusicActivity
    ListView + BaseAdapter, user-guided BBOS6 presentation
    Settings > Music Folders > Add folders -> Android ACTION_OPEN_DOCUMENT_TREE
        |
        v
  MusicFolders
    Explicit persisted folder-tree allowlist
        |
        v
  LocalMusicSource + MusicFileChangeDetector
    Cached startup + incremental SAF document fingerprint comparison
    Optional auto refresh + explicit full rescan
        |
        v
  MusicLibraryDatabase (Room v2 / private SQLite)
    Atomic changed-row updates + selected-tree scan marker
        |
        v
  Native Media3 playback -> MediaLibraryService + ExoPlayer (implemented)
    MusicPlaybackRetention -> private selected-tree queue / position checkpoints
    MediaLibrarySession -> Android media keys + System UI resumption

  Now Playing view — ported BB UI visuals and trackpad seek model
  SymfoniumSource — optional/unused until explicitly selected

BB UI APK (dev.q25.trackpad) — separate, no runtime/build dependency
```

## Playback retention and Android native resumption

`MusicPlaybackRetention.java` is the shared persistence authority for the
Media3 playback queue, selected track, seek position in milliseconds, shuffle
and repeat state. The existing ExoPlayer remains the sole audio engine; no
page-specific playback storage, second service, MediaStore scan, or external
player integration is introduced. State is saved in app-private
`bbmusic_playback` preferences. Queue/metadata is serialized on playlist
changes; position is periodically checkpointed (5 seconds while playing)
and committed on pause/service teardown. Process death can lose at most
the last unsaved interval, not an entire playback session.

When Music connects to an **empty** playback session, the existing
`MusicPlaybackClient` validates the saved SAF documents against the current
selected folder allowlist/read grants, sets the Media3 queue and seek
position, then prepares it with `playWhenReady=false`. The home
**Now Playing...** entry uses the existing `hasCurrentTrack()` / event flow;
opening the entry never starts playback. A fresh play request from the user
overrides the saved queue. Reconnecting to an active session (including one
started by Bluetooth/system controls) never pauses or rewinds it.

`MusicPlaybackService` is now a `MediaLibraryService` with a
`MediaLibrarySession`, retaining native Android notification/transport
controls. The standard Media3 `MediaButtonReceiver` and
`MediaSession.Callback.onPlaybackResumption(..., isForPlayback)` are used
for Bluetooth and system initiated playback after termination/reboot:
`isForPlayback=false` returns one metadata-rich item for a resumption
card; `true` returns the saved playlist, index and position for actual
playback. The library browser exposes only the recent selected-folder
queue, not other storage. System UI resumption visibility depends on the
Android build and user media settings.

**Closing versus backgrounding:** Home backgrounds Music and preserves
normal Media3 background playback. Exiting the root screen via Back pauses
before disconnecting the controller. Dismissing the task from Recents
pauses, checkpoints and shuts down the service. An unexpected system kill
relies on the most recent checkpoint. Stopping from the BBOS Now Playing
controls retains the existing player-level rewind-to-zero / IDLE semantics
but clears persistent resumption so Stop cannot generate a stale Android
playback-resumption card.

**Regression checklist (test the locally built release APK on Q25):**

1. Play track A, seek to a nonzero position, exit with Back and reopen:
   Now Playing is present, paused at the saved position; Play resumes.
2. Pause track A, remove Music from Recents and reopen: position persists,
   no automatic playback.
3. Play track B, press Home, return to Music: background audio continues;
   no seek/queue override occurs.
4. While playing, swipe Music away from Recents: playback pauses, the
   service stops and a new launch restores the paused track.
5. Use Android notification/lockscreen/Bluetooth Play after a closed
   session: Media3 resumes the saved track and position. Verify system
   resumption after a device reboot where the ROM supports it.
6. Press BBOS Stop, leave and reopen: it must not resurrect the prior
   position or cause a stale system resumption card.
7. Remove/revoke the saved track's SAF folder, reopen and test the media
   button: it must never resume a now-disallowed document.
8. Change shuffle/repeat and switch tracks: verify retained context.
   Verify remaining-time seekbar and all existing Now Playing controls.
9. Force-stop the package to verify no crash. Note: Android's force-stop
   state intentionally blocks manifest receivers until an explicit app
   launch, so do not treat force-stop as a Bluetooth resumption test.

Build without uninstalling or clearing the existing app data:

```sh
cd ~/Projects/bb-music
git fetch origin
git switch master
git pull --ff-only
git submodule update --init --recursive
gradle :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

Do not publish an APK until that exact build has passed on-device validation and explicit approval. Retain the existing signing identity for updates.


## Empty-state presentation

The reusable `Bbos6EmptyStateView` is owned by `bbos6-shared` and consumed
through `assets/bbos6`. It is a non-focusable, non-clickable sibling of the
existing `MusicListView` inside the library viewport, **not an adapter row**.
Pages opt in through `Page.setEmptyState(title, description, actionRowCount,
artworkRes)`. `Page.showsEmptyState()` displays content only when the
underlying list has no content beyond any reserved action rows. The caller
reserves space above the centred content for existing actions without
modifying DPAD/trackpad navigation or the ListView row layout. No
unverified or newly invented artwork is introduced.

When Playlists contains zero saved playlists, its single **Add Playlist**
row launches the canonical shared `Bbos6PopupButtonList`. The popup says
**Please select how you would like to add a playlist:** and offers
**New Playlist** and **Import Playlist**. The first opens the existing
playlist-name entry flow; the second opens the existing Android SAF M3U/M3U8
import picker. Both choices use the existing playlist business logic.
The previous standalone **Create playlist** and **Import M3U8** rows remain
absent from the Playlists menu. Below the row, the independent empty-state area displays the
original, unmodified `354.png` artwork, the heading **No Playlists.**, and
**You can import or create playlists for music on your device by selecting Add Playlist**.
The artwork and explanatory text cannot be selected or scrolled to as an item. Empty playlist detail pages use
the same helper and **354.png** illustration under their two Play all / Shuffle
actions, instead of an **Empty playlist** menu item. Search hides the
empty-state panel so search results remain independent. Populated playlists
do not display the panel.

The same empty-state helper now covers **All Songs**, **Artists**,
**Albums**, **Genres**, the individual artist/album/genre group pages,
**Folders**, nested folder browsing, **Settings > Music Folders**, and
the **Add Songs** picker. Library and group pages use their respective
already-verified home/category icons (350/345/351/348); folder pages use
the verified 112.png folder icon. All artwork goes through the existing
`Bbos6EmptyStateComposeView` and is centred within the otherwise empty
list viewport, below the **Add folders** action where applicable.

Truly empty page adapters contain no fake placeholder rows; the shared
empty-state view owns heading, illustration and description. Loading,
permission failures and other error statuses retain their existing
non-selectable message rows, rather than displaying misleading empty states.
The separate `Bbos6SearchEmptyFeedbackView` still handles zero search
results. Home and Settings remain ordinary nonempty menus.

## Managed BBOS6 focus without Android overlay

The canonical `Bbos6FocusPolicy.forManagedContainers(...)` from
`bbos6-shared` prevents Android's built-in focus highlighter from painting
a pale overlay over whole composite BBOS6 panels that already draw their own
selection. It changes the framework's focus drawing behavior only for
container views, not text fields or interactive leaf controls, and does not
alter focusability, key events, trackpad/DPAD navigation, selection palettes
or opacity.

`MusicNowPlayingView` applies it once after building its view hierarchy;
`Bbos6ContextMenu`, `Bbos6PopupButtonList` (including text-to-buttons
navigation), and `Bbos6PropertiesPopup` apply it in the shared library.
The shared button popup also displays a blue button **only while its
button-focus container is focused**. Moving into the playlist name's native
text field turns all buttons silver without losing the remembered selection;
moving back to the horizontal buttons restores the selected face.
Test touch and trackpad for each affected control on the Q25 before merging.

## Playlists (Room + Media3 + M3U8)

Playlists use the existing app-private Room database, migrated **non-destructively
from v2 to v3**. `music_playlists` contains playlist names and IDs;
`music_playlist_entries` stores independently ordered entries with exact
`content://` SAF references and a fallback song title. Repeated songs are
allowed and retain distinct entry IDs. The scanned music catalog remains
the only source of playable tracks. No audio files are moved or copied and
no new broad storage permission is required. Catalog and playlist tables are accessed on
background workers; the native ListView and Media3 controller are reused.

### Interaction

- **Playlists** at Home: **Add Playlist** opens a compact, two-button
  BBOS6 popup with **New Playlist** (name-entry flow) and
  **Import Playlist** (existing native SAF document picker). It uses the
  shared `Bbos6PopupButtonList` helper without screen-specific button
  styling. The New Playlist and Rename playlist name fields now use the
  same shared popup with the native-backed `Bbos6TextField` and horizontal
  **Cancel** / **Save** buttons. Both buttons are now sized by their own
  label length, and the complete button group is horizontally centered
  by the shared popup. The popup field is **30dp high**, exactly
  matching its buttons; the search field retains its original dimensions.
  Save reuses the existing Room playlist
  validation and create/rename logic; Cancel makes no changes. Saved
  playlists follow with entry counts. Tap a playlist to open it;
  long-press its name to access **Add songs**, **Rename playlist**,
  **Export M3U8**, and **Delete playlist** through the existing shared
  `Bbos6ContextMenu`. Delete still opens its existing confirmation and never
  deletes audio files.
- Open playlist: **Play all** (original `334.png`) and **Shuffle**
  (reusing Home's original `339.png`) appear above the ordered track
  list; the four maintenance actions are no longer listed here.
  Playing one track uses the available songs of that playlist as its Media3
  queue. Shuffle uses the existing Media3 queue shuffle mode.
- **Add songs** opens the existing All Songs list as a picker. Enter/tap
  inserts the selected song; Back returns to Playlists when the picker was
  opened from the playlist-name long-press menu. An empty playlist directs
  users back to that long-press menu to add songs. To add a whole
  album, artist, or genre at once, long-press its group row anywhere in the
  music library and choose **Add to playlist**. The same action is available
  for individual track rows. The chooser now uses the shared BBOS6 compact
  list-selection popup: one-line 40dp `Bbos6MediaListRow` entries without
  artwork, plus a final **New playlist...** choice. Playlist IDs, existing
  creation flow, persisted data and SAF scope are unchanged. Use trackpad
  Up/Down and center/Enter, touch or Back; large lists scroll within the
  popup rather than opening a native Android choice box.
- In a playlist, long-press an available track to **Move up**, **Move down**,
  or **Remove from playlist**. The context menu also offers Properties,
  Add to playlist and the separate destructive **Delete audio file** action
  with its existing SAF confirmation. Removing an entry or deleting a
  playlist **never** deletes the audio file.
- If an entry is absent from the currently indexed/authorized music folders,
  it is displayed as unavailable; it cannot be played or replaced by a
  filename-only guess. It can still be moved or removed. Reauthorizing the
  exact song URI makes it playable again on library reload.
- Playlist edits and order survive app restarts. Active queue retention
  continues to use the existing Media3 and playback retention mechanisms.

### M3U8 interoperability and limitations

- **Import Playlist**, reached through **Playlists → Add Playlist**, uses
  the existing `ACTION_OPEN_DOCUMENT` picker and reads
  UTF-8 M3U/M3U8 text (including `#EXTM3U`, `#EXTINF` and CRLF).
  The imported playlist is saved in Room, with a unique name if necessary.
  Absolute paths and `file://` references are resolved against the
  **already-indexed** selected-folder catalog. Relative paths are supported
  only when the picked playlist document has a resolvable primary-storage
  location. Unresolved references are counted and skipped. No arbitrary
  filesystem scan, network playback, filename-only matching, or provider
  permission escalation occurs.
- **Export M3U8** uses Android's `ACTION_CREATE_DOCUMENT` picker. External
  entries use absolute filesystem paths derived from Android's primary
  external-storage location and standard `primary:` SAF document IDs.
  Unsupported opaque/custom-provider IDs and unavailable tracks cannot be
  represented in a portable path-based M3U8; export reports failure rather
  than silently omitting them. The exported file is an interoperability
  artifact, not the app's source of truth. No storage folder is opened
  outside the user's chosen document-picker destination.
- Import is bounded to 2 MiB and 20,000 entries. Only the `content://` song
  URIs from the current authorized catalog are accepted into playlists.
  M3U8 `http://`/`https://` streams are not supported by this offline player.

### Q25 verification

Run the existing release workflow below without uninstalling or clearing data:

```bash
cd ~/Projects/bb-music
git fetch origin
git switch feature/native-playlists-m3u8
git pull --ff-only
git submodule update --init --recursive
gradle :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

Check playlist create/rename/delete, duplicate tracks, group inserts, reordering
via long-press on the trackpad, removing without touching the audio file,
sequential/shuffle playback, process restart persistence, unavailable files
after removing a folder and reauthorizing it, plus M3U8 roundtrip using
primary-storage audio. Test cancellation and unsupported SAF document providers.
Only merge/publish after actual Q25 results and explicit approval.

## Kotlin + Jetpack Compose migration (feature branch, not merged)

The UI migration retains `dev.q25.bbmusic`, the app's existing upgrade
signing identity, Room database, SAF folder grants, playlists, Media3 service,
playback rules, adapter navigation and Q25 keyboard/trackpad interactions.

The first active integration replaces the *presentation only*
`MusicSettingsHeaderView` implementation with a Kotlin `ComposeView`.
It renders the canonical `Bbos6SectionHeader` from the shared submodule.
The old `MusicListView` adapter continues to create the same class and call
`bind(title)`; its non-selectable group-row type and focus rules are intact.

Temporary mixing of Kotlin Compose presentation with the unchanged legacy
Java application runtime is confined to this unmerged migration branch.
It is **not** the final all-Compose Music architecture. Do not remove the
existing application/renderer until each replacement is verified on Q25.
Build and test the release APK in place without uninstalling or clearing data,
and never merge until explicitly approved.

The existing `MusicActivity` now inherits AndroidX `ComponentActivity` on
this feature branch, solely to provide Compose-required lifecycle and saved
state owners to the existing View tree. Its playback, storage, navigation,
keyboard handling, and lifecycle method bodies are otherwise unchanged.

### Album header Compose presentation

`MusicAlbumHeaderView` now renders through the canonical shared
`Bbos6MediaDetailHeader` Composable. It preserves the same 89dp height,
88dp cover, four metadata lines, placeholder artwork and 1dp separator.
`MusicActivity.bindAlbumHeader()` and the `MusicListView` artwork loader
are not modified: the existing cache, SAF-selected sources, stale callback
generation checks, album title/artist/genre formatting, and navigation remain
unchanged. The row is still outside the selectable list adapter.

The Music feature branch pins shared commit
`b7310c4f8682f2394e33c9b23fa65d9373815730`. Verify album detail
artwork (present/missing), Back, scrolling/trackpad selection and rotation
before approving or merging.

### Compose-backed Music empty state

The list viewport's `emptyState` is now a
`Bbos6EmptyStateComposeView` from `bbos6-shared`, implemented entirely
through the shared Compose `Bbos6EmptyState`. It exposes the existing
`setContent(...)` and `setArtworkResource(...)` methods to the unchanged
`updateEmptyState()` logic. The empty-state component remains outside the
selectable `MusicListView` adapter, non-focusable, non-clickable and
transparent behind its content. List selection, keyboard/trackpad navigation,
reserved action-row spacing, Playlists' original 354.png artwork, search,
playback and data are unchanged.

The integration remains unmerged until verified on the Q25. Check the empty
Playlists page and an empty playlist detail, including focus staying on the
action rows, Back, and both touch and physical trackpad behavior.


## Large Compose presentation batch: list rows, search and Now Playing metadata

This batch migrates three additional, distinct Music presentation layers
together, after making their reusable Composables in `bbos6-shared`:

1. **Every non-section Music library/menu/folder/song/playlist row** now
   uses `MusicListRowComposeView` backed by `Bbos6MediaListRow`. The native
   `MusicListView` retains the same adapter, scroll/selection/DPAD rules,
   click and long-click routing, original menu-icon pixel trimming, and
   artwork loader/cache. The artwork binding key still prevents stale
   asynchronous callbacks from leaking into recycled rows. The optional
   Remove control invokes the exact existing `DataSource.activate(position)`.
2. **Search header chrome** uses shared `Bbos6SearchChrome`. The same
   native `Bbos6TextField` remains attached, with the existing text watcher,
   IME and hardware type-to-search logic unchanged.
3. **Now Playing artist/album metadata plates** use `Bbos6MetadataPlate`
   via `MusicBeveledTextLabel`, preserving `setText`,
   `setMetadataFocused`, the native zone/focus controller, caller touch
   listeners and metadata navigation callbacks. Transport/seek controls and
   Media3 playback are not modified.

Shared submodule is pinned to `9656f1d56ccbc224dd5da4bf6b156a75418a0648`.
No application IDs, signing identities, persistent data, SAF grants or
playback methods were changed.

This batch requires Q25 regression testing of all Music library pages,
row icon/cover sizing, fast scrolling/recycling, keyboard/trackpad selected
gradient, long-press context menu, folder Remove touch, type-to-search,
and Now Playing artist/album tap/DPAD and focus.


## Now Playing four-panel and dynamic text Compose batch

The mode header, half-radial artwork backdrop, seek strip and transport
background are now drawn by the shared `Bbos6NowPlayingSurface` using a
non-focusable, non-clickable shared Compose host placed **behind** the
existing native interactive controls. All four panels retain their former
heights, layout containers and children. Native controls, SeekBar rail,
thumb, time calculations, transportation icons, selections and separators
remain unchanged.

Four independent Now Playing text fields (queue count, song title, elapsed
and remaining times) now use Kotlin `MusicPlayerTextView`, which delegates
to the shared `Bbos6NowPlayingText`. The previous Java-facing `setText`
update contract and all formatting computations remain unchanged.

No Java player `render`, `renderFocus`, `renderProgress`, artwork-loader,
trackpad/DPAD, ticker, or action methods were changed. The old drawable
helpers are retained for later parity comparison, not deleted.

Test the Now Playing panel gradients, text alignment, song-title wrapping,
long times, seek-thumb travel and time accuracy, transport button touch and
DPAD, Repeat/Shuffle icons, media-action behavior and focus colors on Q25.

### Mode/seek chrome bevel regression fix

Q25 feedback identified two too-thick glows in the first Compose Now
Playing panel port: beneath the mode header's top line and above the seek
panel's bottom line. Their source is the shared `Bbos6NowPlayingSurface`,
**not** Music seekbar geometry or touch/focus logic. The initial port
replaced the mode reflection with a full-width linear 7dp fade and
flattened the seek reflection into a constant-intensity 5dp strip. The
shared follow-up change restores the original Java `MusicPlayerChrome`
2D fades: row-wise `(1-progress)^1.55` (mode, maximum 42 alpha) and
`rise^3.2` (seek, maximum 52 alpha), both tapering off toward the
horizontal edges. Keep the intentional 1dp separators and all native
SeekBar, transport, DPAD, controller and media methods unmodified.

The application submodule is pinned to the shared fix
`4d5878065438d034dbfb85b495e041e11ed08ab4`.
Review both panels side-by-side with the previously accepted native
release, then verify playback and selection still work. Builds and Q25
visual parity of this revised SHA remain unverified.

## Now Playing: Compose controls, progress rail and album art

After the Q25 acceptance of the corrected mode/seek panel bevels, the
remaining Now Playing visual building blocks now use the shared Compose
library:

- The original Repeat and Shuffle PNG ImageViews still own **all**
  touch and accessibility handling. A passive shared Compose mode
  highlight lives behind each button, inset 1dp to leave the already
  verified header rules visible. The existing `renderFocus()` computes
  precisely the same enabled, selected and current mode states.
- Four original native transport icons, enabled alpha, touch callbacks
  and transport/DPAD zone logic are unchanged. Each sits above the
  shared Compose transport selection gradient. The native foreground
  separator drawable now follows the same-size parent button slots;
  only its parameter type changes from `ImageView[]` to `View[]`.
- The 3dp seek **progress rail** now draws in shared Compose, using the
  original 2dp end guard and entire SeekBar width to cover start/end
  positions precisely. The original Android SeekBar still handles the
  pill, seek gesture, touch-time preview, focus, editing and time math.
  On every SeekBar progress change, the passive Compose rail follows
  the same value. Disabled alpha follows native SeekBar alpha.
- `MusicNowPlayingArtworkView` retains Music's existing resource/
  bitmap setters and async loader callback, but delegates FIT_CENTER
  rendering with 6dp inset to shared `Bbos6NowPlayingArtwork`.
  Original 543.png no-art bitmap resource is untouched.

All application-level Media3 callbacks, library/metadata decisions, seek
policy, native input dispatch, playback session and app data are unchanged.
Shared submodule pins `26d0b6f03bb2767d8ca614cf699f8e98f5ee83fb`.

Test on Q25: Repeat/Shuffle selected/unselected/unavailable, transport
selected/disabled click and DPAD, separator alignment, seek first/last
positions and dragged progress (including disabled track), album covers
present/absent, stop, Back and screen re-entry. Build/device verification
is mandatory before merging.

## Shared Compose modal integration (Properties and context actions)

The previously native `Bbos6ContextMenu.Builder.show(Activity)` and
`Bbos6PropertiesPopup.show(Activity, List)` are now Compose-backed
automatically in Music because `MusicActivity` extends AndroidX
`ComponentActivity`. Both changes live exclusively in
`bbos6-shared`, pinned as submodule
`7c4f4ed91d6905f74e40022f337f83039d74a786`.

The shared bridges retain Java action/row models, all IDs, resource icon
mappings, enabled flags, callbacks, selected IDs, window `Dialog` return
values, Back/outside-touch policy and underlying list/scroll selection.
The new UI is rendered by existing canonical Compose helpers
`Bbos6ContextMenuGrid` and `Bbos6PropertiesPanel`, not Music-only
copies. On non-AndroidX Activities, the original native dialogs still
render without a behavior change.

No Java Music business logic changed. The user's preceding Now Playing
batch passed Q25 inspection, but the new modal integration is untested.
Device checks: touch long-press and trackpad menus on Songs/Albums and
Playlists; icon proportions, disabled item skipping, menu/group scrolling,
Back/Cancel, action activation once on key-up, and Properties display,
scroll, outside touches and Back.

### Compose Dialog bridge Kotlin interop correction

The first shared dialog bridge failed Kotlin compilation because the
AndroidX Java-style `ViewTree*Owner.set(...)` types are not visible as
Kotlin symbols. Shared commit `7ddf64801a9de9466e2c62ec4f872a1e120c672e`
uses the supported `View.setViewTreeLifecycleOwner`,
`View.setViewTreeSavedStateRegistryOwner` and
`View.setViewTreeViewModelStoreOwner` Kotlin extensions instead.
Only shared bridge imports/owner setup changed; Music's modal callers and
business logic are untouched. Build and Q25 behavior still require a retest.

## Compose popup-button and empty-search presentation batch

For Music's `ComponentActivity`, the Java
`Bbos6PopupButtonList.Builder.show(Activity)` uses shared Compose
popup presentation for **button-only** playlist choices and
confirmations, retaining action IDs, selected state, icons, text,
button widths, dimming, Back and callback behavior.

**Create/Rename Playlist text-input popups keep the native shared
`Bbos6PopupButtonList.PopupDialog` with `Bbos6TextField`**.
Native DPAD/trackpad focus crossing, editing, IME and button highlights
remain under the already-tested native implementation. Neither the
`MusicActivity.askPlaylistName()` callbacks nor persisted data changed.

The standalone “No search results found” TextView has been replaced
with the shared passive `Bbos6SearchEmptyFeedbackView`. Search
filtering, ListView navigation, focus and the exact empty-result
visibility conditions are unchanged.

Earlier shared popup batch: `0d3eeb7097d50c8cedc6ef6b57567a8a17dc9645`.
Unmerged until Gradle build and Q25 checks: button-only modal icon/title,
action selection via keyboard and touch, disabled actions, Back,
New/Rename Playlist native input, and empty searches on every library page.

## Q25 text-entry popup focus regression — native fallback restored

Q25 verification of the experimental `AndroidView(Bbos6TextField)`
inside the Compose popup showed two defects: Android's lighter default
focus overlay appeared over the Cancel/Save area, and physical
trackpad left/right navigation stopped after leaving the text field.
That hybrid has been removed. `Bbos6PopupButtonList.Builder.show`
now chooses the native popup whenever `.textInput(...)` is present,
even for Music's `ComponentActivity`; button-only popups remain Compose.
This restores the previous native popup's shared
`Bbos6FocusPolicy.forManagedContainers(panel)` and its original
DPAD/trackpad `dispatchKeyEvent` routing. Source behavior only; new
APK and Q25 device tests still required.

**Mandatory policy:** Android's automatic light/default
full-container focus overlay is strictly forbidden on all BBOS6
custom-managed focus owners. Disable it at its source using the shared
`Bbos6FocusPolicy` on native/Compose ViewGroup owners. Never
compensate with transparent covers or extra colors; preserve native
EditText leaf focus. Update the shared helper first for future changes.

Current shared submodule: `d08c1ac95432fe116ba7c5a0bf5e560f9b5a3e29`.
Retest Create and Rename Playlist: move down from native EditText to
buttons; trackpad left/right moves selected Cancel/Save; DPAD_UP returns
to input; no light overlay; touch, keyboard, Cancel, Save and Back.

## Kotlin Compose application shell and app policy migration

Music's top-level screen container is now `MusicAppShellView.kt`
(`AbstractComposeView` + `AndroidView`). Compose owns the active
Library-versus-Now-Playing surface, black canvas, system-window inset
root and Android-view attachment sequencing. The same native
`libraryShell` and `MusicNowPlayingView` instances are retained,
so ListView scrolling, adapter selection, search input, artwork
caches and Media3 focus zones are not rebuilt. The native ListView
and input components remain transitional; this is **not** a
claim that MusicActivity/navigation and all native subviews are
fully converted.

`MusicActivity` now asks the Kotlin host to show a screen rather
than reparenting the Java FrameLayout directly. The Compose host
runs the caller's focus restoration only *after* the corresponding
native screen is actually attached. The canonical shared
`Bbos6FocusPolicy` disables Android's automatic default
focus decoration on managed container nodes; native EditText
leaf focus remains untouched.

Pure independent policy classes moved from Java to Kotlin without
changing Java callers or persisted identifiers:
`MusicAlbumTag`, `MusicDeletedQueueIndex`,
`TrackpadPressState`, `MusicDocumentPath`,
`MusicFilePolicy`, `MediaSeekPolicy`,
`MusicFileChangeDetector`, `MusicPlaylistPaths`. JVM static
entry points are retained with `@JvmStatic`, and constant
fields with `const val`. No changes to actual player session,
Room database schema, SAF grants, application ID or signing identity.

**Still required for full pure Kotlin+Compose application:** replace
`MusicActivity.java` navigation/data orchestration, the native
`MusicListView.java` row/recycling/DPAD owner, the
`MusicNowPlayingView.java` layout/input owner, and the remaining
Java music domain/services. Convert in isolated testable batches;
never delete proven navigation or media behavior without Q25 parity.

This batch is source-committed only. Compile and device validation
must run before merge; verify Library↔Now Playing focus, list scroll
position restore, search retention, Media3 controls, popup focus,
cover art, trackpad and touch.

## Kotlin library navigation controller with existing shared Compose rows

`MusicListView.java` is converted to `MusicListView.kt`. It still
extends Android `ListView` to retain the already device-tested
physical trackpad navigation, focus, touch scroll/edge alignment and
long-press detection, while the *actual row visuals and settings header*
are rendered through shared Kotlin Compose components. Java
`MusicActivity` retains the same `DataSource` and `RowData` model
and all application navigation and click handlers. Native scrolling
does not imply Java source; this owner is now Kotlin code.

Original functions preserved: `notifyRowsChanged`,
`refreshVisibleRows`, `refreshSelection`,
`loadAlbumArtwork`, `clearArtworkCache`, `dispose`,
`handleVerticalKey` and `canvasColor`. Original icon alpha
bounds, artwork request-key guards, Q25 focus/selection, 68dp rows,
1dp dividers, empty/section header behavior and accessibility text
remain. The shared `Bbos6FocusPolicy` is applied to managed list
containers to prohibit Android's light default focus overlay.

Still pending complete app conversion: Java `MusicActivity` page
controller, `MusicNowPlayingView` platform interaction owner, and
the remaining Java playback/service/SAF/Room classes. This is a
source-only unverified commit: test build, Q25 item selection,
touch long press, artwork and library scroll position before merge.

### Kotlin library artwork loader

`MusicListArtworkLoader.java` is now `MusicListArtworkLoader.kt`,
retaining the same Java-callable `Listener.onLoaded(Bitmap?)` API,
album-keyed 12 MiB LRU cache, two-thread worker, 160px sampled cover
bound, SAF `content:` restriction, main-thread callback dispatch and
generation guard. Duplicate concurrent requests still share a callback
list; invalidation clears only in-memory covers, and close cancels worker
tasks. `MusicListView.kt` still protects recycled Compose rows using
their original binding key. No metadata source, icon mapping, or SAF
permissions changed. Build and Q25 artwork tests still required.

### Kotlin SAF Music folder browser

`MusicFolderBrowser.java` is now `MusicFolderBrowser.kt`. The native
DocumentsProvider APIs, read-only single-thread querying, exact
`MusicFolders.selectedTrees(context)` authorization checks before
the query and before delivering results, hidden-directory/file-type
filtering, folder-first case-insensitive sort, main-thread callbacks,
error messages, and worker cleanup are preserved. `Listener` and
`Entry` keep the existing Java caller ABI so the native MusicActivity
can continue while its navigation controller is migrated. No SAF
permissions or stored folders changed. Q25 Folders browsing, removal
and grant revocation remain mandatory test cases.

### Kotlin Now Playing artwork and generic fallback

`MusicArtworkLoader.java` and
`MusicCoverPlaceholderDrawable.java` now have Kotlin implementations
with unchanged Java class names/constructor APIs. The artwork loader
retains single-thread embedded-picture extraction, existing 600px
downsample limit, release/error logging, main-thread callbacks and
generation-based stale-track filtering; it does not alter source
selection or playback. The generic placeholder keeps its exact
previous paint colors and note geometry. Original numbered BBOS6
assets remain untouched. On-device cover switching and fallback
render tests still required.

## Kotlin Now Playing presentation and input controller

The entire \`MusicNowPlayingView.java\` controller is replaced by
\`MusicNowPlayingView.kt\` without changing the app-facing constructor,
\`MetadataNavigation\` callback interface, \`render()\`, \`directional()\`,
\`activate()\` or \`dispose()\`. All four BBOS6 Now Playing panels use
the existing canonical shared Kotlin Compose surfaces. Other Compose
widgets continue rendering the title/queue/time labels, original cover,
selection faces, Seek rail and artist/album metadata plates.

The existing native \`SeekBar\` and transport/mode \`ImageView\` inputs
remain under Kotlin control for Q25 focus, touch, accurate thumb travel
and DPAD. Media3 remains exclusively in \`MusicPlaybackClient\`.
Vertical zone traversal, absent-metadata skipping, mode and transport
left/right, trackpad seek editing, time countdown, 1s ticker and
stale-track artwork callback guard retain the Java behavior. The
shared \`Bbos6FocusPolicy\` is applied to the Now Playing root to
exclude Android's prohibited default light focus decoration.

**Build and Q25 verification required.** Check all controls, Stop
reset/resumption, shuffle/repeat, metadata jump/Back, seek pill,
trackpad zone traversal, paused artwork, and library return. Pure
Kotlin+Compose completion still requires MusicActivity and remaining
Music domain/service Java conversions; native Android platform
controls can remain as APIs, not separate Java source screens.

### Now Playing Kotlin compiler interoperability fix

Owner's first Gradle build of the Kotlin `MusicNowPlayingView` failed in
both variants because a public Kotlin constructor exposed the
package-private Java `MusicPlaybackClient` type, and the Kotlin
`dp(Float)` helper was called with Java-style `Int` arguments. The
controller now uses an `internal constructor` (still callable from
the existing Java Activity at JVM level) and a `dp(Int)` helper
that converts to `Float` when calling `MusicPlayerChrome.dp`.
No layout dimensions, focus ownership, media operations, or
resource mappings changed. The Gradle 8.11.1 deprecation warning is
unrelated to this compilation failure. A fresh Gradle/Q25 verification
is required; no claims of test success.

### Kotlin persisted selected-folder SAF policy

MusicFolders.java is now MusicFolders.kt, with @JvmStatic preserving the Java caller API. This preserves SharedPreferences keys bbmusic_selected_folders and tree_uris, sorted saved URIs, intersected live read grants for scanning, live write permissions, and display-only primary: stripping. Deleting a selection commits allowlist removal first and then releases OS URI grants, without deleting files. Existing Java unit tests for displayLocation remain in place. Source-only: build and Q25 SAF testing required.

### Kotlin Media3 playback client

MusicPlaybackClient.java is now MusicPlaybackClient.kt. It preserves Java calls, Media3 session connection, a bounded single retry, pending queue precedence, content URI validation, paused queue restoration, native Play/Pause/Stop, shuffle/repeat, backwards deleted-track removal, retained queue filtering, and disconnect ownership. MusicPlaybackRetention persistence and the Media3 service are unchanged. Build and Q25 test before merge.

### Kotlin retained playback state (unchanged queue_v1)

MusicPlaybackRetention.java is now MusicPlaybackRetention.kt. Preference file `bbmusic_playback` and all existing `queue_v1`, index, position_ms, shuffle, repeat, URI/title/artist/album JSON fields retain exactly the same names. Java static APIs remain via `@JvmStatic`; `Saved` retains its Java-readable fields using `@JvmField`. Only exact live authorized SAF tree documents restore, and if the saved current track no longer qualifies, restore clears the queue rather than switching songs. Durable stop/clear, position checkpoint, and deleted-queue index recovery are retained. No Room, signing, or saved-data migration. Build plus Q25 pause/relaunch/Stop/delete test required.

### Kotlin trackpad confirm and Properties adapters

TrackpadPressHandler.java and MusicTrackPropertiesReader.java are now Kotlin. The confirm-key handler preserves the native KeyEvent DPAD_CENTER/Enter/Numpad Enter codes, press-once/hold-once/release contract, Android long-press timeout, and cancellation. The read-only SAF Properties adapter still queries only authorized document URIs off the UI thread, revalidates tree grants before delivery, suppresses synthetic Unknown Artist, and never invents a creation date or filesystem path. Both retain the same Java caller interfaces. This batch is source-only until the next Q25 build; no shared UI helper changed.

### Kotlin playlist repository (same Room schema)

MusicPlaylists.java is now MusicPlaylists.kt. Retains original Java callback methods, single-thread Room executor, main-thread callbacks suppressed after close, exact authorized selected-tree SAF URI filtering, case-insensitive duplicate-name checks, 128-character name bound, stable ordered-entry IDs, transactional add/import/reorder operations and existing Room database schema/version. Does not change any playlist data or rename/duplicate behavior. Requires Gradle build and Q25 playlist creation/import/delete/reorder testing before merge.

## Q25 native library taps and Properties overlay regression fix

Pinned `bbos6-shared` to `fbbf4ca92c88f1a34e649f2d94b6588aa5c7aa7d`. Music's
`MusicListRowComposeView` now inherits the reusable
`Bbos6MediaListRowTouchHost`. Native ListView gets ordinary touch
down/up to restore row activation, long press and scroll; Compose receives
only a gesture starting in the genuine trailing Remove action.
The shared component owns the hitbox and row visual dimensions, not Music.
Existing adapter `onItemClick`, DataSource actions, artwork binding, and
DPAD/trackpad selection are unchanged.

Properties no longer relies on its Compose window accidentally suppressing
Android's default light focus highlight: the shared Java-to-Compose dialog
bridge applies `Bbos6FocusPolicy` at the ComposeView and actual
AndroidComposeView, including read-only Properties panel. No extra overlay
or color workaround introduced. The Q25 regression and strict prohibition
are documented in both repositories. Gradle build and device testing
required before merge; protect playlist editing and native text input.

## Q25 list tap regression, second fix

The first shared touch-pass-through host restored scrolling but not
tap activation. Native `ListView` needs a real native clickable row
target. `Bbos6MediaListRowTouchHost` now routes ordinary row gestures
to Android's standard View click/long-click handling rather than
returning false on ACTION_DOWN. Music's row wrapper rebinds callbacks
on every recycle; `MusicListView` resolves a current row using
`getPositionForView` at activation and calls the same native
`performItemClick` and `OnItemLongClickListener` used previously.
ListView can intercept a drag and cancel the in-progress row press,
preserving native scrolling and preventing activation on fling.
Only gestures beginning inside the visible Remove button remain
Compose-owned. Disabled rows have no click listener. Current shared
submodule pin: `31d8d025d7218f098d2928b44e7655fcc144542c`. No focus/color/playback changes.
Build and Q25 test required.

## Kotlin-native player chrome compatibility cleanup

Q25 owner verified native list touch activation and Properties focus
corrections at Music `0dbbabf` / shared `31d8d02`.
Reference audit across all Music main-source Java/Kotlin files and
existing app tests found no uses of
`HalfRadialGradientDrawable.java` or `MediaChromeDivider.java`;
both obsolete Java-only paint helpers were deleted, rather than
maintaining unnecessary Kotlin replacements.

`MusicSelectionGradient.java` was still used by
`MusicPlayerChrome.java` to draw the old native seek thumb. It is
now `MusicSelectionGradient.kt`, retaining its exact 11-stop light
and 3-stop dark colors, native TOP_BOTTOM orientation, square corners
and Java-callable static factory methods. Compose selection faces
continue to use their canonical `bbos6-shared` components.
No asset index, screen, playback, focus or storage behavior changed.
The next Gradle build and Q25 Now Playing test must confirm parity.

## Kotlin M3U8 playlist transfer controller

`MusicPlaylistTransfer.java` is now `MusicPlaylistTransfer.kt`.
Android's document picker and `ContentResolver` remain the only I/O
interfaces. Same 2 MiB UTF-8/BOM import limit, 20,000 entry cap,
comment/empty line handling, exact selected-file catalog lookup through
`MusicPlaylistPaths`, primary-storage-only verified SAF document IDs,
`#EXTM3U`/`#EXTINF` export format, duration seconds, and newline
sanitization of artist/title. Missing or non-primary-storage tracks
still prevent export; no partial playlist or network/raw file reads
are introduced.

The original Java caller methods `read`, `checkExport`, `write`
and `close`, Java `Callback<T>.onComplete` interface and
`Imported.songs/unmatched/total` fields are preserved. Work stays on
one background executor with callbacks on the main looper, suppressed
after close. No changes to Room schema, SAF permissions, playlist
storage, icon index or shared BBOS6 helpers. Source-committed only:
release compilation and Q25 import/export testing required.

## Kotlin confirmed track deletion controller

`MusicTrackDeletion.java` is now `MusicTrackDeletion.kt`,
preserving the original Java `Listener` and `DisplayNameListener`
callback interfaces, `Result.deletedIds/failed/deletedCount()` ABI,
and destructive confirmation ownership in `MusicActivity`.
A delete operation still deduplicates exact selected document URIs
and verifies current saved SAF tree selection, **persisted write
permission**, exact DocumentsProvider tree/document identities,
and provider `FLAG_SUPPORTS_DELETE` while excluding directories.
Only `DocumentsContract.deleteDocument` performs deletion.

Room scan state is invalidated both before and after the confirmed
worker batch, with deleted IDs removed from persistence and
active Media3 sessions even if the Activity closes. Native
`MusicPlaybackRetention`, `MusicPlaybackService` and
`MusicLibraryDatabase` schemas are unchanged. On Activity
teardown, already-confirmed deletion batches still complete without
delivering callbacks. No files are moved or unselected trees accessed.

Source committed but not built/device tested. On Q25 test a
confirmed single-track deletion, cancellation, grouped deletion,
read-only/unavailable document rejection, playlist/queue
reconciliation, and interrupted-Activity completion using
disposable test files only.

## Optional platform MediaBrowser adapter is Kotlin

`SymfoniumSource.java` has been migrated to `SymfoniumSource.kt` without changing its optional, unconnected role in the current native Music application. All external media operations still use ordinary Android `MediaBrowser` and `MediaController` public APIs, with the existing connection lifecycle, one-shot paged subscriptions, subscription cleanup, controller callbacks and transport requests. Neither source imports a player library, copies another app's catalog nor changes BB Music's Media3 playback engine, SAF permissions, stored library or UI. Java source was deleted. Source-only conversion; compile and Q25 smoke test required.

## Media3 Kotlin playback service

`MusicPlaybackService.java` has been converted to `MusicPlaybackService.kt` while retaining its manifest class name, ExoPlayer single-authority service, `MediaLibrarySession`, Android-system-media-button queue resumption, cached `bbmusic-recent-queue`, approved SAF URI restoration, single-item metadata-only notification preview, exact 5-second checkpoint, deliberate Stop clearing playback resumption, Recents versus Home differences, noisy-audio detection, background confirmed deletion pruning and saved-state format. The static Java-facing `pruneDeletedDocuments(Context, Set<String>)` remains available via `@JvmStatic`. Media3 service callback signatures match the published Kotlin callback definitions. No Room/SAF/persistence schema or BBOS6 assets changed. Source-committed only, not yet compiled or Q25-tested; verify Android system controls, resumption, Stop and cleanup before merge.

### Kotlin service override compatibility (MediaLibraryService)

`MusicPlaybackService.onTaskRemoved` must override `onTaskRemoved(rootIntent: Intent?)`: Android's `Service` Java API exposes a platform-nullable Intent to the Kotlin compiler. Its implementation does not read the Intent; nullable override preserves Recents-dismiss pause/checkpoint and stopSelf behavior. A non-null `Intent` parameter fails debug and release Kotlin compilation with `overrides nothing`. No playback state, data, or service identity changes.

## Local SAF scanner Kotlin port

LocalMusicSource.java is now LocalMusicSource.kt. Same Java-accessible Listener, Song field ABI, ScanSummary fields and methods; original Java source removed. The scanner remains restricted to MusicFolders.selectedTrees and verified audio streams, uses the same sorted length-prefixed verified-audio-v3-album-tags: selection key, conservative file fingerprints, metadata fallbacks, duplicate URI suppression, album-tag legacy revalidation, deterministic song order, single worker, 400-row deletion batches and atomic Room transactions. Cancelled requests cannot commit or deliver stale callbacks. The existing Room v3 database file and saved data are untouched. Full Q25 Gradle and device testing of library cold start, Refresh, Full rescan, folder removal, missing album metadata, playlists and playback is required before merge.

## Compact playlist chooser (shared media-row popup)

`MusicActivity.choosePlaylist` now supplies stable playlist IDs and names
to `Bbos6ListSelectionComposeBridge` in `bbos6-shared`. The final
`New playlist...` choice calls the unchanged `createPlaylist(songs)`;
existing IDs still call `addSongsToPlaylist(id, songs)`. The shared
Compose popup renders one-line `Bbos6MediaListRow` at **40dp** with no
artwork, and owns selected highlighting, list scrolling and DPAD/trackpad
navigation. The ordinary library rows remain **68dp**. Music owns only
the action mapping and dialog lifetime, not a second picker renderer.
Android SAF permissions and document pickers are unaffected.

Q25 checks: long-press song/album/artist/genre and select Add to playlist,
navigate 1 or many playlists with DPAD/trackpad, center/Enter and touch;
confirm Back cancels; verify New playlist still opens the current name
entry, inserts initial songs, and does not modify the database when cancelled.
Build and install the existing release APK only; do not clear app data.

## Playlist directory icon mapping

The saved playlist entries in the **Playlists** directory reuse the already
verified original **337.png** playlist icon. The **Add Playlist** action row
uses the user-specified original **335.png**, packaged unchanged as
`bbos6_menu_add_playlist.png`; its canonical mapping lives in
`bbos6-shared/icon-index.csv`. Both are selected by the existing
`MusicLibraryRowPolicy.menuIconForRow`, rendered through the standard
`MusicListView` icon slot and shared Compose row. The opened playlist also uses
original `334.png` for **Play all** and reuses the same `339.png`
resource as Home's **Shuffle Songs** for **Shuffle**. All four action
and playlist row mappings are owned centrally by the existing Music row
policy; icon display and row focus/trackpad behavior are unchanged.
