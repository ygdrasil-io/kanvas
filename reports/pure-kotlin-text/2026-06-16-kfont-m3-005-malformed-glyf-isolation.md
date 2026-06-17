# KFONT-M3-005 - Malformed glyf isolation suite

Status: done.

Files:

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/truetype-malformed-glyf-isolation.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`

Evidence:

- `ScaledTrueTypeGlyphEvidence` now serializes stable malformed-`glyf` diagnostics for truncated headers, bad contour endpoints, flag-repeat overflow, coordinate truncation, and invalid composite transform flags instead of leaking raw parser exceptions into the fixture surface.
- `reports/font/fixtures/expected/scaler/truetype-malformed-glyf-isolation.json` records one face-level `loca` refusal, seven per-glyph malformed isolation cases, positive-control safe glyph dumps for isolation-eligible faces, and a malformed `gvar` warning snapshot for the variation-only case.
- Composite cycle and missing component glyph cases remain distinct `font.outline-format-unsupported` refusals, while malformed simple-glyph payloads now use `font.scaler.outline-unavailable` with stable `truetype.*` detail codes.
- The suite keeps `fixture-gated` classification and explicitly documents `no-notdef-substitution-runtime-claim`; the current runtime slice proves `refuse-face` and `refuse-glyph` policy evidence only.

Validation:

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests '*MalformedGlyf*' --tests '*GlyphFailurePolicy*' --tests '*CompositeGlyph*' --tests '*Gvar*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

Remaining gate:

- This suite does not claim runtime `.notdef` substitution, broad malformed-font recovery, complete variable-font support, vertical metrics, native hinted parity, or GPU text routing.
