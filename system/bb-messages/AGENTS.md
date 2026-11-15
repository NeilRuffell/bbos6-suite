# BB Messages — RCS backend research

This is a clean Google Messages Vector/LSPosed research module in
`system/bb-messages`, scoped ONLY to the main process of
`com.google.android.apps.messaging`.

- The canonical monorepo is `bbos6-suite`; follow its root `AGENTS.md`.
- Do not carry forward the rejected RecyclerView replacement, native-row
  scrolling, synthetic adapter snapshots or UI overlay from PR #19.
- Never read, print, cache, export or modify message bodies, contacts,
  conversation identifiers, attachments, credentials or private RCS data
  during metadata-only inspection.
- Log class names, method signatures and field **types** only; never field
  values. Keep all reflection bounded and fail closed on Google updates.
- Do not send messages, mark messages read, change default SMS settings,
  open conversations, or alter Google navigation from a research probe.
- No user-visible BBOS6 UI until the necessary RCS backend contracts have
  been demonstrated on the Zinwa Q25. When UI is added, use the canonical
  shared Kotlin/Compose helpers from `../../shared/android/bbos6-ui`.
- Full RCS history, send/receive, read/delivered states, typing, bidirectional
  media attachments and group messaging are mandatory acceptance criteria.
  Mark every unverified capability as unverified.
- Work on feature branches, retain device logs as evidence and update README.
- Never merge without explicit approval.
