# Reusable BBOS6 native Android popup helpers

A standalone, pure-Java/native-Android UI library. No Media3, storage,
music metadata, BB UI Vector/LSPosed or third-party dependency.

Consumed by the independent [`bb-music`](https://github.com/NeilRuffell/bb-music)
application through the pinned `assets/bbos6` Git submodule. Other native Android
applications can use the same module without maintaining their own copy.

## Components

- `dev.q25.bbos6.ui.Bbos6PropertiesPopup` — a native read-only `Dialog`
  with independent property rows and value alignment.
- `dev.q25.bbos6.ui.Bbos6PropertiesItem` — immutable metadata display rows.
- `dev.q25.bbos6.ui.Bbos6ContextMenu` — native interactive BBOS6-style
  grouped icon grid with rounded blue selection, touch and trackpad navigation.
- `dev.q25.bbos6.ui.Bbos6ContextMenuItem` — immutable caller-defined action
  ID, label, icon source and enabled state.

**Context/action menu:** see [CONTEXT_MENU.md](CONTEXT_MENU.md) for screenshot
measurements, visual constants, example usage, keyboard controls and tests.

**Properties/info popup:** the remaining details on this page apply to the
existing read-only Properties helper.

### Presentation contract

Appearance is based on the supplied 317 × 254-pixel BlackBerry OS 6
**Songs → Properties** screenshot, not an assertion that the proprietary
BlackBerry SDK exposed these exact theme colors or measurements.

| Attribute | Current implementation |
| --- | --- |
| Surface | `#212429`, charcoal |
| Outer border | Narrow top-to-bottom grey rim, slightly lighter at the top |
| Corner radius | 10dp |
| Text | White, regular 15sp Android text |
| Insets | 12dp left/right, 10dp top/bottom |
| Minimum line height | 18dp |
| Width | 90% of the available window width |
| Maximum height | 90% of the available window height |
| Excess rows | Native vertical scrolling within the popup |
| Position | Centered, titleless, no visible action buttons |
| Background | Existing screen remains in place, without an extra dim scrim |
| Dismissal | Android Back; outside touches do not dismiss |
| Input | Informational display only; not a menu or long-press recognizer |

The dimensions are density-independent Android equivalents for reuse; they
should be checked visually on the Zinwa Q25 before being treated as a final
pixel match.

## Usage

From an Android `Activity` (example content from the BBOS6 screenshot):

```java
import dev.q25.bbos6.ui.Bbos6PropertiesPopup;

Bbos6PropertiesPopup.builder()
    .addText("/Device/samples/music/")
    .addText("HotHotHeat.m4a")
    .addTrailing("Size:", "2.6 MB")
    .addText("Audio File")
    .addSpacer(3)
    .addTrailing("Created:", "28 Feb 2006 16:00")
    .addTrailing("Last Modified:", "28 Feb 2006 16:00")
    .addInline("Title:", "21 @ 12")
    .addInline("Album:", "Future Breeds")
    .addInline("Artist:", "Hot Hot Heat")
    .addTrailing("Storage:", "Device")
    .show(this);
```

The caller decides which rows to supply and formats all labels and values.
Missing metadata is **not** synthesized by this helper. It does not read
files or take any storage permissions.

`Builder.items()` returns a defensive, unmodifiable list. For a custom
content source, use `Bbos6PropertiesPopup.show(activity, items)`.

## Integrating another standalone Android application

In its `settings.gradle`:

```gradle
include ':bbos6-ui'
project(':bbos6-ui').projectDir = file('assets/bbos6/android/bbos6-ui')
```

Adjust the project-relative path as needed. In the application's module:

```gradle
dependencies {
    implementation project(':bbos6-ui')
}
```

The Music app opens `Bbos6ContextMenu` for playable tracks with
**Properties** and **Delete** actions, and offers **Delete** for artist,
album and genre groups. Properties opens the existing
`Bbos6PropertiesPopup`; Music owns the SAF metadata and deletion logic. Neither shared widget queries files or handles Music
navigation. Other applications can supply any context actions and property
rows through their respective builders without copying UI code.

## Local verification

From the independent `bb-music` repository root:

```bash
git submodule update --init --recursive
gradle :bbos6-ui:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk
```

The model tests verify value preservation and input validation. Music
supplies a working caller for both popup helpers. Follow Music's tested
release-APK/approval workflow; never replace the installed signing identity.

## Compose migration in progress

See [COMPOSE_MIGRATION.md](COMPOSE_MIGRATION.md) for the Kotlin + Jetpack Compose
presentation migration. The existing Java View APIs remain the runtime path
until full parity and Q25 verification; no Music logic has been changed.

### Compose list section headings

`Bbos6SectionHeader(title)` is a 36dp non-focusable, non-interactive
section heading with the existing BlackBerry-style grey vertical gradient,
1dp rim, bold 14sp title, and 16dp side insets. The shared renderer contains
no list-selection state or Music-specific settings logic. Applications retain
their own list/adapter rows and must mark headers non-selectable.

### Compose media-detail header

`Bbos6MediaDetailHeader` is a reusable, non-interactive 89dp detail panel:
88dp artwork, four independently supplied text lines, and a full-width 1dp
separator. It does not fetch artwork or format album/song metadata. Callers
own any business logic and can use a built-in placeholder Painter when no
artwork is available.

### Compose-backed empty state for legacy View hosts

`dev.q25.bbos6.ui.compose.Bbos6EmptyStateComposeView` is a temporary
Kotlin/Compose host for apps migrating an existing Java `ListView` layout.
It delegates to the same reusable `Bbos6EmptyState` Composable and keeps
Java-callable `setContent`, `setArtworkResource` and `setArtwork` APIs.
The enclosing application still decides visibility, content, sizing and
list-scroll position. Neither helper acquires focus or responds to taps.

### Compose media-list rows and app-neutral chrome

`Bbos6MediaListRow` draws 68dp content-sized rows with optional artwork,
track number, second line, duration, selected background and Remove target.
The caller retains list scrolling, selection, actions and artwork cache.
`Bbos6SearchChrome` is only the search-panel background/top border.
`Bbos6MetadataPlate` is a non-focus-owner beveled label with an explicit
highlight flag; app code still chooses which metadata link is selected.
`Bbos6NativeDrawablePainter` exposes source Android drawables in Compose
without an extra rendered image copy.

### Compose Now Playing presentation

`Bbos6NowPlayingSurface(panel)` provides pure-Compose mode/seek/transport
chrome and the right-anchored half-radial artwork backdrop.
`Bbos6NowPlayingSurfaceView` is a non-clickable, non-focusable host for
staged Android View integrations; the parent still owns its interactive
children. `Bbos6NowPlayingText` draws caller-supplied Now Playing labels.
Media3 interaction, seekbar action semantics, and DPAD focus remain outside
the shared package.
