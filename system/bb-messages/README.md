# BB Messages Vector integration

Independent experimental Vector/LSPosed module for Google Messages on the
Zinwa Q25. The renderer reuses the canonical Kotlin/Compose BBOS6 library in
`../../shared/android/bbos6-ui/`.

## Q25 19:37: offscreen scrollToPosition failed; bounded native scrollBy fix awaits test

The 2026-11-14 Q25 logs for `fc098eea` verified the snapshot and
safety fallback, but **not** offscreen activation:

```text
SNAPSHOT nativeItems=124 conversationRows=28 nonInteractiveLottieRows=96
OFFSCREEN native scrollToPosition unavailable: NoSuchMethodException
OFFSCREEN native viewport movement unavailable
SOURCE_PAGE fallback restored native Google inbox
ATTACHED_OPEN originalGoogleActionInvoked=true longPress=true
  nativeQCalls=0 nativePCalls=1 nativeCompleted=1 nativeFailures=0
```

The installed Google Messages RecyclerView is obfuscated and did not
export the assumed method name `scrollToPosition(int)`. That method
lookup is **removed**. The long-press trace confirms Google's native
`P` handler completed; it is not an offscreen-activation pass.

### Revised Google-only offscreen bridge

1. `Bbos6PageList` continues to own the user's scrolling, focus and
   selection. The hidden Google RecyclerView is moved only when a
   selected conversation lacks an attached native row.
2. The Google action bridge examines **real, attached** Google-bound
   conversation row positions and determines whether the selected native
   adapter index lies before or after them. It fails closed without a
   clear direction.
3. The Google adapter bridge uses `View.scrollBy(0, deltaY)`, a stable
   Android View API, **only after verifying that the Google RecyclerView
   itself overrides this method** (not the base View's offset behavior).
   Original support RecyclerView implements `scrollBy` through its
   LayoutManager. It moves at most half of the native viewport per
   step, so intermediate rows are not skipped.
4. After each native layout/pre-draw, require the *real* Google row to
   attach. Only then is the original click/long-click listener invoked,
   after existing adapter identity, item count, exact
   sender/preview/timestamp and uniqueness checks. A pending action is
   canceled on host detach/release. Movement stops after **64 bounded
   native steps** or earlier on unsupported APIs or stale identity,
   restoring the native Google inbox rather than guessing a thread.
5. The verified persistent Back remount monitor and shared helper are
   unchanged. No conversation ID is invented, and no detached
   `ViewHolder.performClick()` or standalone `asvk.I` is used.

**Q25 acceptance pending:** select a conversation beyond the native
viewport; confirm it opens the correct thread, Back returns directly
to BBOS6, and hardware long-press works there. Check
`OFFSCREEN native scrollBy`, `ATTACHED_OPEN`, `SOURCE_PAGE`,
`SNAPSHOT` and any fallback messages. App data is preserved.

## Q25 2026-11-14: Back visual acceptance passed

The user explicitly confirmed on the Zinwa Q25 that after opening
a conversation and pressing Back, the **BBOS6 inbox reappeared
directly**, without the stock Google inbox becoming visible.
Combined with the preceding 19:20–19:21 device logs, this passes
the targeted **Back-to-BBOS6 inbox / no stock-inbox flash**
acceptance check.

This confirms only the tested Back path. Offscreen conversation
activation, long-press/context menu, trackpad boundary scrolling,
selection retention and full-screen BlackBerry styling remain
separate acceptance checks; do not infer success for those.
No additional source change was needed and PR #19 remains unmerged.

## Q25 19:20: Back source-time remount verified by device logs

The 2026-11-14 19:20–19:21 Q25 test confirms the persistent
pre-draw monitor reintercepts Google's recreated inbox after Back:

```text
SOURCE_PAGE persistent inbox draw monitor attached
SOURCE_PAGE visible native inbox detected stage=return_or_initial
Mounted shared Bbos6PageList count=8
SOURCE_PAGE intercepted native inbox before draw returnMount=true
Refreshed shared inbox count=31
SOURCE_PAGE shared inbox host detached finishing=false destroyed=false
ATTACHED_OPEN originalGoogleActionInvoked=true longPress=false
  nativeQCalls=1 nativePCalls=0 nativeCompleted=1 nativeFailures=0
SOURCE_PAGE visible native inbox detected stage=return_or_initial
SOURCE_PAGE retiring detached native inbox session
SOURCE_PAGE snapshot not ready: IllegalStateException
Mounted shared Bbos6PageList count=24
SOURCE_PAGE intercepted native inbox before draw returnMount=true
Refreshed shared inbox count=40
```

**Verified:** the stock Google controller `Q` returned without
exception during a real selected-row click; the original BBOS6
host detached as the conversation opened; on Back, a newly visible
native inbox triggered retirement of the old session and a new
canonical `Bbos6PageList` mount before the next native inbox draw.
This corrects the earlier one-shot-interceptor defect. Native
adapter population remains asynchronous (8 → 31 before navigation,
24 → 40 after Back) and is handled by refresh.

**Not yet proved solely by logs:** no visual frame of the stock
inbox appeared, the correct conversation contents were shown,
selection/scroll position remained stable, or offscreen actions
and long-press worked. These require visual/device interaction
acceptance. There was no failure/rollback message in this log.
Do not mark the whole feature complete or merge without explicit
approval.

## Q25 18:41 Back return: persist BBOS6 across Google fragment recreation

The Q25 log verified the attached native Google action ran the
actual conversation-opening method successfully:

```text
ATTACHED_TRACE Q installed P installed=true
SOURCE_PAGE mounted before first native draw
ATTACHED_OPEN originalGoogleActionInvoked=true longPress=false
  nativeQCalls=1 nativePCalls=0 nativeCompleted=1 nativeFailures=0
```

However, pressing **Back** displayed the **stock Google inbox**.
That violates the BBOS6-only presentation requirement even when
the selected conversation is opened by Google's backend.

### Root cause and correction

The startup code previously installed a **one-shot**
`ViewTreeObserver.OnPreDrawListener` which removed itself after the
first shared-list mount, and `MessagesInspectionHook` ignored
subsequent `MainActivity.onResume` callbacks by gating on a
one-time Activity set. Google can recreate or reinsert its native
RecyclerView on returning from a conversation *without* recreating
the Activity. In that case nothing remounted BBOS6 and the native
inbox became visible.

The updated integration now:

1. Registers one **persistent** per-MainActivity pre-draw monitor.
   Reuses/re-attaches the monitor on later Activity resumes.
2. On every draw pass, searches the attached, visible view tree for
   the **verified Google inbox** `android.R.id.list`,
   `android.support.v7.widget.RecyclerView`, the known adapter
   `vv extends yv`, and the verified LinearLayout parent.
   The old native recycler *inside the BBOS6 host* remains
   `INVISIBLE` and is deliberately excluded. Google conversation
   screens with no visible compatible inbox are unaffected.
3. When a newly visible native inbox appears (including Back),
   mounts the canonical `MessagesInboxList` /
   `Bbos6PageList` before native drawing using the same guarded
   adapter snapshot and Google actions. Returning **false** from
   `onPreDraw` invalidates the pre-swap frame.
4. Uses the established bounded/150ms-throttled snapshot retries for
   that newly visible inbox. Resets the retry window whenever the
   native inbox disappears during conversation navigation.
5. Retires detached stale shared sessions, logs host
   attach/detach, and leaves the current shared list alone while
   its native recycler is hidden. It never implements custom
   per-app scrolling or changes the shared helper.

Useful log markers:
`SOURCE_PAGE persistent inbox draw monitor attached`,
`SOURCE_PAGE shared inbox host detached`,
`SOURCE_PAGE intercepted native inbox before draw returnMount=true`,
`ATTACHED_OPEN`,
`SOURCE_PAGE fallback restored native Google inbox`.

**Status:** implementation committed; Gradle and actual Q25 Back
verification still pending. The same safety fallback to Google
remains for unknown/incompatible versions rather than freezing or
crashing the SMS app. No claim of zero flash or successful Back
remount until on-device testing.

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages \
  :bbos6-ui:verifyKotlinOnly \
  :bbos6-ui:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk

adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'SOURCE_PAGE|ATTACHED_OPEN|ATTACHED_TRACE|BBMessagesPage'
```

Device test: open an initially visible thread, verify the right
conversation, press Android Back, verify the **BBOS6 inbox**
returns without the stock Google inbox appearing, then repeat with
another conversation. The code still needs acceptance for offscreen
conversation activation, long press, focus and selection retention.
Do not merge before explicit approval.

## Q25 18:37 first real attached-row action and controller-trace follow-up

Q25 device log confirms the new source-time shared inbox mode completed
an actual activation attempt: the stock adapter supplied 8 real rows,
the shared `Bbos6PageList` mounted before the first native draw,
the adapter then populated to 28 conversations / 96 Lottie entries,
and the BBOS6-selected row dispatched through Google's original
attached-row listener:

```text
SOURCE_PAGE mounted before first native draw
SNAPSHOT nativeItems=124 conversationRows=28
  independentModelRead=disabled originalNativeActionBridge=enabled
  nonInteractiveLottieRows=96
ATTACHED_OPEN originalGoogleActionInvoked=true longPress=false
```

This establishes that the attached original Google row handler was
called, unlike the rejected detached-holder click. **The log does not
yet establish that the intended conversation screen opened** or that
Back, offscreen activation or long-press work.

Added a narrow read-only controller-outcome trace to answer that
specific question on the next Q25 test:

- Hook only Google's version-checked `asvk.Q(ConversationId):void`
  (and, when present, `asvk.P(eggp):void`). Never change arguments,
  return values or navigation.
- Record completion only within a thread-local scope surrounding
  `performClick()`/`performLongClick()` on the verified **attached**
  native row. Calls outside this scope are ignored.
- `ATTACHED_OPEN` now reports
  `nativeQCalls`, `nativePCalls`, `nativeCompleted`,
  `nativeFailures`, and `longPress` alongside the original
  listener-invoked flag. These are metadata counts only. No message
  contents, contacts, conversation IDs or object identifiers are logged.
- The controller hook logs `ATTACHED_TRACE Q installed P installed=...`
  or an explicit unsupported-signature error. A completed Q indicates
  Google's own controller-opening method ran, but the physical screen
  and intended thread still require device observation.

New diagnostics await build/device verification on the draft branch.
No merge or shared helper modification.

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages \
  :bbos6-ui:verifyKotlinOnly \
  :bbos6-ui:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk

adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'SOURCE_PAGE|SNAPSHOT|ATTACHED_TRACE|ATTACHED_OPEN'
```

Only tap an **initially visible** conversation on the shared list
for this test. Confirm whether the correct conversation opens and
whether Back returns to the BBOS6 inbox. Offscreen positions still
fail closed to stock rather than invoking the wrong thread.

## Q25 18:25: shared list renders; original attached Google actions now wired

The user's latest Q25 device test verified that **the shared page
mounted and refreshed correctly**: first 8 native conversation rows,
then 28 conversation rows among 124 native adapter entries (96
noninteractive Lottie placeholders). All 28 row model lookups returned
`model_id_null`. Activating a BBOS6 row consequently rolled back to
stock UI; this was not a missing enable flag.

### Corrected action contract on the current feature branch

We now use the original Google Messages action path from an
**actual attached native row**, rather than reading the transient
`aszh.a.I.c` ID outside a real click or calling the detached
`performClick()` previously shown to fail.

- The verified `vv.g(aac,int)` native bind callback records a weak
  row-to-adapter-position association, ignoring our synthetic
  snapshot binds. No IDs or content are logged.
- On BBOS6 selection, the Google adapter rebinds a fresh read-only
  presentation snapshot at that index and checks unchanged title and
  preview, its current adapter identity and count. The shared-page
  controller refuses ambiguous duplicate sender/preview/timestamp
  tuples rather than guessing which thread to open.
- `MessagesNativeAttachedActionBridge` searches the **currently
  attached** original Google RecyclerView children, requiring a
  *single* attached row with the exact verified native index, title,
  preview and timestamp. It delegates to that existing row's
  `performClick()` or `performLongClick()`, invoking Google's own
  native delegates and normal click-time context. No fabricated
  conversation ID, standalone `asvk.I` call, manually assembled
  `asvk.Q` invocation or second scrolling controller is used.
- If the selected native row is offscreen, detached, stale,
  ambiguous or noninteractive, the action fails closed and the stock
  inbox is restored. Offscreen activation is **not** yet supported.
- The standalone `asvk.I(int)` read is no longer called in
  snapshots because Q25 verified it yields null IDs outside Google's
  actual click path. New snapshot logs explicitly report
  `independentModelRead=disabled originalNativeActionBridge=enabled`.

This is new code, **not yet compiled or Q25-device verified**. The
next device result must establish whether `View.performClick()` on
an original **attached but parent-invisible** native row invokes
the working Google conversation handler, and whether native long
presses remain functional. An `ATTACHED_OPEN
originalGoogleActionInvoked=true` log means a listener was called;
it is not by itself proof that the intended conversation opened.
Only device testing can establish success.

Test on the same existing branch and flag:

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages \
  :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest \
  :app:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'SOURCE_PAGE|SNAPSHOT|ATTACHED_OPEN|BBMessagesPage|BBMessagesNavigation'
```

No changes to shared UI helpers were necessary, and no merge has
occurred. Stock rollback remains the same two properties + restart.

## Q25 18:05 startup regression: empty mount and model lookup failure

The Q25 log proves **the new integration was already enabled**:
`SOURCE_PAGE shared BBOS6 controller opted in` and
`SOURCE_PAGE watching first draw`. It mounted a premature snapshot
(`nativeItems=0 conversationRows=0`) and then, while the actual
inbox populated, the first refresh failed with
`IllegalStateException: Google model unavailable for native row`.
The full-page session rolled back to the stock Google inbox. Repeated
`SOURCE_PAGE unavailable` warnings followed.

Changes on the same unmerged feature branch:

1. A native count of zero at startup now returns *not ready*, rather
   than mounting an empty shared list. The first-draw watcher retries
   only within its bounded time/pass window.
2. **Presentation is separated from navigation identity:** native
   conversation rows can produce a complete `Bbos6PageList` snapshot
   even when the standalone `asvk.I(position)` model lookup cannot be
   verified. This historical step has now been superseded:
   independent model reads are disabled in the current snapshot path.
3. **Fail-closed activation remains mandatory.** This historical
   model-based activation guard has now been replaced with validation
   of the original, attached Google conversation row before invoking
   its existing listener; see the newer Q25 18:25 section above.
4. `MODEL_RESOLVE_STAGE unavailable=...` now records the **first
   occurrence of each structural failure stage only**, distinguishing
   a native binding failure, null result, unexpected returned type,
   nested model-field failures and reflection errors. No object
   identities or data values are logged.
5. The first-draw observer now marks itself finished and detaches
   from its current live tree observer. This prevents repeated
   failure warnings from an obsolete observer. An unresolved model
   on both old/new snapshots no longer forces an unnecessary Compose
   selection reset every refresh.

**Verification pending:** This is source-level progress, not a
verified APK build or successful Q25 screen. The branch remains
draft/unmerged. The existing property is still correct:
`debug.bbmessages.shared.page.enabled=true`. There is **no new
enable switch**.

Recommended Q25 commands, after rebuilding/installing the branch:

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages \
  :bbos6-ui:verifyKotlinOnly \
  :bbos6-ui:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk

adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'MODEL_RESOLVE_STAGE|SNAPSHOT|SOURCE_PAGE|OPEN blocked|BBMessagesPage'
```

If the shared page appears and a conversation cannot be opened, the
fallback is intentional. Continue investigation of Google's
`asvk.I(int)` outside its native click before allowing activation.
Do not disable the identity guard or synthesize a conversation ID.

## New opt-in integrated BBOS6 inbox (implementation staged; untested on Q25)

**Progress since the failed row-skin experiment:**

- The actual visible inbox uses the existing `MessagesInboxList` consumer,
  which invokes the canonical shared `Bbos6PageList`. Hardware
  DPAD/trackpad, focus, viewport scrolling, touch and selection
  are therefore owned by the established shared helper—not Google
  `RecyclerView` as they were in native-row skin mode.
- A source-time `MainActivity` pre-draw listener attempts to mount the
  shared inbox **before the first native inbox paint**. This replaces
  the rejected 750ms delayed mount. It suppresses a bounded number of
  initial draw passes (maximum 90) while throttling expensive adapter
  snapshots to at least 150ms apart. If the current Google adapter
  cannot provide a complete verified snapshot, it allows Google's
  stock UI instead.
- Added `GoogleMessagesControllerNavigation` as an **app-specific**
  guarded action bridge (not a shared UI helper). At snapshot,
  it resolves the actual Google model through `asvk.I(position)`
  and extracts the typed conversation ID through `aszh.a.I.c`.
  These paths were observed on two Q25 stock-conversation taps, but
  independent invocation is **not yet device-verified**.
- On BBOS6 selection, the bridge rebinds the current native adapter
  position, checks the sender/snippet, current adapter, controller
  identity and exact Google model ID against its saved snapshot,
  and only then invokes the verified `asvk.Q(ConversationId)`
  signature. If anything changes, it refuses activation and
  returns to stock. It no longer uses the rejected detached
  `performClick()`.
- Native inbox rebinds refresh the shared list. Model changes reset
  position-only selection even if display text is unchanged.
  Temporary detach while opening a conversation no longer immediately
  destroys the shared inbox, allowing it to return after Back.
- Google long-press `asvk.P(eggp)` remains unverified for
  independent dispatch. A long-press currently fails closed by restoring
  the stock UI; it does **not** claim an active context menu.
- A JVM unit test checks model/controller equality rejection; this and
  the full build still require execution.

**Important:** This is a new opt-in Q25 test, not a verified working
replacement. The native `asvk.I(int)` accessor is obfuscated and not a
public read-only API. It may have side effects; failure must leave
the stock inbox. Do not test with critical conversations until the
user accepts that risk. No message data, ID values, contacts, hashes
or message text are logged by these new diagnostics.

The new mode is completely separate from the defective native-row skin:

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages \
  :bbos6-ui:verifyKotlinOnly \
  :bbos6-ui:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk

adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'SOURCE_PAGE|BBMessagesPage|BBMessagesNavigation|BBMessagesAdapter.*SNAPSHOT'
```

**Device acceptance still required:** first paint without Google UI
flash, correct visible rows, BBOS6 trackpad selection and viewport
boundary scroll, touch, long-press fallback, opening the correct
conversation, Back preserving the inbox, live updates and safe
fallback on app version change.

**Immediate stock rollback (no uninstall, no data reset):**

```sh
adb -s 202512003630 shell setprop debug.bbmessages.shared.page.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

This entire implementation remains on the existing feature branch in
draft PR #19. Do **not** merge until explicit user approval and Q25
functional testing.

## Active direction: controller-level Google Messages integration (2026-11-14)

**The old visible overlay and late full-page swap are rejected and disabled.**
Q25 testing showed a visible native-Google flash (the module installed the
replacement after a 750 ms delayed lifecycle callback). The shared
`Bbos6PageList` did mount and display conversations, but activation through
`performClick()` on freshly bound, detached Google ViewHolders did not
open conversations. At one point the native adapter reported 124 items,
28 real conversation rows, and 96 noninteractive Lottie animation rows.
This establishes a usable native view/data adapter, **not** a complete
controller/data contract or production-ready inbox.

The active instrumentation now leaves Google's UI unmodified and inspects
native conversation click/long-click callback **class structure only**.
It never invokes detached click handlers. This information is required
before replacing the Google UI at its original creation point, rather
than hiding or overlaying it after rendering. A later replacement must
use canonical shared BBOS6 helpers for every UI surface while preserving
Google's navigation, compose, attachments, SMS/MMS/RCS, typing and status
behavior; those integrations remain unverified. A version-specific
Vector hook alone is **not** evidence that full preservation is feasible.

### Read-only Q25 action-contract inspection

`debug.bbmessages.actionprobe.enabled=true` takes precedence over the
old experimental flags. No BBOS6 list is rendered in this mode.
`BBMessagesActions` logs whether visible native conversation rows have
click/long-click handlers and describes callback classes and reference
field *types* for up to five attached rows and one detached native row.
It never prints contacts, snippets, message data or object values.

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
sleep 3
adb -s 202512003630 logcat -d -v time | grep 'BBMessagesActions'
```

**All legacy overlay opt-ins are disabled in this revision**, even if
properties remain true. This prevents the rejected presentation from
continuing to masquerade as a working BBOS6 integration. The experimental
sources remain only for reference while direct Google controller bindings
are investigated. No production UI replacement is active. Do not merge.

### Q25 navigation mismatch: row skin is NOT the shared list controller (2026-11-14 17:38)

The user verified the new native skin mounts and logs repeated
`NATIVE_GEOMETRY width=720 height=83 faceWidth=720 faceHeight=83
density=1.6 initialPaddingNonzero=true nativeParent=RecyclerView`.
This proves measured skin geometry and the existence of native row
padding; it does **not** prove the pixel appearance, clipping or
navigation are correct. Their Q25 device report says scrolling is
unacceptable.

**Root architectural mismatch:** `MessagesInboxNativeRowBridge`
embeds only `Bbos6MediaListRow` inside each Google
`ConversationListItemView`. The native Google `RecyclerView`
continues to own list scrolling, DPAD/trackpad selection, viewport
placement, touch and focus. The canonical shared
`Bbos6PageList` owns all of those behaviors in Files/Music where
it is actually used, but **is not mounted in this row-skin mode**.
Changing row padding or height will not make native Google
RecyclerView inherit the shared list's behavior.

`MessagesInboxList` already composes `Bbos6PageList`, but the
old `MessagesInboxPageBridge` full-page mode was disabled after
Q25 found a delayed 750ms Google-UI flash and inability to open
conversations through detached native ViewHolder `performClick()`.
It must not simply be re-enabled.

**Correct path:** use the canonical `MessagesInboxList` /
`Bbos6PageList` for one BBOS6-controlled inbox, backed by the
version-checked full Google adapter snapshot. Replace the rejected
detached `performClick` navigation with a separately tested,
fail-closed bridge to Google's real conversation controller; maintain
native conversation identity across live data updates, reject stale
position-only keys, and mount in the original inbox creation/draw
path instead of after a 750ms delayed swap. Prove trackpad/DPAD,
scroll-to-edge, touch, long press, Back and row recycling on Q25.
No app-specific fork of the canonical navigation helper.

**Current status:** native-row visual mode is an incomplete, opt-in
diagnostic, not a working BBOS6 inbox. Recommend disabling it for
normal device use until a true shared-controller integration is
tested:

```sh
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

No production navigation integration is claimed or merged.

### Native row padding / clipping repair (Q25 2026-11-14 17:30)

The user supplied an actual 720-pixel-wide device screenshot and log
confirming repeated `NATIVE_ROW mounted shared Bbos6MediaListRow`.
The first visible BBOS6 rows had cropped leading sender letters,
cropped right-hand timestamps, clipped preview lines, and residual
Google rounded-card/row spacing. The mount log confirms that the
renderer installed but **not** that its geometry was valid.

The row renderer was laying a full-width `Bbos6MediaListRow` out at
`(0,0)` inside Google's existing `ConversationListItemView`
without accounting for the native view's own content padding/clipping.
It now captures that row's original padding, clears the insets only
while the BBOS6 child is mounted, and restores the exact native padding
(and previously tracked height and child alpha) on rollback.
`NATIVE_GEOMETRY` logs a bounded, payload-free diagnostic containing
the first actual row width/height, face size, screen density, presence
of initial padding and parent class. No sender names, texts,
conversation IDs or phone numbers are logged.

The Google Messages app header, floating buttons and conversation
screen remain stock by design at this row-only stage. Do not claim
full-width fidelity or working touch/focus until verified after device
installation. Retest after rebuilding the same branch:

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages :app:assembleDebug && \
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'NATIVE_ROW|NATIVE_GEOMETRY|BBMessagesNativeRows'
```

This is an experimental local Q25 fix, not merged.

### Native row inheritance fix (Q25 2026-11-14 17:26)

Q25 logs confirmed `NATIVE_ROW binding enabled`, followed by repeated
`Native row bridge unavailable: NoSuchFieldException` on all bound rows.
The implementation incorrectly queried `asvj.getDeclaredField("a")`.
The established Google holder inspection already proved that the
`a: android.view.View` field is inherited rather than necessarily
declared directly on `asvj`. The code now uses
`XposedHelpers.getObjectField(holder, "a")`, which traverses
inherited fields, before checking the expected
`ConversationListItemView` runtime class. Structural warnings
are emitted only once per exception class per process.

**Status:** source correction committed, build and Q25 visual
verification pending. No navigational changes, no data reset, no merge.
The default renderer remains opt-in via
`debug.bbmessages.native.rows.enabled=true`. After installing the
updated module, restart Google Messages and check
`NATIVE_ROW` / `BBMessagesNativeRows` logs.

### Opt-in source-time BBOS6 row presentation (unverified build/device; 2026-11-14)

A new, separate experimental flag enables a first **visible** BBOS6
surface using the *existing native Google Messages RecyclerView*:

`debug.bbmessages.native.rows.enabled=true`

In this mode, a version-checked hook on Google's existing
`vv.g(aac,int)` adapter binder adds a `ComposeView` directly as a
**child of the bound `ConversationListItemView`** before that row is
drawn. It uses the shared canonical `Bbos6MediaListRow` with sender,
preview and timestamp extracted from Google's own bound TextViews.
No second list, independent navigation controller, `ViewGroupOverlay`,
late 750-ms activity swap, or synthetic clicks are used. Google's
original RecyclerView, child data sources, and row listeners remain in
place; the visual child is non-clickable, non-focusable and excluded from
accessibility. Android touch and hover dispatch into ComposeView is **not**
proven transparent; physical touch, long-press, trackpad and DPAD behavior
must be checked on the Q25 before acceptance. Google's original children are
made paint-transparent while remaining in the hierarchy, so the
native listeners/data binding remain Google-owned. Unsupported row
types and failed Compose setup retain/restore native presentation,
including original alpha and height.

**Build correction (2026-11-14):** The initial attempt used an anonymous
subclass of `ComposeView` to override touch and hover dispatch, but the
AndroidX `ComposeView` class is final and failed at
`MessagesInboxNativeRowBridge.kt:138` with
`This type is final, so it cannot be extended`. The renderer now
instantiates `ComposeView` directly without overrides, which removes
that reported compilation blocker; a successful build and device
input handling are still pending verification.

This is intentionally a **row-only visual slice**. Google Messages'
headers, compose conversation page and other screens remain stock.
Do not claim full BBOS6 integration, zero visual flash, UI appearance
fidelity, or reliable physical-input behavior until tested on the Q25.

The original `debug.bbmessages.actionprobe.enabled` flag has
precedence. Set it to **false** for visual testing, and keep rejected
`inbox` / `page` modes false. Install the branch APK without
uninstalling Google Messages or clearing its data:

```sh
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-inbox-shared-subtitles
git pull --ff-only origin feature/messages-inbox-shared-subtitles
gradle -p system/bb-messages :bbos6-ui:verifyKotlinOnly :bbos6-ui:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
adb -s 202512003630 shell setprop debug.bbmessages.actionprobe.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.inbox.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.page.enabled false
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -v time | \
  grep -E 'NATIVE_ROW|BBMessagesNativeRows'
```

**Acceptance on device:** row appearance, one native inbox list only,
no native-Google-to-BBOS6 flash, touch and trackpad activation of
the correct thread, long press, DPAD/focus, scrolling, Back, rebind
after scrolling, and no row overlap/click interception.

**Immediate rollback (no uninstall/data loss):**

```sh
adb -s 202512003630 shell setprop debug.bbmessages.native.rows.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

The original rejected row overlay and full-page swap remain disabled.
This UI slice is **not** merged into `master`; merge requires device
acceptance and explicit approval.

### Q25 repeatability and structural binding confirmed (2026-11-14 16:56)

User verified the updated action-probe build installed and ran on
the Zinwa Q25. The stock Google Messages inbox produced:

```text
ATTACHED_HANDLE_PROBE inspected=5 structural=5 failureReasons={}
HANDLE_PROBE nativeItems=8 sampled=8 conversations=8 structural=8
  unbound=0 animation=0 unexpected=0
  repeatedHolderReferences=0 failureReasons={}
MODEL_SOURCE resultClass=aszh path=resultOfI.a.I.c failedAt=none
  typedId=true referenceMatch=true equalityMatch=true
  nativeBinderSeen=true nativeBindEvents=10
  selectionIndexPresent=true selectionMatchesNativePosition=true
MODEL_SOURCE resultClass=aszh path=resultOfI.a.I.c failedAt=none
  typedId=true referenceMatch=true equalityMatch=true
  nativeBinderSeen=true nativeBindEvents=15
  selectionIndexPresent=true selectionMatchesNativePosition=true
```

This establishes the **observed native dispatch contract in two
different conversation taps**: Google's own `asvk.I(int)` resolves a
model carrying the same `ConversationId` later used by `asvk.Q`,
and its integer argument matches the native adapter's bound position.
The separate attached and detached structural checks also succeeded
for all sampled rows; they do **not** assert that a detached holder
contains a persistent `ConversationId`.

**Next integration stage:** implement a Google-specific, fail-closed
navigation adapter for the canonical `Bbos6PageList` that resolves
the *current* native adapter/model and verifies the selected row's
identity before any direct navigation; account for asynchronous
dataset changes and non-conversation rows. Test that independently
invoking Google's model accessor and navigation method preserves
normal SMS/MMS/RCS actions. Then hook presentation at source/creation
time instead of the rejected delayed overlay. No production UI or
independent `I/Q` dispatch has been enabled by these tests. Do not
merge PR #19 until explicit approval and device acceptance.

### Q25 verified native index-to-conversation contract (2026-11-14 16:52)

The user built and tested the action-probe branch on the Q25. Actual
Google Messages conversation activation produced:

```text
MODEL_BIND installed
MODEL_SOURCE resultClass=aszh path=resultOfI.a.I.c
  failedAt=none typedId=true
  referenceMatch=true equalityMatch=true
  nativeBinderSeen=true nativeBindEvents=10
  selectionIndexPresent=true selectionMatchesNativePosition=true
```

This verifies **one actual physical conversation activation**:
the index Google passed into `asvk.I(int)` equals the clicked holder's
native `vv.g(aac,int)` position, and the resulting `aszh.a.I.c`
is the exact `ConversationId` subsequently supplied to
`asvk.Q(ConversationId)`.

**Safety and scope:** The observation alone does not prove `I(int)`
can be called independently without side effects, nor that the index
remains valid when rows are inserted/reordered or the adapter changes.
Do not invent a conversation ID, invoke `Q` from stale row positions,
or enable the rejected after-draw BBOS6 overlay. Before activating a
BBOS6 row, the integration must validate the current native adapter
and selection/controller context, establish safe use of Google's
model accessor, and retain native functionality.

The previous `GoogleMessagesConversationBinding.VerifiedHandle`
incorrectly required `asvj.r.f.H.H`, a transient **activity-wide**
selection field, to be populated before any click. This has been
corrected: per-row `HANDLE_PROBE` and `ATTACHED_HANDLE_PROBE`
now report **structural** controller/holder readiness only, not
identity or navigation success. `VerifiedHandle` stores no
`ConversationId`. Actual model identity remains verified
separately by `MODEL_SOURCE` during Google's own actions.

For another Q25 validation, tap two **different** native inbox
conversations (returning to the inbox between them) and inspect
`MODEL_SOURCE` for each. No rebuild is needed for this comparison
if the same diagnostic version is still installed.

### Q25 model result provides the real ID; adapter correspondence pending (2026-11-14 16:49)

The Q25 real click produced `MODEL_MATCH root=holder candidates=0` in
the controller/activity-excluding scan, while the actual object returned by
`asvk.I(int)` was runtime class `aszh`, and a nested field
`resultOfI.a.I.c` was **the same reference and equal** to the
`BugleConversationId` argument Google actually passed into
`asvk.Q(ConversationId)`. The broader result graph scan reported
`truncated=true`, so only that positive match is established.
The model also has a distinct `ConversationIdType` field `b`,
which must not be conflated with the `ConversationId` interface
argument accepted by Q.

The first detached `asvj` holder showed most of its reference
fields null, apart from the row `View` and a controller reference,
which explains why attempting to read a persistent ID from an
independently bound holder is unsupported.

A new **read-only** `MODEL_SOURCE` check verifies the exact short
`aszh.a.I.c` field path against Google's actual Q argument and
compares the integer argument observed on Google's own
`asvk.I(int)` call against the bound position recorded from the
verified `vv.g(aac,int)` native adapter callback. Adapter positions
are held only in a process-local weak map; never printed.
`MODEL_BIND` reports whether that native binder hook installed.
No method is invoked by our diagnostics, and no conversation
identifier or message content is logged.

Build/install the branch with `actionprobe=true`, open a stock
conversation, and capture:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessages(Model|Actions).*MODEL_(SOURCE|BIND)'
```

Do **not** wire `I(int)` to BBOS6 selected indices without that
correspondence being verified. The rejected overlay remains disabled,
and PR #19 must remain unmerged pending real Q25 testing.

### Q25 active-selection field is NOT a per-row ID (2026-11-14 16:46)

The user’s physical click yielded the following verified field sequence:

- Before `asvb.onClick`: `asvj.r` is `asvk`, `.f` is
  `MainActivity`, `.H` is `aucu`, and the final `.H` is **null**
- After `asvk.I`: that final field is still **null**
- Immediately before `asvk.Q(ConversationId)`: the final field
  contains a `BugleConversationId` matching Google’s actual Q argument
- After `asvk.L` returns: `asvj.r` is already **null**

**Correction:** `asvj.r.f.H.H` traverses a shared activity/controller
path and is a **transient active selection**, not a stable
per-conversation model ID. Prior `ID_DIRECT` matches proved that
Google’s click and Q argument agree at that instant, but **did not
prove** a selectable BBOS6 row can obtain its own ID. Do not wire
this field into BBOS6 conversation selection or issue `asvk.Q` using
it: the selected row might open an unrelated conversation.

Added `MessagesNativeRowModelInspection` to compare the **actual**
`asvk.Q` argument against references reachable from a bound
`asvj` holder while explicitly excluding `asvk`, `Activity`,
`View`, and platform objects. It also compares the object actually
returned from `asvk.I(int)` and describes the reference **types** of
the holder and that result. Maximum graph depth 5, 128 objects and
20 fields per class. The diagnostic prints only class names, field
names, candidate counts, match booleans, truncation state and
field paths—not conversation ID values, contacts, messages, hashes
or object identities. It leaves the stock Google UI and all
navigation behavior unchanged; rejected overlays remain disabled.

Build and install the branch, keep action probe enabled, restart Google
Messages and open a conversation normally. Capture both the initial
holder shape and the real-click comparisons:

```sh
adb -s 202512003630 logcat -d -v time | grep 'BBMessagesModel'
```

If a stable model association cannot be established, BBOS6 navigation
remains blocked. The canonical `Bbos6PageList` is still the intended
UI helper once data/action bindings are verified; **no new UI helper**.

### Q25 pre-click identity unavailable in both row types (2026-11-14 16:43)

A successful user build/installation on Q25 confirmed
`ATTACHED_HANDLE_PROBE inspected=5 verified=0
failureReasons={id_unavailable=5}` and
`HANDLE_PROBE nativeItems=8 sampled=8 conversations=8 verified=0
unbound=8 failureReasons={id_unavailable=8}`.
Both attached and freshly created detached holders expose their original
click delegates and controller, but the exact `ConversationId` lookup
fails **before** a user click. The prior real click verified that the
same field path contains Google's exact navigation argument while
`asvk.Q(ConversationId)` is invoked. It is not established *when*
this field becomes available or whether the identity can be obtained
outside the stock click sequence.

Read-only `ID_STAGE` instrumentation now reports only the runtime
**class names** (or null/missing stage) along the exact
`asvj.r.f.H.H` path for: one native attached row at inspection,
one detached bound row, entry into Google's real `asvb.onClick`,
after actual `asvk.L` and `asvk.I`, and before actual `asvk.Q`.
These hooks never invoke or change methods, read text, print IDs,
or enable a view overlay. A valid direct controller binding must
pass before enabling any BBOS6 navigation.

After building/installing the branch with `actionprobe=true`,
restart stock Google Messages, open one conversation, then capture:

```sh
adb -s 202512003630 logcat -d -v time | grep 'BBMessagesActions.*ID_STAGE'
```

The shared BBOS6 helpers remain untouched; this is application-specific
Google controller inspection. PR #19 remains unmerged.

### Q25 attached identity succeeds, detached binding fails (2026-11-14 16:41)

The user built and installed the previous branch successfully.
`HANDLE_PROBE nativeItems=8 sampled=8 conversations=8 verified=0
unbound=8` showed that none of the eight freshly created, offscreen
ViewHolders satisfied the complete conversation/controller binding.
However, the same device produced
`ID_DIRECT present=true referenceMatch=true equalityMatch=true
controllerVerified=true` on a real, *attached* conversation tap.
The verified direct path is valid for that actual click; its availability
on a detached bound holder is **not** established.

The next diagnostic now categorizes all offscreen binding failures
(`HANDLE_PROBE failureReasons`) and independently checks up to five
**attached** native inbox rows using their own verified click
delegate (`BBMessagesActions ATTACHED_HANDLE_PROBE`). These
checks do not invoke navigation, modify the Google UI, or log
conversation IDs, contacts, or message contents. Do not assume
detached ViewHolders are suitable data sources for a replacement.

After updating the same feature branch, build/install, start stock
Google Messages with `debug.bbmessages.actionprobe.enabled=true`,
and run:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E '(ATTACHED_HANDLE_PROBE|HANDLE_PROBE|ID_DIRECT)'
```

This will distinguish a lifecycle-dependent identity from a code-level
binding mismatch. Existing shared BBOS6 helpers are unchanged; the
rejected overlay modes remain disabled and PR #19 unmerged.

### Q25 exact native conversation/controller binding (2026-11-14 16:37)

On a real stock Google inbox click, the Q25 probe found the **exact
same Java object** that Google supplied to `asvk.Q(ConversationId)`
at the bound native holder path `asvj.r.f.H.H`
(`referenceMatch=true`, `equalityMatch=true`). The broad graph
probe still reported `depthCapReached=true` and
`fieldCapReached=true`; its search was incomplete overall.
The direct, four-field **identified path** is now independently
validated against Google's actual navigation argument. No other
positions' mappings were verified by that single action.

Added `GoogleMessagesConversationBinding`, a Google-specific
read-only bridge that resolves exactly `asvj.r.f.H.H` to a verified
`ConversationId`, and checks the real callback's `asvb.a`
controller, `asvb.b` holder identity, native row click wrapper,
and `asvk.Q(ConversationId):void` signature. No view overlay,
synthetic ID, detached click, or custom BBOS6 helper is introduced.

In action-probe mode, `ID_DIRECT` checks that identity during a
real Google click. `HANDLE_PROBE` samples up to 40 native adapter
positions and reports the number of **verified per-row controller
bindings**, animations, failures, and duplicate object references,
without logging contact data or IDs. All Google UI and navigation
remain untouched. Future presentation must use the canonical shared
`Bbos6PageList` and obtain each selected conversation's *current*
validated handle rather than a stale position or display label.

Q25 build/installation/device acceptance for the new direct
bindings remain outstanding. Full source-time replacement and
SMS/MMS/RCS fidelity are **not** claimed complete.

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessagesActions.*ID_DIRECT|BBMessagesAdapter.*HANDLE_PROBE'
```

### Q25 incomplete ConversationId provenance scan (2026-11-14 16:34)

Q25 read-only probe reached the configured **48-object limit** while
walking the native `asvj` holder graph. It found one candidate
`ConversationId` at `asvj.r.l`, but the candidate was neither the
same object nor equal to the actual argument received by
`asvk.Q(ConversationId)`. This **does not establish a usable ID mapping**:
the probe was truncated and the candidate is not verified as the
clicked conversation's identity.

The probe now scans at most **160 non-platform object references**,
to a maximum depth of 4, and logs `objectCapReached`,
`depthCapReached`, `fieldCapReached`, plus `matchingPath`
(if an equal `ConversationId` is found). No IDs, message text,
contact details, or values are logged. This remains a read-only,
opt-in diagnostic in the stock Google inbox.

After rebuilding, open one conversation normally, then capture:

```sh
adb -s 202512003630 logcat -d -v time | grep 'BBMessagesActions.*ID_LINK'
```

If no matching identity is found, **do not** implement a navigation
binding from `asvj.r.l`; investigate Google's verified row-model
binding rather than guessing or opening the wrong conversation.
Existing shared BBOS6 presentation helpers remain unchanged.
No overlays are enabled, and no merge is authorized.

### Q25 verified navigation method argument types (2026-11-14 16:31)

Q25 device output verifies these *instance methods* on the installed
Google Messages build:

- `asvk.Q(ConversationId): void` receives a
  `bugle.BugleConversationId` during a real conversation tap.
- `asvk.P(eggp): void` receives `eggp` during a real long-press.
- `asvk.L(boolean, asvj): void`, `asvk.I(int): aszr`, and
  `asvk.G(String): int` also occur within the tap/long-press traces.

**Important:** The identity argument belongs to the *particular
conversation*. Passing an invented ID, unrelated ID, or using a row
position as an ID could open the wrong thread. The direct `Q/P`
contract is **not** accepted for BBOS6 activation until that identity
is associated with the selected native conversation.

The active read-only action probe now captures the actual Google
`asvb` callback object during a user tap. While Google calls
`asvk.Q`, it examines only bounded reference fields of the
captured `asvj` holder to find a
`ConversationId`-typed object matching Google's real argument.
It logs `BBMessagesActions ID_LINK` with match booleans, a field
path and object counts—**never the identifier, its hash, contact,
message text or object contents**. It neither invokes any Google
controller method nor changes navigation.

To test after building/reinstalling the branch with actionprobe=true,
open a real conversation in the stock Google inbox, then run:

```sh
adb -s 202512003630 logcat -d -v time | grep 'BBMessagesActions.*ID_LINK'
```

A match would justify extracting that typed identity from each native
holder as a further guarded implementation step; it would still not
prove source-time replacement, complete paging or full RCS behavior.
The earlier overlays remain disabled.

### Q25 observed controller call sequence (2026-11-14 16:29)

Q25 build/device test confirmed `MessagesNativeActionTrace` successfully
hooked 20 methods declared on the captured `asvk` class. A physical,
attached native conversation tap called `asvk.L(2)`,
`asvk.I(1) → aszr`, `asvk.Q(1)`, and `asvk.G(1) → int`.
A native long press called `asvk.L(2)`, `asvk.I(1) → aszr`,
and `asvk.P(1)`. The `asvj` holder declared no methods.
This establishes a **real execution path** but not the semantics,
parameter types or standalone invocation requirements of those
obfuscated controller methods.

The active action probe now logs declared parameter type names and
static/instance status for only `asvk.L/I/Q/G/P` as
`TRACE_SIGNATURE`. For actual Google-initiated `asvk.Q/P`
invocations, it logs only runtime **argument class names** as
`TRACE_ARGTYPE`, not values, IDs, message bodies or contacts.
Nothing is invoked or redirected by these probes, and the rejected
overlays remain disabled.

After rebuilding and installing, open one conversation and long-press
another native conversation, then capture:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessagesActions.*(TRACE_SIGNATURE|TRACE_ARGTYPE|TRACE_PATH action)'
```

The next integration must establish a stable per-conversation controller
reference before attempting any source-time BBOS6 presentation
replacement. Merely calling `asvk.Q/P` by name is **not yet verified
safe or sufficient**.

### Q25 verified real activation and controller tracing (2026-11-14 16:27)

On the stock Google Messages inbox, Q25 device logs confirmed the
actual dispatch sequence for a physical conversation tap:

`fzih.onClick → asvb.onClick`, both entered with the native row
attached, then returned without a logged exception. A physical
long-press dispatched `fzig.onLongClick → asvc.onLongClick`; both
returned `handled=true`. The preceding introspection showed both
inner delegates capture `asvk` and native ViewHolder `asvj`.
These are the **verified callbacks**, not a verified standalone
navigation-controller method or stable conversation identity.

The action probe now traces **synchronous method calls** on those two
captured class types while the real native `asvb`/`asvc` callback is
executing. For safety it installs hooks only if each class declares
at most 80 methods, logs up to 24 calls per user action, and logs
only method class/name, parameter count and return type. It does
not call target methods, inspect arguments, modify results, intercept
messages or read model values. If navigation dispatch is asynchronous,
the path may be incomplete; do not assume absent calls imply absence
of functionality.

After building, enable the already established action probe, restart
Google Messages, physically tap and long-press native inbox rows, and
capture:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessagesActions.*(TRACE_PATH|TRACE enter|TRACE exit)'
```

Use the resulting exact controller signatures to replace detached
ViewHolder clicks with a directly verified native action binding
before attempting BBOS6 presentation replacement. The rejected
overlays remain disabled. No feature acceptance or merge yet.

### Q25 attached versus detached navigation trace (2026-11-14 16:23)

Q25 build and install passed. All five attached native conversation
rows and one detached, freshly bound conversation row exposed matching
listener classes and captured reference **types**:
`fzih` click wrapper (`fzii`, `fzid`, delegate `asvb`);
`fzig` long-click wrapper (`fzii`, delegate `asvc`).
This proves the structural listener classes match. It does **not**
prove the detached view retains the state necessary for navigation;
the previous user test demonstrated detached `performClick()` did not
open conversations.

With `debug.bbmessages.actionprobe.enabled=true`, the module now
also installs read-only hooks on the exact candidate
`fzih.onClick(View)`, `asvb.onClick(View)`, `fzig.onLongClick(View)`
and `asvc.onLongClick(View)` methods, where those signatures exist.
When the user physically activates a stock Google inbox row, the
`BBMessagesActions TRACE` lines show which native callbacks ran and
whether the input view was attached. The inspector also reports up
to 12 captured **reference class types** from the `asvb`/`asvc`
delegates (first attached row and one detached row), without
reading message data. Hooks never replace results, invoke actions,
cancel or change navigation.

Build and install the active branch, ensure actionprobe=true and
both old overlay flags=false, restart Google Messages, and **tap one
stock conversation row**. Return to the inbox, then long-press a
stock row. Capture:

```sh
adb -s 202512003630 logcat -d -v time | \
  grep -E 'BBMessagesActions.*(TRACE|DELEGATE_)'
```

Only after validating the original attached action dispatch should
a source-time BBOS6 presentation replacement be attempted. The
BBOS6 helpers remain the required presentation layer, not a second
list controller; SMS/MMS/RCS engine and all messaging functionality
must remain Google-owned and require device verification.

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

### Q25 verified non-conversation animation rows (2026-11-14 16:10)

A successful Q25 build and install identified an adapter total of 124
native rows, with two inspected conversation positions (0, 27) and three
inspected animation positions (28, 29, 123). The latter are exactly
`com.airbnb.lottie.LottieAnimationView`, with native holder `asxi`,
no sender/snippet TextViews, and neither click nor long-click capability.
The sample does **not** establish what every position 30–122 contains,
nor whether the animations indicate pagination/loading.

The Google-specific adapter snapshot now checks **every** native
position. It includes only verified `ConversationListItemView` rows in
the `Bbos6PageList` conversation model. It recognizes the **exact**
noninteractive `LottieAnimationView` shape above as a non-conversation
animation and excludes it from *conversation selection and activation*.
Any other unexpected/interactive native row still aborts the full-page
mount and preserves stock Google Messages. A nonempty adapter containing
zero verified conversations also preserves Google's UI. The
`BBMessagesAdapter SNAPSHOT` log reports the complete native item
count, verified conversation count, and recognized animation count
(without logging personal message content).

The full-page session tracks the **native adapter total independently**
from the number of BBOS6 conversation rows; otherwise the pre-draw
count check would loop continuously when animation rows are present.
Before invoking a native click/long-click, it rebinds the corresponding
adapter position and verifies the sender/preview still match the
snapshot, failing closed on a detected reorder.

**Remaining validation:** Live paging can depend on Google's own
RecyclerView scrolling, which is not the visible scrolling controller
in this experiment. Recognized animations might represent loading.
Test that all expected conversations become accessible and updates
arrive, rather than assuming 124 native rows equal 124 conversations.
If the BBOS6 list omits conversations or native navigation fails,
disable page mode and retain Google's inbox. No additional scrolling
or selection helpers are introduced.

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
The snapshot accepts verified conversation rows and the separately
verified noninteractive Lottie animation rows (excluded from selectable
conversations). Unknown native row types or incomplete conversation
actions still fail closed, as does a native item-count change while
reading.

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
