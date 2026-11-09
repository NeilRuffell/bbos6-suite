# BB Music — standalone Android application

BB Music is an ordinary Android APK (`dev.q25.bbmusic`). It does not require
BB UI, Vector, LSPosed, root, another player, a custom ROM or SystemUI access.
It has its own Gradle project under `music-app/`.

## Standard local Release workflow (same as TV-DV)

Build and test the **release** APK on the Q25 before publishing:
```bash
gradle -p music-app :app:assembleRelease
adb -s 202512003630 install -r music-app/app/build/outputs/apk/release/app-release.apk
```
After Q25 approval, publish **the same APK** using GitHub CLI (no rebuild):
```bash
gh release create "bb-music-$(date -u +%Y%m%d-%H%M%S)" \
  "music-app/app/build/outputs/apk/release/app-release.apk#BBMusic.apk" \
  --repo NeilRuffell/bb-ui --target "$(git rev-parse HEAD)" \
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
6. Playlists (not implemented yet)
7. **Folders** — browse directories chosen in Settings
8. Shuffle Songs (first-party shuffled playback)
9. Settings

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

The Now Playing stage displays the plain song title above separate, compact
BBOS6-style raised labels for artist and album. `MusicBeveledTextLabel` is the
shared native Android view for these metadata plates: a vertical dark gradient,
subtle rounded border, and native elevation shadow, without any PNG artwork or
third-party dependency. Labels size to their text, are centred in the metadata
column, and ellipsize long values to avoid overlapping the album artwork.
Missing artist or album metadata hides only its corresponding label.

The album is read from `MediaItem.mediaMetadata.albumTitle` already supplied
by `MusicPlaybackClient`; playback logic and the unboxed track title are
unchanged. The metadata parent disables child clipping to preserve the native
shadow.

Artist and album labels are interactive on touch and the Q25 trackpad.
On Now Playing, Up/Down visits Repeat/Shuffle, artist, album, seek and
transport in vertical order, skipping any missing artist/album labels. The
focused metadata label uses the existing BBOS6 Music selection gradient;
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
order. Only directories not beginning with `.` and audio-file candidates are
shown. Hidden directories such as `.thumbnails`, cover artwork, playlists and
other non-audio files are omitted, including from configured-root entries
when a root's display name starts with `.`. This affects only Folders browsing:
Settings can still manage all configured roots and the library scanner's
existing selection remains unchanged.

Audio candidates use the same MIME/extension/playlist exclusion policy as the
library scanner, centralized in `MusicFilePolicy`. A matching extension or
provider MIME type alone does not establish playability: files absent from the
verified music catalog remain non-selectable. Indexed music files show artist,
duration and artwork where available and play through the existing Media3
session. Previous/Next uses the playable tracks in the displayed folder (or
its current filtered results), not the entire library.

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
formats such as WMA when no extractor is available on the device. A playable audio file with unreadable title tags still uses
its filename and Unknown Artist/Album/Genre fallbacks.

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

**Canonical icon reference:** [`assets/bbos6/icon-index.csv`](../assets/bbos6/icon-index.csv).
For every newly identified original BBOS6 icon, update that index in the
same change. The repository-wide rule is documented in [`AGENTS.md`](../AGENTS.md).
Do not rename original numbered PNGs or guess icon identities.

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
| Folders (home and directory rows) | `112.png` | `bbos6_menu_folders.png` |
| Shuffle Songs | `339.png` | `bbos6_menu_shuffle_songs.png` |
| Settings (Options wrench) | `3412.png` | `bbos6_menu_settings.png` |

Android requires referenced PNGs to be present in application resource
directories, so the exact original binary blobs are reused under
`music-app/app/src/main/res/drawable-nodpi/`. No artwork is redrawn or
modified. `MusicActivity.menuIconForRow` owns the menu-to-resource mapping;
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

`MusicSearchBarView.java` provides screenshot-matched search chrome while
reusing the existing native `EditText` and input handler:

- **1dp top separator:** `#5A5D63` at the outer top edge.
- **Panel gradient:** vertical, `#4B4E53` (top) to `#292C31` (bottom).
- **Input field:** inset approximately 6dp horizontally and 6dp above,
  white with a 5dp rounded dark outer rim and a gray-to-white inset
  highlight/shadow that reaches pure white within the first ~5dp.
  Approximately 43dp tall.
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
- Each folder chosen grants **read-only** access to its directory and its
  subfolders using Android's Storage Access Framework (SAF).
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
  visible by filename with Unknown Artist/Album/Genre labels.
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
releases this app's persisted read grant. It **never deletes, moves or
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

**Status:** Playback retention and system resumption changes are committed
on a feature branch; building and Q25 validation remain outstanding.
The shade's original code is unchanged; this is a copied presentation,
not a runtime dependency. Android's native playback resumption callback
is implemented, and no separate playback mini-bar is added in the library.

`SymfoniumSource.java` remains an unused, optional adapter. Direct OneDrive
Microsoft Graph authentication, cloud indexing and downloads are future
features and are not required for local playback.

## Share a non-debug APK with friends

GitHub Actions automatically builds the standalone BB Music **release**
APK when the release workflow or `music-app/app/build.gradle` changes on
`master`. It uploads `BBMusic-<versionName>.apk` to
[GitHub Releases](https://github.com/NeilRuffell/bb-ui/releases).
There is **no Google Play dependency, manual signing-key setup, or GitHub
secret setup**. The release is non-debuggable, zipaligned and signature-verified.
All APKs must be signed for Android to install them, so the GitHub runner
generates a temporary signing key automatically and discards it afterward.

**Tradeoff:** Each release has a different certificate. Android will not
install a later APK over an earlier release (or over a debug build). To switch
versions, uninstall BB Music first; this **deletes saved app data**, including
the private music index and saved folder permissions. Re-select your music
folders afterward. This simple distribution method is best for occasional
personal sharing, not seamless in-place upgrades.

For the next release, increase both `versionCode` and `versionName` in
`music-app/app/build.gradle` and merge the change to `master`.
A public GitHub Release for that version is then created automatically.

## Build and test on Q25

Requires the standalone Android SDK 35, JDK 17 and globally installed Gradle.

```sh
cd ~/Projects/bb-ui
git switch feat/standalone-bb-music
git pull --ff-only
cd music-app
gradle :app:assembleDebug
adb -s 202512003630 install -r app/build/outputs/apk/debug/app-debug.apk
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
cd ~/Projects/bb-ui
git fetch origin
git switch feat/music-playback-retention-system-resumption
git pull --ff-only
gradle -p music-app :app:assembleRelease
adb -s 202512003630 install -r music-app/app/build/outputs/apk/release/app-release.apk
```

Do not merge to master or publish a GitHub Release until this exact APK
has passed on-device validation and explicit approval. Retain the
existing signing identity for updates.
