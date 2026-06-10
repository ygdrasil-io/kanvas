# KAN-039 Nested Clip-Stack V1

KAN-039 selects the bounded M60 nested clip row and keeps it as a stable
refusal. The row is inside the current clip-depth and edge budgets, and its
clip sequence is explicit, but WebGPU visual parity remains below the support
floor and the route diagnostics are still scene-contract evidence rather than
a selector-owned `fallbackReason=none` support route.

## Decision

| Field | Value |
|---|---|
| Closure | `stable-refusal-nested-clip-stack-v1` |
| supportClaim | `False` |
| Row status | `expected-unsupported` |
| Renderer changed | `False` |
| Shader changed | `False` |
| Threshold changed | `False` |
| Edge budget changed | `False` |

## Candidate

| Fact | Value |
|---|---|
| Scene | `m60-bounded-nested-rrect-clip` |
| Source | `BlurredClippedCircleGM` |
| Status | `expected-unsupported` |
| Fallback | `coverage.nested-clip-visual-parity-below-threshold` |
| CPU route | `cpu.coverage.nested-rrect-clip-oracle` |
| GPU route | `webgpu.coverage.nested-rrect-clip.expected-unsupported` |
| Clip depth | `3/4` |
| Edges | `72/256` |
| GPU similarity | `98.48` / `99.95` |
| Blocking condition | `visual-parity-below-threshold-and-selector-route-dump-missing` |

## Clip Sequence

| Index | Type | Op | ClipInteraction | Raw paint op exposed |
|---:|---|---|---|---|
| 0 | `rect` | `intersect` | `ClipInteraction.DeviceRect-or-AnalyticShape` | `False` |
| 1 | `rect` | `intersect` | `ClipInteraction.DeviceRect-or-AnalyticShape` | `False` |
| 2 | `rrect-oval` | `difference` | `ClipInteraction.AaClip` | `False` |

## M57 Baseline

`m57-aaclip-bounded-grid` remains `pass` through
`webgpu.coverage.aaclip-bounded-grid` with `fallbackReason=none`.

## Forensic Blockers

| Fact | Value |
|---|---|
| FOR-301 decision | `SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE` |
| FOR-302 decision | `M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT` |
| Support decision | `KEEP_EXPECTED_UNSUPPORTED` |
| Difference formula matches | `True` |
| Safe local fix applied | `False` |

## Refusal Policy

Refused clip-stack families: `arbitrary-aa-path-intersect`, `multi-shape-aa-difference`, `shader-clip`, `unlowerable-stack`.

## Required Before Support

- WebGPU route with fallbackReason=none
- selector-owned route diagnostics rather than scene-contract-only diagnostics
- visual parity at or above 99.95 without threshold weakening
- clip sequence/depth/type/fallback diagnostics preserved
- m57-aaclip-bounded-grid remains supported
- arbitrary AA clips and complex nested clips remain refused with stable reasons
- no integer scissor substitution for AA clip support

## Validation

| Check | Status | Evidence |
|---|---|---|
| `m60-candidate-visible-refusal` | `pass` | m60-bounded-nested-rrect-clip remains expected-unsupported via coverage.nested-clip-visual-parity-below-threshold. |
| `clip-sequence-diagnostics-visible` | `pass` | Clip sequence is rect/intersect, rect/intersect, rrect-oval/difference with depth 3/4. |
| `budget-diagnostics-preserved` | `pass` | Candidate stays under clip depth 3/4 and edge count 72/256; refusal is visual parity, not budget overflow. |
| `m57-support-baseline-preserved` | `pass` | m57-aaclip-bounded-grid remains pass with WebGPU fallbackReason=none. |
| `forensic-blocker-visible` | `pass` | FOR-301/FOR-302 keep M60 expected-unsupported and block reuse of the superseded analytic model. |
| `clip-policy-refusals-visible` | `pass` | Arbitrary AA clip and unlowerable clip-stack refusals remain stable; no integer scissor substitution is introduced. |
| `policy-preserved` | `pass` | No renderer, shader, threshold, edge-budget, or clip-depth budget change is made. |

## Non-Claims

- KAN-039 does not claim nested clip WebGPU support.
- KAN-039 does not claim arbitrary clip-stack, clipShader, perspective clip, path boolean parity, inverse clip, or complex clip support.
- KAN-039 does not replace arbitrary AA clips with integer scissor.
- KAN-039 does not lower the 99.95 threshold or raise the 256 edge budget / depth 4 clip budget.
- KAN-039 does not port Ganesh or Graphite and does not add SkSL compiler behavior.
