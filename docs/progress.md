# Hayat — Progress Ledger

Status keys: **Implemented & tested** · **Implemented** (covered only by smoke/E2E) · **Unverified on device** · **Planned**.

| Phase | Result | Evidence |
|---|---|---|
| 0 Discovery & architecture | Done | `architecture.md`, `database.md`, `alarm-engine.md`, `permissions.md`, `design-system.md`, `roadmap.md` |
| 1 Foundation | Implemented & tested | Version catalog + 7 convention plugins; AGP 9.5.2 / Kotlin 2.4.21 / compileSdk 37.2; Hilt; design system; type-safe navigation; en/bn resources; Room (45 tables, exported schema); typed DataStore; error model; test fakes |
| 2 Tasks & routines | Implemented & tested | Task CRUD with subtasks/tags/categories/recurrence/reminders, filters, sort, search, bulk actions; routines with activities, conflict detection, pause/duplicate/archive, per-day Completed/Skipped/Postponed/Missed logs; calendar day/week/month; templates |
| 3 Reminder engine | Implemented & tested — **unverified on device** | Recurrence engine, scheduler, linker, AlarmManager scheduler, notifier with Done/Snooze/Skip/Reschedule/Open, boot/update/time/zone receivers, reconciliation worker, channels, capability detection and settings deep links, full-screen alarm activity |
| 4 Health | Implemented (E2E smoke + domain tests) | Water (goals history, glass sizes, chart, reminders), medicine (schedules, dose log, stock & refill, adherence, disclaimer), diet (plans, logs, shopping list), sleep (manual log, averages, bedtime/wake reminders), fitness (plans, exercises, sessions, rest timer) |
| 5 Islamic lifestyle | Implemented & tested (calculations) | Prayer times (Adhan, reference-tested), manual city/coordinates, method/madhab/high-latitude/adjustments, prayer log, prayer reminders; zikr counters with presets; Quran log & goal; fasting log; Hijri with offset |
| 6 Productivity | Implemented (E2E smoke + domain tests) | Habits (build/reduce, streaks, heat map, pause), study subjects + Pomodoro (monotonic clock, process-death safe, end notification), finance reminders (repeat, mark paid, history), important dates (annual, ages, multiple lead reminders), notes (checklists, pin, tags, lock, reminder), journal, mood |
| 7 Analytics & personalisation | Implemented & tested (calculator) | Configurable dashboard cards (order/visibility), Progress tab (7/30 days), cross-record search (Bengali-safe), templates |
| 8 Security & backup | Implemented & tested — Keystore/biometric **unverified on device** | PIN + lockout + Keystore wrap, biometric unlock, timeout, FLAG_SECURE option, locked notes/journal; AES-256-GCM backup, integrity checks, preview, transactional restore |
| 9 Hardening | Partially done | Lint clean (0 errors), release manifest audited, E2E/smoke UI tests, migration test. **Not done:** device matrix, performance profiling, TalkBack audit, Bengali editorial review |
| 10 Release | Partially done | R8 release APK and AAB build (unsigned); signing instructions; versioning; release notes. **Not done:** signed build, store listing, Baseline Profile |

| 11 UI v2 | Implemented (screenshots + UI tests) | Ink/pastel design system, motion system, floating pill nav, redesigned Home and Progress, shared scaffold |
| 12 Server + admin | Implemented & tested (Go, 12 tests) | `server/`: admin panel, anonymous feed, security hardening |
| 13 Announcements in app | Implemented & tested | `:core:network`, Room v2 migration, worker, banners, inbox, notifications, opt-out |

## Numbers (last run)

* 128 Android/JVM tests + 12 Go server tests, 0 failures (6 opt-in screenshot tests skipped).
* ~16.6k lines of production Kotlin, ~1.9k lines of tests, 23 feature modules, 15 core modules.
* Release APK ≈ 3.7 MB (unsigned, R8), AAB ≈ 8.4 MB.

## Next steps (recommended order)

1. Run the device matrix in `release.md` on at least three OEMs and three API levels; fix findings.
2. Add `:benchmark` with Baseline Profile and startup benchmark; measure on a low-end device.
3. TalkBack and 200 % font-scale audit; Bengali editorial review; religious-content review.
4. Home-screen widget (water quick-add, next reminder).
5. Plural-aware count strings; in-app recycle bin for soft-deleted items.
