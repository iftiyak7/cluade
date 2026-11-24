# Hayat — Privacy

*Plain-language privacy statement. The landing page publishes the same content as the privacy policy.*

## Summary

* **Your data stays on your phone.** Tasks, reminders, health logs, prayers, notes, journal and
  every other record are stored only in Hayat's private storage on your device.
* **No account, no tracking.** No sign-up, no analytics, no advertising SDKs, no crash reporters,
  no Firebase.
* **Sync goes only to the server you choose.** New installs connect to a Hayat server (normally
  the owner's own VPS) on first launch. Your data is then uploaded over HTTPS to *that* server
  and nowhere else, so it is backed up and can move to a new phone. The phone stays the working
  copy: everything works offline and syncs later.
* **Announcements.** If *Announcements* is on (default), Hayat downloads a public list of tips and
  updates about once an hour. That request contains nothing about you.

## What sync stores on your server

When connected, the server stores: your account (email and a bcrypt password hash, or an access
key hash), sign-in refresh tokens (only their hashes), and the latest five snapshots of your Hayat
data (the same content as a backup file). Data is encrypted in transit (HTTPS); on the server it is
protected by the server's own security (it is not end-to-end encrypted). You can delete the
account and all its data at any time (`DELETE /api/me`, or by the owner in `/admin/users`).
Logging out keeps your data on the phone.

On the phone, the server address and tokens are stored in a file encrypted with an Android
Keystore key.

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

## Turning announcements off

Settings → General → **Announcements**. Turning it off stops all network requests and deletes
downloaded announcements. Everything else in Hayat works the same.

## Backups

Backups are files you create and keep (optionally encrypted with your password). Hayat never uploads
them. Android cloud backup and device-to-device transfer are disabled for Hayat's data.

## Permissions

See `permissions.md`. Hayat requests no location, contacts, SMS, storage or advertising permissions.

## Contact

The publisher's contact address is shown on the website's privacy page.
