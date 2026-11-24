# Hayat v3 — bug-fix test checklist

Status keys: ✅ automated test passes · 📸 verified in rendered screenshot (Robolectric, 411×891 dp) ·
⚠️ not verifiable here (needs a physical device).

| # | Bug (Section 1) | Fix | Evidence |
|---|---|---|---|
| 1 | Greeting wrong ("Good night" at 7:25 AM) | `DayPart.of(hour)` with 05–12 / 12–17 / 17–21 / 21–05; hour from the **device time zone** via `TimeProvider.zonedNow()`; a 30 s ticker keeps it current while the app stays open. Bengali: শুভ সকাল / শুভ দুপুর / শুভ সন্ধ্যা / শুভ রাত্রি. | ✅ `DayPartTest` (all boundaries; 01:25 UTC = 07:25 Dhaka → Morning) · 📸 `en-home`, `bn-home` show "Good morning" / "শুভ সকাল" at 08:xx Dhaka |
| 2 | Progress visuals don't match values (42 % fills ~36 %) | Removed the segmented bar (gaps under-filled it). `ProgressRing` now insets its round caps (`ringArc`) so the visible arc = value × 360°; `LinearMeter` draws an exact-width bar. Percent text and visuals share one rounded integer. | ✅ `RingArcTest` (visible degrees = 360 × p for 1–100 %) · 📸 42 % ring in `en-home` |
| 3 | Bangla half translated | Formatter: `ml()` → "২৫০ মিলি", `minutes()`, `time()` → "সকাল ১১:৪৬" (day-period words), `countdown()`, `ago()`, Bengali weekday/month names; all new screens have bn strings; Bengali UI uses Hind Siliguri only (English uses Inter). | ✅ `FormatterTest` · 📸 `bn-home`, `bn-home-2`, `bn-progress` |
| 4 | Week strip cut off | `WeekStrip`: 7 equal-width cells that always fit, month label, chevrons, swipe (pager) for previous/next week, respects first day of week. | 📸 full Sun–Sat row in `en-home`, `bn-home` |
| 5 | Duplicates (Quick add bar, settings in two places) | "Quick add" bar removed — only the centre "+" FAB; the Home header no longer has a settings icon (Customise home moved to the bottom of Home and Settings). | 📸 `en-home` · ✅ `AppFlowTest`, `FeatureSmokeTest` (FAB opens every create action) |
| 6 | Ambiguous chart labels, overshooting curves | `ValueBarChart`: bars with exact heights, dashed y-axis guides with values (0, ½, max), labels "Wed Thu Fri Sat Sun Mon Tue" (30 days: every 5th date). `SmoothLineChart` now draws straight segments. | ✅ `FormatterTest.chart weekday labels` · 📸 `en-progress`, `bn-progress` |
| 7 | Unused features shown as "Focus 0 min / Workouts 0" | Progress shows real figures only when used; otherwise a friendly empty state with a **Start** button (Focus timer / Fitness). | 📸 `en-progress-2` |
| 8 | Hijri date shown as "14/6/1448" | `fmt.hijri()` → "14 Jumada II 1448" / "১৪ জমাদিউস সানি ১৪৪৮" on Home, Calendar and Ibadah. | ✅ `DayPartTest.hijri month names`, `FormatterTest.hijri` · 📸 header chip in `en-home`, `bn-home` |

## Server connection (Section 4)

| Check | Evidence |
|---|---|
| Connect screen: Test connection → "Connected · 120 ms" or precise error (wrong URL, offline, SSL, wrong credentials, not a Hayat server, http refused) | ✅ `AppFlowTest` (SSL error message, http refused, sign-in locked until test passes, full connect → Home) · 📸 `en-connect` |
| Tokens + URL stored encrypted (Android Keystore AES-GCM), HTTPS only | code: `KeystoreSessionStore`, network security config · ⚠️ Keystore itself needs a device |
| Settings → Server: status, last sync, re-test, change server, log out | 📸 `en-server` |
| Offline queue, retry/backoff, last-synced | ✅ `SyncEngineTest` (8 cases: push, pull, offline queue kept, conflict, refresh/sign-out, status, URL normalisation) |
| Backend: JWT access + rotating refresh, rate limits, CORS, validation, /api/health | ✅ 22 Go tests on SQLite **and** PostgreSQL 16 |
| Docker Compose stack (api + postgres + Caddy) | ✅ built and run here: health, register, sync PUT/GET, landing page, admin creation, `backup.sh`; Caddyfile validated by `caddy validate` (real certificate needs your domain) |

## Not verified here

* ⚠️ Cold start < 1.5 s, 60 fps scrolling, haptics, Keystore, real network conditions — need a physical device.
* ⚠️ Let's Encrypt certificate issuance — needs your domain and DNS.
