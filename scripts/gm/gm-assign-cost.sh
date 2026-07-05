#!/usr/bin/env bash
# gm-assign-cost.sh — read scanner output and patch each GM's renderCost.
#
# Input format (from SkiaGmScanner):
#   PASS|idx|name|ms
#   FAIL|idx|name|ms|error
#   TIMEOUT|idx|name|timeoutMs
#
# PASS mapping:
#   < 10ms  → TRIVIAL
#   < 100ms → FAST
#   < 1000ms → MEDIUM
#   >= 1000ms → SLOW
# FAIL/TIMEOUT → BLOCKING (unchanged)
#
# Usage:
#   scripts/gm/gm-assign-cost.sh <scan-results.txt>

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GM_DIR="$ROOT/integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm"

INPUT="${1:?Usage: $0 <scan-results.txt>}"

cost_for_ms() {
    local ms="$1"
    if [ "$ms" -lt 10 ]; then echo "TRIVIAL"
    elif [ "$ms" -lt 100 ]; then echo "FAST"
    elif [ "$ms" -lt 1000 ]; then echo "MEDIUM"
    else echo "SLOW"
    fi
}

patched=0
not_found=0
while IFS='|' read -r status idx name ms rest; do
    [ -z "$name" ] && continue
    if [ "$status" = "TIMEOUT" ] || [ "$status" = "FAIL" ]; then
        cost="BLOCKING"
    else
        cost=$(cost_for_ms "$ms")
    fi

    # Find the GM file by its display name (match the `name = "..."` string)
    # Escape dots in the name for grep (they're literal, not regex)
    escaped_name=$(printf '%s' "$name" | sed 's/\./\\./g')
    found=$(grep -rl "override val name = \"$name\"" "$GM_DIR" 2>/dev/null || true)

    if [ -z "$found" ]; then
        echo "WARN: no file found for GM '$name'" >&2
        not_found=$((not_found + 1))
        continue
    fi

    # Check if there are multiple files with the same name (e.g. classes with same base name)
    fcount=$(echo "$found" | wc -l | tr -d ' ')
    if [ "$fcount" -gt 1 ]; then
        # Multiple matches — pick the first one where override val name = "..." exactly
        found=$(echo "$found" | while read -r f; do
            if grep -qE "override val name = \"$escaped_name\"" "$f" 2>/dev/null; then
                echo "$f"
                break
            fi
        done)
    fi

    if [ "$cost" = "BLOCKING" ]; then
        # Already BLOCKING, no change needed
        echo "  $name -> $cost (unchanged)"
    else
        sed -i '' "s/override val renderCost = RenderCost.BLOCKING/override val renderCost = RenderCost.$cost/" "$found"
        echo "  $name -> $cost (patched)"
    fi
    patched=$((patched + 1))
done < "$INPUT"

echo "Patched: $patched (not found: $not_found)"
