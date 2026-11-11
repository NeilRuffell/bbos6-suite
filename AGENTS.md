# BB Music: contributor and AI instructions

This repository is the **standalone native Android Music application** for
the Zinwa Q25. Its Android application ID is `dev.q25.bbmusic`. Preserve
the existing app ID, device data, signing identity, and release workflow.

## Required: canonical BBOS6 assets and shared UI

The repository root's `assets/bbos6/` is a **Git submodule** referencing
`NeilRuffell/bbos6-shared`, not a standalone writable copy.

Before using, identifying, renaming, or remapping original BBOS6 icons—or
changing reusable BBOS6 UI helpers—read
[`assets/bbos6/AGENTS.md`](assets/bbos6/AGENTS.md).

The **only authoritative asset index** is
[`assets/bbos6/icon-index.csv`](assets/bbos6/icon-index.csv).

**Whenever an original numbered BBOS6 icon is positively identified, used, or
assigned a new verified meaning/mapping: update the canonical index as part
of the same shared-library change.** No second index, no inferred mapping,
and no silent application-only identifications.

Workflow for all shared changes:

1. Update a branch of **`bbos6-shared`** (index and/or neutral UI library),
   test it, submit a PR, and obtain approval before merge.
2. After the shared commit is approved, advance `assets/bbos6` to that
   exact commit in this repository and commit its gitlink to a Music PR.
3. Build and test Music with the updated library before merging Music.

Keep **Music-specific operations** (playback, SAF deletion, Music folders,
artist/album/genre grouping, navigation) in this repository. Change shared
helper code only for general reusable behavior, not a single Music screen.

Git submodules pin commits. Pushing new `bbos6-shared` changes does not
automatically update Music; the Music gitlink must be intentionally advanced.

## Native functionality and input

Prefer native Android and Media3 APIs. Keep the current trackpad/DPAD
and touch interactions, existing reusable list and context menu behaviors,
and user-authorized SAF tree boundaries.

Never require user-entered commands for in-app functionality.

## BBOS6 managed focus

For composite BBOS6 screens owning their own trackpad highlight, call
`Bbos6FocusPolicy.forManagedContainers(root)` after assembling child views.
This uses Android's built-in API 26+ default-focus-highlight policy on
containers, without changing focusability, input handling, or custom BBOS6
selection drawing. Do not imitate this with translucent color layers or
global disabling of focus decoration on interactive leaf controls.

`MusicNowPlayingView` uses the shared policy on its trackpad focus owner.
The shared context menu, properties popup, and button-list popup apply
the same policy internally. Leave native `EditText` editing/focus intact.
When modifying shared policy, change `bbos6-shared` first, pin the
consumer submodule to its approved commit, and verify Q25 trackpad/touch.

### Cross-component focus design rule

Treat a remembered selection and actual focus as separate states. Only the
currently focused interaction area may draw its blue BBOS6 highlight.
When moving from a text field to buttons and back, retain the selected
button index but show blue only while the button group owns focus.
Use the shared `Bbos6PopupButtonList` and `Bbos6FocusPolicy`; never add
Music-only colors, transparent covers, duplicate focus suppressors, or
per-screen focus compensation. The complete reusable policy and
trackpad/touch verification rules live in
`assets/bbos6/AGENTS.md` and the shared README. Consult those before
building any new focusable composite controls.

## Canonical BBOS6 text entry

Use the shared `Bbos6TextField` for app-owned editable text fields. It is
the sole source of the existing beveled grey-to-white search-input face,
including the rim, inset gradient, font colors, padding and 43dp minimum
height. It **extends Android EditText** to preserve genuine keyboard,
cursor, selection, IME, clipboard and accessibility behavior; do not
implement a second text-editing engine.

- Music's search field instantiates `Bbos6TextField` and retains its existing
  text watcher, physical-keyboard search and ListView focus behavior.
- `MusicSearchBarView` owns **only** the charcoal search-header panel and
  top rule. Do not copy the text-field drawables back into it.
- `Bbos6PopupButtonList` creates the **same shared text field** for
  New/Rename Playlist; its existing button-focus ownership, Save/Cancel
  callback and text input remain unchanged. The search panel is not
  attached to dialog fields.
- Text editing options belong to the caller; shared helper changes
  require shared-first PR, pinned submodule and Q25 testing.
- Prefer fixing shared appearance at the renderer, never add Music-specific
  compensating overlays, field palette copies or focus suppressors.

## Reusable empty-state UI

Use `Bbos6EmptyStateView` from the shared `bbos6-ui` module for title,
supporting message and optional caller-provided original artwork when a
page has no content. Never insert placeholder messages as selectable or
disabled adapter rows merely to show an empty state. The shared helper owns
only presentation; each application determines emptiness and placement
outside the ListView adapter. Artwork is absent unless supplied and
identified; do not create replacements.

For Music, `MusicActivity.Page.setEmptyState` configures the display and
`updateEmptyState()` positions it below existing playlist action rows.
The underlying `MusicListView` retains scrolling/trackpad selection.
The empty Playlists page uses verified original `354.png` from
`bbos6-shared/icons/`, packaged unchanged as `bbos6_playlist_empty.png`,
with its mapping recorded in the canonical shared `icon-index.csv`.
Its **Add Playlist** row opens the canonical shared `Bbos6PopupButtonList`
with the message "Please select how you would like to add a playlist:"
and the two options **New Playlist** / **Import Playlist**. These invoke
the existing app-specific create and SAF import methods; do not duplicate
their logic or add new screen-specific popup renderers. Playlist creation
and renaming share `askPlaylistName(...)`, which now uses the same popup
with `.textInput(...)`, `.horizontalButtons()` and `.onTextAction(...)`.
Cancel never writes; Save passes native EditText content to the existing
Room validation and persistence path. Previous standalone Create/Import
list entries remain absent.
Changing this shared helper requires the standard shared-first PR and
consumer-submodule workflow.

## Popup button sizing philosophy

Use `Bbos6PopupButtonView` from the shared UI for BBOS6 popup action
faces and intrinsic widths. Its button label, minimum touch width,
padding, silver/blue gradients and shared 30dp height are maintained only
in `bbos6-shared`. For `Bbos6PopupButtonList.horizontalButtons()`,
each button's width is measured from its caption and the complete row is
centered. Horizontal overflow is scrollable without becoming a second
focus owner. Vertical buttons retain their established fixed width.
Music must not compute popup button widths, add compensating margins,
or duplicate popup drawables in app-specific code.

Test the Create/Rename Playlist Cancel/Save row for variable label
widths, centered placement, text-to-button focus, left/right navigation,
and touch before merging. This rule also applies to future apps.

## BBOS6 confirmation dialogs

Use `Bbos6PopupButtonList` from the canonical shared Android module for
app-owned button-only confirmations and simple choice popups. Music's
`showButtonPopup(...)` owns dialog lifetime, while centralized
`showButtonConfirmation(title, message, confirmLabel, callback)` retains
app-specific text/actions and initially selects **Cancel**. Use it for
file deletion, playlist deletion, removing configured folders, and full
library rescan. Do not reproduce popup styling or the shared selected
gradient within this app.

Keep Android-owned SAF permission grant dialogs and document pickers
native; the popup is not a replacement for platform authorization.
For simple one-line text entry, use the existing shared popup's native
`EditText` and horizontal buttons. Keep more complex input or list-selection
dialogs native until suitable shared helpers exist. Never alter deletion authorization,
file security checks, or the selected music-folder allowlist merely to
change a dialog's appearance. Popup sizes are set by the shared helper
using intrinsic dp/sp layout dimensions; never add Music-specific scaling,
pixel offsets, or compensating layout arithmetic.

## Playlist maintenance

Playlists are implemented only in this repository, through
`MusicPlaylists`, `MusicPlaylistTransfer` and the existing
`MusicLibraryDatabase` Room schema (v3). Preserve playlist entries,
order, names and existing music catalog through forward migrations.
A playlist references indexed, user-authorized SAF `content://` songs,
not a new filesystem crawler or an unscoped `MediaStore` query.

Playlist selection uses the existing `MusicListView`,
`Bbos6ContextMenu` and Media3 playback client. Reuse the shared
widgets without maintaining screen-specific copies. Import/export is
explicit, bounded SAF document-picker M3U8 I/O; only resolvable
primary-storage paths can be represented in portable file playlists.
Never silently match a missing entry by filename, access unselected
folders, or delete audio as a side effect of playlist removal.

Release checks must include native trackpad long-press, duplicate
entries, repeat/shuffle queue behavior, persistence, missing/unavailable
files, permission revocation, and M3U8 import/export. See README.

## Build, test and release

In a local clone:

```bash
cd ~/Projects/bb-music
git submodule update --init --recursive
gradle :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

- Preserve `~/.android/debug.keystore` and the existing signing identity.
- Do not uninstall or clear data to deploy routine upgrades.
- Test the **release APK** on the actual Q25 before approval/merge.
- Only after explicit approval, publish **that same tested APK** from
  `app/build/outputs/apk/release/app-release.apk` to
  `NeilRuffell/bb-music` GitHub Releases. Do not rebuild/re-sign in CI.
- Increment `versionCode` on each newly published version and update
  `versionName` appropriately.
- Android applicationId stays `dev.q25.bbmusic`.

Read [`README.md`](README.md) for detailed Music architecture and test cases.

## Source of truth after repository separation

The old `NeilRuffell/bb-ui/music-app/` path is legacy history.
All new Music code, tests, documentation and releases belong **here**.
`NeilRuffell/bb-ui` owns only its own Android system UI work.

## Compose UI migration work-in-progress

The long-term native UI standard is Kotlin + Jetpack Compose, with reusable
BBOS6 presentation implemented **only** in `bbos6-shared`. During the staged
migration, existing Java business logic and View screens remain operational
until exact behavior parity is established. No playback, SAF, catalog,
playlist, Room, session or navigation logic may be modified to facilitate a
visual port. The first Compose-backed consumer is the existing
`MusicSettingsHeaderView` adapter presentation, which must remain a
non-selectable 36dp row and preserve `bind(title)`. See the migration section
of README. App branch Gitlinks pin a specific shared feature commit for
testing, and neither repository may be merged without explicit approval.

ComposeView hosts in a legacy Activity require AndroidX view-tree lifecycle
and saved-state owners. The Music feature branch uses `ComponentActivity`
for that framework integration only; do not modify existing Activity method
bodies or application logic as part of this step.

### Compose album header transition

`MusicAlbumHeaderView` is a Kotlin `AbstractComposeView` backed exclusively
by the app-neutral shared `Bbos6MediaDetailHeader`. Preserve its existing
Java-callable constructor, `bind(...)`, `clear()`, and static `heightDp()`
contract. Keep `MusicActivity.bindAlbumHeader()`, the `MusicListView` art
cache, selected SAF source scope, stale callback generation guard, and
non-selectable header position unchanged.

### Compose empty-state transition

`MusicActivity` uses the shared
`dev.q25.bbos6.ui.compose.Bbos6EmptyStateComposeView`; do not maintain
a Music-only Compose empty state. Preserve `updateEmptyState()` byte-for-byte
apart from the field type/constructor migration. All conditions, reserved
row offsets and original resource IDs remain Music-owned.

### Compose list, search and metadata batch

Use the canonical shared `Bbos6MediaListRow`, `Bbos6SearchChrome` and
`Bbos6MetadataPlate` helpers. Music's `MusicListView` remains the
unchanged native scroll/selection/adapter navigation owner; all artwork
loading, menu-icon trimming and stale-binding checks remain in its Java
controller. Keep native `Bbos6TextField` for search during transition;
only the surrounding chrome is Compose. Now Playing must keep its existing
zone/trackpad/touch listeners and call the same Java methods on
`MusicBeveledTextLabel`. Follow the shared-first, pinned-gitlink PR workflow.

### Compose Now Playing panel and text surfaces

Shared `Bbos6NowPlayingSurface` owns only the original chrome panel
appearances, via the non-focusable `Bbos6NowPlayingSurfaceView` behind native
controls. The shared `Bbos6NowPlayingText` owns only text presentation and
receives Music-calculated strings through `MusicPlayerTextView.setText`.
Never move Music playback/ticker, media controls, seek policy, title/queue
formatting, artwork permission checks or DPAD zone state into UI helpers.
The old native chrome drawables remain available for pixel parity comparison.

### Shared Now Playing bevels are canonical

Only `bbos6-shared` may define the mode-header and seek-strip bevels.
Never compensate for a panel's excessive glow by adding Music-only
background overlays or changing the seekbar dimensions. The reference
separators are 1dp; two-dimensional per-pixel reflection alpha is
controlled by shared `Bbos6NowPlayingSurface`, with regression tests
in `Bbos6NowPlayingBevelTest`.

### Now Playing Compose buttons and progress

Keep mode/transport icon ImageViews and the native SeekBar as the only
touch/DPAD/action owners. Their selection-only Compose hosts and the
paint-only seek rail must never acquire input focus or draw extra rules.
`MusicNowPlayingArtworkView` is only a binding bridge; actual image
rendering is in shared `Bbos6NowPlayingArtwork` and Music retains all
artwork lookups and stale async guards.

### Shared modal Compose transition

For `MusicActivity` (ComponentActivity), context menus and Properties
use the canonical shared `Bbos6ContextMenuComposeBridge` and
`Bbos6PropertiesComposeBridge` automatically through existing Java
APIs. Do not add Music-specific UI dialogs or reimplement action
callbacks. Keep original focus/activation/Back semantics; explicitly
verify both modal types on Q25 before any merge.

### Strict BBOS6 focus-overlay prohibition — Q25 regression guard

Android's default/light full-container focus overlay is **strictly
forbidden** for every BBOS6 custom-managed focus owner in this project
and in `bbos6-shared`. Preserve only the canonical blue selection
gradient for the *actually focused* interaction area. Never mask
Android's overlay with transparency, extra colors or compensating
drawables. The shared `Bbos6FocusPolicy.forManagedContainers(root)`
must disable Android's default focus highlight at native ViewGroup and
ComposeView focus-owning containers; native EditText leaf focus and
cursor/keyboard operations remain intact. A recurrence is a blocking
visual/focus regression.

### Current popup migration boundary

Button-only popups use shared `Bbos6PopupButtonComposeBridge`;
`.textInput(...)` popups use the already Q25-verified native
`Bbos6PopupButtonList.PopupDialog` and canonical native
`Bbos6TextField`, even within AndroidX ComponentActivity. The
Compose/AndroidView hybrid was rejected after Q25 testing because it
painted a light Android focus overlay and trackpad left/right stopped
moving the selected Cancel/Save button. Native popup already handles
DPAD and changes focus between input and buttons correctly. Retain this
fallback until both problems are solved and tested in shared code.
Application playlist validation and callbacks stay in Music.

`Bbos6SearchEmptyFeedbackView` remains the shared passive search
no-results face; Music retains filter and visibility logic.

### App-level Compose shell and Kotlin policies

The outer Library/Now Playing host is `MusicAppShellView` (Kotlin
Compose), not the old native safeViewport FrameLayout. It retains
exact original native screen instances via AndroidView during gradual
migration. Show-after-attach callbacks are mandatory before requesting
player/list focus. Do not manually reparent those views in MusicActivity;
ask the shared shell host to swap them. Apply the canonical
`Bbos6FocusPolicy` to managed ViewGroup focus owners, never overlay
extra default-focus mitigation. Music policy classes converted to Kotlin
must preserve original Java static entry points (via `@JvmStatic`)
and stable numeric constants (via `const val`), and pass existing
Java unit tests before merging. The shell is only an intermediate
step: do not claim full Kotlin/Compose completion until Activity,
ListView, Now Playing and application domain/service Java are converted.

### Kotlin library-list adapter

`MusicListView.kt` replaces the old Java list controller. The underlying
Android `ListView` remains intentionally native for Q25 trackpad/DPAD
scroll, edge alignment, selection recovery and platform long-press
timeouts; each row and section header is already Compose-backed.
Keep the original `DataSource`, `RowData` Java ABI intact so
`MusicActivity.java` can still drive menu/business logic until its own
migration. The Kotlin list must preserve binding-key stale-image guards,
no-art resource fallback, verified original icon alpha-trimming and
artwork cache sharing with `MusicAlbumHeaderView`. No light Android
default focus highlight may overlay the canonical BBOS6 blue row face.
Never replace native selection with a blind Compose LazyColumn without
first reproducing Q25 key and viewport-edge semantics.

### Kotlin embedded-art thumbnail loader

`MusicListArtworkLoader.kt` owns the same authorized SAF-content,
160px downsample, 12 MiB LRU, generation cancellation, two-thread
executor and main-thread callback semantics as the retired Java
implementation. Its `fun interface Listener` remains Java-SAM
compatible for the Kotlin list and Compose album header. Do not move
the request binding key or stale-row check out of
`MusicListView`. All cover lookups remain limited to authorized
music file URIs passed by the app.

### Kotlin SAF folder-browser boundary

`MusicFolderBrowser.kt` replaces the Java file only. Its app-specific
SAF authorization and lifecycle are not shared UI helpers. Never broaden
DocumentsProvider access: each call must verify
`MusicFolders.selectedTrees(context)` before querying and immediately
before callback delivery. Original folder-first sort, hidden-directory
and audio-candidate filter, and cancel-on-close semantics are required.
Native UI rendering is a separate migration.

### Kotlin Now Playing art dependencies

`MusicArtworkLoader.kt` retains the original one-track
`MediaMetadataRetriever` generation guard and 600px thumbnail
limit, keeping the existing Java-caller SAM listener.
`MusicCoverPlaceholderDrawable.kt` preserves generic no-art paint
geometry, not an original numbered BBOS6 asset. Do not change
`MusicNowPlayingView` artwork request ordering, callback stale-URI
key check or loader shutdown while converting it to Kotlin/Compose.

### Kotlin Now Playing controller

\`MusicNowPlayingView.kt\` owns the app-specific Now Playing layout,
zone traversal and input after the Java counterpart was retired.
Canonical surfaces, labels, seek rail, selection and metadata plates
remain in shared BBOS6 Compose. The existing native SeekBar/transport
ImageViews are deliberate Android platform controls used from Kotlin;
do not change DPAD/touch semantics, mode icon assets, progress math,
click handlers or Media3 behavior while replacing layout wrappers.
Protect the 1s ticker and async artwork generation callback across
dispose, and preserve \`Bbos6FocusPolicy\` on the root.

### Kotlin Now Playing constructor and dp interop

`MusicNowPlayingView` exposes its existing Java constructor at the
JVM level but declares it `internal` in Kotlin to avoid leaking the
package-private Java `MusicPlaybackClient` as a public Kotlin API.
Keep `dp(Int)` for existing integer dp constants/paddings, with
conversion to the legacy `MusicPlayerChrome.dp(Context, Float)` so
pixels remain rounded exactly as before. Do not introduce native
focus overlays or change playback/DPAD handlers for compiler repairs.

### Kotlin selected-folder authorization

MusicFolders.kt owns the same persisted SAF allowlist and all permission checks. Keep bbmusic_selected_folders/tree_uris unchanged. Never expand folder access or change grant handling when migrating Java. Remove an allowance BEFORE OS permission release, not files. Keep @JvmStatic until all Java callers are migrated.

### Kotlin Media3 client

MusicPlaybackClient.kt owns Media3 UI-side session connection and still uses the native MediaController. Preserve one bounded retry, current user-selected pending queue over old restoration, SAF content URI restriction, Stop clearing system playback resumption without discarding the in-app item, and deletion reconciliation. Keep the Kotlin class Java-callable while MusicActivity remains Java. Do not change persistent saved queue formats.

### Retained playback Kotlin port

`MusicPlaybackRetention.kt` retains its `bbmusic_playback`/`queue_v1` serialized JSON schema and exact SAF-tree authorization, including no automatic switch when the current track is no longer authorized. Keep `@JvmStatic` and `Saved` field JVM ABI for the Java service and Kotlin client. Never rewrite stored queues, drop metadata, change Stop-clear semantics, or loosen `DocumentsContract` validation during Kotlin/Compose migrations. Verify pause/relaunch, background Media3, deliberate Stop, deletion of queued tracks, and permission revocation on Q25.

### Kotlin confirm-key and Properties adapters

TrackpadPressHandler.kt keeps proven Q25 native event dispatch and state. Do not draw or suppress focus in this app handler; shared Bbos6FocusPolicy owns focus appearance. MusicTrackPropertiesReader.kt must verify selected-tree grants before a DocumentsProvider query and again before callback, on main thread. Preserve native metadata omissions; never invent creation dates or absolute paths; reuse the canonical Bbos6PropertiesItem. No UI copies.

### Kotlin playlist repository

MusicPlaylists.kt remains app-specific. Do not move Room transactions to Compose or shared BBOS6 helpers. Keep exact selected-tree authorization before inserting tracks, original Room database/DAO and playlist ordering, 128-character name limit, serialized worker, cancelable main-thread callbacks and close behavior. During Java Activity migration retain its Callback<T> SAM interface and the playlist method names. No schema migrations or data clearing for the Kotlin port.

## Native ListView tap regression guard

`MusicListRowComposeView` MUST inherit shared
`Bbos6MediaListRowTouchHost` (from the pinned bbos6-shared submodule)
rather than raw `AbstractComposeView`. Compose's nested AndroidComposeView
can consume touch ACTION_DOWN even when `isClickable = false`, preventing
the native ListView from selecting/activating a row. The shared host lets
all ordinary row touches/long-press/scroll reach the ListView; only
gestures beginning within the canonical trailing Remove button bounds
are delivered to the Compose remove action. Update its enabled flag on
every `bindText` recycling operation. Never add per-screen tap handlers.
All Properties modal focus suppression is likewise shared via
`Bbos6ManagedFocusEffect`; no light/default platform focus wash allowed.

## Native Compose-row touch activation — Q25 second-stage regression rule

Returning `false` on ordinary `AbstractComposeView.dispatchTouchEvent`
ACTION_DOWN lets Q25 scroll but **does not** activate a native
`ListView` row. Use shared `Bbos6MediaListRowTouchHost` native
`View.onTouchEvent` click/long-click processing, and bind fresh
`setNativeRowActions` through the Music row wrapper during each adapter
recycle. In `MusicListView`, resolve current row with
`getPositionForView` at invocation, then use existing
`performItemClick` and `OnItemLongClickListener`; never activate an
old recycled index. Android ListView intercepts drag and cancels row
pressed state. Keep Compose pointer routing only for explicit
trailing Remove. The shared helper owns generic gesture policy;
Music owns data and action callbacks. No custom timeout, focus overlay
or per-screen handler allowed.

## Native player compatibility classes

`HalfRadialGradientDrawable` and `MediaChromeDivider` were verified
unused and deleted. Do not resurrect legacy unused rendering code
while migrating Java to Kotlin. The remaining
`MusicSelectionGradient.kt` is a Kotlin compatibility adapter used by
native `MusicPlayerChrome`; its exact blue gradient stops and
`@JvmStatic` factories preserve the established Android drawable
contract until the native player chrome is fully retired in favor of
canonical shared Compose components. No new maintained palette copies.
