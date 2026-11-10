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
