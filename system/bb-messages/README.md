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

## Q25 00:46 third probe — structural collection passed; UI chain deprioritized

The third read-only Q25 diagnostic reported `qe` with position-range callbacks,
`ujo` extending `java.util.AbstractList`, `uji` with range callbacks,
and `ujl`/`hbno` as companion types. This is consistent with list/observer
machinery but **does not identify Google RCS data, message history,
transport, receipts, typing, or attachments**. The probe executed and
returned metadata; those RCS capabilities are still unverified.

Rather than following more types from the UI controller graph, the next
step is an **offline DEX class-definition inventory** of the installed
Google Messages APK/splits. No new Vector hooks, UI changes, or APK
installation are needed for this step.

The new Python standard-library scanner reads only class-definition
descriptors from `classes*.dex` inside those code APKs. It groups
human-readable class names under RCS/IMS/Jibe, conversation, receipt,
typing, media and persistence keywords. These are **name-only heuristic
candidates**, not verified APIs or proof that full RCS is possible.
Only DEX versions 035–040 are supported; unfamiliar formats fail closed.

Run from the repository checkout on the connected Q25:

```bash
cd ~/Projects/bbos6-suite
git fetch origin
git switch feature/messages-rcs-backend-research
git pull --ff-only origin feature/messages-rcs-backend-research
python3 -m unittest discover -s system/bb-messages/tools/tests -v
python3 system/bb-messages/tools/inventory_google_messages_dex.py \
  --serial 202512003630 --sample 12
```

The scanner requests only APK paths using `adb shell pm path`, temporarily
pulls APK code files, reads class names from their DEX definition tables
and automatically deletes the temporary copies. It does **not** request
application private storage, query content providers, read RCS message
databases, stop/launch Google Messages or change user data.

**Offline scanner tests:** five synthetic-DEX Python unittest cases passed
in the isolated development environment; no Q25 scan has been run.

Reference: https://source.android.com/docs/core/runtime/dex-format

## Q25 00:30 first read-only controller probe — PASSED

The Q25's installed Google Messages build confirmed the native action
`asvk.Q(ConversationId)` completed after a genuine stock-UI conversation
open. There were 16 direct controller reference fields. Among them the
runtime classes `fzii`, `fzbv`, `affa`, `awei`, `aweq`,
`gvvj`, `ucc`, `ujp` and `yw` occurred. The runtime conversation
identifier class was
`com.google.android.apps.messaging.shared.api.messaging.conversation.bugle.BugleConversationId`.
Its public interface was
`com.google.android.apps.messaging.shared.api.messaging.conversation.ConversationId`.
It exposed three instance methods returning `bfac`, `String` and
`byte[]`, but **none was called**, and no identifier value was logged.

These observations verify the controller hook and allowlisted class
identities; they **do not prove** RCS historical storage access, RCS
conversation transport, sending, receipts, media or typing.

## Second milestone — bounded class schema probe (Q25 tested)

A second separately opted-in diagnostic reads **class metadata only**
for the nine runtime types above: superclass, interfaces (up to 6),
declared instance field names/types (up to 16) and method signatures
(up to 24, with at most 6 parameter types). Each class is logged once
per process. No values or objects are followed beyond the existing
controller's direct references; no Google methods are called.

It avoids the known Activity, Context, Optional and ConversationId
objects. A different installed Google version fails closed for unknown
types. Class descriptions are strictly *candidates*, not verified
backend services.

After compiling/installing this branch, enable **both** properties
before force-stopping/restarting stock Google Messages:

```bash
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.enabled true
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.schema.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

Open one existing conversation via the **stock** Google UI; retrieve:

```bash
adb -s 202512003630 logcat -d -v time | \
  grep -E 'RCS_SCHEMA|RCS_PROBE'
```

To disable schema inspection for subsequent tests:

```bash
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.schema.enabled false
```

This diagnostic is not a complete RCS integration. The immediate goal
is to obtain enough verified method/field **types** to identify a
read-only conversation-history subscription or data-store contract.

## Q25 00:39 second probe — class-schema collection PASSED

The Q25 reported `RCS_PROBE originalConversationOpenObserved=true completed=true`
and successfully printed bounded `RCS_SCHEMA` metadata. Evidence:

- `fzii` has a `View.OnClickListener` method and a `fzbv` field,
  so at least part of this controller is UI-facing.
- `awei`, `aweq` and `gvvj` implement `gvvp`, exposing
  `Object`-returning methods, without any observed message-history
  interface.
- `ucc` declares an `Executor`, `CopyOnWriteArrayList`,
  and fields typed `qe`, `pk`, `ujo`, `ujl`, `hbno` and `uji`.
  This is an **unclassified** component, not a verified RCS service.
- `yw` extends Android `Observable`; that alone is not evidence
  of an RCS event feed.

**All full-RCS acceptance requirements remain unverified.**

## Third milestone — declared type schema, optional, not yet Q25-tested

To limit further reflection to the actual Q25 evidence, the new
`RcsDeclaredTypeProbe` describes only the six class types above,
and **only when their types are declared directly by `ucc`**.
It collects each class's superclass, interfaces, up to 16 declared
instance field names/types, and up to 32 method signatures. Neither
`ucc`'s field values nor those of any downstream types are read.
No Google method is invoked. This intentionally stops after one
metadata-only type hop.

After a new debug APK is installed, enable all three properties
before restarting stock Google Messages:

```bash
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.enabled true
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.schema.enabled true
adb -s 202512003630 shell setprop debug.bbmessages.rcsprobe.types.enabled true
adb -s 202512003630 logcat -c
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
```

Open one existing conversation in **stock** Google Messages and collect:

```bash
adb -s 202512003630 logcat -d -v time | \
  grep -E 'RCS_TYPES|RCS_PROBE|RCS_SCHEMA'
```

The next decision depends on whether these signatures expose a
specific messaging-data interface. If not, stop expanding the UI
controller graph and investigate the installed APK's messaging
data/transport implementation directly. Neither result should be
represented as verified RCS history or send support.

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
