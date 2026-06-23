# KFONT-M11-009 Text WGSL Parser/Reflection Validation

Status: implemented; PR review pending.

## Scope

This checkpoint adds deterministic text WGSL validation evidence for the
accepted A8 atlas route. It links the `text.a8-mask` WGSL module metadata to
Kotlin resource, binding, instance-layout, and ordering plans without promoting
runtime route support.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/TextWgslValidation.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/TextWgslValidationTest.kt`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/TextWgslValidationPipelineConformanceTest.kt`
- `build.gradle.kts`
- `reports/pure-kotlin-text/text-wgsl-reflection.json`
- `reports/pure-kotlin-text/text-wgsl-validation-report.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md`

## Evidence

- `text-wgsl-reflection.json` records the `text.a8-mask` module id, source id,
  module hash, wgsl4k SHA, fragment entry point, reflected group 2
  texture/sampler/uniform bindings, `TextParams` layout, and instance input
  expectations.
- `text-wgsl-validation-report.json` compares reflected bindings against
  Kotlin `GPUTextBinding`, `GPUTextResourcePlan`, instance layout, and
  `GPUTextOrderingToken` evidence for the accepted A8 atlas subrun.
- Refusal diagnostics are stable for parser failure, binding reflection
  mismatch, missing SDF params, and unregistered text WGSL modules.
- `TextWgslValidationPipelineConformanceTest` registers the ticket-listed
  `gpu-raster:pipelineConformanceTest --tests '*TextWgsl*'` gate against the
  same checked-in dumps.
- The dumps keep `routePromotion:"not-promoted"` and
  `productActivation:false`, and they do not contain Skia-native text objects,
  font bytes, or raw GPU handles.

## Validation

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextWgsl*'
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest --tests '*TextWgsl*'
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

No ticket-local WGSL parser/reflection gate remains for KFONT-M11-009. The next
M11 gate is KFONT-M11-010 for full `MaterialKey` leakage validation. This
checkpoint does not claim visual correctness, executed GPU uploads, SDF route
support, broad GPU text support, route promotion, product activation, or
`dftext` retirement.
