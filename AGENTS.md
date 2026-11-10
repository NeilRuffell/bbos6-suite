# BB UI contributor and AI instructions

This repository owns the BlackBerry-inspired Android system UI / Vector
application. The standalone native Music app has moved to
[`NeilRuffell/bb-music`](https://github.com/NeilRuffell/bb-music).
Music-specific application code, tests, README and APK releases belong there,
**not** under this repository's old `music-app/` directory.

## Canonical BBOS6 asset and UI ownership

The `assets/bbos6/` directory is a **Git submodule** of the
[`bbos6-shared`](https://github.com/NeilRuffell/bbos6-shared) repository.

Read [`assets/bbos6/AGENTS.md`](assets/bbos6/AGENTS.md) before changing
original numbered BBOS6 icons, their meanings/resource mappings, or the
reusable Android BBOS6 UI helpers.

**Do not maintain a second copy** of `icon-index.csv`, the original
`icons/` collection, or `android/bbos6-ui/` in this repository.

- Canonical icon index: `assets/bbos6/icon-index.csv`.
- Canonical original numbered PNGs: `assets/bbos6/icons/<number>.png`.
- Canonical reusable native Java UI helpers:
  `assets/bbos6/android/bbos6-ui/`.
- Search the index **before** selecting an icon.
- Every **verified** BBOS6 icon identification or new mapping must update
  that shared-repository index in the same shared change. Never guess.
- Apply a change to the `bbos6-shared` repo first; review/merge its PR,
  and then advance the `assets/bbos6` gitlink through a BB UI PR.
- A submodule update does not happen automatically when the shared repo
  changes. Do not commit an untracked local submodule change without
  committing it to its own repository first.

The existing asset path remains `assets/bbos6/`, so existing read-only
asset references in BB UI docs and source continue to resolve.

**Privacy caveat:** BB UI is public and `bbos6-shared` is currently
private. Public users without explicit permission cannot recursively clone
the submodule. A public, self-contained build requires approving a visibility
or distribution solution first; do not silently expose private assets.

## BB UI architecture and build

Follow [`README.md`](README.md) and
[`docs/DESIGN_PHILOSOPHY.md`](docs/DESIGN_PHILOSOPHY.md).
Prefer Android SystemUI/notification/Quick Settings machinery over competing
parallel services. Preserve existing trackpad and hardware-key behavior.

Never move features between the Vector BB UI APK and the standalone Music APK.
Use separate feature branches and PRs; do not merge without explicit approval.
