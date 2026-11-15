# BB Messages — clean RCS backend research

## Status

Fresh investigation module started from `bbos6-suite/master`, not from
the rejected RecyclerView-based branch (PR #19). There is **no BBOS6
inbox UI, conversation list, synthetic row binding, scrolling workaround
or Google Messages navigation replacement** in this code.

The goal is an independent, fully BBOS6-styled Messages experience with
**mandatory full Google RCS functionality**. No SMS-only fallback or
partial RCS implementation satisfies the acceptance requirements.

Google Messages remains the installed messaging backend. The module
installs under the same Android application ID, `dev.q25.bbmessages`,
so `adb install -r` updates the existing module without uninstalling it.

## Mandatory acceptance gates (all currently UNVERIFIED for a new UI)

- Complete historical RCS conversations, including older messages.
- Incoming and outgoing RCS messages, correct conversations and ordering.
- Live delivered and read receipts.
- Incoming and outgoing typing indicators.
- Send/receive/preview images, videos, audio and other attachments.
- RCS groups and membership changes.
- Reconnect/resynchronization behavior without missing messages.
- SMS/MMS interoperability without losing existing history.
- Physical Q25 keyboard/trackpad, touch, Back, focus and selection.
- No Google inbox flash, no wrong-conversation opening.

**Do not build or claim a functional BBOS6 messaging client until these
contracts have real Q25 evidence.** The current probe proves none of
the mandatory RCS capabilities.

## First milestone: read-only structural probe

The device previously verified that the Google controller class
`asvk` executes `Q(ConversationId)` on a real native conversation
open. This new minimal probe verifies that method signature and hooks
its **post-call observation** only when explicitly enabled.

After the user opens a conversation in **stock** Google Messages, it
logs one bounded set of controller reference *types* (declared/runtime
class names only) and `ConversationId` method *signatures*. The output
is an evidence-gathering starting point for locating Google's actual
conversation storage/stream and RCS transport services; it is not an
RCS API and does not identify a conversation, determine whether it is
RCS, or read historical messages.

The module:
- Targets only the exact `com.google.android.apps.messaging` main process.
- Runs only with `debug.bbmessages.rcsprobe.enabled=true` at process start.
- Has no injected UI, row/adapter hooks, network connection, database
  access, outgoing message operations or data persistence.
- Logs no names, phone numbers, account data, conversation IDs, message
  text, attachment data, object `toString()`, field values or URLs.
- Does not alter hook arguments/results, dispatch actions or stop Google
  Messages. Unsupported obfuscated contracts fail closed.
- Uses Java + Android's own APIs for this backend-only investigation;
  the existing canonical shared Kotlin/Compose helpers will own every
  future BBOS6 UI component.

## Q25 build/install

```bash
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-rcs-backend-research
git pull --ff-only origin feature/messages-rcs-backend-research

gradle -p system/bb-messages :app:testDebugUnitTest :app:assembleDebug && \
adb -s 202512003630 install -r \
  system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
```

**Before testing:** keep this module enabled in Vector/LSPosed with Google
Messages in scope. The APK contains no launchable activity; open the stock
Google Messages application.

```bash
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

Open one real existing conversation in the **stock** Google Messages UI,
then inspect **metadata-only** logs:

```bash
adb -s 202512003630 logcat -d -v time | grep 'RCS_PROBE'
```

Expected progression if the installed version still matches:

```text
RCS_PROBE installed: read-only native open observer ...
RCS_PROBE originalConversationOpenObserved=true completed=true
RCS_PROBE controllerReference ...
RCS_PROBE controllerReferenceCount=...
RCS_PROBE conversationIdType=...
```

These are diagnostic expectations, **not** a claimed build or device
result. Inspecting a conversation does not establish whether it is RCS.

Turn off and restart Google Messages to return to the inactive probe:

```bash
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.enabled false
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

Do not uninstall Google Messages or clear either app's storage.

## Next investigation

Use actual Q25 structural logs to identify a version-checked,
read-only **data-layer** contract for genuine RCS history and live
state. Then prove each acceptance gate in isolation, including
attachments, receipts and typing, before introducing the independent
BBOS6 interface. Keep Google-specific integration under this module,
and put all reusable visual components in the monorepo's `shared/`
directory. No merges until explicitly approved.

## Rejected prototype

PR #19 contains the earlier native RecyclerView presentation experiment,
its device logs and the confirmed Back remount. It is retained as
historical reference only. Do not copy its UI overlay, synthetic
snapshots or native scrolling controller into this new architecture.
