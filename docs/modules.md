# Hayat — Module Map

All modules are configured by convention plugins in `build-logic/`. Dependency rules are
described in `architecture.md` §3; they are enforced by module boundaries.

## Core (pure Kotlin/JVM — no Android SDK)

| Module | Responsibility |
|---|---|
| `:core:model` | Domain models and enums (tasks, routines, reminders, health, ibadah, productivity, preferences). |
| `:core:common` | `AppResult`/`AppError`, validation helpers, `TimeProvider`, `IdGenerator`, dispatchers, `@ApplicationScope`. |
| `:core:time` | `RecurrenceEngine` (date rules × time rules, DST/zone policies), `RecurrenceCodec`, `HijriCalendar`, date-range helpers. |
| `:core:domain` | Repository contracts, `ReminderScheduler` (engine orchestration), `ReminderLinker`, feature use cases, prayer-time calculation (Adhan), Quran metadata, zikr presets, analytics, templates, security/backup contracts. |
| `:core:navigation` | Type-safe `@Serializable` routes for every screen; deep-link keys. |
| `:core:testing` | In-memory fakes (repositories, alarm scheduler, notifier, clock) for JVM tests. |

## Core (Android)

| Module | Responsibility |
|---|---|
| `:core:database` | Room database (45 tables), entities, DAOs, converters, migrations, exported schema, bulk snapshot DAO. |
| `:core:datastore` | Typed JSON DataStore for `UserPreferences` with corruption recovery and lenient decoding. |
| `:core:data` | Repository implementations, mappers, Hilt bindings, reminder handler/contributor multibindings. |
| `:core:designsystem` | Theme (light/dark/high-contrast), Hind Siliguri typography, spacing/shapes, shared components. |
| `:core:ui` | Shared strings (en/bn), enum labels, locale-aware formatter with Bengali digits, pickers, forms, scaffold, ViewModel base. |
| `:core:notifications` | Notification channels (+ localized names). |
| `:core:alarms` | `AndroidAlarmScheduler`, `AndroidReminderNotifier`, receivers (alarm, action, boot/time/zone), reconciliation worker, full-screen `AlarmActivity`, capability checker. |
| `:core:security` | PBKDF2 PIN hashing, Keystore-wrapped PIN record, lockout, `AppLockController`, AES-GCM primitives. |
| `:core:backup` | Backup container format (gzip + SHA-256 or AES-256-GCM), schema-driven export/restore with validation. |

## Features (presentation only: screens + ViewModels)

| Module | Screens |
|---|---|
| `onboarding` | 6 optional steps: language, theme, day times, categories/Islamic module, notification permission, template |
| `dashboard` | Home cards (configurable), All-features grid |
| `calendar` | Day timeline / week / month, Hijri label |
| `tasks` | List (filters, sort, search, categories, bulk), editor (subtasks, tags, recurrence, reminder), detail |
| `routines` | List, editor (activities, conflicts), detail (per-day status log, 7-day history), **templates** |
| `reminders` | Upcoming / mine / missed / all, editor (schedules), reschedule (notification action target) |
| `water`, `medicine`, `diet`, `sleep`, `fitness` | Health modules |
| `ibadah` | Today (prayer times + log), zikr list, counter, editor, Quran log, fasting, prayer settings |
| `habits`, `study` (+ focus timer), `finance`, `events`, `notes` (+ journal, mood) | Productivity modules |
| `analytics` | Progress tab (7/30-day statistics) |
| `search` | Cross-record search |
| `backup` | Export / restore with passphrase and preview |
| `settings` | General, appearance, reminders & reliability, lifestyle, privacy & security, home cards, about |
| `lock` | PIN pad + biometric unlock overlay |

### Refinements vs. the original proposal

* Templates live in `:feature:routines` (they produce routines; no separate module needed).
* Zikr, prayer and Quran are one `:feature:ibadah` module; journal/mood are in `:feature:notes`;
  focus is in `:feature:study` (ADR-4).
* No `:benchmark` module yet: Baseline Profiles must be generated on a device/emulator, which was
  not available. See `known-limitations.md`.
