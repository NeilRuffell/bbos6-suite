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
