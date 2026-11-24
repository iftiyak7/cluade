# Hayat — Privacy

*Plain-language privacy statement. The landing page publishes the same content as the privacy policy.*

## Summary

* **Your data stays on your phone.** Tasks, reminders, health logs, prayers, notes, journal and
  every other record are stored only in Hayat's private storage on your device.
* **No account, no tracking.** No sign-up, no analytics, no advertising SDKs, no crash reporters,
  no Firebase.
* **One download, nothing uploaded.** If *Announcements* is on (default), Hayat downloads a public
  list of tips and updates from the Hayat server about once an hour while you are online. The
  request contains nothing about you.

## What the announcements request contains

| Sent | Not sent |
|---|---|
| `GET /api/v1/announcements` over HTTPS | Your name, records, settings or language |
| `User-Agent: Hayat/<app version number>` | Device model, Android ID, advertising ID |
| `If-None-Match` (a hash of the last list, to avoid re-downloading) | Cookies or any account/session |

Like any internet connection, the server necessarily sees your IP address. The Hayat server uses it
only in memory to limit abusive request rates; it is **not logged or stored**. (The reverse proxy is
configured not to write access logs — see `deploy/`.)

The app chooses which announcements to show (language, app version, schedule) **on the phone**.

## Turning it off

Settings → General → **Announcements**. Turning it off stops all network requests and deletes
downloaded announcements. Everything else in Hayat works the same.

## Backups

Backups are files you create and keep (optionally encrypted with your password). Hayat never uploads
them. Android cloud backup and device-to-device transfer are disabled for Hayat's data.

## Permissions

See `permissions.md`. Hayat requests no location, contacts, SMS, storage or advertising permissions.

## Contact

The publisher's contact address is shown on the website's privacy page.
