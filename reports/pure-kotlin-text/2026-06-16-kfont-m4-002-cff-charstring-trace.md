# KFONT-M4-002 - CFF Type 2 Charstring Trace Evidence

## Scope

- Generated-fixture evidence for the bounded Type 2 charstring machine only.
- No complete CFF rendering support claim, no broader real-font corpus claim,
  and no native-scaler oracle claim.

## Files

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/cff-charstring-trace.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/KFONT-M4-002-implement-type-2-charstring-stack-machine.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `cff-charstring-trace.json` now fixes deterministic trace evidence for
  generated line/curve/flex, width/hint metadata, and local/global subroutine
  call snapshots.
- `endchar` now refuses leftover operands and trailing executable bytes instead
  of silently accepting them.
- Stack overflow now exits with the dedicated stable code
  `font.scaler.cff.stack-overflow`; trailing bytes now exit with
  `font.scaler.cff.trailing-bytes`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterBuildsLineCurveAndFlexEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterTracesLocalAndGlobalSubroutines --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cff2FixtureInterpreterAppliesVsindexBlendEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterReportsStackAndOperatorRefusals --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterRecordsWidthAndHintMaskMetadata --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterRejectsEndcharRemaindersAndStackOverflow --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffCharStringTraceGoldenMatchesGeneratedEvidence --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterCoversRemainingCurveAndFlexOperators
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```
