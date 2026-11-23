# Hayat — Phase Plan & Progress Ledger

Each phase must compile and pass its tests before the next starts.

| Phase | Scope | Acceptance |
|---|---|---|
| 0 Discovery | Requirements, ADRs, module graph, ER design, alarm design, permissions, design system | Docs reviewed (this folder) |
| 1 Foundation | Gradle + version catalog + convention plugins, Hilt, design system, navigation, localization, theme, Room, DataStore, error model, test infra | App launches, navigates, persists settings; foundation tests pass |
| 2 Tasks & routines | Task/routine CRUD, recurrence, timeline, calendar, completion history | Records persist; recurrence tests pass |
| 3 Reminder engine | Reminder CRUD, alarm scheduling, channels, actions, reboot/time reconciliation, permissions | Engine tests (state transitions, dedupe, DST, zone changes) pass |
| 4 Health | Water, medicine, diet, sleep, fitness | Real persistence + shared reminder engine |
| 5 Ibadah | Prayer log + offline times, zikr, Quran log/goal, fasting, Hijri | Calculation & content tests pass |
| 6 Productivity | Habits, study, focus, finance, events, notes, journal | CRUD + history + reminders |
| 7 Analytics & personalisation | Dashboard customisation, statistics, templates, search | Metrics derived from data, correct after edits/deletes |
| 8 Security & backup | App lock, biometric, encrypted export/import, verification, restore | Valid / corrupt / incompatible backup tests pass |
| 9 Hardening | Lint, accessibility, localisation review, migration verification, policy review | Clean lint baseline, all tests green |
| 10 Release | Signing instructions, versioning, release notes, docs, limitations | `assembleRelease`/`bundleRelease` succeed |

The progress ledger (what was actually built and tested) is maintained in
[`progress.md`](progress.md).
