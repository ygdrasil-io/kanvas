# KAN-038 Dashes Bounded V1

KAN-038 identifies a bounded direct dash candidate from `DashingGM`, but closes
the slice as a stable refusal rather than support. The candidate has a valid
dash interval count and path-effect order, but the committed dashboard evidence
is policy-only and lacks row-specific WebGPU `fallbackReason=none` artifacts and
post-dash verb/edge diagnostics.

## Decision

| Field | Value |
|---|---|
| Closure | `stable-refusal-dashes-bounded-v1` |
| supportClaim | `False` |
| Row status | `expected-unsupported` |
| Renderer changed | `False` |
| Shader changed | `False` |
| Threshold changed | `False` |
| Edge budget changed | `False` |

## Candidate

| Fact | Value |
|---|---|
| Candidate | `skia-gm-dashing-width1-pattern1-1-aa` |
| Source row | `skia-gm-dashing` |
| Dash intervals | `[1.0, 1.0]` |
| Dash interval count | `2/8` |
| Phase | `0.0` |
| Stroke width | `1.0` |
| AA | `True` |
| Path effect order | `before-stroke` |
| Status | `expected-unsupported` |
| Fallback | `coverage.dashing.row-specific-artifacts-required` |
| Post-dash verbs | `not-recorded` |
| Post-dash edges | `not-recorded` |
| Blocking condition | `row-specific_reference_cpu_gpu_diff_stat_route_artifacts_missing` |

## Over-Budget Sentinel

| Fact | Value |
|---|---|
| Scene | `path-aa-dashing-edge-budget` |
| CPU route | `cpu.path-coverage.dashing-oracle` |
| GPU route | `webgpu.coverage.refuse` |
| GPU status | `expected-unsupported` |
| Fallback | `coverage.edge-count-exceeded` |
| Edge budget | `256` |
| Dash intervals | `2/8` |
| Post-dash edges | `exceeded-current-budget` |
| Pipeline key | `coverageKind=pathStrokeDashOverflow,pathFillRule=winding,topology=triangleList,source=DashingGM` |

## Related Sentinels

| Row | Status | Fallback | GPU route |
|---|---|---|---|
| `m54-dash-circle-boundary` | `expected-unsupported` | `coverage.edge-count-exceeded` | `webgpu.coverage.dash-circle.expected-unsupported` |
| `m66-path-aa-dashing-edge-budget-refusal` | `expected-unsupported` | `coverage.edge-count-exceeded` | `webgpu.coverage.refuse.edge-count` |

## Required Before Support

- row-specific Skia reference image for the bounded dash candidate
- row-specific CPU oracle image/diff/stat/route artifacts
- row-specific WebGPU image/diff/stat/route artifacts with fallbackReason=none
- diagnostics that expose dash intervals, phase, post-dash verbs, post-dash edges, and stroke facts
- over-budget sentinel remains refused with coverage.edge-count-exceeded or coverage.dash-budget-exceeded
- no global edge-budget, threshold, or dash interval budget increase

## Validation

| Check | Status | Evidence |
|---|---|---|
| `bounded-dash-candidate-identified` | `pass` | skia-gm-dashing-width1-pattern1-1-aa uses 2/8 dash intervals, phase 0, stroke width 1, path effect before stroke. |
| `bounded-candidate-refused-with-policy-row` | `pass` | skia-gm-dashing remains expected-unsupported via coverage.dashing.row-specific-artifacts-required. |
| `over-budget-sentinel-preserved` | `pass` | path-aa-dashing-edge-budget remains expected-unsupported via coverage.edge-count-exceeded. |
| `dash-budget-taxonomy-visible` | `pass` | coverage.dash-budget-exceeded remains a stable reason code for dash interval overflow. |
| `support-evidence-gap-explicit` | `pass` | Bounded support remains blocked until post-dash verb/edge diagnostics and WebGPU fallbackReason=none artifacts exist. |
| `policy-preserved` | `pass` | No renderer, shader, selector, threshold, edge-budget, or dash interval budget change is made. |

## Non-Claims

- KAN-038 does not claim bounded dash WebGPU support.
- KAN-038 does not claim broad DashingGM, DashCircleGM, dashcubics, caps/joins, hairlines, or stroke-outline support.
- KAN-038 does not add renderer, shader, selector, threshold, edge-budget, or dash interval budget changes.
- KAN-038 does not infer support from existing cross-backend similarity floors or policy-only dashboard rows.
- KAN-038 keeps over-budget dashed Path AA rows refused by stable budget diagnostics.
