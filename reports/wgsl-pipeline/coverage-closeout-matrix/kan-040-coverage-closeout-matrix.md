# KAN-040 Coverage Stroke Clip Closeout Matrix

KAN-040 aggregates the coverage/strokes/clips wave without adding renderer
support. The matrix keeps the only support claim bounded to
`m57-aaclip-bounded-grid`; every other row remains visible as a stable refusal
or dependency-gated row.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 6 |
| supportable-bounded | 1 |
| visible-non-supportable | 1 |
| expected-unsupported | 3 |
| dependency-gated | 1 |
| support claims | 1 |
| unsupported rows | 5 |

## Matrix

| Ticket | Row | Classification | Status | Fallback | Support claim | Missing proofs |
|---|---|---|---|---|---:|---|
| KAN-004 | `m57-aaclip-bounded-grid` | `supportable-bounded` | `pass` | `none` | `True` | none |
| KAN-035 | `skia-gm-hairlines` | `visible-non-supportable` | `expected-unsupported` | `coverage.hairline.row-specific-artifacts-required` | `False` | `rowLocalDiffImage`, `webGpuImage` |
| KAN-036 | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` | `expected-unsupported` | `expected-unsupported` | `coverage.stroke-cap-join-visual-parity-below-threshold` | `False` | `webGpuDiff`, `webGpuImage` |
| KAN-037 | `m60-bounded-stroke-cap-join` | `expected-unsupported` | `expected-unsupported` | `coverage.stroke-cap-join-visual-parity-below-threshold` | `False` | `webGpuSupportDiff`, `webGpuSupportImage` |
| KAN-038 | `skia-gm-dashing-width1-pattern1-1-aa` | `dependency-gated` | `expected-unsupported` | `coverage.dashing.row-specific-artifacts-required` | `False` | `postDashVerbEdgeStats`, `webGpuSupportDiff`, `webGpuSupportImage` |
| KAN-039 | `m60-bounded-nested-rrect-clip` | `expected-unsupported` | `expected-unsupported` | `coverage.nested-clip-visual-parity-below-threshold` | `False` | `webGpuSupportRoute` |

## Claim Guard

| Guard | Value |
|---|---|
| supportRowsMissingProofs | `[]` |
| unsupportedRowsMissingFallback | `[]` |
| unsupportedRowsUnstableReason | `[]` |
| hiddenPromotionRows | `[]` |
| budgetOrThresholdChanges | `[]` |
| pmBundleCategoriesVisible | `True` |

## Required Validation

- `validateKan035HairlinesRootCause`
- `validateKan036ButtStrokeNonHairline`
- `validateKan037CapsJoinsMicroMatrix`
- `validateKan038DashesBoundedV1`
- `validateKan039NestedClipStackV1`
- `pipelineSceneDashboardGate`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `support-claims-have-complete-proofs` | `pass` | The only support row is m57-aaclip-bounded-grid and all support proof booleans are true. |
| `unsupported-rows-have-stable-reasons` | `pass` | All five non-support rows carry stable non-none fallback reason codes. |
| `pm-categories-visible` | `pass` | Matrix includes supportable-bounded, visible-non-supportable, expected-unsupported, and dependency-gated categories. |
| `source-policy-preserved` | `pass` | No source pack reports renderer, shader, threshold, edge-budget, shared-coverage, or hidden promotion changes. |
| `artifact-audit-complete` | `pass` | 100 committed source artifacts are present. |

## Non-Claims

- KAN-040 does not add renderer, shader, selector, PipelineKey, threshold, edge-budget, dash-budget, or clip-depth changes.
- KAN-040 does not claim broad Path AA, hairline, stroke, cap/join, dash, AA clip, or clip-stack support.
- KAN-040 does not promote visible non-supportable, expected-unsupported, or dependency-gated rows to support.
- KAN-040 does not replace AA clip evidence with integer scissor substitution.
- KAN-040 does not port Ganesh or Graphite and does not add SkSL compiler behavior.
