# KAN-042 Image Filter Residual Refusal Matrix

KAN-042 packages the residual image-filter refusal matrix from existing
generated evidence. It separates bounded support from implementation gaps and
dependency-gated rows without adding renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 15 |
| Supportable borne | 5 |
| implementation-gap | 6 |
| dependency-gated | 4 |
| Rows missing stable reason | 0 |
| Dashboard fail rows | 0 |
| Dashboard tracked-gap rows | 0 |

## Supportable borne

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
| `crop-image-filter-nonnull-prepass` | `pass` | `none` | crop bounded support | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` |
| `m61-compose-cf-matrix-transform-dag-v2` | `pass` | `none` | bounded compose/color-filter/matrix-transform DAG | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` |
| `m53-imageblur-bounded-prepass` | `pass` | `none` | bounded blur support | `webgpu.image-filter.blur-bounded-prepass` |
| `m54-imagefilter-transformed-affine` | `pass` | `none` | affine transform support | `webgpu.image-filter.transformed-affine.materialize-final-composite` |
| `m54-matrix-imagefilter-affine` | `pass` | `none` | matrix affine support | `webgpu.image-filter.matrix-affine.prepass` |

## implementation-gap

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
| `image-filter-crop-nonnull-prepass-required` | `expected-unsupported` | `image-filter.crop-input-nonnull-prepass-required` | crop out of scope | `webgpu.image-filter.refuse` |
| `m52-big-tile-image-filter-dag-refusal` | `expected-unsupported` | `image-filter.dag-or-picture-prepass-required` | picture/layer prepass boundary | `webgpu.image-filter.refuse` |
| `m54-imagefilters-graph-boundary` | `expected-unsupported` | `image-filter.dag-or-picture-prepass-required` | recursive DAG and picture-prepass boundary | `webgpu.image-filter.graph.expected-unsupported` |
| `skia-gm-blurbigsigma` | `expected-unsupported` | `image-filter.blur-large-sigma-unsupported` | blur chain / large sigma | `n/a` |
| `skia-gm-perspectiveclip` | `expected-unsupported` | `image-filter.perspective-clip-unsupported` | perspective transform / clip | `n/a` |
| `skia-gm-xfermodeimagefilter` | `expected-unsupported` | `image-filter.xfermode-dag-unsupported` | xfermode image-filter DAG | `n/a` |

## dependency-gated

| Row | Status | Reason | Focus | GPU route |
|---|---|---|---|---|
| `skia-gm-animatedimageblurs` | `dependency-gated` | `image-filter.animated-image-decode-dependency-gated` | animated blur decode | `n/a` |
| `skia-gm-animatedbackdropblur` | `dependency-gated` | `image-filter.animated-codec-backdrop-dependency-gated` | animated / codec-backed backdrop blur | `n/a` |
| `skia-gm-imagefiltersstroked` | `dependency-gated` | `image-filter.path-aa-stroke-dependency-gated` | image-filter plus Path AA stroke breadth | `n/a` |
| `skia-gm-runtimeimagefilter` | `dependency-gated` | `image-filter.runtime-descriptor-scope-dependency-gated` | runtime image-filter descriptor scope | `n/a` |

## Claim Guard

| Guard | Value |
|---|---|
| unsupportedRowsMissingStableReason | `[]` |
| unsupportedRowsMissingCategory | `[]` |
| supportRowsMissingProofs | `[]` |
| hiddenBroadSupportClaims | `[]` |
| unexpectedDashboardRows | `[]` |
| thresholdOrBudgetChanges | `[]` |

## Required Validation

- `validateKan008ImageFilterDagRefusals`
- `validateKan041ImageFilterDagBoundedV3`
- `pipelineSceneDashboardGate`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `stable-refusal-reasons` | `pass` | Every non-support row has a stable image-filter reason code and PM category. |
| `support-refusal-separated` | `pass` | Matrix separates supportable-bounded, implementation-gap, and dependency-gated rows. |
| `dashboard-clean` | `pass` | Generated dashboard carries zero fail rows and zero tracked-gap rows. |
| `no-new-rendering-claim` | `pass` | Pack aggregates existing evidence only and records renderer/shader/threshold/budget changes as false. |

## Non-Claims

- KAN-042 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-042 does not claim arbitrary recursive image-filter DAG support.
- KAN-042 does not claim picture prepass, large layer prepass, BigTile/ImageFiltersGraph broad parity, or CPU readback fallback.
- KAN-042 does not convert dependency-gated or implementation-gap rows into support.
- KAN-042 does not rebuild Skia image-filter internals, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
