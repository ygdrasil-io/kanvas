# 2026-07-12 GM Family Classifier Follow-up

## Scope

Correct the source-aware `scripts/check_missing_gms.py` family classifier so a
Kotlin base GM does not need an exact `cpp_names` membership when the upstream
Skia C++ source already exposes multiple authoritative concrete family members.

## Validation

```bash
PYTHONDONTWRITEBYTECODE=1 python3 scripts/test_check_missing_gms.py
PYTHONDONTWRITEBYTECODE=1 python3 scripts/test_compare_skia_vs_kanvas_gms.py
PYTHONDONTWRITEBYTECODE=1 python3 scripts/test_extract_skia_gm_names.py
PYTHONDONTWRITEBYTECODE=1 python3 scripts/test_extract_kanvas_gm_names.py
python3 scripts/check_missing_gms.py --cpp-gm-dir "${KANVAS_SKIA_GM_DIR:-PATH}"
```

## Observed real-source evidence

- `Variant families from CPP source evidence` increased from `0` to `3`.
- The targeted families now promote out of actionable missing:
  - `anisotropic_image_scale` →
    `anisotropic_image_scale_aniso|linear|mip`
  - `clippedbitmapshaders` →
    `clipped-bitmap-shaders-clamp|mirror|tile` plus `-hq` variants
  - `clipsuperrrect` →
    `clip_super_rrect_pow2|clip_super_rrect_pow3.5`
- Generic/fuzzy buckets remain actionable by regression test (`all`, `circle`,
  `color` stay `missing`).
- No reference PNG files changed.

Relevant CLI excerpt:

```text
--- Variant families from CPP source evidence ---
count: 3
  anisotropic_image_scale.png  <- variants anisotropic_image_scale_aniso.png, anisotropic_image_scale_linear.png, anisotropic_image_scale_mip.png
  clippedbitmapshaders.png  <- variants clipped-bitmap-shaders-clamp.png, clipped-bitmap-shaders-clamp-hq.png, clipped-bitmap-shaders-mirror.png, clipped-bitmap-shaders-mirror-hq.png, clipped-bitmap-shaders-tile.png, clipped-bitmap-shaders-tile-hq.png
  clipsuperrrect.png  <- variants clip_super_rrect_pow2.png, clip_super_rrect_pow3.5.png

=== GM names WITHOUT reference PNG (69) ===
  all.png
```
