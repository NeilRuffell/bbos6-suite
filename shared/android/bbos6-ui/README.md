# Reusable BBOS6 Kotlin/Compose and native Android UI library

**Future-app component bucket:** [COMPONENT_CATALOG.md](COMPONENT_CATALOG.md)
contains the canonical exported-component list, Kotlin/Compose samples,
submodule + Gradle setup, legacy fallback rules and the known missing
reusable abstractions.

This library is a standalone, app-neutral Android Gradle module with
**Kotlin-only implementation and test sources**. It contains Jetpack
Compose components and Kotlin-native Android View/dialog support for
hardware-verified Q25 focus, keyboard and touch paths. Its existing
JVM-callable APIs remain available to Android consumers, but no Java source
files are maintained here. It has no Music business logic, Media3, SAF
storage, BB UI Vector or LSPosed dependency. This conversion is pending
consumer build and Q25 verification.

Consumed by the independent [`bb-music`](https://github.com/NeilRuffell/bb-music)
application through the pinned `assets/bbos6` Git submodule. Other native Android
applications can use the same module without maintaining their own copy.

## Background operations

`dev.q25.bbos6.ui.compose.Bbos6BackgroundLoadingIndicator` displays a compact, non-focusable green striped progress bar for any caller-owned indeterminate background operation. Use it in the app's Compose container at the bottom edge while work is active; hide it when that work stops or fails. The composable animates rightward while mounted, with no app-specific scanner, Media3 or service dependencies. See [component catalogue](COMPONENT_CATALOG.md) for placement and sizing.

## Components

For a complete current component inventory, refer to the [catalogue](COMPONENT_CATALOG.md). The native helpers below are one part of the shared module; they are not its entire public API.

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

### Mode/seek chrome fade fidelity

Mode and seek panels intentionally have a **single 1dp edge separator**;
the secondary 7dp/5dp bevel beneath/above it is *not* a flat, full-width
glow. Their per-pixel nonlinear alpha gradients and horizontal falloff are
implemented in `Bbos6NowPlayingSurface.kt` with pure alpha functions
covered by `Bbos6NowPlayingBevelTest`. This matches the previous native
MusicPlayerChrome formulas and the verified 2513.png / 2486.png references.

### Compose Now Playing controls, rail and artwork

`Bbos6NowPlayingControlSelection` provides independent focus-only
mode and transport backgrounds. `Bbos6NowPlayingSeekRail` draws the
full-range progress rail without taking ownership of the native SeekBar
thumb or its touch/edit semantics. `Bbos6NowPlayingArtwork` displays
caller-provided cover painters with original FIT_CENTER scaling. Passive
Android bridge views are supplied for gradual conversion of existing
Now Playing layouts. No app-specific media or metadata logic lives here.


## Java legacy modal consumers now use shared Compose panels

The canonical `Bbos6ContextMenu.Builder.show(Activity)` and
`Bbos6PropertiesPopup.show(Activity, List)` retain their existing Java
signature, input models, validation and native `Dialog` return type.
When the host is an AndroidX `ComponentActivity`, shared
`Bbos6ContextMenuComposeBridge` and `Bbos6PropertiesComposeBridge`
convert legacy immutable models to the existing Compose
`Bbos6ContextMenuGrid` and `Bbos6PropertiesPanel`.

Native Android `Dialog` still owns window gravity, Back/outside dismissal,
state and return handle. Existing app action IDs, enabled flags, icons,
initial selection, callback timing, focus and navigation are preserved by
the bridge. Context menus remain undimmed and outside-dismissible;
Properties are undimmed, read-only and outside-touch persistent. Compose
tree owners are installed from the caller's ComponentActivity. Activities
without AndroidX lifecycle support continue using the old native dialogs,
so BB UI and other legacy hosts are not silently switched.

New unit tests assert the Java-to-Compose model mapping for both modals.
Q25 tests must verify menu keyboard/trackpad highlight and scrolling,
context long press, action routing/dismissal, properties values/scroll and
Back. No app-specific business logic is moved.


## Shared Compose button-only popup and search feedback

The public `Bbos6PopupButtonList.Builder.show(Activity)` retains
all original Java validation, IDs, and callbacks. For `ComponentActivity`
**button-only dialogs**, the shared Compose panel renders action faces,
title/message/icons, selections, and layout. Native `Dialog` still
controls Back, width, 36% dim and returns its normal handle.
**Any popup containing a native text field** is routed to the existing
`Bbos6PopupButtonList.PopupDialog`, which already owns Android
`EditText` focus, left/right DPAD navigation, input↔button transfer,
callback text, and the shared BBOS6 focus policy. Native popup remains
the fallback for plain `Activity` callers.

The canonical `Bbos6SearchEmptyFeedback` and
`Bbos6SearchEmptyFeedbackView` provide 15sp centered white empty-search
text with 16dp/20dp padding. Visibility and search filtering remain
app-owned. No empty-state row participates in navigation. Both additions
require Q25 build/interaction testing before a merge.


## Q25 blocked Compose text-entry popup experiment

A trial `AndroidView(Bbos6TextField)` inside the shared Compose popup
failed Q25 parity: focusing the button row produced Android's lighter
whole-panel default focus overlay, and the trackpad stopped moving
between Cancel and Save after leaving the editor. The old native
`PopupDialog` correctly handled both cases with
`Bbos6FocusPolicy.forManagedContainers(panel)` and
`dispatchKeyEvent` for DPAD/trackpad traversal. Therefore, revert the
Java text-input route to the already tested native popup until a
properly verified hybrid focus-owner bridge exists. The broken hybrid
implementation has been removed rather than left active or copied
into Music. Pure Kotlin-native Compose popup entry remains independent.

**Strict BBOS6 focus policy:** For custom-managed focus-owning ViewGroups,
including the ComposeView and Compose's Android root, disable only the
platform's `defaultFocusHighlightEnabled` with canonical
`Bbos6FocusPolicy`. Never add a compensating overlay, transparent cover,
new selection gradient, or suppress native EditText leaf focus.
Selection color must track the *actual* button-focus owner; selected
index alone cannot paint a highlight. The fallback retains all
existing input actions, field selection, shortcuts, and dialog semantics.
Device-test input↔buttons focus, DPAD left/right, touch, Cancel/Save and Back
before merging.

## Q25 touch-row and Properties focus regressions

A native ListView row containing an AbstractComposeView can have its
ACTION_DOWN consumed by the inner Compose owner, even when the outer
view is not clickable. Added the neutral
`Bbos6MediaListRowTouchHost` to let the native list receive all
ordinary row tapping, long pressing, scrolling and trackpad movement
while delegating touches *starting on* the optional trailing action
to Compose. `Bbos6MediaListTouchGeometry` provides the one canonical
76dp x 36dp action size and 10dp end inset, shared with the painted row.
A platform-independent JUnit test checks hitbox boundaries and density.

The shared Java-to-Compose dialog host now applies
`Bbos6FocusPolicy` to its ComposeView. The shared
`Bbos6ManagedFocusEffect` applies it again to the actual mounted
AndroidComposeView, for context, Properties and action dialogs.
`Bbos6PropertiesPanel` also applies the effect for direct Kotlin
Compose callers. This disables native platform default/light focus
decoration at the source, without changing panel palette, modal dim
state, UI focusability, or native EditText leaf behavior.

No Android platform focus overlays or compensating layers permitted.
Build and Q25 tap/long-press/Properties parity tests are required.

## Q25 second-stage native list tap fix (Android View click lifecycle)

The first touch-pass-through helper suppressed Compose's ordinary-row
ACTION_DOWN, but on Q25 native ListView still scrolled without invoking
its row click. Returning `false` was not a substitute for an actual
native view click target. The corrected shared
`Bbos6MediaListRowTouchHost` handles normal row gestures using
Android's built-in `View.onTouchEvent` and exposes
`setNativeRowActions(Runnable?, BooleanSupplier?)`, rebound for each
adapter row. This dispatches native click and long-click events while
allowing the parent ListView to intercept a scroll and cancel the
row's in-progress tap. No custom timers or gesture detectors.

The optional trailing Remove button is still owned by Compose, and
continues to use the shared action hitbox. Recycled/disabled rows
must drop native action callbacks. The actual list owner invokes
`performItemClick` for semantic accessibility/click compatibility
and obtains current position at activation time rather than trusting
the recycled bind index. Keyboard/trackpad and the already fixed
Properties focus suppression are unchanged. Build + Q25 tests remain
required before merge.

## Compact BBOS6 list-selection popup

`Bbos6ListSelectionComposeBridge.show(ComponentActivity, title, items, onSelected)`
opens a scrollable app-owned selection dialog with a Compose
`Bbos6ListSelectionPanel`. Its items are unique `Bbos6ListSelectionItem`
stable IDs and single-line labels. Each entry renders the **existing**
`Bbos6MediaListRow` at a compact 40dp (default media rows stay 68dp),
without album artwork, duration, subtitle or icon. The popup uses the
existing charcoal/rim design and focused blue list-row selection; it
supports touch, Up/Down, trackpad/Enter and Back/Escape, and keeps long
lists within the Q25 viewport. Native Android Dialog controls modal
lifetime and dimming.

The host application owns all item queries, callbacks and actions, such
as choosing an existing playlist or opening a separate playlist name
editor. Do not copy or restyle a row in the consuming application.

Verification: run `:bbos6-ui:testDebugUnitTest` and confirm short and
long choices, scrolling, focus visibility, trackpad center activation,
touch, Back, empty-list rejection and no UI regression in 68dp media rows.
