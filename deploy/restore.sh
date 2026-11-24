#!/usr/bin/env bash
# Restore a backup made by backup.sh:  ./restore.sh ../backups/hayat-YYYYMMDD-HHMMSS.sql.gz
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ -f "${1:-}" ]] || { echo "usage: $0 BACKUP.sql.gz" >&2; exit 2; }
read -r -p "This replaces the current database with $1. Type RESTORE to continue: " ok
[[ "$ok" == "RESTORE" ]] || exit 1
gunzip -c "$1" | docker compose -f "$HERE/compose.yaml" --env-file "$HERE/.env" exec -T postgres psql -U hayat -d hayat -v ON_ERROR_STOP=1 >/dev/null
docker compose -f "$HERE/compose.yaml" --env-file "$HERE/.env" restart api
echo "restored"
