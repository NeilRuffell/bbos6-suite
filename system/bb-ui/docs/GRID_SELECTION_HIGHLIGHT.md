# Shared BBUI grid-selection highlight

The common, reusable Canvas-based selection painter is
`app/src/main/java/dev/q25/trackpad/GridSelectionHighlight.java`.

## Appearance

- Gradient top: `#088EEF` (ARGB `0xFF088EEF`).
- Gradient bottom: `#0851B5` (ARGB `0xFF0851B5`).
- Corner radius: approximately **6dp**, scaled by the caller's Android
  display density. This is a visual approximation of the supplied
  BlackBerry OS 6 raster screenshot, which has no physical dp scale.
- Fill only: no imposed border, icon size, text label or focus policy.
  The caller retains responsibility for selection and layout.

## API

```java
GridSelectionHighlight.draw(canvas,
        leftPx, topPx, rightPx, bottomPx,
        view.getResources().getDisplayMetrics().density);
```

Bounds are in the caller's current Canvas coordinate system, and the
helper draws only the rounded gradient within them. Invalid/empty
bounds are ignored. The corner radius is clamped for small targets.

This class must remain **presentation-only** and have no dependency
on Recents, application identities, task launching or page navigation.
It can be reused by other BBUI grid components without copying colour
constants or rounded-rectangle styling. An independent APK needs to
include or depend on the helper source before calling it.

## First integration

`QuickstepIconGridHook.paintGrid()` replaces its previous selected-icon
solid-blue paint and stroke with a single call to
`GridSelectionHighlight.draw()`. The existing 7dp icon padding and
selected item geometry are unchanged; all task hit-testing and
keyboard/touch navigation use the same layout policy as before.

Source branch: `feat/shared-grid-selection-gradient`.
Check on the Q25 before merging. No CI.
