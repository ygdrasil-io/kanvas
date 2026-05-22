#!/usr/bin/env bash
# gm-status.sh — for each upstream .cpp GM file, report coverage status.
#
# Usage:
#   scripts/gm/gm-status.sh                # full TSV report (header + 1 line per cpp)
#   scripts/gm/gm-status.sh --summary      # 1-line summary (counts by status)
#   scripts/gm/gm-status.sh --status=MISSING   # filter by status
#
# Status:
#   PORTED   — at least one matching .kt file with non-trivial onDraw body
#   STUB     — matching .kt exists but body is "// TODO: missing API" or trivial
#   IGNORED  — matching .kt is @Ignore'd / @Disabled (excluded from CI)
#   ALIAS    — matching .kt is just a typealias
#   PARTIAL  — multiple GMs registered, some covered, some not
#   MISSING  — no matching .kt file at all

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

# shellcheck source=_lib.sh
. "$HERE/_lib.sh"

MODE="full"
FILTER=""
for arg in "$@"; do
    case "$arg" in
        --summary) MODE="summary" ;;
        --status=*) FILTER="${arg#--status=}" ;;
        --help|-h)
            sed -n '2,12p' "$0" | sed 's/^# \?//'
            exit 0 ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

INDEX=/tmp/gm-kt-index.$USER.tsv
build_kt_index "$INDEX"

# Per-cpp loop -----------------------------------------------------------
n_ported=0; n_stub=0; n_ignored=0; n_alias=0; n_partial=0; n_missing=0; n_helper=0; n_test_disabled=0

emit() {
    local cpp_bn="$1" gm_count="$2" kt_list="$3" status="$4"
    [ -z "$FILTER" ] || [ "$status" = "$FILTER" ] || return 0
    if [ "$MODE" = "full" ]; then
        printf '%s\t%s\t%s\t%s\n' "$cpp_bn" "$gm_count" "$kt_list" "$status"
    fi
}

[ "$MODE" = "full" ] && printf 'cpp\tn_gm\tkt_files\tstatus\n'

for cpp in "$SKIA_GM_DIR"/*.cpp; do
    cpp_bn=$(basename "$cpp" .cpp)

    # Collect GM names (bash 3 compatible — no mapfile)
    gm_names=()
    while IFS= read -r name; do
        [ -n "$name" ] && gm_names+=("$name")
    done < <(extract_cpp_gms "$cpp")
    gm_count=${#gm_names[@]}

    # Resolve each gm_name → kt file (via index)
    matched_kts=()
    matched_statuses=()
    if [ "$gm_count" -gt 0 ]; then
        for name in "${gm_names[@]}"; do
            while IFS=$'\t' read -r _ kt_bn status; do
                [ -n "$kt_bn" ] || continue
                matched_kts+=("$kt_bn")
                matched_statuses+=("$status")
            done < <(awk -F'\t' -v k="$name" '$1==k' "$INDEX")
        done
    fi

    # Dedup kt files
    unique_kts=()
    if [ ${#matched_kts[@]} -gt 0 ]; then
        while IFS= read -r u; do
            unique_kts+=("$u")
        done < <(printf '%s\n' "${matched_kts[@]}" | sort -u)
    fi
    kt_list="-"
    [ ${#unique_kts[@]} -gt 0 ] && kt_list=$(IFS=,; printf '%s' "${unique_kts[*]}")

    # Compute aggregate status
    n_kt=${#unique_kts[@]}
    if [ "$gm_count" -eq 0 ]; then
        status="HELPER"
        n_helper=$((n_helper+1))
    elif [ "$n_kt" -eq 0 ]; then
        status="MISSING"
        n_missing=$((n_missing+1))
    else
        local_ported=0; local_stub=0; local_ignored=0; local_alias=0; local_test_disabled=0
        for s in "${matched_statuses[@]}"; do
            case "$s" in
                PORTED)        local_ported=$((local_ported+1)) ;;
                STUB)          local_stub=$((local_stub+1)) ;;
                IGNORED)       local_ignored=$((local_ignored+1)) ;;
                ALIAS)         local_alias=$((local_alias+1)) ;;
                TEST_DISABLED) local_test_disabled=$((local_test_disabled+1)) ;;
            esac
        done
        if   [ "$local_ported"        -eq "$gm_count" ]; then status="PORTED";        n_ported=$((n_ported+1))
        elif [ "$local_stub"          -eq "$gm_count" ]; then status="STUB";          n_stub=$((n_stub+1))
        elif [ "$local_ignored"       -eq "$gm_count" ]; then status="IGNORED";       n_ignored=$((n_ignored+1))
        elif [ "$local_alias"         -eq "$gm_count" ]; then status="ALIAS";         n_alias=$((n_alias+1))
        elif [ "$local_test_disabled" -eq "$gm_count" ]; then status="TEST_DISABLED"; n_test_disabled=$((n_test_disabled+1))
        else                                                  status="PARTIAL";       n_partial=$((n_partial+1))
        fi
    fi

    emit "$cpp_bn" "$gm_count" "$kt_list" "$status"
done

if [ "$MODE" = "summary" ]; then
    total=$((n_ported + n_stub + n_ignored + n_alias + n_partial + n_missing + n_test_disabled))
    printf 'GM coverage : %d cpp with DEF_GM\n' "$total"
    printf '  PORTED        : %4d (%5.1f%%)\n' "$n_ported"        "$(awk -v a=$n_ported        -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  TEST_DISABLED : %4d (%5.1f%%)\n' "$n_test_disabled" "$(awk -v a=$n_test_disabled -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  STUB          : %4d (%5.1f%%)\n' "$n_stub"          "$(awk -v a=$n_stub          -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  IGNORED       : %4d (%5.1f%%)\n' "$n_ignored"       "$(awk -v a=$n_ignored       -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  ALIAS         : %4d (%5.1f%%)\n' "$n_alias"         "$(awk -v a=$n_alias         -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  PARTIAL       : %4d (%5.1f%%)\n' "$n_partial"       "$(awk -v a=$n_partial       -v t=$total 'BEGIN{print 100*a/t}')"
    printf '  MISSING       : %4d (%5.1f%%)\n' "$n_missing"       "$(awk -v a=$n_missing       -v t=$total 'BEGIN{print 100*a/t}')"
fi
