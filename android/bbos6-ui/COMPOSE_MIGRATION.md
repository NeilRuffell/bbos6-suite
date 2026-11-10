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
