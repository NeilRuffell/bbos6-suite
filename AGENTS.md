# Canonical BBOS6 shared asset and UI contributor instructions

This repository is the **only writable source of truth** for original numbered
BlackBerry OS 6 assets, verified icon mappings, and the reusable native Android
BBOS6 components. The consuming applications are separate repositories:

- `NeilRuffell/bb-ui` — the Android BB UI application.
- `NeilRuffell/bb-music` — standalone Music application.

Both consume this repository as a **Git submodule** at `assets/bbos6/`.
Neither application should maintain its own editable copy of these assets,
the icon index, or the reusable `android/bbos6-ui/` library.

## Canonical icon identification

The **single source of truth** is [`icon-index.csv`](icon-index.csv).
Original numbered source bitmaps live in [`icons/`](icons/).

**Whenever a numbered BlackBerry OS 6 icon is positively identified, assigned
a new verified meaning, or used with a new verified resource mapping, update
`icon-index.csv` in the same shared-repository change.** Never record the
identification only in chat, in a consuming application, or in a comment.

1. Search `icon-index.csv` by description, keyword, or original number
   **before** selecting an asset. For example:
   `grep -i 'album' assets/bbos6/icon-index.csv`.
2. Keep numbered filenames and original PNG bytes unchanged.
3. Use exactly one row per original numbered filename; update existing rows,
   keep numeric filename order, and retain columns
   `filename,name,keywords,android_resource`.
4. Only document **verified** identifications, based on an actual reference or
   established mapping. Do not infer unidentified icon meanings.
5. Android packaged copies (for instance, resource-named drawables in an
   application's `res/drawable-nodpi`) are build inputs, not an authoritative
   index. Readable packaged filenames are allowed.
6. If the mapping changes, update relevant shared implementation docs.

## Cross-repository changes

An AI agent working in `bb-music` or `bb-ui` must read **this file** before
changing original BBOS6 icon identifications or shared components.

The update order is **mandatory**:

1. Make and review the change in `bbos6-shared` first.
2. Run applicable tests for shared components.
3. Commit and push the shared repository change; use a PR and obtain approval
   before merging to its `master`.
4. In each affected consumer, advance the `assets/bbos6` gitlink to the
   approved shared commit, and commit that gitlink update in its own PR.
5. Verify the consumer builds and its icon mappings before merging that PR.

Do not silently edit a checked-out submodule and commit only the outer
repository. A submodule has independent history and requires its own commit.

A submodule is **pinned**: pushing changes to this repository does not
automatically change a consumer's pinned SHA. To incorporate a shared change,
the consumer must explicitly update its gitlink.

All application-specific behaviors (Music track delete, playback, navigation,
etc.) belong to the consuming app, not to the shared renderer.

## Native Android shared UI

The reusable, app-neutral library lives in
[`android/bbos6-ui/`](android/bbos6-ui/).

- `Bbos6ContextMenu` and `Bbos6PropertiesPopup` implement common visuals.
- Other shared BBOS6 UI changes belong in this library, rather than a
  page-specific visual fork.
- Preserve touch and DPAD/trackpad navigation semantics.
- Use `Bbos6EmptyStateView` for optional heading/text/artwork in empty
  content areas; keep it outside the selectable list adapter. The helper
  must not invent imagery, choose empty-data conditions, or affect focus.
- Keep app-specific actions, data queries, and permissions in the caller.
- `Bbos6PopupButtonList` is the canonical reusable modal stacked-button
  helper. The BB9900 reference uses a brighter, more uniform blue button
  face than the separate context-grid selection: source it from
  `Bbos6SelectionBackground.popupButtonBlue(...)` and do not change the
  existing context-menu gradient to imitate the button popup.
  Buttons use one native rounded gradient with a thin border, **not**
  a black shadow-underlay, inset workaround, or button elevation.
  Preserve optional caller-owned title/icon and DPAD/trackpad activation.
  Do not invent artwork or app-specific button styles.
- Use `Bbos6PopupButtonList` for app-owned confirmations and simple
  button-only choice popups. A title and optional `.message(...)` preserve
  the caller's existing warning text. Place confirmation actions and their
  effects in the app, not the shared component. For destructive actions,
  choose Cancel as the initially selected safe default.
- Prefer extending `Bbos6PopupButtonList` with opt-in text-entry and
  horizontal-button layouts rather than creating a second copy of the popup.
  Use `.textInput(initialValue, hint)`, `.horizontalButtons()`, and
  `.onTextAction((actionId, text) -> ...)` for simple single-line inputs.
  Preserve existing vertical button popups and delegate all editing behavior
  to Android's native `EditText`. Keep validation and actions in the caller.
  Do not maintain a second popup renderer or screen-specific button styles.
- Keep Android-owned system permission prompts and document pickers native.
  Complex input dialogs requiring an editable view are not button-only popups.
- In `Bbos6PopupButtonList`, the selected button index is remembered even
  when an `EditText` has focus, but its blue selection face must appear
  **only while the popup's button-focus owner is focused**. Refresh the
  button faces on focus changes. Do not replace the selection with a
  disabled button or add a transparent overlay; keep native text editing.
- Use the shared `Bbos6FocusPolicy.forManagedContainers(root)` for
  composite BBOS6 panels that own trackpad/DPAD focus and render their own
  selection. Call it **after** the child container hierarchy is assembled,
  before requesting focus. It disables Android's automatic default focus
  highlight only for ViewGroup containers on API 26+, preserving actual
  focusability, event delivery, selection drawing, and native leaf controls
  (especially EditText). Apply to shared popup and context-menu panels.
  Do **not** use transparent overlays, color compensation, global theme
  overrides, or blanket suppression on unrelated native Android views.
- Size BBOS6 popup components using their own fixed, reference-grounded
  `dp`/`sp` dimensions and ordinary Android layout measurement. Do not
  introduce display-density overrides, arbitrary offset compensations,
  row heights derived from viewport width, or proportional screen-filling.
  The button popup and long-press grid each retain their own intrinsic
  sizing, with only a viewport boundary for safe display.
- Introduce new helper behavior in a shared feature branch and advance the
  consumer's pinned submodule in a separate feature PR; never duplicate the
  popup implementation in an app.
- Build this as a Gradle Android library project inside the consuming app.

## Shared BBOS6 text-input appearance

`Bbos6TextField` is the canonical reusable editable field for native BBOS6
screens. It extends Android `EditText` to retain system text editing, IME,
selection, cursor, clipboard and genuine focus behavior, while owning the
existing verified beveled Music-search input appearance (light inset gradient,
rounded grey rim, text/hint colors, field insets and a 43dp minimum height).
No application may maintain a second field-background renderer.

- Use `new Bbos6TextField(context)` for search and app-owned single-line text
  dialogs; set input type, IME options, hints, text watcher, text size and
  value in the caller. The shared helper does not query music or save data.
- The search header's charcoal gradient and top border are **surrounding
  panel**, not part of the text field. Keep that panel separate; do not
  force it into small modal dialogs.
- `Bbos6PopupButtonList` uses this text field internally for optional
  `.textInput(...)`; its input height is **exactly the same 30dp** as
  `BUTTON_HEIGHT_DP` for its buttons. The popup overrides only its input
  instance's 43dp field minimum and sets an explicit shared button-height
  layout constraint. It preserves centered vertical text, input behavior,
  text/button focus ownership, highlighted selection and navigation.
  Do not resize the search field or introduce a popup-specific field renderer.
- Never substitute a fake TextView or custom text engine for native editing.
  A BBOS6-styled native EditText is the intended outcome, not an unstyled
  native Android field.
- Do not add a new field variant, second maintained drawable, focus overlay,
  density adjustment, or screen-specific copies to mimic the screenshot.

## Shared BBOS6 popup button sizing and grouping

`Bbos6PopupButtonView` is the canonical reusable native View for popup
action-button appearance and intrinsic text-sized width. It owns the 17sp
button typography, rounded silver face, existing selected-blue face through
`Bbos6SelectionBackground.popupButtonBlue`, visual text color, horizontal
padding and the common **30dp** button height. The popup owns button
selection, accelerators, callbacks, actual focus and layout.

- **Horizontal popup mode:** set `setContentSized(true)` on every button.
  Width follows the measured label plus the button's own 14dp padding on
  each side, with a 48dp minimum for short captions. Use
  `WRAP_CONTENT` rather than equal-width weights. The popup centers the
  **entire group** with a horizontal LinearLayout inside a
  `HorizontalScrollView` that allows longer groups to overflow safely.
  Keep the horizontal scroller out of the keyboard focus order.
- **Vertical popup mode:** preserve the existing 176dp fixed button width,
  30dp height and 14dp vertical gaps. Neither unrelated popup behavior nor
  icon selection gradients should be changed to fix horizontal sizing.
- **Ownership:** place intrinsic button geometry, visual faces and
  formatting in the button helper; use `Bbos6PopupButtonList` for the
  centered group, scrolling, navigation, selected index and actions.
  Do not copy widths or drawables into Music or other app screens.
- **Focus:** selected blue means the owning button group is currently
  focused; a remembered index remains silver while a field is focused.
  This remains the existing focus policy, not a new visual workaround.
- **Testing:** verify short/long labels in the same horizontal group,
  centering, touch and trackpad left/right, field-to-buttons focus, and
  overflow with several longer actions. Confirm vertical dialogs unchanged.

The screenshot demonstrates label-dependent button widths and a centered
group; sizes not documented as actual BBOS6 API metrics remain approximations.

## Design philosophy: trackpad focus and visual selection

A remembered selection is **state**, not proof that its control currently
has keyboard/trackpad **focus**. BBOS6 custom controls must make those two
concepts explicit:

1. **One focus owner per active interaction area.** The input field owns
   focus while editing. The button container owns focus while navigating
   buttons. A parent screen must not visually claim focus for both.
2. **Draw highlights only for the active focus owner.** In a composite widget,
   the selected index can survive focus transfers; the blue selected
   background appears only when its navigation owner has focus. Refresh
   visuals on real focus changes, not just selection index changes.
3. **Preserve native Android interactions.** Use Android EditText for editing,
   DPAD/trackpad key dispatch for navigation, and real View focus transitions.
   Do not intercept printable input or disable focus to conceal an overlay.
4. **Prevent duplicate framework decoration at the policy level.** Apply
   `Bbos6FocusPolicy` to custom-managed composite containers that already
   render their own focus indicator; keep native leaf-control focus cues.
   Never mask the default highlight with alpha, fake colors, extra drawables,
   overlays, or pixel compensation.
5. **Fix shared ownership, not symptoms on individual screens.** Changes to
   a reusable popup/menu/selection component belong in `bbos6-shared`.
   Consumers supply labels, initial actions, validation and business logic.
6. **Test focus crossings both ways.** On Q25, verify touch, keyboard and
   trackpad for text → button, button → text, menu opening/closing and
   re-entry. Confirm no simultaneous blue selections, no whole-panel glow,
   preserved selected index, and correct confirm/cancel behavior.

These are reusable design rules, not a special case for the Music playlist
dialog. Do not generalize them to unrelated Android-owned dialogs or controls.

## Private owner-maintained dependency

This shared repository is deliberately **private**. The current workflow is
for the owner's authenticated development environment; do not require public
submodule availability or collaborator onboarding. Keep the shared repository
private unless the owner explicitly changes its visibility.

See [README.md](README.md) for paths and usage.

## Kotlin + Jetpack Compose migration target

Future BBOS6 application UI and shared presentation helpers are to use Kotlin
and Jetpack Compose. Migration is staged in feature branches, not directly in
`master`. Do not rewrite application-specific behavior or alter persistent
data as a side effect of converting UI. Keep legacy native Views available
until each Compose replacement is parity-verified for real hardware focus,
touch, keyboard and trackpad. Read
`android/bbos6-ui/COMPOSE_MIGRATION.md` for current progress.

## Compose-backed empty states

The Kotlin `Bbos6EmptyStateComposeView` is the shared transition host for
existing Android View containers. All new empty-state visuals remain in the
`Bbos6EmptyState` Composable, never in a Music-only widget. Preserve the
Java-callable content/artwork methods and the non-focusable, non-clickable
contract so list selection/navigation stays exclusively app-owned.

## Compose Now Playing control-face ownership

The only new Now Playing control visuals are shared
`Bbos6NowPlayingControlSelection`, `Bbos6NowPlayingSeekRail`, and
`Bbos6NowPlayingArtwork`. Consuming applications own selection/focus,
transport action callbacks, native SeekBar interaction and all artwork
loading. Keep the already Q25-verified 1dp mode/seek separators and
nonlinear 2D bevel fades intact when changing these subcomponents.

## Transitional Java modal API to Compose bridge

`Bbos6ContextMenu.Builder.show(Activity)` and
`Bbos6PropertiesPopup.show(Activity, List)` now select shared Compose
presentations for AndroidX ComponentActivity hosts, while maintaining
the native implementation as a compatibility fallback for plain Activity.
Never move caller action IDs, commands, item formatting or storage logic
into the bridge. Preserve Java Dialog return semantics, Q25 DPAD focus and
Back/outside-touch behavior. Test both render paths before deleting legacy
modal renderers.

## BBOS6 custom focus — strictly no Android default overlay

**NON-NEGOTIABLE:** Android's automatic light/default focus-highlight
overlay is prohibited on *every* custom-managed BBOS6 panel, including
ComposeView, AndroidComposeView, shared popup, grid, media-list and modal
focus-owner containers. It obscures the canonical blue selection gradient.
Use the existing shared `Bbos6FocusPolicy.forManagedContainers(root)`
on Android ViewGroup focus owners (including Compose hosts and roots).
Never "fix" an overlay with transparent layers, extra drawables,
dim filters, background color matching, or per-screen hacks. Preserve
native EditText and other leaf controls' real focus/typing behavior.
One active interaction zone draws one BBOS6 selection; selected index
alone is not actual focus. A focus-overlay recurrence blocks Q25
acceptance and merging; validate focus changes in **both** directions.

## Current popup focus routing (Q25 regression and guard)

`Bbos6PopupButtonList.Builder.show(Activity)` uses canonical shared
Compose popup chrome for **button-only** dialogs when the Activity is
a `ComponentActivity`. Any popup with `.textInput(...)` MUST use
the original native `PopupDialog`, regardless of Activity type.
The original native dialog owns DPAD/trackpad dispatch and uses
`Bbos6FocusPolicy`; text input remains native `Bbos6TextField`.
A prior Compose/AndroidView hybrid lost left/right navigation after
leaving the field and drew the default focus overlay on the button
group. That approach is **rejected** until it can provide proper
native↔Compose focus transfer and pass device testing; do not route
input dialogs to the Compose bridge speculatively. The original
plain-Activity native fallback is also retained.

Shared `Bbos6SearchEmptyFeedbackView` owns passive no-results
appearance only; callers retain search filtering, visibility and focus.

## Compose media row native-list touch ownership

The canonical `Bbos6MediaListRow` is paint-only except for its optional
trailing action. In an Android ListView, its `AndroidComposeView` may
consume ACTION_DOWN even when the wrapper is set non-clickable. Always
host it using shared `Bbos6MediaListRowTouchHost`, which passes ordinary
row gestures back to native ListView and forwards only a touch *starting*
within the real trailing-action hitbox to Compose. The hitbox and painted
button MUST share `Bbos6MediaListTouchGeometry` dimensions. Never fix
a list tap by moving action/selection handlers onto a particular screen.
DPAD/trackpad, selection, long press and scrolling remain list-owned.

## Compose modal root focus policy

All BBOS6 Java-to-Compose dialog bridges apply
`Bbos6FocusPolicy.forManagedContainers` to their outer ComposeView
and `Bbos6ManagedFocusEffect` to their mounted AndroidComposeView root.
Read-only `Bbos6PropertiesPanel` applies the same effect when hosted
directly by a Kotlin caller. The entire light/default Android focus
highlight is strictly prohibited on BBOS6-managed modal containers.
Never obscure it with an extra draw layer or disable native leaf editing.
