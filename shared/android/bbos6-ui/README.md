# Reusable BBOS6 Properties popup (Android)

A standalone, pure-Java/native-Android UI library. No Media3, storage,
music metadata, BB UI Vector/LSPosed or third-party dependency.

It is currently consumed by the independent `music-app/` Gradle build; other
Android applications may include the module from their own `settings.gradle`
and add an `implementation project(':bbos6-ui')` dependency.

## Component

- `dev.q25.bbos6.ui.Bbos6PropertiesPopup` — a native `Dialog` and builder.
- `dev.q25.bbos6.ui.Bbos6PropertiesItem` — immutable data for one text,
  inline-property, right-aligned-property or vertical-spacing row.

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

The Music app connects this helper only for long presses on playable
track rows in its library browser. `MusicTrackPropertiesReader` supplies
SAF and indexed metadata; the shared library never queries files or handles
music navigation. Other applications can supply any property rows through
the same builder without copying UI code.

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
