# Music contributor instructions

Before editing BBOS6 icons, mappings, or shared UI helpers,
read assets/bbos6/AGENTS.md.

The canonical asset index is:
assets/bbos6/icon-index.csv

Update it in the bbos6-shared repository, push that change,
then advance the submodule reference. Never create a copy.

Preserve application ID dev.q25.bbmusic and the existing
local APK signing identity.

Build the release APK with:
gradle :app:assembleRelease

Install on the Zinwa Q25 with:
adb -s 202512003630 install -r app/build/outputs/apk/release/app-release.apk

Test before publishing. Never automatically rebuild, re-sign,
uninstall, or clear app data as part of the release workflow.
