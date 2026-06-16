# KFONT-M4-003 - CFF subroutine limits and diagnostics

## Scope

Add deterministic subroutine safety evidence for the generated Type 2 fixture
interpreter without promoting complete real-font CFF rendering support.

## Files

- `font/scaler/src/main/kotlin/org/graphiks/kanvas/font/scaler/FontScaler.kt`
- `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`
- `reports/font/fixtures/expected/scaler/cff-subroutine-trace.json`
- `reports/font/fixtures/expected/scaler/cff-charstring-trace.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M4-cff-cff2-scalers/KFONT-M4-003-add-cff-subroutine-limits-and-diagnostics.md`

## Evidence

- `Type2ExecutionLimits` now makes operand-stack, call-depth, instruction-count,
  and expanded-byte budgets explicit and non-host-derived.
- `cff-subroutine-trace.json` records deterministic local, global, and nested
  subroutine traces with bias, caller byte offset, return byte offset, and
  remaining instruction/expanded-byte budgets.
- Refusal snapshots now cover `font.scaler.cff.subr-out-of-range`,
  `font.scaler.cff.subr-depth-limit`, `font.cff-stack-malformed` for missing
  return, and `font.scaler.cff.instruction-limit`.
- The existing `cff-charstring-trace.json` golden is rebased to include the
  enriched subroutine trace facts without broadening support claims.

## Validation

```bash
rtk ./gradlew --no-daemon :font:scaler:test --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterRecordsNestedSubroutineOffsets --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffType2FixtureInterpreterRejectsInvalidSubroutinePathsWithDedicatedDiagnostics --tests org.graphiks.kanvas.font.scaler.FontScalerSurfaceTest.cffSubroutineTraceGoldenMatchesGeneratedEvidence
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

## Non-Claims

- No complete real-font CFF rendering support claim.
- No complete CFF2 variation support claim.
- No native scaler oracle claim.
