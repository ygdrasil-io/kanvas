#!/usr/bin/env bash
# Shared helpers for GM port tracking scripts.
# Sourced by gm-status.sh / gm-missing-apis.sh / gm-batch.sh.

set -euo pipefail

# ─── Paths ──────────────────────────────────────────────────────────────
SKIA_GM_DIR="${SKIA_GM_DIR:-/Users/chaos/workspace/kanvas-forge/skia-main/gm}"
KT_GM_DIR="${KT_GM_DIR:-skia-integration-tests/src/main/kotlin/org/skia/tests}"
KT_TEST_DIR="${KT_TEST_DIR:-skia-integration-tests/src/test/kotlin/org/skia/tests}"
KANVAS_SKIA_DIR="${KANVAS_SKIA_DIR:-kanvas-skia/src/main/kotlin}"

# ─── cpp extraction ─────────────────────────────────────────────────────

# Print all GM names registered by a .cpp file, one per line.
# Catches:
#   class XxxGM : public GM { ... }              → "XxxGM" (class form)
#   class XxxGM final : public skiagm::GM { ... } → "XxxGM"
#   DEF_SIMPLE_GM(name, ...)                     → "name" (simple form)
#   DEF_SIMPLE_GM_BG(name, ...)                  → "name"
#   DEF_SIMPLE_GM_CAN_FAIL(name, ...)            → "name"
#   DEF_SIMPLE_GPU_GM(name, ...)                 → "name"
#   DEF_SIMPLE_GPU_GM_BG(name, ...)              → "name"
extract_cpp_gms() {
    local cpp="$1"
    {
        # class XxxGM : public (skiagm::)?GM
        (grep -hEo 'class[[:space:]]+[A-Z][A-Za-z0-9_]+(GM)?[[:space:]]*(final[[:space:]]+)?:[[:space:]]*public[[:space:]]+(skiagm::)?GM' "$cpp" 2>/dev/null \
            | awk '{print $2}') || true
        # DEF_SIMPLE_GM family
        (grep -hEo 'DEF_SIMPLE_(GPU_)?GM(_BG|_CAN_FAIL)?[[:space:]]*\([[:space:]]*[A-Za-z_][A-Za-z0-9_]*' "$cpp" 2>/dev/null \
            | sed -E 's/.*\([[:space:]]*//') || true
        # DEF_GM (factory form: DEF_GM(return new XyzGM(...);))
        (grep -hEo 'DEF_GM[[:space:]]*\([[:space:]]*return[[:space:]]+new[[:space:]]+[A-Z][A-Za-z0-9_]+' "$cpp" 2>/dev/null \
            | sed -E 's/.*new[[:space:]]+//') || true
    } | sort -u
}

# ─── kotlin extraction ─────────────────────────────────────────────────

# Print "class_name<TAB>get_name<TAB>status" for a .kt GM file.
# class_name : the public class XxxGM { ... } name (or "-" if none)
# get_name   : the string returned by getName() (or "-" if not literal)
# status     : PORTED | STUB | IGNORED | ALIAS | EMPTY
extract_kt_meta() {
    local kt="$1"
    local class_name get_name status

    class_name=$( (grep -hEo '(class|object|typealias)[[:space:]]+[A-Z][A-Za-z0-9_]+GM\b' "$kt" 2>/dev/null \
        | head -n1 | awk '{print $NF}') || true )
    [ -z "$class_name" ] && class_name="-"

    get_name=$( (grep -hEo 'getName\(\)[^=]*=[[:space:]]*"[^"]+"' "$kt" 2>/dev/null \
        | head -n1 | sed -E 's/.*"([^"]+)".*/\1/') || true )
    [ -z "$get_name" ] && get_name="-"

    if grep -qE '^[[:space:]]*typealias[[:space:]]' "$kt" 2>/dev/null; then
        status="ALIAS"
    elif grep -qE '^[[:space:]]*@(Ignore|Disabled)\b' "$kt" 2>/dev/null; then
        status="IGNORED"
    elif grep -qE '//[[:space:]]*TODO:?[[:space:]]*missing API' "$kt" 2>/dev/null; then
        status="STUB"
    elif grep -qE 'override fun onDraw' "$kt" 2>/dev/null; then
        # Has an `onDraw`. STUB is reserved for the explicit Wave-O/P marker
        # `// TODO: missing API` (already caught above) OR a totally-empty body
        # (only a single `canvas ?: return` early-bail). Anything else — even
        # `onDraw { helperFn(canvas) }` delegating to a private helper — counts
        # as ported, because the heuristic must not penalise files that split
        # their body across `private fun`s (e.g. CropImageFilterGM delegates
        # to drawExampleGrid).
        local body_real_lines
        body_real_lines=$(awk '
            /override fun onDraw/ { in_body=1; brace=0; next }
            in_body {
                n_open=gsub(/\{/, "&"); n_close=gsub(/\}/, "&")
                brace += n_open - n_close
                if (brace < 0) { exit }
                if ($0 ~ /^[[:space:]]*$/) next
                if ($0 ~ /^[[:space:]]*\/\//) next
                if ($0 ~ /^[[:space:]]*\*/) next
                # Treat the bail-out pattern `val c = canvas ?: return` as
                # boilerplate, not real work.
                if ($0 ~ /^[[:space:]]*val[[:space:]]+[A-Za-z_][A-Za-z_0-9]*[[:space:]]*=[[:space:]]*canvas[[:space:]]*\?:[[:space:]]*return[[:space:]]*$/) next
                if ($0 ~ /^[[:space:]]*canvas[[:space:]]*\?:[[:space:]]*return[[:space:]]*$/) next
                print
            }' "$kt" | wc -l | tr -d ' ')
        if [ "${body_real_lines:-0}" -eq 0 ]; then
            status="STUB"
        else
            # Non-empty body — check Test class for @Disabled to surface
            # TEST_DISABLED, otherwise PORTED.
            local kt_bn test_bn1 test_bn2 test_path
            kt_bn=$(basename "$kt" .kt)
            test_bn1="${kt_bn%GM}Test.kt"
            test_bn2="${kt_bn}Test.kt"
            status="PORTED"
            for test_path in "$KT_TEST_DIR/$test_bn1" "$KT_TEST_DIR/$test_bn2"; do
                if [ -f "$test_path" ] && grep -qE '^[[:space:]]*@(Ignore|Disabled)\b' "$test_path" 2>/dev/null; then
                    status="TEST_DISABLED"
                    break
                fi
            done
        fi
    else
        status="EMPTY"
    fi

    printf '%s\t%s\t%s\n' "$class_name" "$get_name" "$status"
}

# ─── matching ───────────────────────────────────────────────────────────

# Build an index file mapping each registered GM name OR class name → kt file.
# Output format: <key>\t<kt_basename>\t<status>
# Cached at /tmp/gm-kt-index.$USER.tsv
build_kt_index() {
    local out="${1:-/tmp/gm-kt-index.$USER.tsv}"
    : > "$out"
    local f meta class_name get_name status
    for f in "$KT_GM_DIR"/*.kt; do
        [ -f "$f" ] || continue
        meta=$(extract_kt_meta "$f")
        class_name=$(printf '%s' "$meta" | cut -f1)
        get_name=$(printf '%s' "$meta"   | cut -f2)
        status=$(printf '%s' "$meta"     | cut -f3)
        local bn
        bn=$(basename "$f")
        # index by class name
        [ "$class_name" != "-" ] && printf '%s\t%s\t%s\n' "$class_name" "$bn" "$status" >> "$out"
        # index by getName() value
        [ "$get_name"   != "-" ] && printf '%s\t%s\t%s\n' "$get_name"   "$bn" "$status" >> "$out"
    done
    sort -u -o "$out" "$out"
}
