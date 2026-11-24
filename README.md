# Hayat — *Your Life, Organized.*

Hayat (হায়াত) is a fully offline Android app for organising daily life: tasks, routines,
reminders and alarms, water, medicine, meals, sleep, fitness, habits, study and focus,
finance reminders, important dates, notes and a journal — plus an optional Islamic lifestyle
module (prayer tracking with on-device prayer times, zikr counters, Quran goals, fasting and
Hijri dates). English and Bengali, light and dark themes.

**Privacy model:** no `INTERNET` permission, no account, no backend, no analytics, no ads.
All data stays in the app's private storage; Android cloud backup and device transfer are
disabled. Users move data only through explicit, optionally AES-256-GCM-encrypted backup files.

## Status

| Area | State |
|---|---|
| All features listed above | **Implemented** — compile, persist to Room/DataStore, wired into navigation |
| Automated tests | **108 tests, all passing** (JVM unit + Robolectric incl. end-to-end Compose UI) |
| Android Lint | **0 errors** across all modules (incl. complete Bengali translations) |
| Release build | `assembleRelease` / `bundleRelease` succeed with R8 (unsigned; see `docs/release.md`) |
| On-device verification | **Not done.** The build environment had no emulator/KVM. Alarm delivery under Doze, OEM battery managers, reboot, notifications and biometrics are **unverified on real devices** — see `docs/known-limitations.md`. |

## Build

Requirements: JDK 17+ (21 used), Android SDK with **platform 37.2** (`platforms;android-37.2`)
and build-tools 36+. Gradle 9.9.2 is fetched by the wrapper.

```bash
./gradlew assembleDebug                 # debug APK  -> app/build/outputs/apk/debug/
./gradlew test testDebugUnitTest        # all unit + Robolectric tests
./gradlew lintDebug                     # Android Lint for every module
./gradlew assembleRelease bundleRelease # R8-minified release APK + AAB
```

Release signing: create an untracked `keystore.properties` (see `docs/release.md`). Without it
the release outputs are unsigned.

## Project layout

```
app/                 Application, MainActivity, navigation shell, Create sheet, app-level tests
build-logic/         Gradle convention plugins (android app/library/compose/feature/room/hilt/jvm)
core/
  model  common  time  domain  navigation  testing      pure Kotlin/JVM
  database  datastore  data  designsystem  ui
  notifications  alarms  security  backup              Android
feature/             23 presentation modules (one per feature area)
docs/                architecture, modules, database, alarm engine, permissions, security,
                     design system, testing, release, known limitations, roadmap, progress
```

Start with [`docs/architecture.md`](docs/architecture.md) and [`docs/progress.md`](docs/progress.md).
