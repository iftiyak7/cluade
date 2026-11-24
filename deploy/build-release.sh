#!/usr/bin/env bash
# Builds deployable bundles: dist/hayat-server-linux-{amd64,arm64}.tar.gz
# Each bundle contains the static server binary, the website and the install files.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/deploy/dist"
rm -rf "$OUT" && mkdir -p "$OUT"
python3 "$ROOT/website/build.py"
for arch in amd64 arm64; do
  stage="$OUT/hayat-$arch"
  mkdir -p "$stage/site"
  (cd "$ROOT/server" && CGO_ENABLED=0 GOOS=linux GOARCH=$arch go build -trimpath -ldflags="-s -w" -o "$stage/hayat-server" .)
  (cd "$ROOT/website" && tar --exclude='*.png' --exclude='build.py' --exclude='site.json' --exclude='README.md' -cf - .) | (cd "$stage/site" && tar -xf -)
  # og:image needs the PNG of the first screenshot
  cp "$ROOT/website/assets/screens/en-home.png" "$ROOT/website/assets/screens/bn-home.png" "$stage/site/assets/screens/"
  cp "$ROOT/deploy/"{install.sh,Caddyfile.tmpl,hayat.service,hayat-backup.service,hayat-backup.timer} "$stage/"
  tar -C "$OUT" -czf "$OUT/hayat-server-linux-$arch.tar.gz" "hayat-$arch"
  rm -rf "$stage"
done
(cd "$OUT" && sha256sum -- *.tar.gz > SHA256SUMS)
ls -la "$OUT"
