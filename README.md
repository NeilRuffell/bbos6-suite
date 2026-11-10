# BBOS6 shared assets and native UI library

This is the **canonical shared dependency** of
[`bb-music`](https://github.com/NeilRuffell/bb-music) and
[`bb-ui`](https://github.com/NeilRuffell/bb-ui).

| Path | Role |
| --- | --- |
| `icons/<number>.png` | Unmodified original numbered BBOS6 assets |
| `icon-index.csv` | Only authoritative identification / resource mapping |
| `android/bbos6-ui/` | Reusable Android Java widgets and tests |
| `AGENTS.md` | Required AI / contributor workflow |
| `tools/validate_icon_index.py` | Basic index consistency check |

The shared library contains the BBOS6 context menu and read-only Properties
popup. App-specific actions and data loading are never implemented in it.

## Consumer integration

Check out a consumer with the shared dependency:

```bash
git clone --recurse-submodules https://github.com/NeilRuffell/bb-music.git
```

If already cloned:

```bash
git submodule update --init --recursive
```

The consumer's Gradle settings include the shared Android module from
`assets/bbos6/android/bbos6-ui`.

**Access:** This repository is currently private; GitHub authentication and
permission to read it are required even when a consuming repository is public.

## Updating shared assets or UI

1. Read [`AGENTS.md`](AGENTS.md), search
   [`icon-index.csv`](icon-index.csv) and verify any new BBOS6 mapping.
2. Commit the original asset/index/shared-code changes in **this repo** on a
   feature branch.
3. Run `python3 tools/validate_icon_index.py`; compile shared Java changes
   using a consuming application's Gradle build.
4. Review and merge the shared-repository PR.
5. In each affected consumer, update the pinned submodule commit and review
   the corresponding consumer PR:

```bash
cd ~/Projects/bb-music
git switch master && git pull --ff-only
git -C assets/bbos6 fetch origin
git -C assets/bbos6 switch --detach origin/master
git add assets/bbos6
git commit -m "Update canonical BBOS6 shared dependency"
git push origin master
```

The above consumer commands are suitable only when updating an approved
branch; use a feature branch/PR when changes have not yet been approved.

A submodule update **does not happen automatically** when shared `master`
moves. Do not clone the index or original asset archive into consumers.
