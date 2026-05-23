#!/usr/bin/env bash
# gm-batch.sh — assign the next un-PORTED .cpp files alphabetically to N agents.
#
# Usage:
#   scripts/gm/gm-batch.sh                      # default: 5 agents × 10 files = 50 next files
#   scripts/gm/gm-batch.sh --agents=3 --per-agent=15
#   scripts/gm/gm-batch.sh --status=MISSING     # only consider MISSING (default: MISSING + STUB + PARTIAL)
#   scripts/gm/gm-batch.sh --plain              # filenames only, one per line (for scripting)
#
# Output (default human-readable):
#   AGENT 1 (10 files): aaa.cpp, all_bitmap_configs.cpp, ..., bigblurs.cpp
#   AGENT 2 (10 files): ...

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

. "$HERE/_lib.sh"

AGENTS=5
PER_AGENT=10
STATUS_FILTER="MISSING|STUB|PARTIAL|TEST_DISABLED"
PLAIN=0

for arg in "$@"; do
    case "$arg" in
        --agents=*)    AGENTS="${arg#--agents=}" ;;
        --per-agent=*) PER_AGENT="${arg#--per-agent=}" ;;
        --status=*)    STATUS_FILTER="${arg#--status=}" ;;
        --plain)       PLAIN=1 ;;
        --help|-h)     sed -n '2,12p' "$0" | sed 's/^# \?//'; exit 0 ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

TOTAL=$((AGENTS * PER_AGENT))

# Get all eligible .cpp basenames (already alphabetical from gm-status.sh)
eligible=()
while IFS= read -r e; do
    [ -n "$e" ] && eligible+=("$e")
done < <(
    "$HERE/gm-status.sh" 2>/dev/null \
        | tail -n +2 \
        | awk -F'\t' -v re="^(${STATUS_FILTER})$" '$4 ~ re { print $1 }' \
        | head -n "$TOTAL"
)

n_eligible=${#eligible[@]}
if [ "$n_eligible" -eq 0 ]; then
    echo "(no eligible .cpp files in status filter: $STATUS_FILTER)" >&2
    exit 0
fi

if [ "$PLAIN" -eq 1 ]; then
    printf '%s\n' "${eligible[@]}"
    exit 0
fi

printf 'Batch plan : %d agents × %d files = %d (got %d eligible)\n' \
    "$AGENTS" "$PER_AGENT" "$TOTAL" "$n_eligible"
printf 'Filter     : status ∈ {%s}\n\n' "$STATUS_FILTER"

idx=0
for ((a=1; a<=AGENTS; a++)); do
    end=$((idx + PER_AGENT))
    [ "$end" -gt "$n_eligible" ] && end="$n_eligible"
    if [ "$idx" -ge "$end" ]; then
        break
    fi
    slice=("${eligible[@]:idx:PER_AGENT}")
    n=${#slice[@]}
    printf 'AGENT %d (%d files): %s\n' "$a" "$n" "$(IFS=,; printf '%s' "${slice[*]}")"
    idx=$end
done
