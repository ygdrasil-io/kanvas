# KFONT-M7-003 - Variable-axis-aware fallback

## Scope landed

- `font:core` now lets `FallbackRequest` carry requested variation coordinates
  and propagates selected fallback coordinates into fallback trace and resolved
  run dumps.
- `CatalogFontResolver` now prefers a covered candidate with requested axis
  support over an equally covered static fallback candidate, clamps requested
  coordinates to supported ranges deterministically, honors compatible named
  instances, and emits bounded diagnostics for unsupported axes and missing
  variation metrics.
- `font:text` shaping fallback evidence now links the bounded variable
  fallback fixtures through the existing shaped-glyph-run dump without
  broadening cluster-safe, renderer, or platform fallback claims.

## Evidence

- `fallback-decision-trace.json` now includes bounded variable fallback cases
  for axis clamping, missing axis support, missing variation metrics,
  named-instance selection, multi-axis ranking, and a CFF2-backed variable
  fallback face.
- `resolved-font-runs.json` now serializes selected fallback typeface IDs with
  deterministic `variationCoordinates` for the bounded variable cases.
- Dedicated `fallback-fixture` assets now exist for
  `fallback-axis-clamped`, `fallback-axis-missing`,
  `fallback-metrics-variation-missing`, `fallback-multi-axis`,
  `fallback-named-instance`, and `fallback-variable-cff2`.
- `fallback-shaped-glyph-run.json` now links those fixtures back to the shared
  fallback trace and resolved-runs dumps without implying cluster-safe
  segmentation or platform/native fallback behavior.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecisionDump*' --tests '*VariableFallback*' --tests org.graphiks.kanvas.font.FontCoreSurfaceTest.prefersCoveredCandidateWithRequestedNamedInstanceOverAxisOnlyCandidate --tests org.graphiks.kanvas.font.FontCoreSurfaceTest.prefersCoveredCandidateWithRequestedMultiAxisSupportOverSingleAxisCandidate
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.FallbackShapingEvidenceTest --tests '*VariableFallback*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

## Remaining gate

No ticket-local gate remains for `KFONT-M7-003`. This remains bounded done
evidence only. It covers named-instance selection, multi-axis ranking,
metrics-aware selection, and a stable `text.fallback.variation-defaulted`
diagnostic when a requested named instance cannot be honored, and it has now
been revalidated alongside cluster-safe fallback segmentation. It still does
not claim host-dependent system-font fallback, CPU oracle promotion, or any
GPU text-route support.
