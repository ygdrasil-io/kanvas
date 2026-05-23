#!/usr/bin/env bash
# gm-missing-apis.sh — list TODO()-flagged public API surface, ranked by call-site count.
#
# Strategy:
#   1. Find all TODO() declarations in kanvas-skia production code.
#   2. For each, extract its fully-qualified name (Class.method).
#   3. Grep all GM ports for call sites of that name.
#   4. Output: <call_count>\t<fqn>\t<decl_site>\t<sample_caller>
#      Sorted by call_count desc.
#
# Usage:
#   scripts/gm/gm-missing-apis.sh                 # full ranked list
#   scripts/gm/gm-missing-apis.sh --top=20        # top N only

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

. "$HERE/_lib.sh"

TOP=""
for arg in "$@"; do
    case "$arg" in
        --top=*) TOP="${arg#--top=}" ;;
        --help|-h) sed -n '2,12p' "$0" | sed 's/^# \?//'; exit 0 ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

# Find all "= TODO(" declarations in kanvas-skia production code.
# Captures (file:line) and extracts the enclosing function / property name.
todos=()
while IFS= read -r t; do
    [ -n "$t" ] && todos+=("$t")
done < <(
    grep -RnE '=[[:space:]]*TODO\(' "$KANVAS_SKIA_DIR" 2>/dev/null \
        | grep -v '/test/' \
        | sort -u
)

if [ ${#todos[@]} -eq 0 ]; then
    echo "(no TODO() declarations found in $KANVAS_SKIA_DIR)" >&2
    exit 0
fi

printf 'callers\tfqn\tdecl_site\tsample_caller\n'

rows=()
for line in "${todos[@]}"; do
    # line is like:  kanvas-skia/.../SkCanvas.kt:421:    public fun drawVertices(...): Unit = TODO(...)
    file=$(printf '%s' "$line" | cut -d: -f1)
    lineno=$(printf '%s' "$line" | cut -d: -f2)
    code=$(printf '%s' "$line"  | cut -d: -f3-)

    # extract a name to search for:  fun NAME( ... )  OR  val NAME   OR  override fun NAME(
    name=$(printf '%s' "$code" \
        | grep -oE '\bfun[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*' \
        | head -n1 | awk '{print $NF}')
    if [ -z "$name" ]; then
        name=$(printf '%s' "$code" \
            | grep -oE '\bval[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*' \
            | head -n1 | awk '{print $NF}')
    fi
    [ -z "$name" ] && continue

    # qualify with enclosing class (last `class XYZ` line above $lineno in $file)
    class=$(awk -v ln="$lineno" '
        NR>=ln {exit}
        /^[[:space:]]*(public|internal|private)?[[:space:]]*(abstract[[:space:]]+|sealed[[:space:]]+|open[[:space:]]+|data[[:space:]]+)?(class|object|interface)[[:space:]]+/ {
            for (i=1; i<=NF; i++) if ($i ~ /^(class|object|interface)$/) { print $(i+1); break }
        }' "$file" | tail -n1 | sed -E 's/[<:({].*//')
    [ -z "$class" ] && class="?"
    fqn="$class.$name"

    # count call sites in skia-integration-tests
    sample_caller=""
    n_callers=$(grep -REl "\b${name}\b" "$KT_GM_DIR" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$n_callers" -gt 0 ]; then
        sample_caller=$(grep -REl "\b${name}\b" "$KT_GM_DIR" 2>/dev/null | head -n1 | xargs basename)
    fi
    rows+=("$(printf '%s\t%s\t%s:%s\t%s' "$n_callers" "$fqn" "$(basename "$file")" "$lineno" "$sample_caller")")
done

# sort by call count desc, then by fqn
printf '%s\n' "${rows[@]}" | sort -t$'\t' -k1,1nr -k2,2 | { [ -n "$TOP" ] && head -n "$TOP" || cat; }
