# FOR-304 Renderer Feature Conversion Wave Closeout

Linear: `FOR-304`

Parent: `FOR-241`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-renderer-feature-conversion-wave-closeout-ticket`

Decision: `FOR_241_CLOSEOUT_NO_READINESS_MOVEMENT`

## Result

FOR-241 is closed as an evidence-heavy renderer conversion wave with no new
readiness movement. The wave improved diagnostics, applied one bounded CPU
payload contract, and added guards that prevent unsafe M60 promotion, but it
did not prove the sprint minimum of two new counted scene conversions from
`partial` or `expected-unsupported` to `supported`.

The PM readiness score therefore stays at `67.75%`, reported as approximately
`70%`.

## Gate Counters

Source: `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.json`
from `pipelineSceneDashboardGate`.

| Counter | Value |
|---|---:|
| Total rows | 93 |
| Pass rows | 70 |
| Expected unsupported rows | 23 |
| Adapter-backed rows | 66 |
| Inventory-derived rows | 45 |
| Generated-evidence rows | 91 |
| Static-evidence rows | 2 |
| M61 rows | 1 |
| M62 rows | 1 |
| M63 rows | 5 |
| M64 rows | 4 |
| M66 rows | 19 |
| M66 Skia-upstream references | 6 |
| M66 test-oracle references | 6 |
| M66 CPU-oracle references | 7 |

Gate failures: `0`.

Warnings preserved:

- `m60-bounded-nested-rrect-clip` CPU parity remains below strict threshold:
  `97.31 < 99.95`.
- `clip-rect-difference` performance is still estimated/reporting-only.
- `runtime-effect-simple` performance is still estimated/reporting-only.

## Sprint Threshold Outcome

| Sprint target | Required | Actual | Outcome |
|---|---:|---:|---|
| Minimum | 2 new counted conversions | 0 proven | Missed |
| Nominal | 3 conversions across at least 2 families | 0 proven | Missed |
| Stretch | 5 conversions across image filters, clip/RRect/path AA, bitmap, runtime effects | 0 proven | Missed |

This is not a failure of the gate. The gate is clean. It is a support-claim
decision: the wave did not land enough reference/CPU/GPU/diff/stat evidence to
change the counted denominators.

## Work Completed

| Slice | Tickets | Outcome | Score impact |
|---|---|---|---|
| Crop/image-filter fidelity and residual boundary | `FOR-242`-`FOR-265` | Improved and bounded selected image-filter evidence, including source-capture/output-clip work and byte-level residual diagnostics. The remaining residual is not a safe support conversion. | No readiness movement |
| Stroke cap/join target-color and coverage audits | `FOR-266`-`FOR-268` | Target-color and coverage-equivalence evidence remain diagnostic. Stroke cap/join stays below strict visual parity. | No readiness movement |
| M60 nested RRect CPU/GPU residual investigation | `FOR-269`-`FOR-286`, `FOR-288`-`FOR-303` | Isolated the M60 path through CPU layer/composite, A8 payload, runtime red dispatch, active AA clip, SkAAClip bands/runs, analytic-model reconciliation, and supersession guard. M60 remains expected unsupported. | No readiness movement |
| Canceled unsafe M60 patch evidence | `FOR-287` | Kept as historical/canceled evidence only. It improved some inner pixels but regressed full-scene score and is not a safe correction. | No readiness movement |

## Key Decisions Preserved

| Decision | Meaning |
|---|---|
| `image-filter.crop-input-nonnull-prepass-required` | Non-selected Crop(input non-null/picture-prepass shapes remain refused unless a bounded renderer contract proves them. |
| `coverage.stroke-cap-join-visual-parity-below-threshold` | M60 stroke cap/join remains expected unsupported. |
| `coverage.nested-clip-visual-parity-below-threshold` | M60 nested RRect clip remains expected unsupported. |
| `KEEP_EXPECTED_UNSUPPORTED` | M60 is not promoted by the CPU/M60 audit chain. |
| `M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED` | Future M60 support/promotion consumers cannot use the superseded FOR-293 analytic model without FOR-301/FOR-302 supersession. |

## Why Readiness Does Not Move

Readiness only moves when a counted denominator changes with complete support
evidence: reference, CPU, GPU, diff/stat artifacts, route diagnostics, stable
fallback policy, and passing gates.

FOR-241 produced valuable negative and diagnostic evidence, but the final
support claim remains unchanged:

- the dashboard still has `70` pass and `23` expected unsupported rows;
- M60 nested clip and stroke cap/join remain expected unsupported;
- image-filter Crop residual work did not prove a new strict support row;
- Path AA edge-budget rows remain explicit expected unsupported;
- font complex shaping and emoji/color-glyph rows remain dependency-gated;
- performance warnings remain estimated/reporting-only, not measured gates.

## Next Backlog

| Priority | Candidate | Why next | Gate before support claim |
|---|---|---|---|
| 1 | Performance measurement for `clip-rect-difference` and `runtime-effect-simple` | The gate already warns that performance is estimated. Turning these into measured non-estimated lanes can move performance/cache readiness if the target model counts them. | Measured CPU/GPU payloads with machine/JDK/backend metadata and quarantine policy. |
| 2 | Bounded image-filter residual policy | FOR-242-FOR-265 narrowed the remaining image-filter residual to byte/present/target-blend boundaries. | A local renderer change must improve strict parity without global threshold or targetColorSpaceBlend enablement. |
| 3 | Path AA edge-budget candidate selection | Several rows are stable refusals with `coverage.edge-count-exceeded`; a smaller selected geometry may be promotable. | Row-specific geometry, CPU/GPU/reference/diff artifacts, no global budget weakening. |
| 4 | Text/glyph dependency gate | Complex shaping and emoji/color glyphs are explicit dependency refusals. | Real shaper/font/color-glyph dependency delivery, not a substitute. |
| 5 | M60 nested clip follow-up | The M60 chain is now well instrumented, but FOR-301/FOR-302 prove the previous analytic model was wrong. | Only reopen if a new renderer-side causal hypothesis is available; do not use FOR-293 as oracle. |

## Validation

Required:

- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk ./gradlew pipelinePmBundle`
- `rtk python3 -m json.tool reports/wgsl-pipeline/for-304-renderer-feature-conversion-wave-closeout.json`
- `rtk git diff --check origin/master...HEAD`
