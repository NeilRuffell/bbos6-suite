# Kotlin + Jetpack Compose UI migration (unmerged feature work)

**Target:** All BBOS6 reusable presentation components and the independent
Music application use Kotlin + Jetpack Compose. No new Java or XML View
screens are to be added as part of this migration.

**Hard constraint:** no changes to Music business logic, database schema,
playlist data, SAF grants, media session, playback rules, screen routing, or
hardware keyboard/trackpad interaction.

## Shared-first stages

1. Establish a shared Compose UI module and migrate purely visual controls.
2. Port composite popups, text input, selection and navigation/focus ownership
   with explicit regression tests for hardware trackpad and touch.
3. Port app-specific *presentation* in Music while maintaining its existing
   data and domain logic. Kotlin translations of domain code must preserve
   method semantics and persisted identifiers.
4. Replace legacy Java Views only when their Compose callers achieve behavior
   parity. Until then, old views remain as compatibility sources on this
   **unmerged** branch.
5. Test debug/unit builds, release APK upgrade in place (no uninstall), and
   real Q25 trackpad, keyboard and touchscreen. Advance Music's pinned shared
   submodule only after the shared commit is approved.
6. Separate PRs; no merge without the owner's explicit approval.

## Current increment

The new `dev.q25.bbos6.ui.compose` package supplies reusable pure-Compose
`Bbos6EmptyState`, `Bbos6PopupButton`, and selection/button face modifiers.
These are *presentation-only*: caller-owned content, selection, input handling
and callbacks are unchanged. Existing native UI remains in use; this increment
does not claim parity-tested replacement of the existing Views.

The selected button must only show blue while its focus owner is active.
The original 144dp artwork, 22sp heading, 16sp message, spacing, 30dp
popup-button height, 17sp label, 48dp minimum horizontal width and the
original per-control gradients are retained as explicit Compose values.

**Not yet verified:** Gradle/Android build, pixel parity and on-device Q25
navigation. Do not deploy or merge based on this increment alone.

## Subsequent shared presentation work

- `Bbos6TextField` now has a Compose implementation using
  `BasicTextField(TextFieldValue)`, retaining editable selection state and
  user-supplied IME and keyboard behavior. It draws the same rim/inset color
  stops as the existing 43dp native field, with an optional 30dp popup minimum.
- `Bbos6PropertiesPanel` now renders the existing read-only
  `Bbos6PropertiesItem` model directly, preserving row order, spacers,
  trailing values and the popup's colors/insets. Its host still owns dialog
  bounds, no-dim behavior, Back handling and outside-touch policy.

Neither helper has been wired into Music or any other consuming application.
Pixel comparison and physical focus/touch behavior are not yet Q25-verified.

## Context menu and action popup (draft implementation)

- `Bbos6ContextMenuGrid` and `Bbos6ContextMenuDialog`: Compose
  grouped grid, untinted caller-owned icons, selection gradients, disabled
  actions, original DPAD skipping/edge rules, Enter/trackpad activation on
  key release, and Back/Escape dismissal. The pure Kotlin movement rules
  have JUnit tests.
- `Bbos6PopupButtonPanel` and `Bbos6PopupButtonDialog`: title, message,
  opt-in text entry, native Compose editable-field behavior, buttons that
  measure from captions in a centered horizontal group, and keyboard
  selection/accelerators. App callbacks remain caller-supplied, not encoded
  in the shared library. The focus and shortcut policies have JUnit tests.
- A shared internal window resolver supports the original context-menu
  no-dim window and the 36% dim of the button popup.

These new Compose helpers are not connected to any Music screen and have
not yet been Q25 parity-tested. The legacy native Java helpers remain the
runtime implementation until their equivalents are verified. No app logic
or stored data was modified.

## Kotlin-only Compose models and Properties dialog

- Compose context actions use `Bbos6ContextAction`, a reusable Kotlin model
  for stable IDs, labels, untinted caller-owned icons and enabled state.
- Read-only Properties content uses Kotlin `Bbos6PropertyRow` variants:
  text, inline, trailing and spacer, retaining empty values without filling
  missing metadata. Unit tests validate actions, rows and input constraints.
- `Bbos6PropertiesDialog` owns the reusable Compose modal presentation:
  centered, 90%-bounded, no additional dim layer, Back to dismiss, outside
  touch ignored. The row content remains app-supplied.

**Temporary compatibility:** Existing Java helper classes remain for legacy
View consumers; the new Compose API no longer requires them. Music code and
persistent application data are unchanged. Runtime parity awaits Compose
screen integration and on-device testing.

## First active Music presentation migration (feature branches only)

The shared `Bbos6SectionHeader` reproduces the existing Music Settings
36dp, non-selectable header without introducing a new app-specific renderer.
In the consumer feature branch, only the existing
`MusicSettingsHeaderView` presentation shell is replaced by a Kotlin
`ComposeView` wrapper. Its `bind(title)` contract, height and non-focusable
list-row behavior remain intact; Music list adapters, navigation, playback,
metadata and stored data are not changed. This is a transitional compatibility
step, not the final all-Compose Music screen architecture.

A local Gradle build and on-device Settings list/DPAD/touch comparison are
required before accepting the Music branch. Do not merge either PR before
explicit approval.

## Reusable media-detail header

`Bbos6MediaDetailHeader` adds the original 88dp square-image/detail layout
with 1dp bottom separator, four single-line ellipsized text rows and fixed
colors/spacing. It is independent of Music data, file access and artwork
loading. A Music feature branch will use it in the album page via a Kotlin
presentation wrapper while retaining the existing artwork loader, its
binding-generation guard, adapter selection and page navigation.

## Shared Compose-backed empty-state host

`Bbos6EmptyStateComposeView` is the shared Kotlin `AbstractComposeView`
adapter for existing Android View hosts. It delegates all visible rendering
to `Bbos6EmptyState` and exposes Java-callable `setContent(...)`,
`setArtworkResource(...)`, and `setArtwork(...)` methods. It cannot
be selected or clicked, and does not change any host's empty-data detection,
reserved list rows, viewport alignment or keyboard focus. Caller-supplied
drawables are drawn by a shared Compose `Painter`, keeping their intrinsic
aspect ratio without another Android View.

Music's transitional integration changes only the type/constructor of its
empty-state member, leaving `MusicActivity.updateEmptyState()`, list-row
selection, search and playback logic untouched. The Java native helper is
kept intact for existing consumers until all applications migrate.

The new host requires a successful release build and Q25 review of empty
Playlists and empty playlist-detail states. No parity claim before testing.

## Batch: Music list rows, search chrome and Now Playing metadata plates

Compose now provides `Bbos6MediaListRow`, `Bbos6SearchChrome`, and
`Bbos6MetadataPlate` as app-neutral shared presentations. The list
helper owns the 68dp artwork/text/duration/track-number/remove layout and
the existing BBOS6 selection colors. The search helper owns only the
charcoal panel and top rule; the editable Android field remains native in
the transitional Music view. Metadata plates retain intrinsic measured width
and explicit highlight state supplied by the original Now Playing controller.

The shared `Bbos6NativeDrawablePainter` is exposed for other transitional
Compose hosts so original caller-provided Drawables are not copied into
screen-specific renderers. Music retains its current ListView adapter,
artwork decoding/cache, menu-icon alpha trimming, selection and DPAD logic,
playlist/file removal callbacks, and playback/navigation methods.

All three UI conversions require release-build and on-device parity checks.

## Now Playing presentation batch

The native player remains responsible for its Media3 controller, ticker,
track position/seek policy, button actions, focus zones, DPAD/trackpad
routing, artwork lookup and lifecycle.

The shared Compose package now provides:

- `Bbos6NowPlayingSurface` and its non-interactive
  `Bbos6NowPlayingSurfaceView` host for the mode, half-radial artwork,
  seek-strip and transport backgrounds. Their original gradient stop
  palettes, separator placement and 56%-width right-edge radial geometry
  are recorded in the shared helper. The shallow beveled glints are
  Compose-drawn interpretations pending screenshot parity.
- `Bbos6NowPlayingText` for caller-formatted queue position, title and
  elapsed/remaining labels. It owns only text presentation and alignment.

Native ImageView buttons, the exact native SeekBar/pill geometry and
MusicPlayerChrome's existing foreground separators and selection drawables
remain unchanged during this stage. Android View wrappers own all layouts,
player actions and state. Build and physical Q25 comparison are required
before merging.

## Q25 visual regression: overly thick mode/seek accent lines

A Q25 tester observed a thick glow immediately below the mode header's
top edge and above the seek panel's bottom edge. Comparing the original
Java `MusicPlayerChrome` renderer with the Compose port isolated a
**shared-helper regression**, not a playback, sizing or layout bug:

- Mode header (verified BBOS6 2513.png mapping): the original reflected
  at most 42/255 alpha in a narrow horizontal centre and reduced its
  opacity *on each physical row* as `(1 - progress)^1.55`. The first
  Compose port substituted a full-width linear vertical fade that missed the nonlinear and horizontal taper.
- Seek panel (verified 2486.png): the original reflection peaked at only
  52/255 in the horizontal centre and brightened toward the bottom as
  `rise^3.2`. The Compose port substituted a constant-strength 5dp
  strip, making the entire lower region appear thick and bright.

The **1dp silver/white edge rule is intentional** in both original
backgrounds and is retained. The Compose helper now reproduces the
original per-physical-pixel nonlinear fade *and* horizontal white-to-clear
falloff, using exact original alpha curves and recorded gradient stops.
New `Bbos6NowPlayingBevelTest` protects the intensity functions.

No new Music-specific graphics or changes to native SeekBar, button
semantics, focus, Media3 or navigation. Q25 screenshot parity is required
before calling the fix visually verified or merging.

## Q25 Now Playing interactive visual migration batch

The app-neutral shared `Bbos6NowPlayingControlSelection` now renders
highlight faces **behind** the original interactive Repeat/Shuffle and
four transport ImageViews. MODE highlights reuse `bbos6MediaListSelectionFace`
and preserve 1dp header top/bottom edges; TRANSPORT highlights use its
original distinct 11-stop blue gradient. Both stay transparent unless
their existing Android host owns focus.

`Bbos6NowPlayingSeekRail` renders the full-width 3dp rail and its
completed white progress in the same unpadded coordinate system,
independent of native thumb padding and offset. A passive
`Bbos6NowPlayingSeekRailView` is placed behind the unchanged native
SeekBar, which still owns touch, focus and seek calculations. The shared
helper accepts the guard, rail height, maximum and position from callers;
it does not use Media3. `Bbos6NowPlayingRailTest` checks the normalized
fraction endpoints.

`Bbos6NowPlayingArtwork` draws caller-provided original art using
FIT_CENTER and 6dp inset. The consuming Music app still owns the media
art loader, original no-art resource and asynchronous stale-key guard.

All changes are presentation-only; preserve the already Q25-verified
panel backgrounds, title/clock labels, playback actions, transport glyphs,
trackpad focus zones, seek thumb geometry and app data.
A release APK and Q25 parity test are mandatory before merging.

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

## Kotlin view-tree owner compatibility correction

A Q25 Gradle build of the initial Compose Java-dialog bridge failed at
`bbos6-ui:compileDebugKotlin` and `compileReleaseKotlin` because the
bridge tried to import the Java-style `ViewTreeLifecycleOwner`,
`ViewTreeViewModelStoreOwner` and `ViewTreeSavedStateRegistryOwner` types
into Kotlin. The installed AndroidX lifecycle and savedstate APIs expose
the setters to Kotlin as `View.setViewTreeLifecycleOwner`,
`View.setViewTreeViewModelStoreOwner` and
`View.setViewTreeSavedStateRegistryOwner` extensions. The bridge now
calls these supported methods on the ComposeView directly. No change to
model translation, modal rendering, callbacks, focus, or data semantics.
Recheck both compile variants and Q25 after this import-only API fix.

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
