# Hayat server

> v3: PostgreSQL (production) or SQLite (dev/tests), app accounts with JWT + rotating refresh tokens,
> and offline-first snapshot sync. Deploy with Docker Compose — see `../deploy/README.md`.
>
> App API: `GET /api/health` · `POST /api/auth/{register,login,key,refresh,logout}` · `GET|DELETE /api/me`
> · `GET|PUT /api/sync/snapshot` (ETag / If-Match revisions, 10 MB max, last 5 kept).
> Admin: `/admin` (announcements), `/admin/users` (accounts, access keys).

One Go binary that serves:

* the landing page (static files from `HAYAT_SITE_DIR`),
* `GET /api/v1/announcements` — the anonymous, read-only feed the app polls,
* `/admin` — the admin panel for publishing banners and notifications.

## Privacy by design

* The feed takes **no parameters** and sets **no cookies**; the app filters by language and version
  on the phone. No accounts, device IDs, analytics or tracking.
* Client IPs are used only in memory for rate limiting and are never logged or stored.
* The database stores only admin accounts, admin sessions (token hashes), announcements and an
  admin audit log.

## Security

| Area | Measure |
|---|---|
| Passwords | bcrypt (cost 12), min 12 characters; unknown usernames take the same time (dummy hash) |
| Brute force | 5 failed logins per 15 min per IP **and** per username → locked out |
| Sessions | 256-bit random token, only its SHA-256 stored; `__Host-` cookie, `HttpOnly`, `Secure`, `SameSite=Strict`, 12 h expiry; password change revokes all sessions |
| CSRF | per-session token on every form + `Origin` check on all state-changing requests |
| XSS | `html/template` auto-escaping, strict CSP (`script-src 'self'`, no inline script), plain-text content only |
| Links | announcements may only link to `https://` URLs or allow-listed `hayat://` screens; images must be `https://` |
| Headers | CSP, HSTS, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, `noindex` on admin |
| Limits | body size caps, server timeouts, feed rate limit 60/min per IP, 50 live items max |
| Static site | no directory listings, dotfiles never served |

## Run locally

```sh
go build -o hayat-server .
./hayat-server create-admin -user yourname          # prompts for a password
HAYAT_INSECURE_DEV=1 HAYAT_SITE_DIR=../website ./hayat-server serve
# open http://127.0.0.1:8080/admin
```

`HAYAT_INSECURE_DEV=1` allows cookies over plain HTTP and must never be used in production.
Production runs behind Caddy (automatic HTTPS) — see `../deploy/`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `HAYAT_ADDR` | `127.0.0.1:8080` | listen address (keep it on localhost behind the proxy) |
| `HAYAT_DB` | `hayat.db` | SQLite database file |
| `HAYAT_SITE_DIR` | — | landing page directory |
| `HAYAT_ADMIN_TZ` | `Asia/Dhaka` | time zone for the admin schedule fields |
| `HAYAT_TRUST_PROXY` | `1` | use the proxy's `X-Forwarded-For` (only from loopback) for rate limiting |

## Feed format (`version: 1`)

```json
{"version":1,"generated_at":"2026-11-24T06:00:00Z","items":[{
  "id":1,"kind":"banner|notification|both","title_en":"…","body_en":"…","title_bn":"…","body_bn":"…",
  "cta_label_en":"…","cta_label_bn":"…","cta_url":"hayat://ibadah","image_url":"https://…",
  "lang":"all|en|bn","min_version":0,"priority":50,"starts_at":"…","ends_at":"…","updated_at":"…"}]}
```

Responses carry an `ETag`; the app sends `If-None-Match` and gets `304 Not Modified` when nothing changed.

## Tests

```sh
go test ./...
```

Covers login lockout, timing-safe unknown users, cookie flags, hashed session storage, CSRF and
Origin checks, publish → feed → unpublish, schedule windows, unsafe-link rejection, output
escaping, session revocation on password change, security headers, feed rate limiting and static
file safety.
