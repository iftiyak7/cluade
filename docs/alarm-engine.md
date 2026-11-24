# Hayat — Reminder & Alarm Engine

## Goals and honest limits

The engine aims to deliver every reminder at its scheduled time, never twice, and to keep
an accurate record of what happened. It **cannot guarantee** delivery under all conditions:

* **Force-stop** (Settings → Force stop) cancels all alarms; Android delivers nothing until
  the user opens Hayat again. On next launch the reconciler marks old occurrences `MISSED`
  and reschedules.
* Without the *Alarms & reminders* special access (`SCHEDULE_EXACT_ALARM`, denied by default
  on Android 14+ for new installs), Android may delay alarms — typically by minutes, during
  Doze by longer. Hayat records `precision = INEXACT` and shows a banner.
* Some OEM battery managers kill or restrict apps beyond AOSP behaviour. The Settings →
  Reminders → *Reliability* screen explains how to exempt Hayat; it cannot detect every OEM.
* Notifications disabled at app or channel level → occurrence is still recorded and logged
  as `NOTIFICATION_BLOCKED`.

## Components

| Component | Module | Responsibility |
|---|---|---|
| `RecurrenceEngine` | `:core:time` | Pure function: `(Schedule, window, zone) → List<Instant>`. No Android. |
| `ReminderRepository` / `ReminderOccurrenceRepository` | `:core:domain` contracts, `:core:data` impl | Definitions, schedules, occurrences, action logs. |
| `ReminderScheduler` | `:core:domain` | Orchestration: materialise horizon, pick next due occurrence per reminder, call `AlarmScheduler`. |
| `AlarmScheduler` (interface) | `:core:domain` | `schedule(reminderRequestCode, at, style) → Precision`, `cancel(requestCode)`. |
| `AndroidAlarmScheduler` | `:core:alarms` | AlarmManager implementation (exact / alarm-clock / inexact). |
| `AndroidCapabilityChecker` (permission coordinator) | `:core:alarms` | `canScheduleExactAlarms`, notification permission & channel state, full-screen-intent capability, intents to the right Settings screens. |
| `AlarmReceiver` | `:core:alarms` | Receives the OS alarm, delivers all due occurrences of that reminder, reschedules. |
| `AlarmActionReceiver` | `:core:alarms` | Done / Snooze / Skip / dismissed (delete-intent) from notifications. |
| `SystemEventReceiver` (BootReceiver) | `:core:alarms` | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, exact-alarm permission state changed → enqueue reconciliation. |
| `ScheduleReconciliationWorker` | `:core:alarms` | Periodic (6 h, battery-not-low not required) + expedited one-off. Idempotent. |
| `NotificationChannelManager` | `:core:notifications` | Creates channels; maps reminder category → channel. |
| `ReminderNotifier` | `:core:notifications` | Builds notifications with unique PendingIntents. |
| `DeliveryLogRepository` (`RoomDeliveryLogRepository`) | `:core:data` | Writes `alarm_delivery_logs` (IDs and event types only, no user content). Pruned after 30 days. |
| `ReminderTargetHandler` (multibound) | `:core:data` | Propagates Done/Skip to the owning feature (medicine → dose log, water → intake, task → complete, habit → completion). |

## Scheduling semantics

Two distinct kinds of rule (never conflated):

1. **Wall-clock** (`ONCE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`): "08:00 every day" means 08:00
   on the local clock of the schedule's zone.
2. **Elapsed interval** (`INTERVAL`): "every 60 min between 08:00 and 22:00". Each day the
   window start is resolved to an instant, then occurrences are produced by adding the
   interval as an elapsed `Duration` until the window-end instant. Across a DST shift the
   wall-clock labels move, the spacing stays exactly 60 min.

### Time policies (all covered by unit tests in `:core:time`)

| Situation | Policy |
|---|---|
| Local time falls in a DST **gap** (e.g. 02:30 does not exist) | Shifted later by the gap length (java.time `ZonedDateTime.of` semantics) → fires 03:30. |
| Local time occurs **twice** (overlap) | Earlier offset only → fires once. |
| Monthly on 29/30/31 in a shorter month | `CLAMP` (default) → last day of month; `SKIP` → no occurrence that month. |
| Yearly on Feb 29 | `CLAMP` → Feb 28 in non-leap years; `SKIP` → leap years only. |
| Zone policy `DEVICE` (default) | Rule follows the device zone. On `TIMEZONE_CHANGED`, future un-acted occurrences are deleted and regenerated → "08:00" stays 08:00 local after travel. |
| Zone policy `FIXED` | Rule pinned to a stored zone (e.g. a recurring call in Asia/Dhaka while travelling). |
| Manual clock change (`TIME_SET`) | Instants are absolute; reconciliation runs, due ones within the grace window are delivered late, older ones become `MISSED`. |
| Missed (not delivered, > 15 min late) | Marked `MISSED`; one summary notification "N reminders were missed" — never a burst. |

## Occurrence lifecycle

```
SCHEDULED ──fire──▶ DELIVERED ──Done──▶ COMPLETED
    │                   │ ├──Skip──▶ SKIPPED
    │                   │ ├──Snooze─▶ SNOOZED ──fire──▶ DELIVERED …
    │                   │ └──replaced by a newer occurrence / end of day──▶ MISSED (if requires_completion) 
    ├──reminder edited / disabled / deleted──▶ CANCELLED (future only; history kept)
    └──not fired, > grace──▶ MISSED
```
Dismissing (swiping) a notification logs `DISMISSED` in `reminder_action_logs`; the status
stays `DELIVERED`. It is **not** completion.

## Algorithm

`ReminderScheduler.reconcile(now)`:
1. For each enabled, non-deleted reminder: materialise occurrences in `[now − grace, now + 7 d]`
   (max 200 per reminder) with `INSERT … ON CONFLICT IGNORE` on `(reminder_id, scheduled_at)`.
2. Occurrences still `SCHEDULED` with `scheduled_at < now − 15 min` → `MISSED`.
3. Next due = min over `SCHEDULED`/`SNOOZED` of `coalesce(snoozed_until, scheduled_at)`.
4. Register at most 400 OS alarms (earliest first); the rest are registered as earlier ones fire.
5. Cancel OS alarms for disabled/deleted reminders.

`AlarmReceiver.onReceive` (`goAsync`, ≤ 9 s): load due occurrences for the reminder (effective
time ≤ now + 30 s, status `SCHEDULED`/`SNOOZED`), atomically transition them to `DELIVERED`
(`UPDATE … WHERE status IN (…)` — a duplicate broadcast updates 0 rows → logged
`DUPLICATE_IGNORED`, nothing posted), post one notification, then schedule the next alarm.

## PendingIntent identity

* OS alarm: `AlarmReceiver`, action `hayat.alarm.FIRE`, data `hayat://reminder/{requestCode}`,
  request code = `reminders.request_code`, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. One per
  reminder; rescheduling replaces it.
* Notification actions: `AlarmActionReceiver`, data `hayat://occurrence/{id}/{action}` →
  distinct by `Intent.filterEquals`; all `FLAG_IMMUTABLE`.
* Notification id = `request_code` (a newer occurrence of the same reminder replaces the older
  notification instead of stacking).

## Notification channels

| Channel id | Importance | Used for |
|---|---|---|
| `alarms_critical` | HIGH, alarm audio usage, bypasses nothing by itself | User-selected *alarm style* reminders (wake-up, critical medicine) |
| `medicine` | HIGH | Medicine doses, refills |
| `routine` | DEFAULT | Routine activities, tasks with time |
| `water` | LOW (silent by default) | Water reminders |
| `ibadah` | DEFAULT | Prayer / zikr / Quran reminders |
| `tasks` | DEFAULT | Tasks, finance, events, study, generic |
| `system` | LOW | Missed-reminder summaries, backup results |

Users change sound/vibration per channel in Android settings (deep-linked from Hayat);
Hayat respects user overrides and never recreates a channel the user changed.

## Full-screen alarms

Only `style = ALARM` reminders request a full-screen intent (`AlarmActivity`, shown over the
lock screen). On Android 14+ `NotificationManager.canUseFullScreenIntent()` is checked; if
not allowed the same notification is posted at high priority without the full-screen
intent. Low-priority reminders never use full-screen UI.
