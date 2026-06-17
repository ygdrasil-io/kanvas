# KFONT-M4-001 - CFF INDEX/DICT Parser Evidence

## Scope

- Generated-fixture evidence for bounded CFF/CFF2 INDEX and DICT parsing only.
- No Type 2 execution completeness claim, no broader real-font corpus claim, no
  CFF rendering support claim, and no CFF2 variation output claim.

## Files

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/cff-index-dict.json`
- `reports/font/fixtures/provenance/index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/KFONT-M4-001-implement-cff-index-and-dict-parser.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

## Evidence

- `cff-index-dict.json` now records source/typeface IDs, bounded INDEX counts and
  object ranges, typed DICT operators, `FDArray` and `FDSelect` facts, CFF2
  private/local-subroutine metadata, and explicit non-claims.
- `CFFScaler.tableEvidence()` and `CFF2Scaler.tableEvidence()` now surface the
  typed parse facts needed by later M4 tickets without widening support claims.
- Stable parser refusals now use the ticketed codes:
  `font.scaler.cff.index-bounds`,
  `font.scaler.cff.index-offsize-unsupported`,
  `font.scaler.cff.dict-operand-malformed`,
  `font.scaler.cff.required-operator-missing`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffIndexDictGoldenMatchesGeneratedEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffTableEvidenceUsesStableSpecificCffParseDiagnostics --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffTableEvidenceRefusesMalformedIndexAndDictDeterministically
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```
