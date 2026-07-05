#!/usr/bin/env bash
# gm-bisect.sh — binary search for GMs that block/freeze rendering.
#
# Temporarily subsets META-INF/services to a range of GMs and runs the test
# suite.  Used to isolate a JVM-level hang that JUnit @Timeout cannot recover
# from (native WebGPU freeze, SIGKILL of the test worker, …).  Also usable
# with --timeout to smoke-test a specific band of GMs.
#
# Usage:
#   scripts/gm/gm-bisect.sh                      # list registered GMs
#   scripts/gm/gm-bisect.sh --from=0 --to=50      # run GMs 0..49
#   scripts/gm/gm-bisect.sh --from=0 --to=10 --timeout=60
#
# Strategy for finding a blocker:
#   1. scripts/gm/gm-bisect.sh        → see total count (e.g. 475)
#   2. Confirm head works:
#      scripts/gm/gm-bisect.sh --from=0 --to=50 --gradle-timeout=300
#   3. Binary search: double until failure, then halve:
#      scripts/gm/gm-bisect.sh --from=0 --to=100  → works
#      scripts/gm/gm-bisect.sh --from=0 --to=200  → works
#      scripts/gm/gm-bisect.sh --from=0 --to=300  → HANGS → blocker in 200..300
#      scripts/gm/gm-bisect.sh --from=200 --to=250 → works
#      scripts/gm/gm-bisect.sh --from=250 --to=300 → HANGS → blocker in 250..300
#      … until a single GM is isolated.
#
# Requires: PROJECT_ROOT, or run from project root.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

SERVICE_FILE="integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm"
BACKUP_FILE="${SERVICE_FILE}.bisect-backup"

# ─── parse args ──────────────────────────────────────────────────────
FROM=0
TO=0
GRADLE_TIMEOUT=300   # Gradle worker timeout in seconds (Gradle uses minutes)
JUNIT_TIMEOUT=120    # per-GM JUnit timeout in seconds

for arg in "$@"; do
    case "$arg" in
        --from=*)    FROM="${arg#--from=}" ;;
        --to=*)      TO="${arg#--to=}" ;;
        --gradle-timeout=*) GRADLE_TIMEOUT="${arg#--gradle-timeout=}" ;;
        --junit-timeout=*)  JUNIT_TIMEOUT="${arg#--junit-timeout=}" ;;
        --help|-h)
            sed -n '3,18p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

# ─── ensure service file exists ──────────────────────────────────────
if [ ! -f "$SERVICE_FILE" ]; then
    echo "ERROR: $SERVICE_FILE not found" >&2
    exit 1
fi

# ─── read all GM names ──────────────────────────────────────────────
mapfile -t ALL_GMS < <(grep -vE '^\s*(#|$)' "$SERVICE_FILE" || true)
TOTAL=${#ALL_GMS[@]}

if [ "$TOTAL" -eq 0 ]; then
    echo "ERROR: no GMs registered in $SERVICE_FILE" >&2
    exit 1
fi

# ─── list-only mode ─────────────────────────────────────────────────
if [ "$FROM" -eq 0 ] && [ "$TO" -eq 0 ]; then
    echo "Registered GMs: $TOTAL        (use --from=N --to=M to run a subset)"
    echo "---"
    for i in "${!ALL_GMS[@]}"; do
        printf '%4d  %s\n' "$i" "${ALL_GMS[$i]}"
    done
    exit 0
fi

# ─── validate range ──────────────────────────────────────────────────
if [ "$FROM" -lt 0 ] || [ "$TO" -gt "$TOTAL" ] || [ "$FROM" -ge "$TO" ]; then
    echo "ERROR: invalid range --from=$FROM --to=$TO (total=$TOTAL, need 0 <= from < to <= total)" >&2
    exit 1
fi

# ─── backup & subset ─────────────────────────────────────────────────
echo "=== Bisect: GMs [$FROM, $TO) of $TOTAL ==="
cp "$SERVICE_FILE" "$BACKUP_FILE"

cleanup() {
    if [ -f "$BACKUP_FILE" ]; then
        cp "$BACKUP_FILE" "$SERVICE_FILE"
        rm -f "$BACKUP_FILE"
        echo "=== Restored $SERVICE_FILE ==="
    fi
}
trap cleanup EXIT

# Write subset to service file
{
    for line in "${ALL_GMS[@]:FROM:TO-FROM}"; do
        echo "$line"
    done
} > "$SERVICE_FILE"

SUBSET_COUNT=$((TO - FROM))
echo "Testing $SUBSET_COUNT GMs (indices $FROM..$((TO-1)))"
echo "  Gradle worker timeout: ${GRADLE_TIMEOUT}s"
echo "  JUnit per-GM timeout:  ${JUNIT_TIMEOUT}s"

# ─── run tests ─────────────────────────────────────────────────────
# --rerun-tasks forces Gradle to re-run every task (compile, process
# resources, test) so the patched service file is actually used.
set +e
./gradlew :integration-tests:skia:test \
    --no-daemon --rerun-tasks \
    -Dkanvas.gm.timeout.seconds=$JUNIT_TIMEOUT \
    2>&1
EXIT_CODE=$?
set -e

echo ""
echo "=== Bisect result: GMs [$FROM, $TO) → exit code $EXIT_CODE ==="

# Print timing summary from output
if [ "$EXIT_CODE" -ne 0 ]; then
    echo "=== FAILURES/TIMEOUTS detected in this range ==="
fi

exit $EXIT_CODE
