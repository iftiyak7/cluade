# Hayat — Known Limitations

Honest list of what is not done, not verified, or limited by Android itself.

## Verification gaps
1. **No on-device testing.** Everything was verified with JVM and Robolectric tests only
   (`testing.md`). Alarm timing under Doze/OEM battery managers, reboot recovery, full-screen
   alarms, notification actions, BiometricPrompt and AndroidKeyStore behaviour are unverified.
2. **No performance measurement.** Cold start, jank, battery and memory were not measured; no
   Baseline Profile is generated yet (needs a device; `:benchmark` module not created).
3. **Accessibility** was built in (content descriptions, state descriptions, 48 dp targets,
   status never by colour alone, reduce-motion, high contrast) but not audited with TalkBack.
4. **Bengali copy** was written by the developer, not reviewed by a professional editor.

## Platform limits (cannot be fully solved by an app)
5. Reminders **cannot be guaranteed**: force-stop cancels all alarms until the app is opened;
   OEM battery managers may delay or kill them; without "Alarms & reminders" access timing is
   inexact. Hayat records missed occurrences and shows the precision it actually got.
6. After a reboot, alarms are restored after the user unlocks the device (no direct-boot support).
7. Per-reminder vibration/sound cannot override Android notification channels on Android 8+;
   users change them per channel in system settings (deep-linked).
8. With more than 400 active reminders, only the earliest 400 OS alarms are registered at a time;
   the rest are registered by later reconciliation passes (every 6 h or on events).

## Product scope deferred
9. Home-screen widget (Glance) and quick-add tile are not implemented (a deep link for quick-add
   water exists in `MainViewModel`).
10. Database at-rest encryption (SQLCipher) deferred — ADR-10, `security.md`.
11. No note attachments (images/files) — avoids file-path handling surface in v1.
12. Bundled religious text is intentionally minimal (7 short adhkar, surah names/verse counts).
    No Quran verses, translations, hadith or long duas are bundled; users add their own.
13. Prayer times are calculated (Adhan library) and may differ from local mosques; Hijri dates
    use Umm al-Qura tables with a ±2-day user offset and may differ from local moon sighting.
14. City list for prayer times is short (Bangladesh divisions/major cities + a few international
    cities); other places use manual coordinates.
15. No nutrition database: calories are user-entered only.
16. No automatic sleep or step detection: sleep and workouts/steps are logged manually.
17. Search uses SQL `LIKE` substring matching (correct for Bengali, no stemming or ranking).
18. `PluralsCandidate` lint warnings: some English count strings are not plural-aware
    ("1 activities"); Bengali is unaffected.
19. Undo for deletions is via confirmation dialogs only (soft-deleted rows are purged after 30 days
    but there is no in-app recycle bin yet).
20. Analytics "Activity" heat map counts recorded actions; it is not a weighted score.

## Data durability
21. Data exists only on the device. Uninstalling the app or losing the phone deletes it unless
    the user exported a backup file. This is stated in onboarding-adjacent screens (Privacy,
    Backup, About).

## Build environment notes (not product issues)
22. The development sandbox rate-limited Maven Central; builds there used a session-local Gradle
    init script pointing at Google's Maven Central mirror. The committed build uses the standard
    `google()` and `mavenCentral()` repositories.
23. Gradle wrapper checksum is not pinned yet (see `release.md` checklist).
