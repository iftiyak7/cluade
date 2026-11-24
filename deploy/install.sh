#!/usr/bin/env bash
# Hayat server installer / updater for Ubuntu 22.04+, Debian 12+.
# Run from the unpacked release folder:
#   sudo ./install.sh --domain example.com --email you@example.com [--apk /path/to/hayat.apk]
# Re-running it updates the binary and the website; data and admin accounts are kept.
set -euo pipefail

DOMAIN="" EMAIL="" APK=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain) DOMAIN="${2:-}"; shift 2 ;;
    --email) EMAIL="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,6p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
[[ $EUID -eq 0 ]] || die "run with sudo"
[[ "$DOMAIN" =~ ^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$ ]] || die "--domain must be a domain name like hayat.app"
[[ "$EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || die "--email must be a valid address (used for Let's Encrypt notices)"
[[ -z "$APK" || -f "$APK" ]] || die "--apk file not found: $APK"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ -x "$HERE/hayat-server" && -d "$HERE/site" ]] || die "run from the unpacked release folder (hayat-server and site/ missing)"
command -v apt-get >/dev/null || die "this script supports Debian/Ubuntu (apt) only"

echo "==> Installing packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl gnupg ca-certificates >/dev/null
if ! command -v caddy >/dev/null; then
  echo "==> Installing Caddy (official repository)"
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq
  apt-get install -y -qq caddy >/dev/null
fi

echo "==> Creating service user and folders"
id hayat >/dev/null 2>&1 || useradd --system --home /var/lib/hayat --shell /usr/sbin/nologin hayat
install -d -o root -g root -m 0755 /opt/hayat
install -d -o hayat -g hayat -m 0700 /var/lib/hayat /var/lib/hayat/backups
install -d -o root -g hayat -m 0750 /etc/hayat

echo "==> Installing server and website"
install -o root -g root -m 0755 "$HERE/hayat-server" /opt/hayat/hayat-server.new
mv -f /opt/hayat/hayat-server.new /opt/hayat/hayat-server
rm -rf /opt/hayat/site.new
cp -r "$HERE/site" /opt/hayat/site.new
if [[ -n "$APK" ]]; then install -d /opt/hayat/site.new/download && install -m 0644 "$APK" /opt/hayat/site.new/download/hayat.apk; fi
if [[ -z "$APK" && -f /opt/hayat/site/download/hayat.apk ]]; then install -d /opt/hayat/site.new/download && cp /opt/hayat/site/download/hayat.apk /opt/hayat/site.new/download/; fi
chown -R root:root /opt/hayat/site.new && chmod -R a+rX,go-w /opt/hayat/site.new
rm -rf /opt/hayat/site.old; [[ -d /opt/hayat/site ]] && mv /opt/hayat/site /opt/hayat/site.old
mv /opt/hayat/site.new /opt/hayat/site

cat > /etc/hayat/hayat.env <<ENV
HAYAT_ADDR=127.0.0.1:8080
HAYAT_DB=/var/lib/hayat/hayat.db
HAYAT_SITE_DIR=/opt/hayat/site
HAYAT_TRUST_PROXY=1
HAYAT_ADMIN_TZ=Asia/Dhaka
ENV
chmod 0640 /etc/hayat/hayat.env; chown root:hayat /etc/hayat/hayat.env

echo "==> Configuring systemd"
install -m 0644 "$HERE/hayat.service" "$HERE/hayat-backup.service" "$HERE/hayat-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now hayat.service hayat-backup.timer >/dev/null
systemctl restart hayat.service

echo "==> Configuring Caddy for $DOMAIN"
sed -e "s/__DOMAIN__/$DOMAIN/g" -e "s/__EMAIL__/$EMAIL/g" "$HERE/Caddyfile.tmpl" > /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
systemctl enable caddy >/dev/null && systemctl reload-or-restart caddy

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  echo "==> Opening ports 80 and 443 in ufw"
  ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null
fi

sleep 1
if curl -fsS http://127.0.0.1:8080/healthz >/dev/null; then echo "==> Hayat server is running"; else echo "!! server not responding; check: journalctl -u hayat -n 50"; fi

cat <<DONE

Done. Next steps:
  1. Point DNS for $DOMAIN (A/AAAA) and www.$DOMAIN to this server, if not done yet.
     Caddy gets the HTTPS certificate automatically once DNS resolves.
  2. Create your admin account (you will be asked for a password, min 12 characters):
       sudo -u hayat HAYAT_DB=/var/lib/hayat/hayat.db /opt/hayat/hayat-server create-admin -user YOURNAME
  3. Open https://$DOMAIN/admin
  Feed URL for the app build:  https://$DOMAIN/api/v1/announcements
  Backups: /var/lib/hayat/backups (daily, 14 days kept)
DONE
