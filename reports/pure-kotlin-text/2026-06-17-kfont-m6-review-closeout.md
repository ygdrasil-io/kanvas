# 2026-06-17 KFONT M6 Review Closeout Audit

Status: documentation-only audit wave.

## Scope

- Re-audit the three remaining M6 tickets still marked `review` after their
  bounded implementation PRs merged:
  `KFONT-M6-002`, `KFONT-M6-004`, and `KFONT-M6-006`.
- Distinguish merged prerequisite slices from tickets that are still blocked on
  absent reviewed fixture families, promoted dumps, or runtime adoption work.
- Avoid synthetic-only substitutions for the missing shaping fixtures.

## Findings

- `KFONT-M6-002` is now closable: reviewed GSUB fixture provenance is checked
  in, parser/runtime tests cover the positive and malformed fixture set, and
  `gsub-trace.json` / `shaped-glyph-run.json` are now fixture-backed Latin
  evidence rather than M6-001 contract placeholders.
- `KFONT-M6-004` is now closable: reviewed GPOS fixture provenance is checked
  in, parser/runtime tests cover the positive and malformed fixture set, and
  `gpos-trace.json` / `shaped-glyph-run.json` are now fixture-backed Latin
  evidence rather than contract placeholders.
- `KFONT-M6-006` is not closable in this wave: the policy slice remains contract-level
  only until the per-script shaping fixture families from `KFONT-M6-007`,
  `KFONT-M6-008`, and `KFONT-M6-009` land, runtime GSUB/GPOS consumes
  `ResolvedFeatureSet`, and the `drawString` compatibility path records
  explicit complex-feature non-enablement.

## Outcome

- `KFONT-M6-002` can move to `done`.
- `KFONT-M6-004` can move to `done`.
- `KFONT-M6-006` moves to `blocked`.

## Validation

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGsubFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserReportsReviewedMalformedGsubFixtureFontsAsDiagnostics --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGposFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserReportsReviewedMalformedGposFixtureFontsAsDiagnostics
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGsubFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedGposFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gsubTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns --tests org.graphiks.kanvas.text.OpenTypeLayoutEngineContractTest
rtk git diff --check
```

## Non-Claims

- No complete GSUB support claim.
- No complete GPOS support claim.
- No complete script-policy support claim.
- No complex-script shaping promotion.
- No native shaper oracle claim.
