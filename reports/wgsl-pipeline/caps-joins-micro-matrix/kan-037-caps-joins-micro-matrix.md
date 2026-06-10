# KAN-037 Caps Joins Micro-Matrix

KAN-037 selects one cap/join candidate, keeps two sentinels visible, and closes
the slice as a stable refusal rather than support. The diagnostic-only WebGPU
render remains below the strict support policy because `round-round` boundary
coverage equivalence is not proven and closed-contour join CPU evidence is not
present.

## Decision

| Field | Value |
|---|---|
| Closure | `stable-refusal-micro-matrix` |
| supportClaim | `False` |
| Row status | `expected-unsupported` |
| Renderer changed | `False` |
| Shader changed | `False` |
| Threshold changed | `False` |
| Edge budget changed | `False` |

## Candidate

| Fact | Value |
|---|---|
| Candidate | `round-round` |
| Cap | `round` |
| Join | `round` |
| Miter limit | `4.0` |
| Status | `expected-unsupported` |
| Fallback | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| Blocking condition | `missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells` |
| Residual max delta | `48` |

## Sentinels

| Sentinel | Cap | Join | Decision | Max delta |
|---|---|---|---|---:|
| `butt-bevel` | `butt` | `bevel` | `stable-refusal` | `25` |
| `square-bevel` | `square` | `bevel` | `stable-refusal` | `1` |

## WebGPU Refusal

| Fact | Value |
|---|---|
| Route | `webgpu.coverage.refuse` |
| Diagnostic route | `webgpu.coverage.stroke-cap-join.experimental-render` |
| Fallback | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| Path verbs | `9/96` |
| Coverage edges | `18/256` |
| Dash intervals | `0/8` |
| Stroke facts | width `10.0`, caps `butt+round+square`, joins `bevel+round+bevel`, miter `4.0` |
| Transform | `n/a` |
| Clip stack depth | `n/a` |
| Pipeline key axis | `pathAaStrokeCapJoinBlocked` |

## CPU Evidence Boundary

- Open-contour caps: `True` via `cpu.coverage.stroke-cap-join-oracle`.
- Closed-contour joins: `False`; classification `support-blocker`.

## Required Before Support

- closed-contour CPU join oracle evidence
- round-round CPU/GPU coverage equivalence for boundary cells
- production WebGPU route with fallbackReason=none
- WebGPU support image/diff/stat artifacts
- no threshold or edge-budget weakening

## Validation

| Check | Status | Evidence |
|---|---|---|
| `candidate-round-round-stable-refusal` | `pass` | round-round remains coverage.stroke-cap-join-visual-parity-below-threshold with missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells. |
| `sentinels-visible` | `pass` | butt-bevel and square-bevel remain stable-refusal sentinels in the same M60 scene. |
| `route-diagnostics-complete` | `pass` | Route exposes cap/join/stroke width/miter/edge/dash/device bounds/pipeline key facts. |
| `closed-contour-gap-explicit` | `pass` | Closed-contour join CPU evidence is absent and therefore blocks support. |
| `policy-preserved` | `pass` | No renderer, shader, selector, threshold, or edge-budget change is made. |

## Non-Claims

- KAN-037 does not claim support for round-round, butt-bevel, square-bevel, or broad caps/joins.
- KAN-037 does not change renderer, shaders, selector, PipelineKey, threshold, or edge-budget behavior.
- KAN-037 does not lower the 99.95 support threshold or increase the 256 WebGPU AA edge budget.
- KAN-037 does not treat diagnostic-only WebGPU images as production support.
- KAN-037 does not satisfy closed-contour join CPU evidence; it records that gap as a support blocker.
