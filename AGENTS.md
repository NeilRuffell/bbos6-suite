# Repository-wide contributor guidance

## Original BlackBerry OS 6 icon identification

The canonical, searchable reference for original numbered BBOS6 icon assets is
[`assets/bbos6/icon-index.csv`](assets/bbos6/icon-index.csv). This rule applies
to **all** BB UI components and the independent `music-app/`, not only the Music
menu.

**Whenever an original numbered BBOS6 icon is positively identified, used,
or assigned a verified new meaning, update the index in the same change.**
Do not leave the mapping solely in chat, source-code comments, screenshots,
or application-specific documentation.

- Search the index by descriptive name or keyword **before** looking through
  the numbered PNG collection or assigning a resource.
- Keep the original `assets/bbos6/icons/<number>.png` filenames and bytes
  unchanged. Android-packaged copies may use readable resource names.
- Each original filename has one index row. Update existing rows instead of
  creating duplicates; keep the rows sorted by their numeric filenames.
- Fields: `filename` (original numbered PNG), `name` (verified meaning),
  `keywords` (searchable aliases and contexts), `android_resource`
  (readable Android drawable resource name, if used; otherwise blank).
- Add only verified identifications. Do not guess meanings or assign labels to
  unidentified icons. Confirm an icon against actual BBOS6 references or a
  documented, verified mapping before recording it.
- When a mapping changes or a new one is established, update related
  implementation documentation as necessary, but keep the index canonical.

Examples:
`grep -i 'album' assets/bbos6/icon-index.csv`
`grep -i '350.png' assets/bbos6/icon-index.csv`


## BB Music APK build, installation and release workflow

**Mandatory for changes to `music-app/`: preserve the tested-APK workflow.**
Detailed commands and paths are maintained in [`music-app/README.md`](music-app/README.md).

1. Build locally with `gradle -p music-app :app:assembleRelease`.
2. Install the **same release APK** on the Q25 with
   `adb -s 202512003630 install -r music-app/app/build/outputs/apk/release/app-release.apk`.
3. Test on the Q25 and obtain explicit approval.
4. Only then upload that **exact, unchanged APK file** to a GitHub Release
   (`gh release create`). Never rebuild, re-sign, or publish automatically
   between the device test and the release.

- `music-app/app/build.gradle` configures the Release variant to use the
  existing local Gradle debug signing identity (the same approach used in
  TV-DV), so Gradle produces an installable `app-release.apk` without any
  manual `keytool`, `zipalign`, `apksigner`, or signing scripts.
- **Do not** generate ephemeral signing keys or use a GitHub Actions job to
  build and sign a different APK for publishing. Do not introduce a new
  signing/deployment workflow without an explicit user request.
- Preserve `~/.android/debug.keystore` (and its certificate) on the build
  machine; losing or replacing it makes subsequent same-package updates
  incompatible with versions signed using the old identity.
- **Increment `versionCode` for every newly published APK** and update
  `versionName` appropriately; confirm that the new versionCode exceeds
  the installed/published version. Do not depend on a timestamp-named GitHub
  release tag to change Android's embedded versionCode.
- Older GitHub-released APKs that were signed with discarded temporary
  certificates cannot be updated in place using the local key. This is a
  one-time compatibility limit of those older installations, not a reason
  to repeatedly uninstall or reset app data.
- On any subsequent matching-signature update, prefer `adb install -r`;
  never uninstall or clear application data as a normal deployment step.
