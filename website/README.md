# Hayat website

Static, bilingual (English `/`, Bengali `/bn/`) landing page and privacy policy.

* No trackers, cookies, analytics or third-party requests; fonts are self-hosted (Hind Siliguri, OFL).
* No inline scripts or styles, so the server's strict Content-Security-Policy applies unchanged.
* Pages are generated from `build.py` so both languages stay in sync.

```sh
# set domain, contact_email (and play_url when published) in site.json, then:
python3 build.py
```

Screenshots in `assets/screens/` come from the app's screenshot test
(`./gradlew :app:testDebugUnitTest --tests '*ScreenshotCapture*' -PscreenshotsDir=...`);
convert them to WebP with the snippet in the repository history or any image tool.

The APK download link (`apk_url`, default `/download/hayat.apk`) is served from the same directory —
the deploy script copies a signed release APK to `download/hayat.apk`.
