# BBOS6 shared assets and native UI library

This is the **canonical shared dependency** of
[`bb-music`](https://github.com/NeilRuffell/bb-music) and
[`bb-ui`](https://github.com/NeilRuffell/bb-ui).

| Path | Role |
| --- | --- |
| `icons/<number>.png` | Unmodified original numbered BBOS6 assets |
| `icon-index.csv` | Only authoritative identification / resource mapping |
| `android/bbos6-ui/` | Reusable Android Java widgets and tests |
| `AGENTS.md` | Required AI / contributor workflow |
| `tools/validate_icon_index.py` | Basic index consistency check |

The shared library contains the BBOS6 context menu, read-only Properties
popup, and the app-neutral `Bbos6EmptyStateView`. App-specific actions,
messages, selection/visibility conditions and data loading never live here.

### Empty-state presentation

`Bbos6EmptyStateView` is a non-interactive `LinearLayout` for an empty
content region. The caller controls when it is visible and supplies a heading,
optional supporting text, and optionally a drawable via `setArtwork(Drawable)`
or `setArtworkResource(int)`. The default is **no artwork**; no placeholder
assets are generated or inferred. A null or zero artwork clears the image.

Place it outside a `ListView` adapter, preferably inside the list's host
container or in a distinct content panel. The empty state does **not** add a
list item, become focusable/selectable, handle DPAD/trackpad events or
change the host's scrolling and selection logic. Layout bounds and the
conditions for displaying it are application-owned.

Music's empty Playlists screen opts in to original `354.png` artwork, explicitly
selected by the project owner. The asset remains unmodified under `icons/`;
its verified mapping is recorded in the canonical `icon-index.csv`. Other
empty-state consumers do not receive that artwork automatically.

### Popup button list

`Bbos6PopupButtonList` is a standalone modal dialog helper inspired by the
provided BBOS6 confirmation screenshot. It is implemented **only in this shared
library**. Integration into individual applications requires a pinned
submodule commit and separate application PR.

The default presentation uses a charcoal rounded panel and thin rim, with
vertically stacked rounded buttons. Based on direct pixel samples from the
provided 640 × 480 BlackBerry simulator screenshot, the selected button
transitions from a bright upper blue to a stable **#1766DB** lower face.
The unselected button transitions from light silver to a stable
**#AFB1B4** lower face. Both are single native `GradientDrawable`
backgrounds with a thin one-pixel stroke and a 4dp radius, **without**
a black underlay or View elevation.

The shared `Bbos6SelectionBackground.popupButtonBlue(...)` owns the
popup-specific blue palette, avoiding a copy in Music. The separately
verified `Bbos6ContextMenu` grid selection colors and strokes remain
unchanged; they are not interchangeable with the screenshot's button face.

The optional `.message(String)` adds wrapped, non-interactive explanatory
text between the title and button list. This preserves warnings on delete,
remove, or rescan confirmations without using native Android `AlertDialog`
buttons. App-owned button-only confirmations should use this helper;
`Cancel` should be the initially selected action for irreversible operations.
Keep Android-owned SAF permission/picker screens native, and retain native
input views where users need to type.

The helper owns only presentation and input: touch activation; Up/Down to
change the focused button; Q25 trackpad/Enter to activate; Back/Escape to
dismiss; disabled button skipping; and matching underlined, unique letter
shortcuts when available. It accepts a caller-supplied, optional title and
optional drawable/resource icon; no question-mark icon, artwork or action
is synthesized. It accepts labeled action IDs and dispatches them to the
caller after dismissing by default. A bounded scrolling panel supports
longer button lists without exceeding the available screen height.

Example API (documentation only; **do not call from an app yet**):

```java
Bbos6PopupButtonList.builder()
        .title("Changes made!")
        .message("Save the changes?")
        // .titleIconResource(R.drawable.a_caller_supplied_icon)
        .addButton("save", "Save")
        .addButton("discard", "Discard")
        .addButton("cancel", "Cancel")
        .initiallySelected("save")
        .onAction(actionId -> { /* app-specific behavior belongs here */ })
        .show(activity);
```

No new numbered original BBOS6 assets are introduced or mapped in this change.
The screenshot informs approximate styling; exact historical theme values
are not asserted. Do not duplicate this button renderer in consuming apps.

### Compact Bold 9900-inspired sizing

Both `Bbos6PopupButtonList` and `Bbos6ContextMenu` use compact,
component-owned dimensions chosen from the supplied 640 × 480 Bold 9900
simulator screenshot (a visual reference, **not verified SDK metrics**).
Android handles `dp`/`sp` conversion normally; there is no device-density
override, screen-percentage zoom, post-layout translation, compensating
offset, or cell-size-derived vertical scaling.

- Button popup: a nominal **320dp** panel, **176 × 30dp** buttons, **14dp**
  spacing, **17sp** button labels, **18sp** heading, and **4dp** button radius.
  The existing shared blue selection gradient is reused with the same
  corner radius as the silver face. A native viewport bound prevents a
  popup exceeding the available display on narrower devices.
- Long-press context grid: stable **94dp** rows, **42dp** icons and **14sp**
  labels, with **112dp** single-column, **212dp** two-column, and **312dp**
  multicolumn panel widths. The existing touch, trackpad/DPAD and
  accelerator behavior is unchanged. Android's vertical layout measures
  icons and wrapped labels instead of calculating proportions from a
  dynamically stretched cell or shrinking rows to fit.
- For menus too tall to display, the existing bounded `ScrollView` scrolls
  content. The full usable viewport bounds the scroll height; no ad hoc
  corrections are applied to the items or selection.

The button popup and context grid intentionally have different intrinsic
sizes because they are different BlackBerry controls. Direct reference-to-Q25
comparison showed the button footprints were already similar; the inaccurate
appearance came from an extra opaque-black drawable layer, View elevation
and an excessively dark full-height gradient. The new simple face has no
visual compensations, offset tricks, or app-specific copies. Confirm
colors and touch behavior on the actual Q25 before merging.

## Consumer integration

Check out a consumer with the shared dependency:

```bash
git clone --recurse-submodules https://github.com/NeilRuffell/bb-music.git
```

If already cloned:

```bash
git submodule update --init --recursive
```

The consumer's Gradle settings include the shared Android module from
`assets/bbos6/android/bbos6-ui`.

**Access:** This is an owner-maintained private repository. Use your authenticated
GitHub account when initializing its submodule. Public distribution is not
part of the current setup.

## Updating shared assets or UI

1. Read [`AGENTS.md`](AGENTS.md), search
   [`icon-index.csv`](icon-index.csv) and verify any new BBOS6 mapping.
2. Commit the original asset/index/shared-code changes in **this repo** on a
   feature branch.
3. Run `python3 tools/validate_icon_index.py`; compile shared Java changes
   using a consuming application's Gradle build.
4. Review and merge the shared-repository PR.
5. In each affected consumer, update the pinned submodule commit and review
   the corresponding consumer PR:

```bash
cd ~/Projects/bb-music
git switch master && git pull --ff-only
git -C assets/bbos6 fetch origin
git -C assets/bbos6 switch --detach origin/master
git add assets/bbos6
git commit -m "Update canonical BBOS6 shared dependency"
git push origin master
```

The above consumer commands are suitable only when updating an approved
branch; use a feature branch/PR when changes have not yet been approved.

A submodule update **does not happen automatically** when shared `master`
moves. Do not clone the index or original asset archive into consumers.
