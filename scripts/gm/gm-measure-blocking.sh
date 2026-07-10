#!/usr/bin/env bash
# Measure the current BLOCKING Skia GMs in timeout-safe batches of five.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PYTHON_HELPER="$HERE/gm_measure_blocking.py"

if [[ "${GM_SCAN_FIXTURE:-}" == "1" ]]; then
    names="$1"
    output="$2"
    if [[ "$names" == "1,2,3,4,5" ]]; then
        printf 'PASS|1|a|12\n' >> "$output"
        exit 124
    fi
    printf 'PASS|%s|fixture-%s|12\n' "$names" "$names" >> "$output"
    exit 0
fi

usage() {
    printf '%s\n' \
        'Usage: scripts/gm/gm-measure-blocking.sh [--output DIR] [--names a,b,c]' \
        '       scripts/gm/gm-measure-blocking.sh --self-test' \
        '' \
        'Runs exactly three attempts in batches of five, with a 10-second scanner timeout.'
}

discover_blocking_names() {
    (
        cd "$ROOT"
        ./gradlew --no-daemon -q -Pkanvas.scan.listBlocking=true :integration-tests:skia:generateSkiaScan
    ) | sed -n 's/^GM_ENTRY|\([0-9][0-9]*\)|.*$/\1/p'
}

run_scan() {
    local names="$1"
    local output="$2"
    local rc

    output="$(cd "$(dirname "$output")" && pwd)/$(basename "$output")"
    : > "$output"
    if [[ "${GM_MEASURE_SELF_TEST:-}" == "1" ]]; then
        GM_SCAN_FIXTURE=1 "$GM_SCAN_COMMAND" "$names" "$output" || rc=$?
        rc="${rc:-0}"
    else
        (
            cd "$ROOT"
            ./gradlew --no-daemon -q \
                "-Pkanvas.scan.indices=$names" \
                -Pkanvas.scan.timeout=10 \
                "-Pkanvas.scan.output=$output" \
                :integration-tests:skia:generateSkiaScan
        ) || rc=$?
        rc="${rc:-0}"
    fi

    python3 "$PYTHON_HELPER" \
        --append-scanner-records \
        --names "$names" \
        --attempt "$CURRENT_ATTEMPT" \
        --scanner-output "$output" \
        --attempts-ndjson "$ATTEMPTS_NDJSON"
    SCAN_RC="$rc"
}

run_campaign() {
    local attempt start end batch_names fallback_name raw_output
    for ((attempt = 0; attempt < ATTEMPT_COUNT; attempt++)); do
        CURRENT_ATTEMPT="$attempt"
        for ((start = 0; start < ${#GM_NAMES[@]}; start += 5)); do
            end=$((start + 5))
            if ((end > ${#GM_NAMES[@]})); then
                end=${#GM_NAMES[@]}
            fi
            batch_names="$(IFS=,; printf '%s' "${GM_NAMES[*]:start:end-start}")"
            raw_output="$(mktemp "$OUTPUT_DIR/.scanner.XXXXXX")"
            run_scan "$batch_names" "$raw_output"
            rm -f "$raw_output"

            if [[ "$SCAN_RC" == "124" ]]; then
                printf '%s\n' "$batch_names" >> "$TIMED_OUT_BATCHES"
                while IFS= read -r fallback_name; do
                    [[ -z "$fallback_name" ]] && continue
                    raw_output="$(mktemp "$OUTPUT_DIR/.scanner.XXXXXX")"
                    run_scan "$fallback_name" "$raw_output"
                    rm -f "$raw_output"
                done < <(python3 "$PYTHON_HELPER" \
                    --fallback \
                    --names "$batch_names" \
                    --attempt "$attempt" \
                    --attempts-ndjson "$ATTEMPTS_NDJSON")
            fi
        done
    done
}

write_reports() {
    python3 "$PYTHON_HELPER" \
        --aggregate \
        --names "$NAMES" \
        --attempts-ndjson "$ATTEMPTS_NDJSON" > "$OUTPUT_DIR/attempts.json"
    JAVA_VERSION="$JAVA_VERSION" python3 "$PYTHON_HELPER" \
        --input "$OUTPUT_DIR/attempts.json" \
        --json-out "$OUTPUT_DIR/report.json" \
        --markdown-out "$OUTPUT_DIR/report.md" \
        --backend webgpu
}

self_test() {
    local temporary_dir
    temporary_dir="$(mktemp -d)"
    trap 'rm -rf "$temporary_dir"' RETURN
    GM_MEASURE_SELF_TEST=1 GM_SCAN_COMMAND="$HERE/gm-measure-blocking.sh" \
        "$HERE/gm-measure-blocking.sh" \
        --names 1,2,3,4,5 \
        --output "$temporary_dir" \
        --attempt-count 1 \
        --skip-reports
    [[ "$(wc -l < "$temporary_dir/attempts.ndjson" | tr -d ' ')" == "5" ]]
    grep -Fx '1,2,3,4,5' "$temporary_dir/timed-out-batches.txt" >/dev/null
    printf 'self-test: fixture timeout retained one record and fell back to four units\n'
}

if [[ -n "${GM_SCAN_COMMAND:-}" && "${GM_MEASURE_SELF_TEST:-}" != "1" ]]; then
    printf '%s\n' 'GM_SCAN_COMMAND is reserved for --self-test fixtures.' >&2
    exit 2
fi

OUTPUT_DIR="$ROOT/build/reports/gm-measure-blocking"
NAMES=""
ATTEMPT_COUNT=3
SKIP_REPORTS=0

while (($#)); do
    case "$1" in
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --names)
            NAMES="$2"
            shift 2
            ;;
        --self-test)
            self_test
            exit 0
            ;;
        --attempt-count)
            ATTEMPT_COUNT="$2"
            shift 2
            ;;
        --skip-reports)
            SKIP_REPORTS=1
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            printf 'unknown argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "$ATTEMPT_COUNT" != "3" && "${GM_MEASURE_SELF_TEST:-}" != "1" ]]; then
    printf '%s\n' 'Production measurement always uses exactly three attempts.' >&2
    exit 2
fi
if [[ -z "$NAMES" ]]; then
    NAMES="$(discover_blocking_names | paste -sd, -)"
fi
if [[ -z "$NAMES" ]]; then
    printf '%s\n' 'No current BLOCKING GM names were discovered.' >&2
    exit 1
fi

IFS=, read -r -a GM_NAMES <<< "$NAMES"
for name in "${GM_NAMES[@]}"; do
    if [[ -z "$name" ]]; then
        printf '%s\n' 'GM names must be nonblank and comma-separated.' >&2
        exit 2
    fi
done

mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
ATTEMPTS_NDJSON="$OUTPUT_DIR/attempts.ndjson"
TIMED_OUT_BATCHES="$OUTPUT_DIR/timed-out-batches.txt"
: > "$ATTEMPTS_NDJSON"
: > "$TIMED_OUT_BATCHES"

UNAME_VALUE="$(uname -a)"
JAVA_VERSION="$(java -version 2>&1 | head -n 1 || true)"
JAVA_VERSION="${JAVA_VERSION:-unknown}"
GIT_HEAD="$(git -C "$ROOT" rev-parse HEAD)"
python3 "$PYTHON_HELPER" \
    --environment-out "$OUTPUT_DIR/environment.json" \
    --uname "$UNAME_VALUE" \
    --java-version "$JAVA_VERSION" \
    --git-head "$GIT_HEAD"

run_campaign
if [[ "$SKIP_REPORTS" == "0" ]]; then
    write_reports
fi
