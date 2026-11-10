# Reusable BBOS6 native Android popup helpers

A standalone, pure-Java/native-Android UI library. No Media3, storage,
music metadata, BB UI Vector/LSPosed or third-party dependency.

It is currently consumed by the independent `music-app/` Gradle build; other
Android applications may include the module from their own `settings.gradle`
and add an `implementation project(':bbos6-ui')` dependency.

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
project(':bbos6-ui').projectDir = file('../shared/android/bbos6-ui')
```

Adjust the project-relative path as needed. In the application's module:

```gradle
dependencies {
    implementation project(':bbos6-ui')
}
```

The Music app opens `Bbos6ContextMenu` on long presses of playable
track rows with one action, **Properties**. The action opens the existing
`Bbos6PropertiesPopup`, with `MusicTrackPropertiesReader` supplying SAF and
indexed metadata. Neither shared widget queries files or handles Music
navigation. Other applications can supply any context actions and property
rows through their respective builders without copying UI code.

## Local verification

From the BB UI repository root:

```bash
gradle -p music-app :bbos6-ui:testDebugUnitTest :app:assembleRelease
adb -s 202512003630 install -r music-app/app/build/outputs/apk/release/app-release.apk
```

The model tests verify value preservation and input validation. The popup
has not yet been wired into Music actions, so visual testing requires a
caller. Follow the repository's existing tested-APK/approval workflow before
publishing; do not replace the existing Android signing identity.
