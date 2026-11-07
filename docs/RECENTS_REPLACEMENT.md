# BBOS6 Recents replacement — one native owner

## Decision and hard constraint

Replace the old portrait two-column Quickstep task-card layout with a BlackBerry OS 6-style grid of **application icons**. Continue to use Android's actual task and activity management, and the Q25's existing Launcher3/Quickstep overview entry point. The Vector/LSPosed APK is the current integration vehicle; a native ROM implementation is the target.

**One Recents presentation at a time.** Do not add a second Activity, floating overlay, custom task backend, hidden stock Recents UI, or duplicate Quickstep view hierarchy. A hidden/invisible/intercepted stock renderer beneath BBUI is unacceptable. If the required Quickstep host cannot be adapted to mount exactly one renderer under Vector, stop and document that limitation rather than shipping a second UI.

## Current baseline — stock restored

Branch: `feat/bbos6-recents-replacement`, based on `master`.

- Deleted `app/src/main/java/dev/q25/trackpad/NativeRecentsGridHook.java` rather than disabling it behind `bb_ui_vertical_recents`.
- Removed its `app/src/main/assets/xposed_init` entry, so BBUI no longer installs any old Recents grid hooks.
- Removed the Launcher3-to-SystemUI `RECENTS_VISIBILITY` broadcast and stale status-bar appearance state; these existed solely for the retired Recents grid.
- Preserved the SystemUI BlackBerry-key popup and changed `showNativeRecents()` to forward the existing `CommandQueue.toggleRecentApps()` command with a thread-local bypass of our BB-key interception. The native command is posted to the main looper **after** the menu's input handler returns; no timed workaround is used. The menu is **not** a Recents renderer. On-device confirmation is pending.
- The active Recents presentation is now stock Quickstep **only**. This is a transitional baseline, not the final BBOS6 design.
- The deprecated global setting `bb_ui_vertical_recents` is obsolete and can be deleted on the device.

## Confirmed source-level integration boundary

The retired hook targeted these Q25 Quickstep classes:

- Process/package: `com.android.launcher3`
- `com.android.quickstep.views.RecentsView`
- `com.android.quickstep.views.TaskView`
- `com.android.launcher3.PagedView`
- `com.android.quickstep.orientation.RecentsPagedOrientationHandler`

Those references verify which implementation the old code targeted, **not** that a replacement renderer has been tested on the current device build. Inspect installed Quickstep signatures and native layout ownership before implementing hooks.

SystemUI receives the BlackBerry-key Recents command in `PullDownHook.installRecentsInterception()`. Its popup's Switch Application action forwards the **same** native `toggleRecentApps()` command without recursively opening the BB menu. The CommandQueue owner is referenced from its constructor as well as native toggle callbacks. Keep this entry routing distinct from Quickstep's visual renderer. Do not add a competing Recents command path.

## Native Recents dispatch investigation (0.9.98)

The Q25 logs supplied after the earlier 0.9.97 build show the BlackBerry menu opened and `Switch Application forwarded to native Quickstep toggle` logged. This only proves `CommandQueue.toggleRecentApps()` returned; Android enqueues this event for asynchronous delivery. No real Quickstep transition is verified by that log. There is also a `Native Recents command queue unavailable` warning from a separate class-loader entry, alongside successful registration on the first entry, so do not mistake the warning for proof that the working hook is absent.

The 0.9.98 investigation:
- Posts the **same native** Quickstep toggle after the popup Enter key dispatch has returned, avoiding an in-progress overlay/window transition.
- Adds read-only hooks on native `Recents.toggleRecentApps()` and `OverviewProxyRecentsImpl.toggleRecentApps()` when those classes exist, logging whether dispatch reaches the callback.
- For `OverviewProxyRecentsImpl`, reads its existing `OverviewProxyService.getProxy()` connection, logging `Native Quickstep proxy connected=true/false`. Does not construct a new service, connection, launcher or Recents view.
- Keeps stock Quickstep as the exclusive Recents presentation. The result must be tested on Q25.

Interpretation after pressing **Switch Application**:
- `Switch Application native toggle queued` only: native CommandQueue message queued; callback dispatch or Quickstep connection is not yet demonstrated.
- `Native Recents callback reached`: downstream SystemUI callback actually received the request.
- `Native Quickstep proxy connected=false`: native SystemUI has no connected Overview proxy at callback time; investigate Launcher3/TouchInteractionService or native user/setup state rather than adding a second Recents implementation.
- `... connected=true` but no Overview: inspect Quickstep task/gesture state and Launcher3 exceptions. Do not claim the routing works without visible Q25 verification.

Focused log:
```sh
adb -s 202512003630 logcat -d -s 'BB7-PullDown:V' 'OverviewProxyRecentsImpl:V' 'OverviewProxyService:V' '*:S' | tail -100
```

## Proposed replacement architecture

| Layer | Sole owner | Future BBUI role |
| --- | --- | --- |
| Tasks and process state | Android ActivityTaskManager / platform task services | Read the existing task records; do not keep a competing task store |
| Overview lifecycle, launch/dismiss and transitions | Native Launcher3/Quickstep | Reuse existing state transitions and task actions |
| Overview presentation | One active Quickstep-hosted BBOS6 icon-grid renderer | Display real task application icons and labels, selected/focused state, and trackpad-compatible layout |
| BlackBerry key menu | Existing `ShadeUiController` and `PullDownHook` | Forward Switch Application to the same native Overview entry point |
| Vector | Temporary process-specific Xposed adapter | Hook the actual `com.android.launcher3` UI owner without adding a window |
| Future ROM | Direct Quickstep/Launcher3 source integration | Move the renderer and input policy into ROM; remove Xposed adapter |

The presentation model and keyboard/trackpad policy should not import Xposed types. Use a minimal Q25-specific Vector bridge for class/lifecycle adaptation.

## Native scrim visibility diagnostic (0.9.114; Q25 inspection pending)

Q25 logs for 0.9.113 confirmed two things:
`Native Overview background hooks: depth=true scrim=true nativeScrimView=true`,
and `SCRIM-APPLIED native Launcher3 ScrimView background=#26000000`.
The wallpaper remained visually untinted, however. **Those logs only
prove the colour was assigned to a Java view, not that the view
contributed any visible pixels.**

The next build makes *no visual or functional corrections*. It logs:
- `SCRIM-DRAW` when the native ScrimView actually receives `onDraw`,
  with native Canvas clipping.
- `SCRIM-STATE` 1.2 seconds after opening Recents: actual
  background ColorDrawable colour, view alpha, visibility,
  `isShown`, `getGlobalVisibleRect`, size, draw count and position
  relative to RecentsView.
- `SCRIM-ABOVE` and `SCRIM-PARENT` to reveal native siblings,
  potential occlusion and parent-level opacity.

This instrumentation distinguishes a colour-assignment success
from a visibility/opacity/compositor failure. It does not add any
overlays, change the tint, restore stock cards, modify task launching,
or compensate with an extra paint operation.

Branch: `diag/recents-native-scrim-visibility`, version 0.9.114.
Do not merge before the Q25 log shows the actual compositing blocker.
No CI.

## Correct background ownership: Launcher3 native ScrimView (0.9.113; Q25 verification pending)

Q25 evidence: the 0.9.112 build removed the default blue/blurred Overview
effect, but the wallpaper remained apparently completely unobscured even
though BBUI was still drawing `0x26000000` in
`RecentsView.draw(Canvas)`. This establishes that removing the native
depth/scrim was insufficient to display the requested tint.

**Root cause and verified platform architecture**

The lineage-24.0 Launcher3 source confirms that the native launcher
`res/layout/launcher.xml` stacks the native full-screen
`com.android.launcher3.views.ScrimView` above the workspace and
*below* the Quickstep `overview_panel` (RecentsView).
`WorkspaceStateTransitionAnimation.setScrim()` obtains the target
`LauncherState.getWorkspaceScrimColor()` and passes those values to
`ScrimView.setScrimColors()`. `ScrimView` applies the background using
its `setBackgroundColor()` and a native `ColorDrawable`.

Prior 0.9.112 hooked `OverviewState.getWorkspaceScrimColor()` to
**transparent** instead of using that proper scrim. The remaining
`RecentsView.draw(Canvas)` paint was confined to the *icon renderer*
and was not the authoritative background tint. Leaving that native
scrim transparent accounted for the user's uncolored wallpaper.

**Fix**

- Keep the Q25-requested black with **85% transparency** exactly:
  `0x26000000` (~15% opacity, alpha 38/255).
- Have `OverviewState.getWorkspaceScrimColor()` return a
  `ScrimColors(background=0x26000000, foreground=TRANSPARENT)`.
  Launcher3's *existing* scrim handles positioning, compositing, and
  state transitions. No new overlay or auxiliary window.
- Continue returning `0f` from `OverviewState.getDepthUnchecked()`
  while BBUI mode is enabled to avoid the original native blur.
- Remove `canvas.drawColor(...)` from the exclusive RecentsView painter.
  It now draws **only** the BBUI icon grid, focus, heading, and divider.
- Add a read-only native `ScrimView.setScrimColors` observation.
  The log `BBUI-RecentsIcons: SCRIM-APPLIED native Launcher3 ScrimView
  background=#26000000` confirms the native background actually
  received the intended color on the device.
- Both state hooks revert to original Launcher3 behavior when
  `debug.bbui.recents.icons=false`; no stock Recents cards have been
  restored.

Source-level layout and compositing architecture confirmed. The effect
still requires actual Q25 installation and visual verification.
Branch: `fix/recents-native-scrim-tint`. Never use CI.

## Native blur/scrim conflicts with requested black tint (0.9.112; Q25 verification pending)

Issue: the translucent black painter (85% transparency / alpha 38)
did not visually replace Launcher3's blurred Overview background.
Inspection of lineage-24.0 Launcher3 source confirmed two separate
native effects: `OverviewState.getDepthUnchecked()` returns 70%
depth by default, and `OverviewState.getWorkspaceScrimColor()`
provides the themed `overviewScrimColor`. BBUI's prior
`canvas.drawColor(0x26000000)` only painted a weak black tint;
it did not disable either native background effect.

This version adds two Xposed method hooks to the existing Launcher3
module, active **only** while
`debug.bbui.recents.icons=true`:

- `OverviewState.getDepthUnchecked` returns `0f`, avoiding native
  Overview depth/blur.
- `OverviewState.getWorkspaceScrimColor` returns transparent
  background/foreground scrim values, avoiding native color overlays.
- When BBUI mode is off, both hooks call the original Launcher3 code.

The single BBUI `RecentsView.draw(Canvas)` painter remains unchanged
and continues drawing `0x26000000` over what is visible underneath.
No new background View, overlay, activity, card renderer, or duplicate
task manager is created. The compact grid, centered heading, native
task identity mapping, and directional paging are unchanged.

**Note:** 85% transparency means only ~15% black opacity; the tint is
intentionally subtle. Removing native blur makes the source below more
visible, not darker than the specified alpha. This must be verified
on the Q25 before merging.

Branch: `fix/recents-native-blur-black-tint`. Do not run CI.

## Shared selected-application heading (0.9.111; Q25 verification pending)

Match the attached BlackBerry OS 6 screenshot: display the currently
selected application's name **once**, horizontally centered above the
compact 4x2 icon grid, with a thin gray horizontal separator between
the heading and the grid. No labels are drawn below individual icons.

The heading reads the exact same native task identity and package-manager
application label used for Recents selection/launch diagnostics. The
single BBUI Canvas painter draws the header after its icon pass; no
separate Android View, new UI layer, activity or stock card is added.
The separator spans the full compact grid width and is always centered
on that grid. The label ellipsizes to fit the grid's width.

The layout preserves icon sizing, viewport centering, 8 tasks per page,
touch hit-testing, horizontal page switching, the native canvas scroll
correction, and the 85%-transparent-black background. Unit tests check
that heading center and divider width align across pages.

Test branch: `feat/recents-selected-app-header`. Do not merge until
verified on the Q25. No CI.

## Compact four-by-two grid (0.9.110; Q25 verification pending)

The user requested a much tighter grid without changing the icon size.
Reduce each icon's surrounding cell to **82dp** and inter-cell spacing to
**6dp** (from the previous near-full-width grid with 8dp spacing).
Center the complete four-column block horizontally and retain vertical
centering of the complete two-row block. The 55dp normal icon renderer
and selected-app labels retain their existing sizing.

At the Q25's logged 720px viewport and 1.4 density, the grid occupies
approximately 484px of horizontal space rather than almost the full
720px, and approximately 238px of vertical space rather than 340+px.

The shared layout's x/y coordinates feed both the painter and tap
hit-testing. Touch swipes, keyboard paging, current task identity, the
transparent black background and existing native Canvas scroll
cancellation are unchanged. Added tests for compact bounds and
consistent positioning across page 1/2/3.

The change is isolated on `feat/recents-compact-grid` for device
verification; no CI.

## Translucent black Recents background (0.9.109; Q25 verification pending)

Replaced the painter's opaque gray `Color.rgb(28,32,39)` background
with translucent black `0x26000000`: alpha 38/255, approximately
15% opacity (85% transparency). All icons, selected labels, focus
indicators, and horizontal page navigation remain unchanged.

This is paint-layer transparency, not a change to Quickstep's window
translucency or its underlying scene. What appears behind the black
tint depends on the existing Launcher3 drawing stack. Verify actual
visual output on the physical Q25 before merging.

## Vertically centered Recents grid (0.9.108; Q25 verification pending)

The 4-column x 2-row grid now centers **its entire two-row cell block**
vertically inside the visible Quickstep RecentsView viewport. Previously,
the grid began at a fixed 18dp top inset. The shared
`RecentsIconGridLayout.calculate()` now computes
`gridHeight = ROWS * cellHeight + (ROWS - 1) * spacing` and
`top = max(reservedTopInset, (viewportHeight - gridHeight) / 2)`.
The reserved top inset remains a lower bound for very short viewports.

This affects only vertical cell coordinates, so icon sizes, label rendering,
hit testing, horizontal page selection, and task identity continue using
the same shared geometry. Every page stays at the same vertical position.
Unit tests cover viewport centering and alignment across multiple pages.

Developed on `feat/recents-vertical-centering`; verify on Q25 before
merging to master. Run Gradle tests locally; do not use CI.

## Left/Right page changes (0.9.107; Q25 verification pending)

The 4x2 icon grid already allowed horizontal touch swipes; however, the
shared keyboard layout policy `RecentsIconGridLayout.nextIndex()` crossed
from the lower row of one page to the upper row of the next page on
**Down**, while **Right** stopped at the fourth column. This caused
vertical-direction page navigation despite horizontal visual paging.

Corrected directional selection in the existing BBUI-owned renderer:

- Up/Down changes rows **within the current page only**.
- Right from the fourth column advances to the **next page**. It prefers
  the same row and falls back to the first icon if that row is absent
  from the next (partially filled) page.
- Left from the first column returns to the **previous page**, preserving
  the row.
- Horizontal touch swipes and Page Up/Page Down remain unchanged.
- Still one exclusive BBUI renderer, eight task icons per page, with the
  Q25-verified scroll-offset correction and app/task identity fix intact.
- Unit tests cover both page-edge directions, partial last pages,
  preventing vertical page crossings, and end-of-list boundaries.

Implemented on `feat/recents-horizontal-page-navigation`. The previous
`0.9.106` commit has already fast-forwarded into `master`. This
navigation update should be tested on the Q25 before another merge.
Build/test with local Gradle; no CI.

## Wrong application opens — source analysis and diagnostic fix (0.9.106)

Issue reported on Q25 **before** the 4x2 change: selected icon sometimes
does not match the application Android launches.

**Evidence:**
- Earlier 0.9.102 on-device logs confirmed Quickstep's
  `IconAppChipView.getDrawable()` returned `null` for seven native TaskViews;
  BBUI successfully retrieved fallback installed-package icons instead.
- BBUI previously cached those fallbacks using a `WeakHashMap<View,Drawable>`
  indexed by the **Quickstep TaskView instance**, not by its underlying task.
- The native Quickstep `TaskView` is a recyclable `ViewPool.Reusable`.
  `RecentsView` recycles those native views as tasks are removed/reloaded.
  A recycled view can point at a different native Task while retaining a
  prior cached BBUI icon. This is a proven source-level stale-identity defect
  and a plausible explanation for the mismatch.
- Previous log captures had no app-selection/task-launch ID tracing, so the
  full reported symptom has **not yet been independently proven on-device**.

**Correction and diagnostics:**
- Cache fallback icons by `packageName|userId` derived from the **currently
  bound TaskContainer.task.key.baseIntent**; never index by TaskView.
- Display the selected application's package-manager label **beneath the
  selected icon only**, derived from that same bound task identity.
- On selection changes, log `BBUI-RecentsIcons: SELECT index=... page=...
  taskId=... package=... component=... label=...`.
- On touch or keyboard launch, log `LAUNCH-REQUEST` with the *same* native
  TaskView's first TaskContainer task ID and package; log
  `LAUNCH-RESULT success=...` when native Quickstep confirms its result.
- No new app launch intent, recents model, visual card, overlay or secondary
  UI. Native `TaskView.launchWithoutAnimation` continues to own task
  activation.
- This diagnostic fix is on `feat/recents-4x2-pages`. Local Gradle/ADB
  install is required; no CI. Don't merge until user confirms which
  application launches and whether names match.

For an incorrect launch, immediately collect:

```sh
adb -s 202512003630 logcat -d -s 'BBUI-RecentsIcons:I' '*:S' \
  | grep -E 'SELECT|LAUNCH-REQUEST|LAUNCH-RESULT|LAUNCH-FALLBACK'
```

## Eight icons per page (0.9.105; pending Q25 verification)

The BBUI-owned Recents grid is now **4 columns × 2 rows**, giving exactly
**8 task icons per page**. Task 9 starts page 2; task 17 starts page 3.

- The exclusive BBUI painter and the Q25-verified native carousel canvas
  scroll cancellation from 0.9.104 are unchanged.
- Page selection, tap hit-testing, left/right touch swipes and Page Up/Down
  already derive page capacity from `RecentsIconGridLayout.PAGE_SIZE`; the
  shared constant now evaluates to 8. Hardware arrow keys keep using
  the same shared four-column grid navigation.
- No stock Recents thumbnails, task cards, or secondary UI are introduced.
- Regression unit tests cover 8, 9 and 17 tasks, page offsets,
  directional selection, and empty or too-small viewports.
- This revision is staged on `feat/recents-4x2-pages` until physically
  confirmed on the Q25. Build and install locally; no CI.

## Q25 verified blank-screen cause and fix (0.9.104)

Q25 log from 2026-11-07 13:42:42 showed the BBUI painter loaded all
8 native task icon Drawables, yet its own grid was invisible. The
`LauncherRecentsView` had `scrollX=3850` while Android supplied a
Canvas matrix with X translation `-3850` and clipping interval
`[3850, 4570]`. BBUI painted at viewport coordinates `[0, 720]`;
all icon pixels were outside the Canvas clipping region. This conclusively
identified a **native Quickstep horizontal-pager transform leak**, not a
missing icon, Vector hook, transparent view, or extra overlay.

Correction: within the exclusive `RecentsView.draw(Canvas)` hook,
`canvas.save()`, `canvas.translate(root.getScrollX(), root.getScrollY())`,
`paintGrid(root, canvas)`, and `canvas.restoreToCount(...)`.
This cancels Quickstep's native pager translation **only** while drawing the
BBUI-owned 4x4 viewport grid. It does not alter task binding, lifecycle,
navigation state, click actions, or underlying Android system services.

The patch is committed but **the corrected drawing requires physical Q25
verification**. Do not reintroduce Quickstep cards to fix viewport painting.

## Exclusive BBUI Recents presentation (0.9.101)

Root cause: the screenshot of 0.9.99 shows stock Quickstep card
geometry, thumbnail empty slots and pill-shaped app-chip headers. The 0.9.100
attempt changed native TaskView drawing but still kept Quickstep's layout
and interaction model. That does **not** meet the user's requirement.

**Current architecture** (branch `feat/bbos6-recents-replacement`):

- **One authoritative native host:** Quickstep `RecentsView`, which remains
  responsible for task enumeration, task identity, native launch and task
  lifecycle. No new Activity, overlay window or second task list is created.
- **One exclusive presentation:** intercept `RecentsView.draw(Canvas)`
  and paint BBUI's **4x2 application icon grid** to the host canvas.
  The stock `RecentsView.draw`, `dispatchDraw`, **all native TaskView
  drawing**, thumbnail widgets, header chips, previews, labels, stock
  focus visuals and live-tile drawing are **not invoked**.
- Native TaskView objects are retained as *headless data/action endpoints*
  because Quickstep itself owns their task binding. They do **not** render
  in this mode; this is not two concurrent renderers. A future ROM port
  can remove their UI inflation completely while keeping the task model.
- Remove separate stock Overview action and empty-message sibling views
  from the active window rather than simply setting their alpha to zero.
- BBUI owns *all* grid hit-testing, per-page selection, touch-swipe paging,
  DPAD navigation and blue icon highlight. It does not invoke Quickstep
  carousel snapping, tile layout, gesture recognition or task-card focus.
- Enter/trackpad click/tap invokes the **existing native Quickstep task
  object**, preferably through its non-animated native task launch API.
  Back still follows Android's native Overview exit behavior.
- No app labels or boxes: only the actual native app icon Drawables and
  a compact blue focus indicator on a solid BBUI dark background.
- The flag `debug.bbui.recents.icons=true` remains opt-in until Q25
  confirmation. **Reboot** to return to stock behavior after testing.
  The old `NativeRecentsGridHook` has been deleted.
- CI remains disabled on this branch (workflow is manual-only), all commits
  use `[skip ci]`, and testing uses **local Gradle and ADB only**.

**Validation status:** implementation committed, **not yet confirmed
on-device**. If the original cards are visible again, inspect the hook
registration and Q25 Quickstep class/method signature; do not add another
overlay or preserve stock card layout.

### Device test (single consolidated check)

```sh
cd ~/Projects/bb-ui
git fetch origin
git switch feat/bbos6-recents-replacement
git pull --ff-only
gradle :app:assembleDebug :app:testDebugUnitTest && \
adb -s 202512003630 install -r app/build/outputs/apk/debug/app-debug.apk && \
adb -s 202512003630 reboot
```

After Android has rebooted and Launcher3/Vector is loaded:

```sh
adb -s 202512003630 shell setprop debug.bbui.recents.icons true
```

BlackBerry button → Switch Application should use the **same** native Quickstep Overview, now presented as an icon grid. Check multiple tasks, arrows/trackpad, Enter, Back, touch launch, Home, and reopen. The first result can indicate that Q25 Quickstep layout or icons differ from the upstream source; do not merge without validation.

If stock Recents appears instead of icons or layout is abnormal, gather *only*:

```sh
adb -s 202512003630 logcat -d -s 'BBUI-RecentsIcons:V' '*:S' | tail -80
```

Rollback: `adb -s 202512003630 reboot` resets the temporary property, returning to stock Quickstep. This reboot also reconstructs any styled native TaskViews; setting the property false without a restart is not a complete view-teardown mechanism.

## Next implementation checkpoint

1. Inspect the actual installed Q25 Quickstep layout and task APIs. Determine where native Recents renders cards, handles focus and opens/dismisses tasks. Confirm whether a true single-renderer replacement is possible from Vector.
2. Build an icon-grid view **inside the existing native Overview host**, replacing the stock presentation, not covering it. Preserve existing Quickstep lifecycle, task actions, accessibility and system gestures. Never retain two renderer trees.
3. Map directional keys/trackpad to grid selection, Enter to native task opening and Back to the native Overview exit. Confirm BBOS6-specific sizing and selection visuals against verified references.
4. Test with real tasks, empty overview, running foreground app, dismissal, rotation, sleep/wake, gesture entry, Home, Back and repeated opening. Check no blank/duplicate surfaces, crashes, stale views or unreachable tasks.
5. After Q25 verification, remove obsolete adaptation code and document the direct LineageOS ROM integration points.

No icon resource identity or exact grid geometry is asserted at this stage; first verify the BBOS6 reference. Any original numbered icon used must be indexed in `assets/bbos6/icon-index.csv` under the repository-wide policy.

## Build and first on-device baseline test

```sh
cd ~/Projects/bb-ui
git fetch origin
git switch feat/bbos6-recents-replacement
git pull --ff-only
gradle :app:assembleDebug :app:testDebugUnitTest && \
adb -s 202512003630 install -r app/build/outputs/apk/debug/app-debug.apk && \
adb -s 202512003630 shell settings delete global bb_ui_vertical_recents && \
adb -s 202512003630 reboot
```

After boot, press the BlackBerry key, select **Switch Application**, and press Enter. Verify that Quickstep's *stock* Recents appears (no other task switcher or hidden view), selecting a task opens that same Android task, and Back/Home close Overview normally. Also reopen the BB popup to verify its Notifications and Quick Settings actions remain functional. If Overview still does not open, capture `adb -s 202512003630 logcat -d -s 'BB7-PullDown:I' 'BB7-PullDown:E' '*:S' | tail -100` and inspect the native Quickstep service state. The native menu action is only source-verified pending device test. `master` remains unchanged.
