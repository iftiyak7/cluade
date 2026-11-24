# Hayat — Testing

Run everything: `./gradlew test testDebugUnitTest` (JVM modules use `test`, Android modules
`testDebugUnitTest`). Lint: `./gradlew lintDebug`.

## Results of the last full run

**108 tests, 0 failures.** Android Lint: 0 errors in all modules (warnings: mostly
`PluralsCandidate` for count strings).

| Module | Tests | Kind | What is covered |
|---|---:|---|---|
| `core:time` | 24 | JVM | Daily/every-N/weekly/every-N-weeks/monthly/yearly rules; 29/30/31 clamp vs skip; Feb 29; start/end bounds; DST gap shift & overlap single-fire (America/New_York); elapsed-interval spacing across DST; windows crossing midnight; device vs fixed zone; codec round-trip & rejection; Hijri (1 Ramadan 1447 = 2026-02-18) with offset; week/month grids. |
| `core:domain` | 36 | JVM | **Reminder scheduler**: bounded horizon, first alarm, duplicate broadcast ignored, dismiss ≠ done, done propagates once, snooze & redelivery, reschedule validation, never-fired → missed without a burst + summary, late-within-grace delivery, unanswered → missed, disable cancels, exact-alarm denial → inexact recorded, notifications blocked logged, **time-zone change keeps wall-clock**, quiet hours silent, per-reminder cap, editing regenerates future & keeps history, in-app completion, linker idempotence, OS alarm cap. Plus task filters/sort/Bengali search/validation, routine timeline & conflicts, templates, water goals, medicine dose planning, habit streaks (build/reduce/unscheduled days), finance amounts & due dates, annual events/ages, focus timer, PIN policy, sleep, Quran totals (= 6236), zikr presets, **prayer times vs. the published Adhan reference** (Raleigh 2015-07-12, ±1 min), Dhaka ordering/adjustments/madhab, analytics from records. |
| `core:data` | 10 | Robolectric + in-memory Room | Unique stable request codes, schedule replacement, scheduler end-to-end on Room (dedupe, delivery, Done → task completed, reminder removed), recurring task history, subtasks/tags + FK cascade, FK enforcement, stock adjustment exactly once & revert, **Bengali substring search, literal `%`/`_`, locked notes hidden**, atomic snapshot rollback, water goal versioning, zikr counts never negative. |
| `core:database` | 3 | Robolectric | Schema JSON exported for every version, migration chain completeness, `MigrationTestHelper` validation with data surviving. |
| `core:datastore` | 3 | JVM | Round trip & persistence, corrupt file → defaults, unknown enum values tolerated. |
| `core:alarms` | 4 | Robolectric (ShadowAlarmManager) | Exact when allowed, replace not duplicate, inexact fallback when denied, alarm-clock style, cancel. |
| `core:security` | 4 | JVM | PIN hashing never stored raw, wrong attempts → lockout (persisted), lost key never unlocks, AES-GCM tamper detection, lock controller timeout. |
| `core:backup` | 6 | Robolectric + Room | Table allow-list equals schema, plain & encrypted round trip, wrong/missing passphrase, tampered/truncated/foreign files, newer schema, unknown table, **SQL-injection column name**, orphan rows → rollback with existing data intact, stale OS alarms cancelled after restore. |
| `core:common`, `core:model` | 6 | JVM | Validation, LIKE escaping, error mapping, weekday sets, habit success, quiet hours. |
| `app` | 12 | Robolectric + Hilt + Compose UI | Manifest policy (no forbidden permissions, backup disabled, receivers not exported); Bengali/English resources; **end-to-end**: onboarding skip, all bottom tabs, feature grid, task creation persisted, empty-title validation; **every feature screen and every Create editor opens without crashing**; water logging persists; zikr counter increments; **app lock** hides content, rejects wrong PIN, unlocks with correct PIN. |

Feature modules have no module-local unit tests; their ViewModels and screens are exercised by
the app-level end-to-end and smoke tests against the real Hilt graph and database.

## Not verified (requires devices)

The environment had **no emulator or physical device** (no KVM). The following were therefore
**not** tested and must be before release:

* Alarm delivery timing on API 26, 29, 31, 33, 34, 36/37 devices; Doze and App Standby buckets.
* OEM battery managers (Xiaomi, Oppo/Realme, Vivo, Samsung, Huawei/Honor, Tecno/Infinix).
* Reboot recovery (`BOOT_COMPLETED`), app update (`MY_PACKAGE_REPLACED`), manual clock and time-zone changes on a device.
* Full-screen alarm on the lock screen (Android 14+ `USE_FULL_SCREEN_INTENT` policy).
* Notification actions from the shade (Done/Snooze/Skip/Reschedule) on real launchers.
* BiometricPrompt and AndroidKeyStore (Robolectric uses a test wrapper).
* Per-app language switching on Android 12 and below (AppCompat locale storage).
* TalkBack walkthrough, large font scales (200 %), RTL-unrelated layout overflow in Bengali.
* Airplane-mode run of every feature (the app has no network permission, so this is expected to pass).
* Performance: cold start, scrolling, battery usage; Baseline Profile generation.

Recommended device test matrix and scripts are in `release.md`.
