#!/usr/bin/env bash
# gm-tag-blocking.sh — add `override val renderCost = RenderCost.BLOCKING` to
# every Kotlin GM file that doesn't already declare renderCost.
#
# Usage:
#   scripts/gm/gm-tag-blocking.sh
#   scripts/gm/gm-tag-blocking.sh --check   # dry-run: only list files to patch

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GM_DIR="$ROOT/integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm"

CHECK=0
for arg in "$@"; do
    [ "$arg" = "--check" ] && CHECK=1
done

patched=0
for f in "$GM_DIR"/*/*.kt; do
    already=0
    if grep -q 'override val renderCost' "$f" 2>/dev/null; then
        already=1
    fi

    # Add import if missing
    if ! grep -q 'import org.graphiks.kanvas.skia.RenderCost' "$f" 2>/dev/null; then
        if grep -q 'import org.graphiks.kanvas.skia.RenderFamily' "$f" 2>/dev/null; then
            if [ "$CHECK" -eq 1 ]; then
                echo "$f  [missing import]"
            else
                sed -i '' '/import org.graphiks.kanvas.skia.RenderFamily/a\
import org.graphiks.kanvas.skia.RenderCost
' "$f"
            fi
        fi
    fi

    if [ "$already" -eq 1 ]; then
        continue
    fi
    if grep -q 'val minSimilarity' "$f" 2>/dev/null; then
        if [ "$CHECK" -eq 1 ]; then
            echo "$f  [missing property]"
        else
            sed -i '' '/val minSimilarity/i\
    override val renderCost = RenderCost.BLOCKING
' "$f"
        fi
        patched=$((patched + 1))
    fi
done

echo "Patched: $patched files"
