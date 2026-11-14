# BlackBerry-button application menu (bottom-left popup)

## Layout and interaction

The BB hardware key opens a transient, 224dp-wide vertical menu at the
bottom-left. The menu preserves the BB-style gradient, blue focus highlight,
48dp rows, and touch/trackpad input.

Visual order:

1. **Close Application** (shown only for an eligible foreground app)
2. **Switch Application** (opens the existing native Quickstep Recents UI)
3. **Notifications**
4. **Quick Settings**

Notifications is initially highlighted from Home/ordinary applications, to
retain the existing first-BB/Enter shortcut. When a shade is open, its current
page is initially highlighted. Up/Down moves by one visible row; Enter,
trackpad click or touch activates the highlighted row. Back or a second BB
press dismisses the menu. Left/Right do not select another row.

**Close Application** is never offered on Home, SystemUI or where SystemUI
lacks the privilege to remove a foreground task. The exact top task is
captured before the menu opens; it must still be foreground at activation.
The action asks the native ActivityTaskManager to remove that task. It does
not force-stop a package or remove other Recents entries. Failed operations
display a short error and are logged.

**Switch Application** reuses the system's native Quickstep Recents with its
existing BB UI grid presentation. The menu directly invokes the native
CommandQueue show-recents path (and only falls back to a guarded toggle),
preventing recursion into the BB-button interception hook. If a shade is
open, it is dismissed before switching.

While the BB-button popup is visible, the **real SystemUI status bar**
temporarily displays the current, locale-formatted date centered in place of
notification indicators. The regular App notification area and the separately
rendered Hub category strip are both suppressed visually, without detaching
notification views or changing notification data. The date uses Android's
native TextClock and updates for date/timezone changes. Clock, signal, battery,
and privacy indicators are untouched. Back, Home, screen-off, task switching,
shade navigation, and failed popup creation restore the original icon mode.
Status-bar reinflation while the popup is open rebinds the date to the current
native PhoneStatusBarView. No extra SystemUI window is created.

The BB key is the **only** way to open the popup. Trackpad Up from the first
notification icon, media row, or first unlocked Quick Settings row stays
within the shade. Native finger swipe-up dismiss remains unchanged.
Selecting Notifications or Quick Settings switches existing retained pages
without rebuilding the shade or replaying its opening animation.

## Ownership and deferred features

`ShadeStatusNavigation` owns focus and order; `ShadeUiController` owns
shade session state. `ShadeDateHeader` is the menu-only presentation;
`ShadeHeaderSegmentPolicy` handles hit testing with or without Close.
`ForegroundTaskActions` guards task removal. `PullDownHook` owns
system integration and popup lifetime. `NativeStatusBarMenuDate` owns
transient native status-bar date presentation; `NativeStatusBarHubIcons`
honors that temporary override before restoring the user's icon mode.

`UnifiedShadeHost` contains **no** duplicate header or popup. The separate
40dp media shuffle/repeat bar remains unchanged.

App-item context menus, app-specific Options/Help and BB long-press Recents
are **not** part of this iteration.

## Build and test on Q25

```sh
cd ~/Projects/bb-ui
git fetch origin
git switch feat/bb-application-actions-menu || \
  git switch --track origin/feat/bb-application-actions-menu
git pull --ff-only
gradle :app:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r app/build/outputs/apk/debug/app-debug.apk && \
adb -s 202512003630 shell am crash com.android.systemui
```

Test BB/Up/Enter on all four rows; check that Close disappears on Home,
closes the current foreground task but not a background task, and that
Switch opens BB UI's existing Recents. Also verify the original shade
navigation, media toolbar, finger drag, Back and screen-off cleanup.

Rollback without reinstall:

```sh
adb -s 202512003630 shell setprop debug.bb7pulldown.enabled false
adb -s 202512003630 shell am crash com.android.systemui
```

Cross-app task removal and native Recents dispatch still require device
validation; a successful build does not prove the firmware permits either.

## BB-button menu status-date validation (monorepo)

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/vector-bb-menu-status-date
git pull --ff-only origin feature/vector-bb-menu-status-date
gradle -p system/bb-ui :app:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r system/bb-ui/app/build/outputs/apk/debug/app-debug.apk && \
adb -s 202512003630 shell am crash com.android.systemui
```

Check both **By application** and **By notification type (Hub)** modes.
Open the BB-button popup: a live date replaces notification icons but
clock, connectivity, battery and privacy indicators stay visible.
Receive notifications while the popup remains open: icons must not appear.
Dismiss with Back, Home, screen-off, Close Application, Switch Application,
and by opening either shade page: the appropriate icons must return.
Also check a mode change while the popup is open, midnight/timezone changes,
and status-bar reinflation. Device-test results are required before merging.
