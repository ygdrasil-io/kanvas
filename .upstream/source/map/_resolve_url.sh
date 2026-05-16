#!/usr/bin/env bash
#
# Résout un `path:line` upstream Skia en URL GitHub clickable.
#
# Usage :
#   ./_resolve_url.sh "src/core/SkPoint.cpp:42"
#     → https://github.com/google/skia/blob/main/src/core/SkPoint.cpp#L42
#
#   awk -F'\t' '$2=="SkPoint::length" {print $4}' .upstream/source/map/**/*.tsv \
#     | xargs -I{} ./_resolve_url.sh {}
#
# Pin upstream : modifier UPSTREAM_REV ci-dessous pour épingler un commit SHA
# plutôt que `main`.

set -euo pipefail

UPSTREAM_REV="${UPSTREAM_REV:-main}"
URL_BASE="https://github.com/google/skia/blob/${UPSTREAM_REV}/"

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <path:line> [<path:line> ...]" >&2
    exit 2
fi

for loc in "$@"; do
    # Remplace le PREMIER `:` par `#L` ; on garde le reste tel quel.
    printf '%s%s\n' "${URL_BASE}" "${loc/:/#L}"
done
