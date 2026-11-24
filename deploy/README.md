# Deploying Hayat (website + announcements + admin)

One small VPS (512 MB RAM is plenty) running Ubuntu 22.04+/Debian 12+. Caddy terminates HTTPS
with automatic Let's Encrypt certificates and forwards to the Hayat server on `127.0.0.1:8080`.

```
Internet ──443──▶ Caddy (TLS, gzip/zstd, no access logs) ──▶ hayat-server (127.0.0.1:8080, sandboxed systemd unit)
                                                              └─ /var/lib/hayat/hayat.db (+ daily backups)
```

## 1. Build the bundle (on any machine with Go 1.24+ and Python 3)

```sh
deploy/build-release.sh          # → deploy/dist/hayat-server-linux-{amd64,arm64}.tar.gz + SHA256SUMS
```

Use `amd64` for most VPS providers, `arm64` for ARM servers (e.g. Ampere, Graviton).

## 2. DNS

Create `A` (and `AAAA` if your VPS has IPv6) records for `yourdomain` and `www.yourdomain`
pointing to the VPS.

## 3. Install on the VPS

```sh
scp deploy/dist/hayat-server-linux-amd64.tar.gz you@your-vps:
ssh you@your-vps
tar -xzf hayat-server-linux-amd64.tar.gz && cd hayat-amd64
sudo ./install.sh --domain yourdomain --email you@yourdomain [--apk ~/hayat-release.apk]
sudo -u hayat HAYAT_DB=/var/lib/hayat/hayat.db /opt/hayat/hayat-server create-admin -user yourname
```

Open `https://yourdomain/admin`. Re-run `install.sh` with a newer bundle to update; data is kept.

## 4. Point the app at your server

```sh
./gradlew :app:assembleRelease -PhayatFeedUrl=https://yourdomain/api/v1/announcements
```

(or put `hayatFeedUrl=…` in `~/.gradle/gradle.properties`). Builds without it never use the network.

## What the installer does

* Installs Caddy from its official apt repository.
* Creates a locked-down `hayat` system user; binary in `/opt/hayat`, data in `/var/lib/hayat` (mode 700).
* systemd unit with strict sandboxing (read-only system, no capabilities, syscall filter, no new privileges).
* Daily consistent database backup at ~03:17 (`hayat-backup.timer`), 14 days kept.
* Writes `/etc/caddy/Caddyfile` (HTTPS, `www` → apex redirect, **no access logs**), validates and reloads it.
* Opens 80/443 in `ufw` if ufw is active (it never enables ufw itself, so SSH access is not affected).

## Operations

| Task | Command |
|---|---|
| Logs | `journalctl -u hayat -f` |
| Restart | `sudo systemctl restart hayat` |
| Change admin password | `sudo -u hayat HAYAT_DB=/var/lib/hayat/hayat.db /opt/hayat/hayat-server set-password -user NAME` |
| Manual backup | `sudo systemctl start hayat-backup` |
| Restore | stop `hayat`, copy a backup over `/var/lib/hayat/hayat.db` (owner `hayat`, mode 600), start `hayat` |

## Security checklist

- [ ] SSH: key-only login, root login disabled (`PermitRootLogin no`, `PasswordAuthentication no`).
- [ ] Unattended security upgrades: `sudo apt install unattended-upgrades`.
- [ ] Firewall allows only 22, 80, 443.
- [ ] Admin password is unique and ≥ 12 characters; consider a password manager.
- [ ] Copy `/var/lib/hayat/backups` off the server occasionally.
