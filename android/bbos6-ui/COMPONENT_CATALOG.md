# BBOS6 component catalogue — the reusable UI bucket

This is the canonical entry point for building a **new Kotlin + Jetpack
Compose Android application** with the project's shared BlackBerry OS 6
presentation. The source of truth is
[`android/bbos6-ui/`](./), maintained in **`bbos6-shared`**; a consumer
uses its pinned `assets/bbos6` Git submodule. Do not copy helper source
or original numbered assets into applications.

**State at shared `master` commit `8a056bd`:** the module contains
25 Kotlin source files (Compose surfaces, models, adapters and internal
navigation helpers) **and 11 Java source files** (legacy Views, dialog
APIs, models, styling/focus compatibility). It is a Kotlin + Compose /
native Android interoperability library, **not a Kotlin-only or
Compose-only library**. The listed Compose entry points exist in source;
that does not establish full device parity for every integration.

## Pick a component

Import the Compose APIs from `dev.q25.bbos6.ui.compose`. Items marked
*View host* are for a surrounding native Android View layout. Items
marked *legacy native* retain their Java implementation and can still be
called from Kotlin.

| Need | Shared API | What the caller still owns |
| --- | --- | --- |
| Section heading | `Bbos6SectionHeader(title)` | Page sections and whether rows are selectable |
| Empty content | `Bbos6EmptyState(heading, description, artwork)` | When empty, artwork selection and viewport sizing |
| Empty content inside a native layout | `Bbos6EmptyStateComposeView` (*View host*) | Placement outside the selectable list and visibility |
| Media/list row face | `Bbos6MediaListRow(...)` | List data, selection, scrolling, event routing and artwork loading |
| Native ListView + Compose row touch routing | `Bbos6MediaListRowTouchHost` (*View host*) | Adapter position, click/long click and scroll owner |
| Media/detail header | `Bbos6MediaDetailHeader(...)` | Content, artwork loading and navigation |
| Search panel face | `Bbos6SearchChrome()` | Input field, typing, filtering and results |
| Empty search feedback | `Bbos6SearchEmptyFeedback(message)` or `Bbos6SearchEmptyFeedbackView` | Show/hide and focus |
| Edit field | `Bbos6TextField(value, onValueChange, ...)` | Validation, input state and focus policy integration |
| Native editable field | `dev.q25.bbos6.ui.Bbos6TextField` (*legacy native*) | IME options, watchers, hint and input type |
| Context action grid | `Bbos6ContextMenuDialog` / `Bbos6ContextMenuGrid` | `Bbos6ContextAction` IDs, icons, actions |
| Context menu for native Activity | `dev.q25.bbos6.ui.Bbos6ContextMenu` (*legacy API with Compose bridge*) | Action IDs and callbacks |
| Confirmation / choice buttons | `Bbos6PopupButtonDialog` / `Bbos6PopupButtonPanel` | `Bbos6PopupAction` IDs, confirmation effects |
| Native dialog / text-entry popup | `dev.q25.bbos6.ui.Bbos6PopupButtonList` (*legacy native API*) | Input validation and callbacks |
| Read-only Properties | `Bbos6PropertiesDialog` / `Bbos6PropertiesPanel` | `Bbos6PropertyRow` content, metadata lookups |
| Properties from native Activity | `dev.q25.bbos6.ui.Bbos6PropertiesPopup` (*legacy API with Compose bridge*) | Row content |
| Metadata label | `Bbos6MetadataPlate(text, textSizeSp, highlighted)` | Track/selection state, activation |
| Now Playing chrome | `Bbos6NowPlayingSurface(panel)` / `Bbos6NowPlayingSurfaceView` | Media3, state, input and child controls |
| Now Playing labels | `Bbos6NowPlayingText(...)` | Queue position, clock formatting |
| Mode/transport focus | `Bbos6NowPlayingControlSelection(...)` / `Bbos6NowPlayingControlSelectionView` | Focus zone and actions |
| Seek progress rail | `Bbos6NowPlayingSeekRail(...)` / `Bbos6NowPlayingSeekRailView` | Native SeekBar, seeking and time |
| Cover art | `Bbos6NowPlayingArtwork(...)` | Painter, URI authorization, cache and placeholder |
| Blue selection and popup faces | `bbos6MediaListSelectionFace`, `bbos6ContextSelectionFace`, `bbos6PopupButtonFace` | Real focus owner and selected state |
| Managed native/Compose focus | `Bbos6FocusPolicy`, `Bbos6ManagedFocusEffect` | Which container owns DPAD and what is selected |

**Important:** A shared **row face is not a complete list widget**.
`bb-music` currently owns its own `MusicListView` data source,
native scrolling, selection restoration and Q25 trackpad policy. Do
not copy that implementation into future apps. A generic, verified
app-neutral list controller is a **separate extraction task**, to be
developed in this shared module.

## Set up a new application (one canonical dependency)

From the new Android repository's root:

```bash
git submodule add https://github.com/NeilRuffell/bbos6-shared.git assets/bbos6
git submodule update --init --recursive
# Pin the shared gitlink to the specific commit tested for this application.
git add .gitmodules assets/bbos6
```

In the root `settings.gradle`:

```gradle
include ':app'
include ':bbos6-ui'
project(':bbos6-ui').projectDir = file('assets/bbos6/android/bbos6-ui')
```

In the application module `build.gradle`:

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android' version '2.4.21'
    id 'org.jetbrains.kotlin.plugin.compose' version '2.4.21'
}
android {
    buildFeatures { compose true }
}
dependencies {
    implementation project(':bbos6-ui')
    // Configure your app's own Compose BOM and UI dependencies as usual.
}
```

The application still needs normal Android Gradle plugin/SDK configuration,
matching Compose and Kotlin dependencies, and its own manifest/activity.
The snippets above show **shared module wiring**, not a complete standalone
build script. See the working `bb-music` `settings.gradle`,
`app/build.gradle`, and `app/src/main` sources for full project setup;
the standalone module is not an APK to install by itself.

**Other existing repo:** `bb-ui` has the asset submodule but its
`master` `settings.gradle` only includes `:app`. Reusing
`bbos6-ui` there requires explicit Gradle integration, review and
device verification. Merely having the `assets/bbos6` directory is
not sufficient.

## Kotlin/Compose examples: choose, import, reuse

The imports below reference concrete API signatures in the shared
Compose package:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.q25.bbos6.ui.compose.Bbos6SectionHeader
import dev.q25.bbos6.ui.compose.Bbos6EmptyState

@Composable
fun EmptyResults() {
    Column {
        Bbos6SectionHeader(title = "Library")
        Bbos6EmptyState(
            heading = "No items",
            description = "Nothing to display",
            artwork = null
        )
    }
}
```

```kotlin
import androidx.compose.runtime.Composable
import dev.q25.bbos6.ui.compose.Bbos6PopupAction
import dev.q25.bbos6.ui.compose.Bbos6PopupButtonDialog

@Composable
fun ConfirmAction(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Bbos6PopupButtonDialog(
        actions = listOf(
            Bbos6PopupAction("cancel", "Cancel"),
            Bbos6PopupAction("confirm", "Confirm")
        ),
        onDismissRequest = onDismiss,
        onAction = { id, _ -> if (id == "confirm") onConfirm() },
        title = "Continue?",
        initialSelectionId = "cancel"
    )
}
```

```kotlin
import androidx.compose.runtime.Composable
import dev.q25.bbos6.ui.compose.Bbos6PropertiesDialog
import dev.q25.bbos6.ui.compose.Bbos6PropertyRow

@Composable
fun FileDetails(onDismiss: () -> Unit) {
    Bbos6PropertiesDialog(
        items = listOf(
            Bbos6PropertyRow.Text("Document"),
            Bbos6PropertyRow.Inline("Type:", "Text File")
        ),
        onDismissRequest = onDismiss
    )
}
```

Pure Compose `Bbos6TextField` uses a caller-owned
`androidx.compose.ui.text.input.TextFieldValue` and `onValueChange`.
The app owns data state, keyboard actions and input validation. For a
**Q25 text-entry button popup**, the existing
`Bbos6PopupButtonList` with native `Bbos6TextField` is the proven
focus/DPAD fallback; the mixed Compose text-entry bridge was rejected
after Q25 focus/navigation regression. Do not silently replace it.

## Reuse boundaries — what must NOT be copied

- **Shared:** BBOS6 visual geometry, original icon-index meanings,
  reusable menu/field/empty-state/row/popup visuals, keyboard/focus
  utilities when app-neutral. Modify these **only** here.
- **Music-only:** `MusicActivity`, `MusicListView`,
  `MusicLibraryGroupPolicy`, `MusicLibraryRowPolicy`,
  `MusicLibraryPageRows`, SAF scanners, playlists, Media3 and Room.
  A future app may have an analogous architecture, but these are not
  a second copy of the shared component bucket.
- **BB UI-only:** privileged SystemUI, notification shade and
  Vector/LSPosed integration. Do not migrate this into the shared
  UI library.
- **Original assets:** select known numbered icons through the
  authoritative `icon-index.csv`; preserve bytes and filenames.
  Unknown historical identifications must never be guessed.

When any generic behavior is missing, add it at the shared-helper
level **first**, test it with the Q25's touchscreen, physical keyboard,
trackpad and DPAD, then update the consumer's pinned submodule commit
in its own PR. Do not merge either PR without explicit approval.

## Known work before calling the bucket complete

1. **Java compatibility classes remain**: 11 in the shared UI module.
   Some Compose equivalents exist, but Java native focus/input fallbacks
   are still intentionally used for Q25 parity.
2. **Drop-in list/controller absent**: shared Compose media rows are
   presentation primitives; Music's native list/adapter/navigation
   policy is not yet an app-neutral helper.
3. **Mixed consumer adoption**: `bb-music` includes the shared
   Gradle module; `bb-ui` does not on `master`. Each app pins a
   separately tested shared gitlink, so library changes are not
   automatic across apps.
4. **Per-component integration tests and examples**: the shared
   module has Kotlin and Java unit tests, but an independent example
   app and complete Q25 parity suite for every Compose dialog/input
   path are not established by this repository audit.
5. **Documentation migration**: older README/migration notes contain
   historical statements like "pure Java" or "unmerged." Treat those
   as historical checkpoints; this catalogue describes the currently
   inspected `master` API inventory. Keep it updated with every
   new/retired exported component.

Related: [COMPOSE_MIGRATION.md](COMPOSE_MIGRATION.md),
[CONTEXT_MENU.md](CONTEXT_MENU.md),
[repository workflow](../../AGENTS.md).
