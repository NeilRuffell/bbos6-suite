# BB Messages Vector integration

Independent experimental Vector/LSPosed module for Google Messages on the
Zinwa Q25. The renderer reuses the canonical Kotlin/Compose BBOS6 library in
`../../shared/android/bbos6-ui/`.

## Q25 inspection findings (2026-11-14)

The target process and hook were verified on-device. Read-only inspection
reported 167 native View instances on
`com.google.android.apps.messaging.main.MainActivity`.

The inbox currently uses:

- `android.support.v7.widget.RecyclerView`, resource `android:id/list`
- `com.google.android.apps.messaging.home.list.ConversationListItemView`
- `conversation_name` and `conversation_snippet` native TextViews
- `conversation_timestamp_and_unread_badge` container (text child unverified)
- `messages_list_container` enclosing the inbox

These are version-specific observed classes and IDs, not a public stable API.

## Implementation: experimental opt-in

The default module mode is inspection only; **Google Messages remains intact**.

When `debug.bbmessages.inbox.enabled=true` before process start, the
target-specific hook mounts the canonical shared `Bbos6MediaListRow` visual
onto each verified native `ConversationListItemView` using a passive
`ViewGroupOverlay`. It reads the currently bound native sender/preview text,
and attempts a timestamp only if an actual timestamp TextView is present.
No message database or Google-internal adapter is accessed.

The native RecyclerView and original row retain the scrolling, click,
long-press, focus and app data lifecycle; the overlay is not an input target.
The row height is set to 52dp.

### Q25 inspection follow-up: missing Compose lifecycle (2026-11-14)

With `debug.bbmessages.inbox.enabled=true`, device logcat confirmed:

- `Inspection enabled for main Google Messages process`
- `BBOS6 inbox row rendering enabled for verified native list`
- Repeated `No compatible lifecycle owner; native row retained`

The installed Google Messages view tree does not expose lifecycle owners
compatible with the injected Compose library. A BB Messages-only
`InboxRowCompositionOwner` now supplies a short-lived AndroidX lifecycle,
saved-state registry and ViewModelStore **to the Compose overlay only**.
It is created when a visible inbox row is mounted and destroyed when that
RecyclerView row detaches, so recycling cannot retain a destroyed owner.
Native owners are reused when available; Google's own view tree is never
modified. The row bridge also avoids duplicate attachments from constructor
hooks and removes text observers and the old overlay on detach.

**This fallback is not yet build-verified or tested on Q25.** If it cannot
create the Compose host, the hook logs the exception and does not shrink
the original row. Captured logs must include `BBMessagesInbox` lines; a
successful "Mounted BBOS6 inbox row face" indicates the host was installed,
not necessarily that it rendered correctly. Sender and preview are displayed with the
canonical blue BBOS6 selected-row face when Google actually focuses the row.

This is an **experimental visual replacement**, not yet an accepted production
hook. Google remains responsible for conversation IDs and activation. Unread
badges, attachment thumbnails and status icons are **not yet replicated** in
the BBOS6 paint layer; the toolbar/FAB still use Google presentation. The
`MessagesInboxList` full-page Compose presenter is a separate app-owned
component and is not mounted: replacing the RecyclerView itself would
otherwise duplicate Google's scrolling/adapter/navigation logic.

## Build, install, enable, inspect

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleDebug
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
```

Enable BB Messages in Vector and scope it **only** to Google Messages,
`com.google.android.apps.messaging`. Then opt into this experiment:

```sh
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled true
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | grep -E 'BBMessagesInspect|BBMessagesInbox'
```

Verify display density, row clipping, inbox scrolling and new/edited incoming
rows, trackpad up/down selection, click to open, long-press, Back and restoring
selection, touch gestures, unread status and conversation updates. Google
Messages remains the authoritative messaging backend.

## Roll back without uninstalling or clearing data

```sh
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

Alternatively disable BB Messages scope in Vector and restart the app.

## Integration and privacy boundaries

- The structural inspection logs only view class names and resource IDs.
  It does not log contact names, snippets or message contents.
- The renderer reads native displayed text in memory only to paint the UI;
  it never persists or logs message data or sends/changes messages.
- The hook is limited to the main Google Messages process. If the verified
  inbox view class is unavailable, it leaves the native inbox intact.
- Overlay/Compose lifecycle interoperability in the third-party process
  requires hardware testing; the row-local lifecycle fallback is not yet
  verified by a successful build or Q25 rendering test.
- Shared UI components must change under `shared/` first. Google-specific
  view discovery, display binding and opt-in configuration remain here.
- Do not merge until Q25 inspection, device acceptance and explicit approval.
