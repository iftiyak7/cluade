# Hayat — Android Permission Matrix

Least privilege. Every permission below is requested only in context, when the user enables
the feature that needs it, and the app keeps working (with reduced capability) if denied.

| Permission | Type | Declared | When requested | If denied |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` (API 33+) | Runtime | ✅ | When the user first enables a reminder, or in onboarding step "Notifications" (optional) | Reminders still recorded; banner in Reminders & Settings explains notifications are off with a button to system settings. |
| `SCHEDULE_EXACT_ALARM` (API 31+) | Special access (user-granted in Settings) | ✅ | Explained in Settings → Reminders → *Exact timing*, and when creating an alarm-style reminder | Falls back to inexact `setAndAllowWhileIdle`; occurrences record `precision = INEXACT`; UI says reminders may be late. |
| `USE_FULL_SCREEN_INTENT` (API 34+ restricted) | Normal / special on 14+ | ✅ | Not requested proactively; status shown for alarm-style reminders | High-priority heads-up notification without full-screen UI. |
| `RECEIVE_BOOT_COMPLETED` | Normal | ✅ | Install time | — (needed to restore alarms after reboot) |
| `VIBRATE` | Normal | ✅ | Install time | — |
| `USE_BIOMETRIC` | Normal | ✅ (via androidx.biometric) | When enabling biometric unlock | PIN only. |
| `WAKE_LOCK` | Normal | merged from WorkManager | — | — |
| `USE_FINGERPRINT` | Normal (legacy) | merged from androidx.biometric for API < 28 | — | — |
| `INTERNET` | Normal | ✅ | Install time | Used only by `:core:network` to download the anonymous announcements feed (ADR-22). Settings → General → Announcements turns it off. |
| `ACCESS_NETWORK_STATE` | Normal | ✅ | Install time | Lets WorkManager wait for a connection before fetching the feed. |
| `FOREGROUND_SERVICE` | — | ❌ not used | Focus timer uses a persisted start instant + a scheduled end alarm instead of a long-running service. | — |

## Explicitly **not** requested

`ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
`READ_CONTACTS`, `READ_SMS`, `CALL_PHONE`, `READ/WRITE_EXTERNAL_STORAGE`,
`MANAGE_EXTERNAL_STORAGE`, `USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

* Cleartext HTTP is disabled (`usesCleartextTraffic="false"` + network security config trusting
  system CAs only).
* A build-time test (`ManifestPolicyTest`) fails the build if any forbidden permission appears in
  the merged manifest or cleartext traffic becomes allowed.
* Prayer times use a manually chosen city or typed coordinates — no location permission.
* Backups use the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) —
  no storage permission.

## Exported components

| Component | Exported | Why |
|---|---|---|
| `MainActivity` | yes (launcher) | Launcher entry. Deep links are internal only (no `<intent-filter>` with `http`). |
| `SystemEventReceiver` | yes | Required to receive `BOOT_COMPLETED` etc.; it only enqueues reconciliation and validates the action string. |
| `AlarmReceiver`, `AlarmActionReceiver` | **no** | Only reachable through Hayat's own immutable PendingIntents. |
| `AlarmActivity` | no | Launched by full-screen intent. |
| WorkManager / startup provider | as merged by library | — |
