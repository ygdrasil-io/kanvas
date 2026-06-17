# KFONT-M7-002 - Fallback decision trace

## Scope landed

- `font/core` now exposes deterministic fallback evidence through
  `FallbackCandidateTrace`, `FallbackEvidenceCase`,
  `FallbackEvidenceWriter`, and `defaultFallbackEvidenceBundle()`.
- Two checked-in canonical dumps now cover the bounded fallback slice:
  `reports/font/fixtures/expected/fallback/fallback-decision-trace.json` and
  `reports/font/fixtures/expected/fallback/resolved-font-runs.json`.
- A third checked-in shaping-linked dump now binds the same bounded slice to
  deterministic shaping output:
  `reports/font/fixtures/expected/shaping/fallback-shaped-glyph-run.json`.
- Six dedicated per-fixture assets now sit alongside the aggregate dumps:
  `fallback-emoji-preference.json`, `fallback-family-generic.json`,
  `fallback-family-unavailable.json`, `fallback-locale-serbian.json`,
  `fallback-missing-glyph.json`, and `fallback-script-arabic.json`.
- The current bundle records six deterministic cases:
  `fallback-family-generic`, `fallback-script-arabic`,
  `fallback-locale-serbian`, `fallback-emoji-preference`,
  `fallback-missing-glyph`, and `fallback-family-unavailable`.

## Evidence

- `FallbackDecisionTrace` now serializes request family facts, generic family,
  script and locale hints, ordered candidate families, per-candidate reasons,
  selected/rejected typefaces, stable refusal diagnostics, and deterministic
  cluster ranges for complete-miss evidence.
- `ResolvedFontRunEvidence` records deterministic text ranges, cluster ranges,
  selected `TypefaceID`, host-dependent markers, fallback reasons, and shaping
  diagnostic codes for the same bounded fixture cases, while refusal
  `diagnosticRanges` preserve complete-miss text and cluster coverage when no
  run can be emitted.
- `defaultFallbackShapedGlyphRunEvidenceJson()` now emits a shaping-owned
  `fallback-shaped-glyph-run` dump keyed by fixture ID with
  `decisionTraceRef`, `resolvedRunsRef`, and `fixtureAssetRef` links back to
  the fallback dumps, plus deterministic selected/rejected typeface facts,
  bounded glyph runs, and shaping diagnostics for the same six cases.
- `defaultFallbackEvidenceBundle()` now also emits one compact
  `fallback-fixture` JSON per fixture so later shaping and dashboard evidence
  can reference bounded cases directly instead of slicing the aggregate dumps.
- `FallbackDecisionDumpTest` asserts byte-identical checked-in dump output and
  verifies script fallback, locale hinting, emoji preference, missing-glyph,
  and family-unavailable refusal diagnostics without leaking HarfBuzz,
  FreeType, or `SkTypeface` wording into the evidence.
- `FallbackShapingEvidenceTest` asserts the checked-in shaping dump byte for
  byte and verifies that Arabic/script fallback, emoji preference,
  missing-glyph refusal, and family-unavailable refusal all preserve explicit
  links back to the fallback trace and per-fixture assets.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecisionDump*'
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecision*'
rtk ./gradlew --no-daemon :font:core:test
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.FallbackShapingEvidenceTest
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

This slice keeps `KFONT-M7-002` in `review`, not `done`. The current evidence
does not yet add variable-axis-aware fallback, cluster-safe fallback
segmentation, host-dependent system scan facts, CPU oracle promotion, or any
GPU text-route claim.
