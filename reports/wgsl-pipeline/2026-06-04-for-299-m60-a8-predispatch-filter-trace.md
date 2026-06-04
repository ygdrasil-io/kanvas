# FOR-299 M60 A8 Pre-dispatch Filter Trace

Linear: `FOR-299`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `A8_SRCINPAYLOAD_LAYER_CLIP_INTERSECTION_EXPLAINS_HOLE`

Exact gap/result: `Every candidate-minus-runtime-002 pixel is iterated by A8 srcInPayload; 3,272 pixels are skipped because active AA clip coverage is zero, 10 more round to zero source alpha after coverage modulation, and 11 edge pixels have zero blurred-mask alpha.`

## Result

FOR-299 adds opt-in CPU trace metadata for
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` and regenerates the
FOR-294 raw capture over the complete red candidate domain. Normal rendering,
fallback policy, blend behavior, GPU/WebGPU routing, and M60 support status are
unchanged.

| Measure | Value |
|---|---:|
| Red candidate pixels | 22424 |
| `candidate-minus-runtime-002` pixels | 3293 |
| Original target pixels | 59 |
| Red runtime dispatch pixels | 9088 |
| Raw pre-dispatch events | 22424 |
| Raw pre-dispatch pixels | 22424 |
| Raw A8 blend-skip events | 13228 |
| Raw A8 blend-skip pixels | 13228 |
| Raw A8 `srcInPayload` dispatch events | 9088 |
| Raw A8 `srcInPayload` dispatch pixels | 9088 |
| Pre-dispatch coords outside candidate | 0 |
| Blend-skip coords outside candidate | 0 |
| Dispatch coords outside candidate | 0 |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Pre-dispatch pixels | Blend-skip pixels | Dispatch pixels | Missing pre-dispatch | Skip reasons | Zero blurred-mask pixels | Avg blurred mask | Avg masked alpha |
|---|---:|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 59 | 3293 | 3282 | 0 | 0 | `{'A8_SRCINPAYLOAD_MASK_ALPHA_ZERO': 11, 'A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO': 3272, 'A8_SRCINPAYLOAD_SRC_ALPHA_ZERO_AFTER_COVERAGE': 10}` | 11 | 193.964166 | 193.964166 |
| `original-59-targets` | 59 | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` | 59 | 59 | 59 | 0 | 0 | `{'A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO': 59}` | 0 | 155.644068 | 155.644068 |
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 0 | 2275 | 0 | 2275 | 0 | `{}` | 0 | 136.732308 | 136.732308 |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 0 | 2275 | 0 | 2275 | 0 | `{}` | 0 | 136.732308 | 136.732308 |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 0 | 2270 | 0 | 2270 | 0 | `{}` | 0 | 137.140088 | 137.140088 |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 0 | 2268 | 0 | 2268 | 0 | `{}` | 0 | 137.045855 | 137.045855 |

## Serialized A8 Fields

| Field | Status |
|---|---|
| `a8SkipReason` | present |
| `a8SpanLeft` | present |
| `a8SpanRight` | present |
| `activeClipBounds` | present |
| `blurredMaskAlpha` | present |
| `compositeX0` | present |
| `compositeX1` | present |
| `compositeY0` | present |
| `compositeY1` | present |
| `layerBounds` | present |
| `maskHeight` | present |
| `maskLocalX` | present |
| `maskLocalY` | present |
| `maskOriginLeft` | present |
| `maskOriginTop` | present |
| `maskWidth` | present |
| `maskedAlphaBeforeBlend` | present |
| `sourceLayerBounds` | present |

## Source Needles

| Needle | Present |
|---|---|
| `cpuTraceHasA8PayloadMetadata` | True |
| `cpuTraceRecordsPredispatchWithoutDispatchSource` | True |
| `cpuTraceRecordsBlendSkipWithoutDispatchSource` | True |
| `srcInPayloadDispatchCarriesMetadata` | True |
| `for294RawSerializesMaskLocalX` | True |
| `for294RawSerializesBounds` | True |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`
- FOR-292 decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`
- FOR-293 decision: `RED_DRAWRRECT_RUNTIME_VISIBILITY_STILL_AMBIGUOUS`
- FOR-294 decision: `RED_DRAWRRECT_RUNTIME_ROOT_DISPATCH_FOUND_OUTSIDE_TARGETS`
- FOR-295 decision: `ORIGINAL_TARGETS_OUTSIDE_RED_RUNTIME_DISPATCH_DOMAIN`
- FOR-296 decision: `RED_RUNTIME_DISPATCH_DOMAIN_SPATIALLY_SEPARATE_FROM_ORIGINAL_TARGET_CLUSTER`
- FOR-297 decision: `RUNTIME_RED_DISPATCH_REQUIRES_ADDITIONAL_UNMODELED_FILTER`
- FOR-298 decision: `A8_SRCINPAYLOAD_FILTER_STILL_UNEXPOSED`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-a8-predispatch-filter-trace-for299.json`
