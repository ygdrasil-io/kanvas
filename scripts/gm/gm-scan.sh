#!/usr/bin/env bash
# gm-scan.sh — scan GMs in batches with process-level timeout.
#
# Each batch runs in its own Gradle subprocess. Unix `timeout` kills the
# JVM if a GM hangs (WebGPU native freeze that JUnit @Timeout can't catch).
#
# Usage:
#   scripts/gm/gm-scan.sh --batch=50 --batch-timeout=300
#   scripts/gm/gm-scan.sh --batch=50 --batch-timeout=300 --pgm-timeout=30

set -euo pipefail

# portable timeout: use gtimeout (macOS coreutils) or timeout (Linux) or fallback
portable_timeout() {
    local sec=$1; shift
    if command -v gtimeout &>/dev/null; then
        gtimeout "$sec" "$@"
    elif command -v timeout &>/dev/null; then
        timeout "$sec" "$@"
    else
        perl -e '
            alarm shift;
            my @cmd = @ARGV;
            exec { $cmd[0] } @cmd or die "exec: $!";
        ' "$sec" "$@"
    fi
}

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

SERVICE_FILE="integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm"
BATCH_SIZE=50
PER_GM_TIMEOUT=60
BATCH_TIMEOUT=300

for arg in "$@"; do
    case "$arg" in
        --batch=*)        BATCH_SIZE="${arg#--batch=}" ;;
        --pgm-timeout=*)  PER_GM_TIMEOUT="${arg#--pgm-timeout=}" ;;
        --batch-timeout=*) BATCH_TIMEOUT="${arg#--batch-timeout=}" ;;
        --help|-h)        sed -n '3,12p' "$0" | sed 's/^# \?//'; exit 0 ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

mapfile -t ALL_GMS < <(grep -vE '^\s*(#|$)' "$SERVICE_FILE" || true)
TOTAL=${#ALL_GMS[@]}

BACKUP_FILE="${SERVICE_FILE}.scan-backup"
cp "$SERVICE_FILE" "$BACKUP_FILE"
trap 'cp "$BACKUP_FILE" "$SERVICE_FILE"; rm -f "$BACKUP_FILE"' EXIT

N_BATCHES=$(( (TOTAL + BATCH_SIZE - 1) / BATCH_SIZE ))
echo "=== GM Scan: $TOTAL GMs, batch=$BATCH_SIZE, timeout=${PER_GM_TIMEOUT}s/GM ==="

for ((b=0; b<N_BATCHES; b++)); do
    START=$(( b * BATCH_SIZE ))
    END=$(( START + BATCH_SIZE ))
    [ "$END" -gt "$TOTAL" ] && END="$TOTAL"
    N=$(( END - START ))

    : > "$SERVICE_FILE"
    for ((i=START; i<END; i++)); do
        echo "${ALL_GMS[$i]}" >> "$SERVICE_FILE"
    done

    set +e
    portable_timeout "$BATCH_TIMEOUT" ./gradlew cleanTest :integration-tests:skia:test \
        --no-daemon -q \
        -Dkanvas.gm.timeout.seconds=$PER_GM_TIMEOUT 2>&1
    RC=$?
    set -e

    if [ $RC -eq 124 ]; then
        echo ">>> BATCH $((b+1))/$N_BATCHES [$START..$END) TIMEOUT (${BATCH_TIMEOUT}s) <<<"
    fi
done

cp "$BACKUP_FILE" "$SERVICE_FILE"
echo "=== Done ==="
