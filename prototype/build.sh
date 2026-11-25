#!/usr/bin/env bash
# Rebuild the standalone prototype from its editable sources.
set -euo pipefail
cd "$(dirname "$0")"

cat \
  src/_head.html \
  src/style-a.css \
  src/style-b.css \
  src/style-c.css \
  src/_mid.html \
  src/app-a.js \
  src/app-b.js \
  src/app-c.js \
  src/_tail.html \
  > index.html

echo "Built prototype/index.html ($(wc -c < index.html) bytes)"