# Task 3 report — source-aware missing-reference classification

## Status

Implemented Task 3 in `scripts/check_missing_gms.py` with TDD:

- added `normalize_name(value: str) -> str`
- added `classify_reference(gm_name, references, cpp_names)`
- added optional CLI flag `--cpp-gm-dir PATH`
- preserved no-argument invocation
- added classifier + CLI fixture tests in `scripts/test_check_missing_gms.py`
- did not modify any reference or generated-render PNG

## TDD evidence

### RED

Command:

```bash
rtk python3 scripts/test_check_missing_gms.py
```

Exact output:

```text
FEEE
======================================================================
ERROR: test_prefix_family_is_reported_as_variant (__main__.CheckMissingGmsClassificationTest.test_prefix_family_is_reported_as_variant)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/Users/chaos/.codex/worktrees/a13d/kanvas/scripts/test_check_missing_gms.py", line 42, in test_prefix_family_is_reported_as_variant
    result = checker.classify_reference(
             ^^^^^^^^^^^^^^^^^^^^^^^^^^
AttributeError: module 'check_missing_gms' has no attribute 'classify_reference'

======================================================================
ERROR: test_separator_alias_is_reported_without_being_direct_match (__main__.CheckMissingGmsClassificationTest.test_separator_alias_is_reported_without_being_direct_match)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/Users/chaos/.codex/worktrees/a13d/kanvas/scripts/test_check_missing_gms.py", line 34, in test_separator_alias_is_reported_without_being_direct_match
    result = checker.classify_reference("lineargradientrt", {"linear_gradient_rt"}, None)
             ^^^^^^^^^^^^^^^^^^^^^^^^^^
AttributeError: module 'check_missing_gms' has no attribute 'classify_reference'

======================================================================
ERROR: test_unmatched_name_is_actionable_missing (__main__.CheckMissingGmsClassificationTest.test_unmatched_name_is_actionable_missing)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/Users/chaos/.codex/worktrees/a13d/kanvas/scripts/test_check_missing_gms.py", line 53, in test_unmatched_name_is_actionable_missing
    result = checker.classify_reference(
             ^^^^^^^^^^^^^^^^^^^^^^^^^^
AttributeError: module 'check_missing_gms' has no attribute 'classify_reference'

======================================================================
FAIL: test_cli_fixture_reports_source_aware_headings (__main__.CheckMissingGmsClassificationTest.test_cli_fixture_reports_source_aware_headings)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/Users/chaos/.codex/worktrees/a13d/kanvas/scripts/test_check_missing_gms.py", line 120, in test_cli_fixture_reports_source_aware_headings
    self.assertIn("--- Normalized aliases ---", output)
    ~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
AssertionError: '--- Normalized aliases ---' not found in 'Reference PNGs:     4\nGM names extracted: 3\nMatched: 0 (0 direct + 0 parameterized)\n\n=== REFERENCE PNGs WITHOUT Kotlin GM (4) ===\n\n  aarectmodes.png\n  clipped-bitmap-shaders-clamp.png\n  clipped-bitmap-shaders-tile.png\n  linear_gradient_rt.png\n\n=== GM names WITHOUT reference PNG (3) ===\n\n  aa_rect_effect.png\n  clippedbitmapshaders.png\n  lineargradientrt.png\n'

----------------------------------------------------------------------
Ran 4 tests in 0.008s

FAILED (failures=1, errors=3)
```

### GREEN

Command:

```bash
rtk python3 scripts/test_check_missing_gms.py
```

Exact output:

```text
....
----------------------------------------------------------------------
Ran 4 tests in 0.011s

OK
```

## Changed files

- `scripts/check_missing_gms.py`
- `scripts/test_check_missing_gms.py`
- `.superpowers/sdd/task-3-report.md`

## Implementation notes

- `scripts/check_missing_gms.py`
  - renamed Kotlin extractor helper to `extract_kotlin_gm_names`
  - imports C++ GM-name extraction from `scripts/extract_skia_gm_names.py`
  - adds `argparse` parsing for optional `--cpp-gm-dir`
  - classifies each Kotlin GM as:
    - `direct`
    - `normalized-alias`
    - `variant-family`
    - `missing`
  - emits explicit source-evidence and evidence headings:
    - `source-evidence: unavailable`
    - `--- Normalized aliases ---`
    - `--- Variant families from CPP source evidence ---`
    - `=== ACTIONABLE missing references ===`

- `scripts/test_check_missing_gms.py`
  - adds direct classifier tests from the brief
  - adds CLI fixture coverage with temporary reference/Kotlin/C++ directories

## Verification commands and output

### No-argument behavior preserved

Command:

```bash
rtk python3 scripts/check_missing_gms.py
```

Exact leading output:

```text
Reference PNGs:     998
GM names extracted: 891
Matched: 792 (788 direct + 4 normalized-alias + 0 variant-family)

source-evidence: unavailable

--- Normalized aliases ---
count: 4
  colorcubert.png  <- alias color_cube_rt.png
  convex_lineonly_paths.png  <- alias convex-lineonly-paths.png
  convex_lineonly_paths_stroke_and_fill.png  <- alias convex-lineonly-paths-stroke-and-fill.png
  lineargradientrt.png  <- alias linear_gradient_rt.png

--- Variant families from CPP source evidence ---
count: 0
```

### Explicit external-source diagnostic

Command:

```bash
rtk python3 scripts/check_missing_gms.py \
  --cpp-gm-dir /Users/chaos/workspace/kanvas-forge/skia-main/gm
```

Exact leading output:

```text
Reference PNGs:     998
GM names extracted: 891
Matched: 890 (788 direct + 4 normalized-alias + 98 variant-family)

source-evidence: cpp-gm-dir=/Users/chaos/workspace/kanvas-forge/skia-main/gm

--- Normalized aliases ---
count: 4
  colorcubert.png  <- alias color_cube_rt.png
  convex_lineonly_paths.png  <- alias convex-lineonly-paths.png
  convex_lineonly_paths_stroke_and_fill.png  <- alias convex-lineonly-paths-stroke-and-fill.png
  lineargradientrt.png  <- alias linear_gradient_rt.png

--- Variant families from CPP source evidence ---
count: 13
  all.png  <- variants all_bitmap_configs.png, all_variants_8888.png
  circle.png  <- variants circle_sizes.png
  color.png  <- variants color4blendcf.png, color4f.png, color4shader.png, color_cube_cf_rt.png, color_cube_rt.png, colorcomposefilter_alpha.png, colorcomposefilter_wacky.png, coloremoji_blendmodes_colrv0.png, coloremoji_blendmodes_sbix.png, coloremoji_blendmodes_svg.png, coloremoji_blendmodes_test.png, coloremoji_colrv0.png, coloremoji_sbix.png, coloremoji_svg.png, coloremoji_test.png, colorfilteralpha8.png, colorfilterimagefilter.png, colorfilterimagefilter_layer.png, colorfiltershader.png, colormatrix.png, colorspace.png, colorspace2.png, colorwheel.png, colorwheel_alphatypes.png, colorwheelnative.png
```

No PNG files were modified.

## Self-review

- Confirmed the implementation is limited to the requested script and tests.
- Confirmed no runtime `referenceName` inference was added for Kotlin classes.
- Confirmed the CLI still runs with no arguments.
- Confirmed the new output is explicit about source evidence, aliases, variants, and actionable missing references.

## Concerns

1. The external-source diagnostic is useful but still noisy:
   - generic C++ names such as `all`, `color`, and `image` create broad `variant-family` buckets
   - this is diagnostic-only output, not runtime behavior

2. Existing Kotlin-name extraction gaps remain outside this task’s brief:
   - some getter-based Kotlin GM names are still not extracted by `extract_kotlin_gm_names`
   - that affects the older `REFERENCE PNGs WITHOUT Kotlin GM` section independently of the new classifier

3. The C++ evidence lane depends on the quality of `scripts/extract_skia_gm_names.py`:
   - if upstream GM-name extraction misses a C++ GM, the classifier cannot use it as source evidence

## Commits

- `30a716fae9caf560ec4d12ec050d1cde3ea02340` — `feat: classify GM references from CPP source`

---

## Task 3 fix follow-up

Addressed every Important review finding:

- restored legacy no-argument summary/count semantics and headings from `2ba925c0`
- reused `is_parameterized_variant(...)` for the legacy matched count path
- made source-aware alias/family evidence diagnostic-only and explicitly unavailable without `--cpp-gm-dir`
- suppressed generic/fuzzy CPP buckets such as `all`, `circle`, and `color`
- retained explicit family evidence in focused classifier coverage

Tests run:

- `rtk python3 scripts/test_check_missing_gms.py`
- `rtk python3 scripts/check_missing_gms.py`
- `rtk python3 scripts/check_missing_gms.py --cpp-gm-dir /Users/chaos/workspace/kanvas-forge/skia-main/gm`

Observed verification:

- no-arg output restored `Matched: 844 (788 direct + 56 parameterized)`
- no-arg output reports `source-evidence: unavailable` and keeps legacy section headings
- source-aware output no longer classifies generic buckets as authoritative variant families

Implementation commit:

- `99252bcb54343ecb8feebee08b763bfeab8b76da` — `fix: restore legacy GM checker semantics`

---

## Pre-PR corrective follow-up for extractor/compare scripts

Focused fixes landed in:

- `scripts/extract_skia_gm_names.py`
- `scripts/test_extract_skia_gm_names.py`
- `scripts/compare_skia_vs_kanvas_gms.py`
- `scripts/test_compare_skia_vs_kanvas_gms.py`

Key corrections:

- resolved inherited `RuntimeShaderGM` base-constructor names from real upstream C++:
  `runtime_shader`, `threshold_rt`, `spiral_rt`, `unsharp_rt`,
  `color_cube_rt`, `color_cube_cf_rt`
- resolved constructor/state-driven `getName()` builders for:
  - `ClippedBitmapShadersGM` → `clipped-bitmap-shaders-{tile,mirror,clamp}` and `-hq`
  - `AnisotropicGM` → `anisotropic_image_scale_{linear,mip,aniso}`
  - `ClipSuperRRect` constructor literal pass-through
- stopped treating placeholder names like `<unresolved:...>` as authoritative API or compare output
- ignored commented-out `DEF_GM(...)` registrations instead of inventing disabled names
- removed the checked-in `/Users/chaos/...` path from `compare_skia_vs_kanvas_gms.py`
- added explicit `--cpp-gm-dir` plus `KANVAS_SKIA_GM_DIR` / extractor-default resolution

Fresh verification:

- `rtk python3 scripts/test_extract_skia_gm_names.py` → `OK (5 tests)`
- `rtk python3 scripts/test_compare_skia_vs_kanvas_gms.py` → `OK (5 tests)`
- `rtk python3 scripts/test_check_missing_gms.py` → `OK (7 tests)`
- `rtk python3 scripts/check_missing_gms.py --cpp-gm-dir /Users/chaos/workspace/kanvas-forge/skia-main/gm`
- `rtk python3 scripts/compare_skia_vs_kanvas_gms.py --cpp-gm-dir /Users/chaos/workspace/kanvas-forge/skia-main/gm | rg -n "<unresolved:|runtime_shader|threshold_rt|spiral_rt|clipped-bitmap-shaders|clip_super_rrect|anisotropic_image_scale" -S`

Actual-source evidence summary from the fresh extractor run:

- authoritative names now include all requested real upstream patterns above
- `AUTHORITATIVE_COUNT 789`
- `UNRESOLVED_COUNT 117`

Residual concern:

- `scripts/check_missing_gms.py` still reports `Variant families from CPP source evidence: 0`
  on this real-source run. After the generic/noisy buckets were intentionally
  suppressed, the remaining trustworthy evidence is showing up primarily as
  concrete upstream variant names rather than checker-promoted family buckets.
