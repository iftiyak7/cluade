# Deploy Hayat on your VPS (Docker Compose)

Runs three containers: **api** (Hayat server: app API, admin panel, landing page), **postgres**
(PostgreSQL 16, not reachable from the internet) and **caddy** (HTTPS with automatic Let's Encrypt
certificates). Tested images: `postgres:16-alpine`, `caddy:2-alpine`, Hayat image ≈ 32 MB.

```
Internet ─443─▶ caddy ──▶ api:8080 ──▶ postgres:5432 (internal network only)
```

Requirements: Ubuntu 22.04 or 24.04, 1 GB RAM, a domain whose DNS you control.

## 1. Point your domain at the server

At your DNS provider create an **A** record (and **AAAA** if the VPS has IPv6) for
`api.yourdomain.com` (or the bare domain) → your VPS IP. Optionally `www.` too.

## 2. Install Docker (copy-paste, as a sudo user)

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER" && newgrp docker
```

Firewall (if you use ufw):

```bash
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
```

## 3. Get the code and configure

```bash
sudo mkdir -p /opt/hayat && sudo chown "$USER" /opt/hayat
git clone https://github.com/iftiyak7/cluade.git /opt/hayat
cd /opt/hayat/deploy
cp .env.example .env
sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
nano .env        # set DOMAIN and ACME_EMAIL; choose SIGNUP=open or closed
chmod 600 .env
```

## 4. Start

```bash
docker compose up -d --build
docker compose ps                       # all three "running", postgres "healthy"
curl -s https://YOUR_DOMAIN/api/health  # {"service":"hayat","status":"ok",...}
```

The first HTTPS request can take ~30 s while Caddy obtains the certificate.

## 5. Create your admin account

```bash
docker compose run --rm api create-admin -user yourname
```

Open `https://YOUR_DOMAIN/admin` → **Announcements** (banners/notifications) and **Users**
(app accounts, access keys).

## 6. Connect the app

On first launch the app shows **Connect to your server**: enter `https://YOUR_DOMAIN`, tap
**Test connection** (shows `Connected · 120 ms`), then sign in with email + password (if sign-up is
open) or an access key from `/admin/users`. To pre-fill the URL in your build:

```bash
./gradlew :app:assembleRelease -PhayatServerUrl=https://YOUR_DOMAIN
```

## 7. Daily backups

```bash
mkdir -p /opt/hayat/backups
(crontab -l 2>/dev/null; echo "17 3 * * * /opt/hayat/deploy/backup.sh >> /opt/hayat/backups/backup.log 2>&1") | crontab -
/opt/hayat/deploy/backup.sh             # test it once now
```

Backups are gzip-compressed `pg_dump` files in `/opt/hayat/backups`, 14 days kept. Copy them off
the server regularly. Restore: `./restore.sh ../backups/hayat-YYYYMMDD-HHMMSS.sql.gz`.

## Update

```bash
cd /opt/hayat && git pull && cd deploy && docker compose up -d --build
```

## Operations

| Task | Command |
|---|---|
| Logs | `docker compose logs -f api` |
| Restart | `docker compose restart api` |
| Change admin password | `docker compose run --rm api set-password -user NAME` |
| Close sign-up | set `SIGNUP=closed` in `.env`, then `docker compose up -d` |
| Database shell | `docker compose exec postgres psql -U hayat -d hayat` |

## Security notes

* PostgreSQL has no published port; it lives on an internal Docker network.
* The api container runs as non-root on a read-only filesystem with all capabilities dropped.
* App auth: bcrypt passwords, 15-minute JWT access tokens (HS256), 60-day refresh tokens that
  rotate on every use; reuse of an old refresh token revokes the whole session family.
* Rate limits: auth endpoints 20/min per IP (+ lockout after 5 failed logins), API 120/min per user.
* Caddy writes no access logs. Keep `.env` private (`chmod 600`); never commit it.
* Harden SSH (`PasswordAuthentication no`, `PermitRootLogin no`) and enable unattended upgrades:
  `sudo apt-get install -y unattended-upgrades`.
