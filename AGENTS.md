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

## Private owner-maintained dependency

This shared repository is deliberately **private**. The current workflow is
for the owner's authenticated development environment; do not require public
submodule availability or collaborator onboarding. Keep the shared repository
private unless the owner explicitly changes its visibility.

See [README.md](README.md) for paths and usage.
