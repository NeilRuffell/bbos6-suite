# BBOS6 Suite

This is the canonical monorepo for BBOS6 applications and shared UI.

- shared/: canonical icons, icon-index.csv and reusable UI
- apps/music/: independent Music APK and application logic
- apps/files/: independent Files APK and application logic
- system/bb-ui/: independent System UI project

Each consumer's assets/bbos6 path links to the same shared/
directory. Do not create Git submodules or duplicate UI helpers.

Update reusable functionality in shared/ first.
Maintain application-specific functionality in its own directory.
Build and test all affected applications before merging.
Never merge changes without explicit approval.

Historical submodule instructions in imported documentation are
superseded by this monorepo architecture.
