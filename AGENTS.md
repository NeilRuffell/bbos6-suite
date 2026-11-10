# Canonical BBOS6 shared-asset rules

icon-index.csv is the single authoritative asset index.
icons/ contains original numbered BlackBerry OS 6 assets.

Before choosing an icon, search icon-index.csv.

Whenever a numbered BBOS6 asset is positively identified,
used, or assigned a verified meaning, update icon-index.csv
in the same change.

Preserve original filenames and original asset bytes.
Use one row per numbered filename, sorted numerically.
Never invent unidentified icon meanings.

Commit and push shared changes to bbos6-shared first.
Then update the submodule commit references in consuming
repositories. Do not maintain separate copies of this index.

The reusable Android UI helpers are in android/bbos6-ui/.
Keep them application-neutral.
