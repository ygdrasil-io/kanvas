# KFONT-M4-004 - CFF scaler path output

## Scope

Add deterministic generated-fixture evidence for CFF glyph path output and
metrics without promoting complete real-font CFF rendering support.

## Files

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/cff-scaler-path-output.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/KFONT-M4-004-implement-cff-scaler-path-output.md`

## Evidence

- `CFFScaledGlyphEvidence` now records deterministic source/typeface identity,
  outline commands, path hashes, bounds, metrics, width source, and linked
  charstring evidence for the bounded generated CFF scaler route.
- `cff-scaler-path-output.json` covers basic line/curve output, subroutine path
  output, flex output, missing-glyph refusal, and malformed-glyph refusal.
- Missing glyphs now surface the stable
  `font.scaler.cff.path-output-unavailable` diagnostic in the dump, and
  malformed charstrings retain the original refusal while also emitting
  `font.scaler.cff.glyph-malformed`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Non-Claims

- No complete real-font CFF rendering support claim.
- No complete CFF2 variation support claim.
- No native scaler oracle claim.
- No GPU text-route claim.
