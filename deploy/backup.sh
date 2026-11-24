#!/usr/bin/env bash
# Daily PostgreSQL backup for the Hayat compose stack. Keeps 14 days.
# Install (as the user that runs docker):  crontab -e  →  17 3 * * * /opt/hayat/deploy/backup.sh >> /opt/hayat/backups/backup.log 2>&1
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${BACKUP_DIR:-$HERE/../backups}"
mkdir -p "$DEST"
chmod 700 "$DEST"
file="$DEST/hayat-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -f "$HERE/compose.yaml" --env-file "$HERE/.env" exec -T postgres \
  pg_dump -U hayat -d hayat --no-owner --clean --if-exists | gzip -9 > "$file.tmp"
mv "$file.tmp" "$file"
chmod 600 "$file"
find "$DEST" -name 'hayat-*.sql.gz' -mtime +14 -delete
echo "$(date -Is) backup ok: $file ($(du -h "$file" | cut -f1))"
