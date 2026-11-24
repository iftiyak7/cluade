# Hayat — Release Guide

## Versioning

* `versionName` follows SemVer `MAJOR.MINOR.PATCH` (currently `1.0.0`).
* `versionCode` = monotonically increasing integer; bump on every store upload
  (suggested: `MAJOR*10000 + MINOR*100 + PATCH`).
* Any Room schema change bumps `HayatDatabase.VERSION`, adds a `Migration` and a migration test,
  and is called out in release notes. Backups record the schema version.

## Signing (never commit credentials)

1. Create a keystore outside the repository:
   `keytool -genkeypair -v -keystore ~/keys/hayat-release.jks -alias hayat -keyalg RSA -keysize 4096 -validity 10000`
2. Create `keystore.properties` in the project root (ignored by git):
   ```properties
   storeFile=/absolute/path/to/hayat-release.jks
   storePassword=…
   keyAlias=hayat
   keyPassword=…
   ```
3. Build: `./gradlew bundleRelease assembleRelease`
   * AAB: `app/build/outputs/bundle/release/app-release.aab`
   * APK: `app/build/outputs/apk/release/app-release.apk`
4. For Play: enrol in Play App Signing and upload the AAB.

Without `keystore.properties`, release outputs are produced **unsigned** (this is what the
development environment produced: `app-release-unsigned.apk` ≈ 3.7 MB, `app-release.aab` ≈ 8.4 MB).

## Pre-release checklist

- [ ] `./gradlew test testDebugUnitTest lintDebug assembleRelease bundleRelease` all green.
- [ ] Gradle wrapper: add `distributionSha256Sum` for Gradle 9.9.2 in `gradle/wrapper/gradle-wrapper.properties` (the checksum host was unreachable from the build environment).
- [ ] Device matrix (see below) passed, including airplane mode.
- [ ] Release merged manifest permissions reviewed (expected: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, VIBRATE, USE_BIOMETRIC, USE_FINGERPRINT (merged by androidx.biometric), WAKE_LOCK (WorkManager)).
- [ ] Play Console declarations: exact alarms (`SCHEDULE_EXACT_ALARM`, user-granted), full-screen intent (alarm-clock style wake-up/critical reminders only), Data safety: *no data collected or shared*.
- [ ] Privacy policy text: data stays on device; no collection.
- [ ] Religious content review by a qualified person (zikr phrases, prayer method labels).
- [ ] Bengali copy review by a native editor.
- [ ] Baseline Profile generated on a device (`benchmark` module to be added).
- [ ] Release notes updated.

## Device test matrix (manual)

| Scenario | API 26/28 | API 31/33 | API 34/36+ |
|---|---|---|---|
| Daily reminder fires at time (exact allowed) | | | |
| Exact alarms denied → arrives late, UI shows "may be late" | n/a | | |
| Doze (screen off 1 h+) | | | |
| Reboot: reminder after reboot fires | | | |
| Time-zone change: 08:00 stays 08:00 local | | | |
| Manual clock forward 3 h: missed summary, no burst | | | |
| Force-stop then open: missed recorded, alarms restored | | | |
| Done / Snooze / Skip / Reschedule from the shade | | | |
| Wake alarm full-screen on lock screen (and fallback) | | | |
| Notifications permission denied → banner, records kept | n/a | | |
| App lock + biometric + timeout | | | |
| Locked note via device credential | | | |
| Backup export (encrypted) → uninstall → install → restore | | | |
| Language switch en ↔ bn without restart | | | |
| TalkBack walkthrough of Home, Add, Task editor, Lock | | | |
| OEM: Xiaomi / Samsung / Oppo / Vivo / Tecno battery settings | | | |

## Release notes — 1.0.0 (initial)

First release. Offline tasks, routines (with templates), universal reminders & alarms, water,
medicine, meals, sleep, fitness, habits, study & focus timer, finance reminders, important dates,
notes/journal/mood, optional Ibadah module (prayer times & log, zikr, Quran, fasting, Hijri),
progress statistics, search, encrypted backup/restore, app lock. English and Bengali.
