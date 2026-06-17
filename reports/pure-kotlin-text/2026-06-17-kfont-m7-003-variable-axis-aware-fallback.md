# KFONT-M7-003 - Variable-axis-aware fallback

## Scope landed

- `font:core` now lets `FallbackRequest` carry requested variation coordinates
  and propagates selected fallback coordinates into fallback trace and resolved
  run dumps.
- `CatalogFontResolver` now prefers a covered candidate with requested axis
  support over an equally covered static fallback candidate, clamps requested
  coordinates to supported ranges deterministically, and emits bounded
  diagnostics for unsupported axes and missing variation metrics.
- `font:text` shaping fallback evidence now links the bounded variable
  fallback fixtures through the existing shaped-glyph-run dump without
  broadening cluster-safe, renderer, or platform fallback claims.

## Evidence

- `fallback-decision-trace.json` now includes bounded variable fallback cases
  for axis clamping, missing axis support, missing variation metrics, and a
  CFF2-backed variable fallback face.
- `resolved-font-runs.json` now serializes selected fallback typeface IDs with
  deterministic `variationCoordinates` for the bounded variable cases.
- Dedicated `fallback-fixture` assets now exist for
  `fallback-axis-clamped`, `fallback-axis-missing`,
  `fallback-metrics-variation-missing`, and `fallback-variable-cff2`.
- `fallback-shaped-glyph-run.json` now links those fixtures back to the shared
  fallback trace and resolved-runs dumps without implying cluster-safe
  segmentation or platform/native fallback behavior.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FallbackDecisionDump*' --tests '*VariableFallback*'
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.FallbackShapingEvidenceTest --tests '*VariableFallback*'
```

## Remaining gate

This remains bounded review evidence only. It does not claim named-instance
compatibility, multi-axis breadth beyond the checked `wght` cases, cluster-safe
fallback segmentation, host-dependent system-font fallback, CPU oracle
promotion, or any GPU text-route support.
