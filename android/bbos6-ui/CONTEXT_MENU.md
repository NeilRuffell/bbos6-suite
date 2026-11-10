# BBOS6-style context/action grid

`Bbos6ContextMenu` is a **standalone native Android Java helper** in
`shared/android/bbos6-ui/`. It renders the BlackBerry OS 6-style popup shown
in the supplied 551 × 564 screenshot (Reply / Forward / Reply To All,
Mark Unopened / Open / Flag for Follow Up, File / Delete / Full Menu).

It is **not** a copy of the BlackBerry Java SDK classes. The reference
image determines the visual appearance; exact original theme constants
and popup sizing behavior are not known from the screenshot.

## Presentation

| Detail | Implementation |
| --- | --- |
| Panel | Slate-blue vertical shading, top approximately `#445D79`, bottom `#3A4D61` |
| Border | Thin, light-gray upper rim fading to darker side/bottom rim |
| Corners | 8dp rounded outer perimeter, using native clip-to-outline |
| Grid | Up to 3 equal columns by default; automatically narrows to one for a single action and expands as groups grow |
| Groups | Thin 2dp gray/dark separator between groups, inset horizontally |
| Selection | Blue vertical gradient (`#138FE4` to `#0750A8`) with 7dp rounded corners and subtle rim |
| Icons | Caller-provided drawables, untinted, centered above labels |
| Text | White, centered, 15sp, max two lines with ellipsis |
| Tile shape | Inset from row edges; square-ish cell proportions |
| Width | 98% of available viewport width, capped at 560dp |
| Height | Up to 94% of usable viewport height; native scrolling when required |
| Window | Native centered, titleless `Dialog`, no extra dim layer |
| Dismiss | Back, Escape or touch outside, without changing underlying selection |
| Hardware | Up/Down/Left/Right across enabled grid cells; Enter/trackpad click activates on key-up |
| Touch | Tap an enabled tile to activate, pressed-state highlighting |
| Callback | Delivers the selected `Bbos6ContextMenuItem` to the caller |

The layout reduces row height where practical to keep all three screenshot
rows visible on a square Q25 screen. It does not squeeze tiles below 94dp;
larger sets of actions instead scroll within the panel. For a menu with
only one action, the panel occupies one centered icon column, rather than
showing two empty columns; the configured column count is an upper bound.

**No icons, labels, menu commands, media controls or app-specific actions are
hardcoded** into the helper. The screenshot's nine commands are examples of
content that a calling app could supply, not fixed menu items.

## Usage

Example from any native Android `Activity` using its own resources:

```java
import dev.q25.bbos6.ui.Bbos6ContextMenu;
import dev.q25.bbos6.ui.Bbos6ContextMenuItem;

Bbos6ContextMenu.builder()
    .addGroup(
        Bbos6ContextMenuItem.resource("open", "Open",
            android.R.drawable.ic_menu_view),
        Bbos6ContextMenuItem.resource("details", "Properties",
            android.R.drawable.ic_menu_info_details),
        Bbos6ContextMenuItem.resource("edit", "Edit",
            android.R.drawable.ic_menu_edit))
    .addGroup(
        Bbos6ContextMenuItem.resource("delete", "Delete",
            android.R.drawable.ic_menu_delete))
    .initiallySelected("open")
    .onAction(item -> {
        switch (item.id()) {
            case "open":
                openSelectedItem();
                break;
            case "details":
                showProperties();
                break;
            case "edit":
                editSelectedItem();
                break;
            case "delete":
                confirmDeletion();
                break;
        }
    })
    .show(this);
```

The `openSelectedItem()` and similar methods in this example belong to
the **calling application**; the shared helper only returns action IDs.

The builder offers:

- `addGroup(items...)` or `addGroup(List<items>)`: one visual action group;
  a separator is inserted automatically before the next group
- `columns(int)`: defaults to 3; accepts 1–6
- `initiallySelected(String)`: choose an initial highlighted action by ID,
  falling back to the first enabled action
- `dismissOnAction(boolean)`: defaults to true
- `onAction(OnActionListener)`: required action callback
- `show(Activity)`: creates and displays the native dialog; returns `Dialog`

Icon sources are caller-owned via
`Bbos6ContextMenuItem.resource(id, label, R.drawable.icon)`,
`Bbos6ContextMenuItem.drawable(id, label, drawable)`, or a text-only
`Bbos6ContextMenuItem.text(id, label)`. `withEnabled(false)` keeps an
action visible but unselectable. Action IDs must be unique across groups.

Resource IDs resolve in the host application's merged Android resources.
Images are not tinted or replaced by helper-generated BlackBerry artwork.

## Separation from application behavior

This module only renders a menu when a caller explicitly opens it.
It does not register long-press listeners on music rows, modify playback,
intercept global system keys, depend on Vector/SystemUI, or choose which
actions are present. Future apps can reuse the same layout by supplying
different groups and icon resources.

The existing `Bbos6PropertiesPopup` in this library remains a separate
read-only metadata panel. A context-menu action can open it explicitly.

## Tests

Pure-Java unit tests cover grouped grid movement, partial rows, edge
clamping, disabled cells and action-item validation.

```bash
cd ~/Projects/bb-music
git submodule update --init --recursive
gradle :bbos6-ui:testDebugUnitTest :app:assembleRelease
```

Compilation and on-device Q25 appearance/interaction must be verified before
the branch is merged or any APK is published.
