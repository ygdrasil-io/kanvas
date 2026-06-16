# KFONT-M3-002 TrueType Gvar IUP Interpolation

Status: done.

## Scope

KFONT-M3-002 closes the bounded TrueType `gvar` IUP slice in `font/scaler`:

- contour-by-contour interpolation for partial simple-glyph point sets;
- one-explicit-point propagation, wraparound interpolation, and untouched-
  contour isolation;
- `avar`-mapped variation positions flowing into the same bounded IUP logic;
- composite outline evidence that depends on interpolated child deltas;
- malformed tuple diagnostics that keep the default outline route visible when
  fallback remains semantically valid.

## Evidence

- `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` is the golden
  for explicit vs inferred per-point deltas, min/default/max outline hashes,
  `avar`-normalized evidence, composite child evidence, and malformed tuple
  diagnostics.
- `FontScalerSurfaceTest` now covers:
  - single explicit point propagation across a contour;
  - wraparound interpolation across the untouched segment;
  - contours with no referenced points staying unchanged;
  - `TrueTypeGlyfScaler` `avar` remapping before `gvar` lookup;
  - composite outlines inheriting interpolated child deltas;
  - malformed tuple diagnostics with default-outline fallback.
- `dump-evidence-index.json`, `fixture-evidence-manifest.json`, and
  `font-fixture-inventory.json` register the new golden without broadening
  support claims beyond this bounded slice.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*IUP*' --tests '*Gvar*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Non-Claims

- No phantom-point metrics claim.
- No vertical metrics claim.
- No complete variable-font parity claim.
- No complete hinting VM claim.
- No A8/SDF artifact claim.
- No GPU glyph route claim.

## Remaining Gates

KFONT-M3-003 remains the next variation ticket for phantom points, advance
deltas, and metrics-side variation claims.
