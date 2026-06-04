# FOR-300 M60 Active AA Clip Coverage

Linear: `FOR-300`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `A8_ACTIVE_CLIP_CAUSE_BOUNDED_NO_SAFE_FIX`

Exact gap/result: `The 59 original pixels are inside the A8 source/mask/composite/layer spans and map to mask coordinates by the observed mask origin, but every runtime outcome has activeAaClip coverage 0 while the FOR-293 analytic difference-clip model predicts nonzero coverage. The immediate cause is therefore runtime SkAAClip coverage, not A8 iteration, mask bounds, layer scope, or coordinate offset. The exact SkAAClip band/raster difference cause is bounded but not proved enough for a safe local fix.`

## Result

FOR-300 consumes the FOR-299 A8 pre-dispatch trace and compares the original
59 target pixels, `candidate-minus-runtime-002`, and all `red-runtime-*`
components without changing renderer code. The immediate runtime cause is the
active AA clip coverage sampled inside `SkBitmapDevice.blend`: the original
59 pixels are in the A8 mask/source/layer/composite spans but receive
coverage 0 and therefore only produce A8 `blendSkip` events.

| Measure | Value |
|---|---:|
| Red candidate pixels | 22424 |
| `candidate-minus-runtime-002` pixels | 3293 |
| Original target pixels | 59 |
| Red runtime dispatch pixels | 9088 |
| Raw pre-dispatch pixels | 22424 |
| Raw A8 outcome pixels | 22316 |
| Raw A8 dispatch pixels | 9088 |
| Runtime active coverage zero pixels | 13192 |
| Runtime active coverage nonzero pixels | 9124 |

## Component Comparison

| Component | Pixels | Bounds | Original targets | Pre-dispatch | Blend-skip | Dispatch | Runtime cov=0 | Runtime cov>0 | Skip reasons | FOR-293 analytic cov=0 | FOR-293 analytic cov>0 | Final readback |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---|
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 59 | 3293 | 3282 | 0 | 3272 | 10 | `{'A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO': 3272, 'A8_SRCINPAYLOAD_SRC_ALPHA_ZERO_AFTER_COVERAGE': 10}` | 0 | 3293 | `{'finalWhiteLayer': 3293}` |
| `original-59-targets` | 59 | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` | 59 | 59 | 59 | 0 | 59 | 0 | `{'A8_SRCINPAYLOAD_ACTIVE_CLIP_COVERAGE_ZERO': 59}` | 0 | 59 | `{'finalWhiteLayer': 59}` |
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 0 | 2275 | 0 | 2275 | 0 | 2275 | `{}` | 0 | 2275 | `{'finalWhiteLayer': 496, 'finalRedTint': 1526, 'finalOther': 253}` |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 0 | 2275 | 0 | 2275 | 0 | 2275 | `{}` | 0 | 2275 | `{'finalRedTint': 1526, 'finalOther': 253, 'finalWhiteLayer': 496}` |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 0 | 2270 | 0 | 2270 | 0 | 2270 | `{}` | 0 | 2270 | `{'finalRedTint': 1528, 'finalWhiteLayer': 488, 'finalOther': 254}` |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 0 | 2268 | 0 | 2268 | 0 | 2268 | `{}` | 0 | 2268 | `{'finalRedTint': 1528, 'finalOther': 252, 'finalWhiteLayer': 488}` |

## Bounds And Coordinates

| Field | Value |
|---|---|
| FOR-293 mask filter bounds | `{'left': 2, 'top': 2, 'right': 590, 'bottom': 590, 'margin': 5}` |
| FOR-293 difference oval device bounds | `[16, 16, 576, 576]` |
| FOR-293 draw oval device bounds | `[8, 8, 584, 584]` |
| Observed nonzero active coverage bounds inside A8 candidate | `{'left': 4, 'top': 4, 'right': 588, 'bottom': 588, 'rightInclusive': 587, 'bottomInclusive': 587}` |
| Observed zero active coverage bounds inside A8 candidate | `{'left': 16, 'top': 16, 'right': 576, 'bottom': 576, 'rightInclusive': 575, 'bottomInclusive': 575}` |
| Original source/layer/composite contains pixels | `59/59/59` |
| Original mask offset matches mask origin | `True` |
| Original active clip trace bounds | `[{'left': 0, 'top': 0, 'right': 1164, 'bottom': 802}]` |
| Original source layer bounds | `[{'left': 2, 'top': 2, 'right': 590, 'bottom': 590}]` |
| Original composite bounds | `[{'left': 2, 'top': 2, 'right': 590, 'bottom': 590}]` |

`activeClipBounds` in the FOR-299 trace is the draw clip parameter serialized by
the A8 trace, not a dump of `SkAAClip.getBounds()` or its bands. The decisive
coverage value still comes from the runtime `activeAaClip.coverage(x, y)` path
recorded in A8 dispatch/blend-skip outcomes.

## Ruled Out

- Layer scope: original pixels are inside the observed layer/source/composite
  spans, and the red runtime components use the same bounds.
- Coordinate offset: `device - maskLocal` equals the serialized mask origin for
  all original target pixels.
- Mask/source bounds: the original pixels are iterated in pre-dispatch and have
  nonzero blurred mask alpha.

## Remaining Named Gap

The remaining bounded gap is the difference between the FOR-293 analytic
difference-clip model and the runtime `SkAAClip` coverage. FOR-300 does not
apply a local fix because the trace does not serialize the true `SkAAClip`
bounds, bands, or intermediate `op(kDifference)` inputs. A safe correction
needs a follow-up that captures those internals before changing clip CTM or
`SkAAClip` difference semantics.

Recommended next ticket: Instrument true SkAAClip getBounds/bands and coverage probes during the M60 clipRRect(kDifference) stack, then compare transformed vs untransformed oval coverage before changing SkAAClip.op or clip CTM handling.

## Source Needles

| Needle | Present |
|---|---|
| `a8BlendSkipRecordsActiveClipCoverageZero` | True |
| `blendSamplesActiveAaClipCoverage` | True |
| `a8TraceCarriesMaskAndLayerBounds` | True |
| `canvasDifferenceUsesSkAAClipDifference` | True |
| `clipPathDifferenceRasterizesWithCurrentMatrix` | True |
| `skAAClipDifferenceCombinesAlpha` | True |

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
- FOR-299 decision: `A8_SRCINPAYLOAD_LAYER_CLIP_INTERSECTION_EXPLAINS_HOLE`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-active-aa-clip-coverage-for300.json`
