# KFONT-M7-002 - Fallback decision trace

## Scope landed

- `font/core` now exposes deterministic fallback evidence through
  `FallbackCandidateTrace`, `FallbackEvidenceCase`,
  `FallbackEvidenceWriter`, and `defaultFallbackEvidenceBundle()`.
- Two checked-in canonical dumps now cover the bounded fallback slice:
  `reports/font/fixtures/expected/fallback/fallback-decision-trace.json` and
  `reports/font/fixtures/expected/fallback/resolved-font-runs.json`.
- The current bundle records six deterministic cases:
  `fallback-family-generic`, `fallback-script-arabic`,
  `fallback-locale-serbian`, `fallback-emoji-preference`,
  `fallback-missing-glyph`, and `fallback-family-unavailable`.

## Evidence

- `FallbackDecisionTrace` now serializes request family facts, generic family,
  script and locale hints, ordered candidate families, per-candidate reasons,
  selected/rejected typefaces, and stable refusal diagnostics.
- `ResolvedFontRunEvidence` records deterministic text ranges, cluster ranges,
  selected `TypefaceID`, host-dependent markers, fallback reasons, and shaping
  diagnostic codes for the same bounded fixture cases.
- `FallbackDecisionDumpTest` asserts byte-identical checked-in dump output and
  verifies script fallback, locale hinting, emoji preference, missing-glyph,
  and family-unavailable refusal diagnostics without leaking HarfBuzz,
  FreeType, or `SkTypeface` wording into the evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecisionDump*'
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecision*'
rtk ./gradlew --no-daemon :font:core:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

This slice keeps `KFONT-M7-002` in `review`, not `done`. The current evidence
does not yet add complete-miss cluster ranges, shaping-plan or
`shaped-glyph-run` trace propagation, dedicated per-fixture fallback assets,
variable-axis-aware fallback, cluster-safe fallback segmentation, host-dependent
system scan facts, CPU oracle promotion, or any GPU text-route claim.
