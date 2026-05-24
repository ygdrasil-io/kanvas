#!/usr/bin/env bash
# upstream-rebaseline.sh — enrich gm-status with disabled/source tags and buckets.
#
# Usage:
#   scripts/gm/upstream-rebaseline.sh > reports/upstream-rebaseline/YYYY-MM-DD.tsv
#
# Output columns:
#   upstream_cpp n_gm kt_files gm_status source_tags test_files disabled_tags bucket

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"

GM_STATUS="${GM_STATUS:-$HERE/gm-status.sh}"
KT_MAIN_DIR="${KT_MAIN_DIR:-skia-integration-tests/src/main/kotlin/org/skia/tests}"
KT_EXTRA_MAIN_DIR="${KT_EXTRA_MAIN_DIR:-integration-tests/src/main/kotlin/org/skia/tests}"
KT_TEST_DIR="${KT_TEST_DIR:-skia-integration-tests/src/test/kotlin/org/skia/tests}"
KT_EXTRA_TEST_DIR="${KT_EXTRA_TEST_DIR:-integration-tests/src/test/kotlin/org/skia/tests}"
GPU_TEST_DIR="${GPU_TEST_DIR:-gpu-raster/src/test/kotlin/org/skia/gpu/webgpu}"

tag_pattern='STUB\.[A-Za-z0-9_.-]+|INTRACTABLE\.[A-Za-z0-9_.-]+|MISSING_FIXTURE|SLOW\.GM_STRESS|ALIAS:'

normalize_tags() {
    sed -E 's/[.),:;]+$//' | sed '/^[[:space:]]*$/d' | sort -u | paste -sd, -
}

tags_in_file() {
    local file="$1"
    [ -f "$file" ] || return 0
    (rg --no-filename -o "$tag_pattern" "$file" 2>/dev/null || true) | normalize_tags
}

find_source_file() {
    local kt="$1"
    if [ -f "$KT_MAIN_DIR/$kt" ]; then
        printf '%s\n' "$KT_MAIN_DIR/$kt"
    elif [ -f "$KT_EXTRA_MAIN_DIR/$kt" ]; then
        printf '%s\n' "$KT_EXTRA_MAIN_DIR/$kt"
    fi
}

test_candidates_for_kt() {
    local kt="$1"
    local base="${kt%.kt}"
    local stem="${base%GM}"

    printf '%s\n' \
        "$KT_TEST_DIR/${stem}Test.kt" \
        "$KT_TEST_DIR/${base}Test.kt" \
        "$KT_EXTRA_TEST_DIR/${stem}Test.kt" \
        "$KT_EXTRA_TEST_DIR/${base}Test.kt" \
        "$GPU_TEST_DIR/${stem}WebGpuTest.kt" \
        "$GPU_TEST_DIR/${stem}CrossBackendTest.kt" \
        "$GPU_TEST_DIR/crossbackend/${stem}CrossBackendTest.kt"
}

bucket_for() {
    local status="$1"
    local tags="$2"
    local cpp="${3:-}"
    local test_files="${4:-}"

    if [[ "$tags" == *"ALIAS"* ]]; then
        printf 'alias\n'
    elif [ "$status" = "HELPER" ]; then
        printf 'helper\n'
    elif [[ "$cpp" =~ ^(mac_aa_explorer)$ ]]; then
        printf 'platform-gated\n'
    elif [[ "$cpp" =~ ^(bitmaprect|blurs|drawbitmaprect|drawminibitmaprect|imageblur2|verylargebitmap)$ ]]; then
        printf 'slow-or-reference\n'
    elif [[ "$cpp" =~ ^(alpha_image|arcto|readpixels)$ ]]; then
        printf 'fixture-gated\n'
    elif [[ "$cpp" =~ ^(composeshader|dashcubics|rrect|fiddle)$ ]]; then
        printf 'implementation\n'
    elif [[ "$cpp" =~ ^(circulararcs|cubicpaths|encode|gammatext|gradient_dirty_laundry|gradtext|hugepath|lattice|nonclosedpaths|orientation|overstroke|pathfill|polygons|quadpaths|strokefill|strokes|trickycubicstrokes)$ ]]; then
        printf 'partial-coverage\n'
    elif [[ "$tags" =~ (EMOJI_TABLES|FONTATIONS|COLR_V1|LIBERATION_FM|FREETYPE|PDF_TABLE_SUBSET_FONTMGR) ]]; then
        printf 'font-gated\n'
    elif [[ "$tags" =~ (FFMPEG|WEBP|COMPRESSED_TEXTURES|YUVA|YUV|LAZY_YUV|ANIM_CODEC|ANIMATED_IMAGE|ENCODE_SRGB_WEBP|ICO_DECODE) ]]; then
        printf 'codec-gated\n'
    elif [[ "$tags" =~ (FIXTURE|MISSING_FIXTURE) ]]; then
        printf 'fixture-gated\n'
    elif [[ "$tags" =~ (SKSL|RUNTIME_SHADER|CLIP_SUPER_RRECT|NORMAL_MAP_SHADER|LIT_SHADER|COLOR_CUBE_CF|CF_SHADER_CHILD|DEFERRED_SHADER|NULL_CHILD_RT|RAW_IMAGE_SHADER|LOCAL_MATRIX_RT|ALPHA_IMAGE_SHADER) ]]; then
        printf 'wgsl-runtime-gated\n'
    elif [[ "$tags" =~ (GANESH|GRAPHITE|GR_|GPU_ONLY|GPU_|_GPU|VULKAN|GL_TEXTURE_RECTANGLE|CLOCKWISE|SLUG) ]]; then
        printf 'gpu-intractable\n'
    elif [[ "$tags" =~ (EDGE_AA_IMAGE_SET|EDGE_AA_QUAD|ASYNC_RESCALE|SURFACE_SNAPSHOT_SUBSET|IMAGE_MAKE_SCALED|MAKE_WITH_COLOR_FILTER|COLOR4F_BLEND_CF|COLOR_FILTER_PRIV|GAUSSIAN_COLOR_FILTER|IFX\.MULTIPLE_FILTERS_SPAN|DRAW_VERTICES|RSXBLOB|DF_TEXT|PIXMAP_SCALE|SURFACE_PROPS|SRC_RECT_CONSTRAINT|BACKDROP_FILTER|PATH_EFFECT_CTM|POLY_TO_POLY|PERSPECTIVE_ADDPATH|ALPHA8_IMAGE_AS_MASK|BLURRECT_GALLERY|BLUR_RECTS_FULL|BLUR_RECT_COMPARE|GRADIENT_INTERPOLATION|MISSING_API|MESH|PATH_MEASURE_EXPLOSION|RECORDOPTS|SAVE_BEHIND|STROKEDLINE_CAPS|TEXT_IMAGE_FILTER|XYZ) ]]; then
        printf 'implementation\n'
    elif [ "$status" = "PORTED" ]; then
        printf 'ported\n'
    elif [ "$status" = "MISSING" ]; then
        printf 'missing-mapping\n'
    elif [[ "$test_files" =~ SLOW\.GM_STRESS ]] || [[ "$cpp" =~ (verylargebitmap|drawminibitmaprect|imageblur2) ]]; then
        printf 'slow-or-reference\n'
    elif [ "$status" = "PARTIAL" ]; then
        printf 'partial-untagged\n'
    elif [ "$status" = "TEST_DISABLED" ]; then
        printf 'disabled-untagged\n'
    elif [ "$status" = "STUB" ]; then
        printf 'stub-untagged\n'
    else
        printf 'needs-triage\n'
    fi
}

tmp_status=$(mktemp -t upstream-rebaseline-status.XXXXXX)
"$GM_STATUS" > "$tmp_status"

printf 'upstream_cpp\tn_gm\tkt_files\tgm_status\tsource_tags\ttest_files\tdisabled_tags\tbucket\n'

tail -n +2 "$tmp_status" | while IFS=$'\t' read -r cpp n_gm kt_files gm_status; do
    source_tags=""
    test_files=""
    disabled_tags=""

    if [ "$kt_files" != "-" ]; then
        IFS=',' read -r -a kt_array <<< "$kt_files"
        for kt in "${kt_array[@]}"; do
            src=$(find_source_file "$kt" || true)
            if [ -n "${src:-}" ]; then
                tags=$(tags_in_file "$src")
                [ -z "$tags" ] || source_tags=$(printf '%s\n%s\n' "$source_tags" "$tags" | tr ',' '\n' | normalize_tags)
            fi

            while IFS= read -r candidate; do
                [ -f "$candidate" ] || continue
                test_files=$(printf '%s\n%s\n' "$test_files" "$candidate" | sed '/^$/d' | sort -u | paste -sd, -)
                tags=$(tags_in_file "$candidate")
                [ -z "$tags" ] || disabled_tags=$(printf '%s\n%s\n' "$disabled_tags" "$tags" | tr ',' '\n' | normalize_tags)
            done < <(test_candidates_for_kt "$kt")
        done
    fi

    [ -n "$source_tags" ] || source_tags="-"
    [ -n "$test_files" ] || test_files="-"
    [ -n "$disabled_tags" ] || disabled_tags="-"

    combined_tags=$(printf '%s\n%s\n' "$source_tags" "$disabled_tags" | tr ',' '\n' | sed '/^[-[:space:]]*$/d' | normalize_tags)
    bucket=$(bucket_for "$gm_status" "$combined_tags" "$cpp" "$test_files")

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$cpp" "$n_gm" "$kt_files" "$gm_status" "$source_tags" "$test_files" "$disabled_tags" "$bucket"
done

rm -f "$tmp_status"
