# FOR-301 M60 SkAAClip Band Trace

Linear: `FOR-301`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE`

Exact gap/result: `The runtime SkAAClip parent is full over the 59 original pixels, the rasterized difference path is also full over the same pixels, and kDifference alpha merge therefore produces result coverage 0. This explains the FOR-300 runtime hole without changing normal rendering.`

## Result

FOR-301 adds opt-in runtime instrumentation for the `SkAAClip` state produced
by the M60 `clipRRect(kDifference)` stack. The trace captures parent, rasterized
path, and final difference result bands/runs plus coverage probes for the
original 59 pixels, `candidate-minus-runtime-002`, and the `red-runtime-*`
components.

| Measure | Value |
|---|---:|
| Probe groups | 6 |
| Probe pixels | 12440 |
| Parent bounds | `{'left': 0, 'top': 0, 'right': 1164, 'bottom': 802}` |
| Path bounds | `{'left': 16, 'top': 16, 'right': 576, 'bottom': 576}` |
| Result bounds | `{'left': 0, 'top': 0, 'right': 1164, 'bottom': 802}` |
| Parent row/run count | 1/1 |
| Path row/run count | 489/2889 |
| Result row/run count | 491/2915 |
| CTM scale | sx=2.0, sy=2.0 |

## Component Comparison

| Component | Pixels | Bounds | Parent cov=255 | Path cov=255 | Result cov=0 | Result cov>0 | FOR-293 cov=0 | FOR-293 cov>0 | Difference formula matches |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|
| `original-59-targets` | 59 | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` | 59 | 59 | 59 | 0 | 0 | 59 | True |
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 3293 | 3281 | 3281 | 12 | 0 | 3293 | True |
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 2275 | 0 | 0 | 2275 | 0 | 2275 | True |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 2275 | 0 | 0 | 2275 | 0 | 2275 | True |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 2270 | 0 | 0 | 2270 | 0 | 2270 | True |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 2268 | 0 | 0 | 2268 | 0 | 2268 | True |

## Interpretation

The 59 original pixels are inside the result bounds and have full parent
coverage. They also have full coverage in the rasterized difference path, so
`kDifference` alpha merge computes `parent * (255 - path) / 255 = 0`. The
runtime bands therefore explain the FOR-300 `activeAaClip.coverage == 0`
observation. FOR-293's analytic model remains contradicted by runtime evidence;
FOR-301 does not apply a renderer fix because changing CTM/path modelling now
would need separate before/after reference evidence.

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-skaaclip-band-trace-for301.json`

## Source Needles

| Needle | Present |
|---|---|
| `skAAClipDebugSnapshotExposesBands` | True |
| `skAAClipDebugSnapshotExposesLineProbes` | True |
| `traceIsOptIn` | True |
| `traceDefaultsDisabled` | True |
| `clipDifferenceRecordsParentPathResult` | True |
| `normalRenderingStillUsesDifferenceOp` | True |
| `for301TestSerializesBands` | True |
