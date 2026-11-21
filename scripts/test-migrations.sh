#!/usr/bin/env bash
# Applies all migrations to a throwaway Postgres and checks key invariants.
# Usage: scripts/test-migrations.sh   (needs Postgres 16 binaries, or set $PGBIN)
# The temp cluster is stopped on exit; its mktemp dir is left for the OS to clean.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGBIN="${PGBIN:-$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | tail -1)}"
D="$(mktemp -d)"
PORT="${PGPORT_TEST:-55433}"

as_pg() {
  if [ "$(id -u)" = 0 ]; then su postgres -c "$*"; else bash -c "$*"; fi
}
[ "$(id -u)" = 0 ] && chown postgres "$D"
trap 'as_pg "$PGBIN/pg_ctl -D $D/data stop -m immediate" >/dev/null 2>&1 || true' EXIT

as_pg "$PGBIN/initdb -D $D/data -A trust" >/dev/null
as_pg "$PGBIN/pg_ctl -D $D/data -o '-p $PORT -k $D' -l $D/pg.log -w start" >/dev/null
P=(psql -h "$D" -p "$PORT" -U postgres -v ON_ERROR_STOP=1 -q)

for f in "$ROOT"/database/migrations/*.sql; do
  echo "apply $(basename "$f")"
  "${P[@]}" -f "$f"
done

expect_fail() {
  if "${P[@]}" -c "$2" >/dev/null 2>&1; then echo "FAIL: $1"; exit 1; else echo "ok: $1"; fi
}

"${P[@]}" -c "INSERT INTO tokens(mint,creator,bonding_curve,created_slot,created_at) VALUES ('M','C','B',1,now());"
"${P[@]}" -c "INSERT INTO orders(intent_id,side,mode,mint,requested_lamports,min_out) VALUES ('00000000-0000-0000-0000-000000000001','buy','paper','M',1000,1);"
expect_fail "duplicate buy for same intent rejected" \
  "INSERT INTO orders(intent_id,side,mode,mint,requested_lamports,min_out) VALUES ('00000000-0000-0000-0000-000000000001','buy','paper','M',1000,1);"
"${P[@]}" -c "INSERT INTO audit_log(actor_kind,action,hash) VALUES ('system','boot','\\x00');"
expect_fail "audit_log update blocked" "UPDATE audit_log SET action='x';"
expect_fail "audit_log delete blocked" "DELETE FROM audit_log;"
expect_fail "score out of range rejected" \
  "INSERT INTO token_scores(mint,safety_score,momentum_score,liquidity_score,opportunity_score,factors) VALUES ('M',101,0,0,0,'[]');"
"${P[@]}" -c "INSERT INTO config_versions(config,active) VALUES ('{}',true);"
expect_fail "only one active config version" "INSERT INTO config_versions(config,active) VALUES ('{}',true);"
echo "migrations OK"
