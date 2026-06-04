# FOR-298 M60 A8 srcInPayload Runtime Filter

Linear: `FOR-298`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `A8_SRCINPAYLOAD_FILTER_STILL_UNEXPOSED`

Exact gap: `FOR-294/FOR-297 expose post-dispatch root blend events for A8 srcInPayload, but not the skipped pre-dispatch A8 loop state needed to separate span iteration, mask bounds/offset, or layer/source/clip intersection: missing a8SkipReason, a8SpanLeft, a8SpanRight, activeClipBounds, blurredMaskAlpha, compositeX0, compositeX1, compositeY0, compositeY1, layerBounds, maskHeight, maskLocalX, maskLocalY, maskOriginLeft, maskOriginTop, maskWidth, maskedAlphaBeforeBlend, sourceLayerBounds.`

## Result

FOR-298 compares `candidate-minus-runtime-002`, the four `red-runtime-*`
components, and the original 59 target pixels without changing rendering.
The runtime A8/source-in filter is still not exposed by the current
artifacts: FOR-294 records post-dispatch root blend writes, but skipped A8
loop pixels do not carry span, mask offset/bounds, masked-alpha, or
layer/source/clip intersection metadata.

| Measure | Value |
|---|---:|
| `candidate-minus-runtime-002` pixels | 3293 |
| `candidate-minus-runtime-002` A8 `srcInPayload` pixels | 0 |
| Original target pixels | 59 |
| Original targets inside `candidate-minus-runtime-002` | 59 |
| Original targets inside observed A8 `srcInPayload` runtime | 0 |
| Observed red runtime component pixels | 9088 |
| Observed red runtime components | 4 |
| Runtime coords outside reconstructed candidate | 0 |

## Component Comparison

| Component | Pixels | Bounds | Original targets | A8 `srcInPayload` pixels | Final white/layer | Final red-tint | Avg mask | Avg clip | Avg alpha-after-clip |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| candidate-minus-runtime-002 | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 59 | 0 | 3293 | 0 | 195.074097 | 240.901002 | 180.975099 |
| red-runtime-000 | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 0 | 2275 | 496 | 1526 | 138.114286 | 253.757802 | 136.872088 |
| red-runtime-001 | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 0 | 2275 | 496 | 1526 | 138.114286 | 253.757802 | 136.872088 |
| red-runtime-002 | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 0 | 2270 | 488 | 1528 | 138.52511 | 253.755066 | 137.280176 |
| red-runtime-003 | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 0 | 2268 | 488 | 1528 | 138.432099 | 253.753968 | 137.186067 |

## Missing Runtime Filter Fields

| Field | Status |
|---|---|
| `a8SkipReason` | missing |
| `a8SpanLeft` | missing |
| `a8SpanRight` | missing |
| `activeClipBounds` | missing |
| `blurredMaskAlpha` | missing |
| `compositeX0` | missing |
| `compositeX1` | missing |
| `compositeY0` | missing |
| `compositeY1` | missing |
| `layerBounds` | missing |
| `maskHeight` | missing |
| `maskLocalX` | missing |
| `maskLocalY` | missing |
| `maskOriginLeft` | missing |
| `maskOriginTop` | missing |
| `maskWidth` | missing |
| `maskedAlphaBeforeBlend` | missing |
| `sourceLayerBounds` | missing |

The local raw FOR-294 build artifact is `present` at
`build/reports/for294/m60-expanded-red-drawrrect-runtime-trace-for294.raw.json`. Raw A8 `srcInPayload` events inspected: `9088`.
The raw artifact is optional and not required for the decision because the
versioned FOR-294..FOR-297 artifacts and trace schema already show that the
pre-dispatch A8 filter state is not serialized.

## Source Needles

| Needle | Present |
|---|---|
| `a8SrcInPayloadDispatchSourcePresent` | True |
| `a8LoopUsesMaskLocalCoordinates` | True |
| `a8LoopHasMaskedAlphaSkipBeforeDispatch` | True |
| `traceRecordSchemaIsPostBlendOnly` | True |
| `for294RawJsonSerializesOnlyEventFields` | True |

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

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, runtime trace,
or setPixel behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-a8-srcinpayload-runtime-filter-for298.json`
