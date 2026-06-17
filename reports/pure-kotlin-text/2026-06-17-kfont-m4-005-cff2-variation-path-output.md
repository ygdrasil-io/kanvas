# KFONT-M4-005 - CFF2 variation path output

## Scope

Add deterministic generated-fixture evidence for normalized CFF2 variation path
output and stable refusal diagnostics without promoting complete real-font CFF2
support.

## Files

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/cff-charstring-trace.json`
- `reports/font/fixtures/expected/scaler/cff-scaler-path-output.json`
- `reports/font/fixtures/expected/scaler/cff2-variation-trace.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/KFONT-M4-005-implement-cff2-variation-path-output.md`

## Evidence

- `CFF2Scaler` now normalizes user-space `fvar` coordinates, applies `avar`
  remapping, and routes `blend` through bounded CFF2 `VariationStore`
  resolutions before producing path and metrics evidence.
- `cff2-variation-trace.json` records distinct path hashes for default, min,
  max, and named coordinate sets, along with normalized coordinates and
  per-`blend` vector evidence including `vsindex`, region indexes, scalars, and
  blended values.
- CFF Type 2 evidence now serializes `blendVectors`, so the existing
  `cff-charstring-trace.json` and `cff-scaler-path-output.json` goldens are
  rebased to keep linked CFF/CFF2 trace evidence deterministic.
- Stable refusal coverage now includes dedicated
  `font.scaler.cff2.blend-stack-malformed` diagnostics plus deterministic
  `cff2.vsindex-invalid`, `cff2.variation-store-missing`,
  `cff2.variation-axis`, and `cff2.variation-position-non-finite` snapshots.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2FixtureInterpreterAppliesVsindexBlendEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScalerNormalizesUserSpaceVariationCoordinatesBeforeBlendAndMetrics --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScalerScaledGlyphEvidenceUsesNormalizedVariationPosition --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScalerAppliesAvarCoordinateMappingBeforeBlend --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScaledGlyphEvidenceRefusesBlendWhenVariationStoreIsMissing --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScaledGlyphEvidenceRefusesInvalidVsIndexDeterministically --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScaledGlyphEvidenceReportsUnknownRequestedAxisWithoutThrowing --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2ScaledGlyphEvidenceReportsNonFiniteAxisWithoutThrowing --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2BlendRejectsMalformedStackWithDedicatedDiagnostic --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2VariationTraceGoldenMatchesGeneratedEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffCharStringTraceGoldenMatchesGeneratedEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffScalerPathOutputGoldenMatchesGeneratedEvidence
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Non-Claims

- No complete real-font CFF2 variation support claim.
- No HVAR/VVAR/MVAR advance-delta claim.
- No native scaler oracle claim.
- No GPU text-route claim.
