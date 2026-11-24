#!/usr/bin/env bash
# One-shot Hayat setup for a Hostinger "Ubuntu + CloudPanel" VPS. Safe to run again.
#   cd /opt && tar -xzf hayat-vps.tar.gz && bash hayat/deploy/setup-cloudpanel.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"
say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
[[ $EUID -eq 0 ]] || die "Run as root."
command -v clpctl >/dev/null || die "CloudPanel (clpctl) not found. Use the normal README steps instead."

IP="$(curl -fsS4 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')"
DEFAULT_DOMAIN="${IP//./-}.sslip.io"
PORT="${HAYAT_PORT:-18090}"   # host port for the API (localhost only); CloudPanel often uses 8080
export HAYAT_PORT="$PORT"
if ss -ltn | grep -q "127.0.0.1:${PORT} " && ! docker ps --format "{{.Names}}" 2>/dev/null | grep -q "^hayat-api-1$"; then
  die "Port ${PORT} is busy. Run again with another one, e.g.  HAYAT_PORT=18095 bash $0"
fi

say "1/7 Docker"
if ! docker compose version >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
# Docker may be installed but not running (e.g. an interrupted install).
if ! docker info >/dev/null 2>&1; then
  systemctl enable --now containerd docker 2>/dev/null || true
  sleep 3
fi
if ! docker info >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now containerd docker
fi
docker info >/dev/null 2>&1 || die "Docker is not running. Check: systemctl status docker"
docker compose version

say "2/7 Settings (.env)"
if [[ ! -f .env ]]; then
  read -r -p "Domain [${DEFAULT_DOMAIN}]: " DOMAIN
  DOMAIN="${DOMAIN:-$DEFAULT_DOMAIN}"
  read -r -p "Your email (for HTTPS certificate notices): " EMAIL
  [[ "$EMAIL" == *@*.* ]] || die "Please enter a valid email."
  cp .env.example .env
  sed -i "s|^DOMAIN=.*|DOMAIN=${DOMAIN}|" .env
  sed -i "s|^ACME_EMAIL=.*|ACME_EMAIL=${EMAIL}|" .env
  sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|" .env
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
  chmod 600 .env
else
  echo ".env already exists — keeping it."
fi
DOMAIN="$(grep '^DOMAIN=' .env | cut -d= -f2)"

say "3/7 Starting database and Hayat server (first build takes a few minutes)"
COMPOSE=(docker compose -f compose.yaml -f compose.cloudpanel.yaml)
"${COMPOSE[@]}" up -d --build postgres api
for _ in $(seq 1 60); do
  curl -fsS "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1 && break
  sleep 2
done
curl -fsS "http://127.0.0.1:${PORT}/api/health" || die "Server did not start. Logs: ${COMPOSE[*]} logs api"
echo

say "4/7 CloudPanel reverse proxy for ${DOMAIN}"
if clpctl site:add:reverse-proxy --domainName="${DOMAIN}" --reverseProxyUrl="http://127.0.0.1:${PORT}" \
    --siteUser='hayat' --siteUserPassword="$(openssl rand -hex 16)"; then
  echo "Site created."
else
  echo "(Site may already exist — continuing.)"
fi

say "5/7 HTTPS certificate (Let's Encrypt)"
if ! clpctl lets-encrypt:install:certificate --domainName="${DOMAIN}"; then
  echo "Certificate not issued yet. Check that ${DOMAIN} points to this server and ports 80/443 are open,"
  echo "then run:  clpctl lets-encrypt:install:certificate --domainName=${DOMAIN}"
fi

say "6/7 Daily database backup (03:17)"
mkdir -p /opt/hayat/backups && chmod 700 /opt/hayat/backups
LINE="17 3 * * * BACKUP_DIR=/opt/hayat/backups ${HERE}/backup.sh >> /opt/hayat/backups/backup.log 2>&1"
( crontab -l 2>/dev/null | grep -v 'hayat/deploy/backup.sh' ; echo "$LINE" ) | crontab -
BACKUP_DIR=/opt/hayat/backups "${HERE}/backup.sh" || echo "(First backup failed; it will retry tonight.)"

say "7/7 Admin account"
if [[ -t 0 ]]; then
  read -r -p "Admin username [admin]: " ADMIN
  ADMIN="${ADMIN:-admin}"
  "${COMPOSE[@]}" run --rm api create-admin -user "$ADMIN" || echo "(Admin may already exist. To change its password: ${COMPOSE[*]} run --rm api set-password -user $ADMIN)"
fi

echo
curl -fsS "https://${DOMAIN}/api/health" >/dev/null 2>&1 && HTTPS_OK=yes || HTTPS_OK=no
say "Done"
echo "  Health:       https://${DOMAIN}/api/health   (HTTPS working: ${HTTPS_OK})"
echo "  Admin panel:  https://${DOMAIN}/admin"
echo "  App server:   https://${DOMAIN}"
echo "  Secrets are in ${HERE}/.env (never share this file)."
