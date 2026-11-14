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

## Target architecture: one shared list controller

**The existing per-row native overlay is a visual experiment, not the final
integration.** It uses `Bbos6MediaListRow` only and inherits Google's
RecyclerView scrolling, selection and focus rules. It therefore cannot meet
Files parity and should not be promoted to a finished BBOS6 inbox.

The correct screen must mount the existing `MessagesInboxList` consumer,
which directly delegates to canonical shared `Bbos6PageList`. That helper,
not an app-specific list implementation, owns visible scrolling, selected
row, trackpad/DPAD, touch, activation and long-press policy. Match Files'
52dp row height and shared list configuration; avoid custom row sizes or
per-screen scroll math.

Google Messages must still own message transport, data, background updates,
RCS, and the original conversation-opening/long-press actions. The view tree
inspection proves the native RecyclerView and visible row text identifiers,
but does **not** identify the adapter's full item-count/data binding, stable
conversation IDs, offscreen rows, or action dispatch. A visible-row snapshot
is insufficient for a full inbox: only the native adapter can expose
offscreen conversations.

### Read-only native adapter capability inspection

The module now emits `BBMessagesAdapter` diagnostic lines after the existing
view-tree inspection. These contain class and method signatures, adapter
count and native capability flags **only**, never sender/message text, IDs or
contact values. The inspection does not bind, mutate or select conversations.

On the Q25 build tested 2026-11-14, Google Messages exposes an obfuscated
`RecyclerView` implementation with **no method named `getAdapter`**
(`NoSuchMethodError`). It contained five visible native rows at the sampled
moment. This does **not** establish the adapter's total item count or confirm
any stable conversation identifiers.

The next diagnostic revision prints a bounded inventory of *declared and
runtime class names* for non-primitive RecyclerView instance fields. This
lets us locate an R8-renamed adapter reference without guessing method names.
It never logs any field values, contact names, message objects, or message text.
It does not yet replace the Google-owned inbox with `Bbos6PageList`.

The subsequent Q25 field inventory identified an active `RecyclerView`
field `F` declared as `zc` with runtime class `wk`, alongside other
obfuscated native view implementation fields. This is **not sufficient
evidence** to designate `F` the adapter; the field could serve another
RecyclerView role. The following diagnostic adds bounded
`CANDIDATE` / `CANDIDATE_METHOD` signatures and superclass chains for
active non-platform reference fields. It does not invoke any unidentified
methods, read conversation objects, or modify UI. Identify the adapter
contract and stable item/action mappings before enabling a full-page swap.

### Verified obfuscated adapter binding (2026-11-14)

The latest Q25 log identifies `RecyclerView.n` as the native adapter:

- Field declared `yv`, with runtime `vv`, a subclass of `yv`
- `yv.a(): int` native item count
- `yv.dR(int): int` item view type
- `yv.hG(int): long` native item ID
- `yv.n(ViewGroup, int): aac` native ViewHolder creation wrapper
- `yv.o(aac, int): void` native ViewHolder binding wrapper
- `vv.e(ViewGroup, int)` and `vv.g(aac, int)` create/bind implementations

`GoogleMessagesAdapterBridge` performs a strict version check for exactly
this observed class/method contract. Unsupported Google builds are left
untouched; there is **no fallback based on guessed field names**.

The new normal inspection calls the verified item-count and identity
accessors, logging **only** count and boolean availability/distinctness
of first/last IDs. It never logs raw IDs or message contents.

To verify whether the adapter can supply the full BBOS6 page, a separate
**opt-in** `debug.bbmessages.bindprobe.enabled=true` probe invokes Google's
normal create/bind wrappers for at most the first and last native positions.
It checks for sender/preview TextView presence and native click/long-click
capability without logging their values or invoking clicks. This is
application-specific *data/action integration*, **not** a new list helper.

The probe is required to validate off-screen item handling before the
full-page `MessagesInboxList → Bbos6PageList` is mounted. The old row
overlay must be disabled. After collecting the probe, set the probe
property false and restart Google Messages. No full-page UI replacement
is activated by the diagnostic.

To inspect the stock adapter, **disable the old visual experiment first**:

```sh
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.bindprobe.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
sleep 3
adb -s 202512003630 logcat -d -v time | grep -E 'BBMessagesAdapter.*(RESOLVED|IDENTITY|BIND_PROBE|unavailable|failed)'
adb -s 202512003630 shell setprop debug.bbmessages.bindprobe.enabled false
```

This is the required adapter evidence before implementing the one-list
replacement and native action bridge. **No full-page replacement is enabled
in this diagnostic commit.** Existing Google inbox content is retained.

### Q25 row-kind rejection (2026-11-14 16:00)

The first full-page mount attempt failed safely with
`IllegalStateException: Unexpected native row class` during the
complete adapter snapshot. Prior diagnostics verified only the first and
last native items. They did **not** prove all eight positions are
conversations. Google Messages remained visible.

The bridge now reports which adapter position caused that failure and,
on rejection, logs a bounded, structural `ROW_KIND` record for **every**
native adapter entry (up to 64). Records contain position, actual row
and ViewHolder classes, and booleans for the sender/snippet and native
click/long-click controls. No raw message text, contact information,
conversation IDs, adapter object values or click actions are logged.

This inspection is necessary before choosing how non-conversation native
entries should be represented by the existing BBOS6 helpers. Do not
blindly omit unknown rows or declare the complete shared-list replacement
functional merely because the adapter's first/last entries bind.

To run this diagnostic with page mode enabled, build/install the branch
and restart Google Messages using the commands below, then capture:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessagesPage|BBMessagesAdapter.*(ROW_KIND|ROW_KINDS)|FATAL EXCEPTION'
```

### Q25 larger-inbox diagnosis (2026-11-14 16:06)

With the full-page mode enabled, the device reported
`Unexpected native row class at adapter index 28`. The fallback
`ROW_KINDS` diagnostic observed **124 native adapter items**, then
stopped because of an earlier 64-item diagnostic cap. The original Google
inbox remained active; its scrolling was therefore unchanged. The earlier
8-item native adapter count was only the count at the earlier inspection,
**not** a fixed inbox size.

The diagnostic now samples the **failed position** and adjacent adapter
positions, plus the first and last positions, regardless of the full
adapter size. It never loops over all 124 rows to diagnose one mismatch
and does not log message text or sender information. The strict
full-page snapshot continues to fail closed: non-conversation rows must
be identified and represented using the appropriate existing shared
helpers before replacing Google's native list.

After rebuilding, inspect:

```sh
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
sleep 3
adb -s 202512003630 logcat -d -v time | grep -E 'ROW_KIND|BBMessagesPage'
```

### Q25 full-page Compose host lifecycle correction (2026-11-14 16:03)

After the row-kind diagnostic change, the complete native adapter snapshot
passed, but the full-page ComposeView could not attach:
`ViewTreeLifecycleOwner not found from
com.google.android.apps.messaging.ui.ImeDetectCoordinatorLayout`.
The injected FrameLayout and ComposeView already carried local owners;
Compose's default **window recomposer** nevertheless looked up the
Google-owned outer content view, where no compatible lifecycle owner
was available. The existing exception rollback restored Google's inbox.

The page host now uses AndroidX
`View.createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle)`
and calls `ComposeView.setParentCompositionContext(...)` **before**
attaching ComposeView. The recomposer belongs only to the injected host
and is cancelled on detachment or lifecycle destruction. No tags or
AndroidX owners are inserted on the Google-owned root. The normal shared
`Bbos6PageList` remains the single list controller.

**Not yet build- or device-verified.** Q25 must confirm that the page mounts,
that its rows match Files' scrolling/navigation behavior, and that native
Google conversation click/long-click/actions still function. Disable the
page property and force-stop Google Messages to return to the stock inbox.

## Shared full-page inbox experiment — build/device test pending

**Implementation now exists behind an independent flag:**
`debug.bbmessages.page.enabled=true`. This mode bypasses the older
`debug.bbmessages.inbox.enabled` per-row overlay and mounts the
pre-existing `MessagesInboxList → Bbos6PageList` as the **sole visible
inbox list controller**. No alternative scroll, focus, selection or list
helper is implemented in BB Messages.

For the currently verified Google Messages build, the
`GoogleMessagesAdapterBridge` snapshots **every** adapter position,
using Google's own `yv.n(...)` / `yv.o(...)` ViewHolder wrappers.
Each native conversation is read from already-bound `conversation_name`,
`conversation_snippet` and any available timestamp text. No message
database, external SMS API or separate data store is introduced.
The snapshot fails closed if any position cannot bind to a conversation
row with native click and long-click actions, or if the native item count
changes while reading.

Google's native RecyclerView is preserved **attached and measured but
invisible** behind the Compose page in a reversible wrapper. The
`Bbos6PageList` renders all rows and controls touch, trackpad, focus and
scrolling. Bound Google native ViewHolder click/long-click listeners are
invoked on activation, never synthetic message intents. The module hooks
Google's observed `vv.g(aac,int)` bind method to coalesce model
refreshes; binds originating in a snapshot are ignored.

**Important limitations for Q25 acceptance:**

- Google's `hG(int)` returned invalid/non-distinct IDs in the device log.
  Temporary snapshot-position keys are used and the shared list selection
  is reset on content changes. These are not stable conversation IDs.
- Presence of native click listeners on detached ViewHolders does **not**
  prove they can activate a conversation; physical click and long-click
  tests must verify behavior before this mode is accepted.
- Native adapter updates, receiving a new SMS, returning from a
  conversation, Back and original toolbar/FAB behavior need device tests.
- If any snapshot or native activation fails, the host attempts to restore
  the original Google RecyclerView, leaving message data untouched.
- The inspected Java class and method names are Google-version-specific.
  Other Google Messages versions fail closed.

**Do not consider this finished or merge it until Q25 acceptance.**

### Build and enable the full-page experiment

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk

adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.bindprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
sleep 3
adb -s 202512003630 logcat -d -v time | grep -E 'BBMessagesPage|BBMessagesAdapter|FATAL EXCEPTION'
```

The first status to check is `Mounted shared Bbos6PageList count=N`.
Then test 8/8 conversations visible (or the current actual count), trackpad
up/down and click, long-press, touch scroll, Back from a conversation and
incoming message reordering. The log has no sender/message contents.

### Disable without uninstalling

```sh
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

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

### Q25 follow-up: overlay parent owner (2026-11-14)

The next Q25 run showed `java.lang.IllegalStateException:
ViewTreeLifecycleOwner not found from android.view.ViewOverlay$OverlayViewGroup`.
The ComposeView had an owner, but the Android overlay's actual parent did not.
Compose's window-recomposer lookup inspects that parent while attaching.

The BB Messages row bridge now attaches a zero-size, non-interactive native
owner anchor into the row overlay **before** mounting Compose. The anchor
assigns lifecycle, saved-state and ViewModelStore owners to the overlay
parent. The original Google RecyclerView and its row remain the scroll/action
owners; the anchor and Compose overlay are removed together on detach.
If the mount fails, the original row is retained and further attempts are
suppressed until that row reattaches, preventing repeated stack-trace spam.

The Q25 build/install succeeded and logcat confirmed repeated
`Mounted BBOS6 inbox row face (isolated Compose lifecycle)`. User device
testing then confirmed that overlay scrolling and list appearance did **not**
match Files, because Google remained the list controller. That verified
overlay remains a legacy experiment and is not the accepted design.

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
- The legacy overlay's mount succeeded on Q25, but its interaction
  geometry differs from Files. The new full-page Bbos6PageList integration
  still requires build/device acceptance for scrolling, native conversation
  actions, updates, keyboard, trackpad, touch and Back.
- Shared UI components must change under `shared/` first. Google-specific
  view discovery, display binding and opt-in configuration remain here.
- Do not merge until Q25 inspection, device acceptance and explicit approval.
