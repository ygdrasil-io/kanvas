# KFONT-M6-002 / KFONT-M6-004 Latin Layout Fixture Evidence

Date: 2026-06-18
Status: implemented and independently reviewed with bounded fixture evidence.

## Scope

This wave closes the remaining fixture and promoted-dump gates for the bounded
Latin simple-layout slices `KFONT-M6-002` and `KFONT-M6-004`. It does not add
contextual GSUB, mark or cursive GPOS, required-script shaping support,
paragraph behavior, native shaper parity, or any GPU text route claim.

## Evidence

- `scripts/generate_pure_kotlin_text_m6_shaping_fixtures.main.kts` now
  generates ten deterministic synthetic `.otf` fixtures for reviewed GSUB and
  GPOS simple-lookup cases plus malformed/refusal fixtures.
- `reports/font/fixtures/provenance/index.json` records those fixtures as
  `synthetic-kanvas` assets under the accepted Apache-2.0 policy, with stable
  hashes, sizes, owner tickets, and expected dump links.
- `reports/font/fixtures/expected/shaping/gsub-trace.json` now carries
  checked-in GSUB single, multiple, ligature, no-match, and malformed-refusal
  evidence owned by `KFONT-M6-002`, including lookup indices, coverage-match
  facts, input/output glyph IDs, and cluster action details.
- `reports/font/fixtures/expected/shaping/gpos-trace.json` now carries
  checked-in GPOS single, pair format 1, pair format 2, and malformed-refusal
  evidence owned by `KFONT-M6-004`, including matched glyph/class facts,
  value records, and before/after positioning vectors.
- `reports/font/fixtures/expected/shaping/shaped-glyph-run.json` is now a
  fixture-backed promoted dump shared by both tickets and records reviewed
  ligature and kerning cases, explicit `traceRefs`, and final positioning
  vectors without reusing the old M6-001 contract-only golden.
- `reports/font/fixtures/expected/shaping/opentype-layout-contract-gsub-trace.json`,
  `reports/font/fixtures/expected/shaping/opentype-layout-contract-gpos-trace.json`,
  and
  `reports/font/fixtures/expected/shaping/opentype-layout-contract-shaped-glyph-run.json`
  preserve the original M6-001 contract-only evidence so the bounded fixture
  promotion does not silently widen the contract claim.

## Validation

```bash
rtk kotlin scripts/generate_pure_kotlin_text_m6_shaping_fixtures.main.kts
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGsubFixtureFontsFromRepo
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedGposFixtureFontsFromRepo
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gsubTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.OpenTypeLayoutEngineContractTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

## Non-Claims

- No complete GSUB/GPOS support claim.
- No Greek, Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK, or emoji shaping
  support claim.
- No native shaper oracle claim.
- No GPU text route claim.

## Remaining Gate

No remaining ticket-local gate. Contextual GSUB, mark/cursive GPOS,
required-script shaping rows, and any broader support promotion remain separate
tickets and non-claims.
