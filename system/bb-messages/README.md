# BB Messages Vector integration

Independent experimental Vector/LSPosed module for Google Messages on the
Zinwa Q25. The intended presentation reuses the canonical Kotlin/Compose
BBOS6 UI library in `../../shared/android/bbos6-ui/`.

## Current status: inspection only

The first milestone is to identify the actual conversation-list host, its
stable conversation identifiers, lifecycle and click/long-press actions on
the installed Google Messages version. The module currently **does not**
replace any views, access message contents, or change message handling.

The hook logs **view class names and resource identifiers only** on
resumption of a Google Messages Activity. It does not log displayed text,
message addresses, senders, or message bodies. The finite log is bounded
to 240 view nodes / 14 levels per Activity instance.

Enable this APK in Vector for the Google Messages package only.

## Build and install the inspection APK

From the monorepo checkout on the feature branch:

```sh
gradle -p system/bb-messages :bbos6-ui:verifyKotlinOnly :app:assembleDebug
adb -s 202512003630 install -r system/bb-messages/app/build/outputs/apk/debug/app-debug.apk
```

Enable scope for `com.google.android.apps.messaging` in Vector, then
force-stop/reopen the target app (never clear its data):

```sh
adb -s 202512003630 shell am force-stop com.google.android.apps.messaging
adb -s 202512003630 shell monkey -p com.google.android.apps.messaging 1
adb -s 202512003630 logcat -d -s BBMessagesInspect:I '*:S'
```

Identify `RecyclerView`, `ComposeView`, or another actual inbox host from
the logged hierarchy. Do not assume Google Messages uses a particular
widget. Some Compose children cannot be discovered from a native View
hierarchy alone; further target-version-specific inspection may be needed.

Do not enable a full-screen BBOS6 list until source IDs, live row updates,
open-conversation actions and fallback/teardown are independently verified.

The existing `Bbos6PageList` uses `Bbos6MediaListRow` and supports row
activation, keyboard/DPAD navigation, BBOS6 focus and optional one-line
secondary text plus trailing metadata. A future target-specific adapter
must supply conversation IDs and actions. No message data is queried here.
