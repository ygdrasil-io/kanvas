# KFONT-M11-008 GPU Text Ordering Trace

Date: 2026-06-23

## Status

KFONT-M11-008 is implemented as GPU-gated evidence. The slice adds a
deterministic ordering trace for the accepted A8 atlas subrun and stable
negative refusals for missing or unsafe ordering proofs.

## Evidence

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextOrderingTrace.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextOrderingTraceTest.kt`
- `reports/pure-kotlin-text/gpu-text-ordering-trace.json`

The trace links:

- subrun `atlas-page-generation-split.0`
- resource plan `gpu-text-resource-a8-0`
- upload plan `gpu-text-upload-a8-page-0`
- instance buffer plan `gpu-text-instance-buffer-a8-0`
- draw task `draw-text-a8-001`
- atlas generation check `check:a8-page-0:generation`
- draw-before-eviction barrier `barrier:draw-before-eviction-a8-page-0`

## Refusals

The report includes deterministic refusal rows for:

- missing upload-before-sample edge:
  `text.gpu.upload-before-sample-edge-missing` /
  `unsupported.text.upload_plan_missing`
- stale atlas generation:
  `text.gpu.atlas-generation-stale` /
  `unsupported.text.atlas_generation_stale`
- unsafe eviction before dependent draw:
  `text.gpu.eviction-before-dependent-draw` /
  `unsupported.text.eviction_before_dependent_draw`
- instance upload after draw:
  `text.gpu.instance-upload-after-draw` /
  `unsupported.text.instance_upload_after_draw`

## Validation

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextOrderingTrace*'
```

## Non-Claims

This slice does not execute GPU uploads, does not implement a general GPU task
graph scheduler, does not claim SDF ordering evidence, does not claim broad GPU
text support, and does not retire `dftext`.
