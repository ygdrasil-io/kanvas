# KAN-041 Image Filter DAG Bounded V3

KAN-041 promotes a reporting/evidence slice for two bounded image-filter DAG
scenes and keeps larger or out-of-scope graph shapes refused with stable
diagnostics.

## Summary

| Metric | Count |
|---|---:|
| Support scenes | 2 |
| Expected-unsupported scenes | 3 |
| Support rows missing proofs | 0 |
| Implicit CPU readback fallbacks | 0 |
| Max node budget | 4 |
| Max intermediate texture budget | 4 |

## Support Scenes

| Scene | Nodes | Intermediates | Fallback | Route | Strict Skia parity claim |
|---|---:|---:|---|---|---:|
| `crop-image-filter-nonnull-prepass` | `2/4` | `1/4` | `none` | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` | `False` |
| `m61-compose-cf-matrix-transform-dag-v2` | `3/4` | `1/4` | `none` | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` | `False` |

## Stable Refusals

| Scene | Nodes | Fallback | Non-claim |
|---|---:|---|---|
| `m52-big-tile-image-filter-dag-refusal` | `3/4` | `image-filter.dag-or-picture-prepass-required` | No arbitrary image-filter DAG, picture/layer prepass, or BigTile GPU support is claimed by this row. |
| `m54-imagefilters-graph-boundary` | `4/4` | `image-filter.dag-or-picture-prepass-required` | Does not claim arbitrary image-filter DAG support, recursive scheduling, or picture prepass support. |
| `image-filter-crop-nonnull-prepass-required` | `2/4` | `image-filter.crop-input-nonnull-prepass-required` | None |

## Claim Guard

| Guard | Value |
|---|---|
| supportRowsMissingProofs | `[]` |
| supportRowsMissingFallbackNone | `[]` |
| unsupportedRowsMissingStableReason | `[]` |
| implicitCpuReadbackFallbacks | `[]` |
| overBudgetSupportRows | `[]` |
| hiddenPicturePrepassSupport | `[]` |

## Required Validation

- `validateKan006IntermediateTextureOwnership`
- `validateKan008ImageFilterDagRefusals`
- `pipelineM61ImageFilterDagV2PromotionPack`
- `pipelineSceneDashboardGate`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `two-bounded-support-scenes` | `pass` | crop-image-filter-nonnull-prepass and m61-compose-cf-matrix-transform-dag-v2 both carry complete reference/CPU/GPU/diff/stat/route proofs with fallbackReason=none. |
| `graph-diagnostics-visible` | `pass` | Support graphs expose node count, bounds, intermediate ownership, pass order, and byte estimates. |
| `unsupported-graphs-stable` | `pass` | M52 BigTile, M54 ImageFiltersGraph, and out-of-scope Crop(input=nonNull) remain expected-unsupported with stable reasons. |
| `no-hidden-readback` | `pass` | Support graph ownership records cpuReadbackFallback=false and no support row claims picture prepass. |
| `policy-preserved` | `pass` | No renderer, shader, threshold, PipelineKey, or budget change is made. |

## Non-Claims

- KAN-041 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-041 does not claim arbitrary recursive image-filter DAG support.
- KAN-041 does not claim picture prepass, large layer prepass, BigTile/ImageFiltersGraph broad parity, or CPU readback fallback.
- KAN-041 does not claim strict Skia parity for crop-image-filter-nonnull-prepass; it keeps risk.fidelity-gap visible.
- KAN-041 does not rebuild Skia image-filter internals, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
