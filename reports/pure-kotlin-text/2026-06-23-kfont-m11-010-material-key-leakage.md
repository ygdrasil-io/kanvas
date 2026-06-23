# KFONT-M11-010 MaterialKey Leakage Validation

Status: implemented and independently reviewed.

## Scope

This checkpoint adds deterministic `MaterialKey` leakage validation for text
GPU handoff plans. It proves that glyph IDs, atlas coordinates, atlas
generations, upload tokens, and live texture handles stay out of
`MaterialKey` identity, while legitimate material/color changes properly
produce distinct material identifiers.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextMaterialKeyLeakage.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextMaterialKeyLeakageTest.kt`
- `reports/pure-kotlin-text/text-material-key-leakage-report.json`
- `reports/pure-kotlin-text/gpu-text-binding-plan.json`
- `reports/pure-kotlin-text/gpu-text-resource-plan.json`
- `reports/pure-kotlin-text/gpu-text-upload-plan.json`
- `reports/pure-kotlin-text/gpu-text-instance-layout.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextResourcePlan.kt`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-010-add-materialkey-leakage-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`

## Evidence

- `text-material-key-leakage-report.json` records 12 leakage cases: 7 clean
  (glyph-ID variance, atlas-rect variance, generation variance, upload-token
  variance, live-handle variance, and legitimate color changes) and 5 `leak-detected`
  negative fixtures (one per forbidden field category).
- All 5 required excluded fields (`glyphId`, `atlasRect`, `atlasGeneration`,
  `uploadToken`, `liveTextureHandle`) are validated across variance and negative
  scenarios.
- `GPUTextBinding` constructor enforces `materialKeyExcludedFields` containing
  all required fields.
- The `no-material-key-leakage-validation-claim` is removed from resource plan
  nonClaims now that validation evidence exists.
- The report keeps `routePromotion:"not-promoted"` and `productActivation:false`,
  and does not contain Skia-native text objects, font bytes, or raw GPU handles.

## Validation

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextMaterialKeyLeakageTest*'
rtk git diff --check
```

## Remaining Gate

No ticket-local `MaterialKey` leakage gate remains for KFONT-M11-010. This
checkpoint does not claim visual correctness, executed GPU uploads, SDF route
support, broad GPU text support, route promotion, product activation, or
`dftext` retirement.
