# BB Messages Vector module

This is a separate Vector/LSPosed module targeting Google Messages
(`com.google.android.apps.messaging`), not SystemUI.

- Reuse `../../shared/android/bbos6-ui/` for every BBOS6 UI surface.
- Do not copy or fork shared components or original numbered assets.
- Restrict hooks to the Google Messages main process.
- Never mutate message data, send messages, change default-SMS status,
  or alter Google Messages navigation while merely inspecting its UI.
- Verify the installed Q25 Google Messages hierarchy and action bindings
  before proposing any view replacement.
- Keep failures isolated and reversible; preserve the original Google
  Messages UI until the replacement is functionally tested.
- Test physical trackpad, keyboard, DPAD, touch, Back, scrolling, and
  conversation activation before calling a replacement complete.
- Work on feature branches and do not merge without explicit approval.
