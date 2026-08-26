# SDD ledger — plan: docs/superpowers/plans/2026-08-26-hard-clip-rrect-inverse-translate.md

## Preflight

| Task pairing | Producer / consumer | Finding |
|---|---|---|
| Task 1 only | Planner and mapper RRect admission, inverse stencil state, public Surface route, complement CPU oracle, catalog, and promotion are delivered together. | No cross-task interface exists. |

| Task | Internal consistency check | Finding |
|---|---|---|
| Task 1 | The exact finite non-zero matrix predicate is already identical in the planner and mapper; both will add `InverseWinding` admission while the existing stencil geometry carries `inverseFill` and the oracle independently takes the triangle complement. | Consistent. The wave is RRect-only and preserves the existing DRRect inverse refusal. |

Baseline at stacked parent `e0f1956c4c1659e223118ced977abb62dd2683cb`: focused W13 gate and `verifyPromotedGpuEvidence` passed for 68 cases (66 renders, 2 refusals). The global `check` baseline is independently known red on master in `FontTelemetrySchemaTest` and is outside this plan.

## Task 1 complete

Source/tests commit `4e6ba7d71d812fd88b3b3f6959beaa834641068b` admits only exact finite translated `FillRRect` consumers through inverse-Winding hard path clips. The focused GREEN gate passed; four native bundles are exact (`0` differing pixels, `0` max channel difference, one submission, `HardClipStencilProducer -> AnalyticRRect`), and promoted evidence was rebaselined from 68/66/2 to 72/70/2 with `verifyPromotedGpuEvidence` green. Full command/result trace: `task-1-report.md`.
