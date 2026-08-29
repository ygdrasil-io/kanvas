# W64 — Shaping, fallback, variable/color font and emoji evidence

This checkout contains real vendored font fixtures under
`reports/font/fixtures/fonts/` (including Liberation, Arabic, Devanagari,
Thai/CJK, COLRv1 color, and RobotoFlex variable-font data). These are evidence
fixtures, not an implicit system-font source or a production fallback.

The pure Kotlin font stack provides parsed OpenType shaping, cluster-safe
fallback, variation-axis selection/clamping, color-glyph parsing, and explicit
emoji route/refusal diagnostics. GPU text promotion remains separately gated;
these tests do not imply a native GPU text claim.

Verification:

```text
./gradlew --offline --no-daemon :font:core:test \
  --tests '*FallbackDecisionDumpTest' --tests '*VariableFallbackEvidenceTest' \
  --tests '*FontCatalogTest' --tests '*FontFixtureManifestTest' --tests '*FontCoreSurfaceTest' \
  --tests '*FontTelemetrySchemaTest'
./gradlew --offline --no-daemon :font:text:test \
  --tests '*Shaping*' --tests '*Fallback*' --tests '*Arabic*' \
  --tests '*Variable*' --tests '*Devanagari*' --tests '*Thai*'
```

Result: 192 targeted tests passed (56 font-core and 136 font-text). The stale
telemetry assertion was corrected to read `validationTask` from the telemetry
bundle and `pmBundleTask` from the claim dashboard; no renderer behavior was
changed. No `gpu-renderer-scenes` files were modified.
