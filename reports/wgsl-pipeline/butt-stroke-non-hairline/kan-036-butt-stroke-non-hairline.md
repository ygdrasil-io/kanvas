# KAN-036 Butt Stroke Non-Hairline

KAN-036 selects one bounded non-hairline butt-cap stroke row and closes it as a
stable refusal, not as support. The current WebGPU selector still refuses stroke
style facts with `coverage.stroke-cap-join-visual-parity-below-threshold` before any WebGPU image or diff
artifact exists.

## Decision

| Field | Value |
|---|---|
| Closure | `stable-refusal-existing-selector` |
| supportClaim | `False` |
| Row status | `expected-unsupported` |
| Renderer changed | `False` |
| Shader changed | `False` |
| Threshold changed | `False` |
| Edge budget changed | `False` |
| Readiness delta | `0` |

## Selected Row

| Fact | Value |
|---|---|
| Fixture | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` |
| Source GM | `CircularArcsStrokeButtGM` |
| Stroke width | `15` |
| Stroke cap | `kButt_Cap` |
| Hairline included | `False` |
| Dash included | `False` |
| Fill included | `False` |
| AA | `True` |

## WebGPU Refusal

| Fact | Value |
|---|---|
| Status | `expected-unsupported` |
| Route | `webgpu.selected-cell-test-harness.refused` |
| Fallback | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| Path verbs | `67/96` |
| Coverage edges | `66/256` |
| Edge budget reason | `not coverage.edge-count-exceeded` |
| Clip stack depth | `n/a/4` |
| Stroke facts | width `15.0`, caps `butt`, joins `miter` |
| Device bounds | `[39.832764, 39.832764, 67.49814, 67.49814]` |

## Artifact Availability

| Artifact | Available | Evidence |
|---|---:|---|
| Skia reference | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` |
| CPU oracle | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` |
| CPU vs Skia diff | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/cpu-vs-skia-diff.png` |
| WebGPU stable refusal | `True` | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| WebGPU image | `False` | `stable-refusal-before-debug-images` |
| WebGPU diff | `False` | `stable-refusal-before-debug-images` |

## Required Before Support

- adapter-backed WebGPU image
- WebGPU diff/stat artifact
- fallbackReason=none
- CPU vs Skia support-ready decision
- no threshold or edge-budget weakening

## Validation

| Check | Status | Evidence |
|---|---|---|
| `selected-row-bounded` | `pass` | The row is one non-hairline, no-dash, butt-cap selected cell with strokeWidth=15. |
| `webgpu-stable-refusal` | `pass` | WebGPU refuses through coverage.stroke-cap-join-visual-parity-below-threshold with edges 66/256 and verbs 67/96. |
| `support-claim-blocked` | `pass` | No WebGPU image/diff exists and CPU-vs-Skia remains in raster-audit status. |
| `policy-preserved` | `pass` | No renderer, shader, selector, threshold, or edge-budget change is made. |

## Non-Claims

- KAN-036 does not claim support for the selected butt stroke row.
- KAN-036 does not claim broad stroke, cap, join, dash, or hairline support.
- KAN-036 does not change renderer, shader, selector, PipelineKey, threshold, or edge-budget behavior.
- KAN-036 does not lower the 99.95 support threshold or increase the 256 WebGPU AA edge budget.
