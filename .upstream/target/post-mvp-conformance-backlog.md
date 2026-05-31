# Post-MVP Conformance Backlog

Status: Proposed
Date: 2026-05-28
Target: `.upstream/target/rendering-conformance-performance-target.md`
Input: `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md`

## Purpose

This backlog starts after the WGSL/WebGPU MVP and M36-M40 static dashboard
closeout. It should move Kanvas from static evidence toward generated
conformance, adapter-backed captures, measured performance, and carefully
promoted rendering families.

## Baseline

Current M40 dashboard state:

| Signal | Count | Interpretation |
|---|---:|---|
| Scene rows | 11 | P0, M37, M38, and M39 selected P1 rows. |
| `pass` | 7 | CPU/GPU/reference evidence exists for selected rows. |
| `tracked-gap` | 2 | P0 route evidence exists, but GPU capture is not attached. |
| `expected-unsupported` | 2 | Path AA breadth remains explicit and scoped. |
| `fail` | 0 | No current dashboard row is a failing support claim. |
| CPU perf `estimated` | 5 | Static informational metrics, not benchmark gates. |
| GPU perf `estimated` | 5 | Static informational metrics, not benchmark gates. |

## Milestones

| Milestone | Goal | Definition of Done |
|---|---|---|
| M41 Generated Conformance Dashboard | Generate dashboard rows from test outputs. | At least three scenes are emitted by a test/report task with reference, CPU, GPU or expected-unsupported, route JSON, diff, and stats artifacts. Static-only rows remain labelled as such. |
| M42 Adapter-Backed P0 GPU Captures | Close current P0 tracked gaps. | `solid-rect` and `analytic-aa-convex` either have adapter-backed GPU captures and diffs or a documented reason they remain tracked-gap with follow-up tickets. |
| M43 Real Benchmark Harness | Replace selected estimated metrics with measured benchmark output. | At least two CPU and two GPU rows have measured host/JDK/backend/adapter metadata, warm/cold context, baseline name, and regression label. Estimated fields remain non-gating. |
| M44 First Real Path AA Family Promotion | Promote one narrow Path AA family to rendered support. | One selected family has CPU/GPU/reference artifacts, route diagnostics, passing thresholds, and reduced expected-unsupported inventory without weakening fallback diagnostics. |
| M45 Image-Filter DAG Subset V1 | Extend image-filter support beyond the selected M38 pre-pass. | A bounded multi-node or multi-family image-filter subset renders through explicit pre-pass/layer contracts with dashboard evidence and stable out-of-scope diagnostics. |
| M46 Generated Evidence Expansion | Convert high-value static pass rows to generated evidence. | At least five additional dashboard rows are generated from test/report outputs, merged export remains 0 tracked-gap / 0 fail, and remaining static rows are explicitly listed with owners. |
| M47 Remaining Static Evidence Hardening | Convert remaining static pass rows and keep Path AA policy rows visible. | Remaining static pass rows are generated evidence, expected-unsupported Path AA rows remain explicit policy sentinels, and the merged export remains 0 tracked-gap / 0 fail. |
| M48 Skia Scene Coverage Expansion | Expand from clean dashboard evidence to a representative MEP scene pack. | Add 8-12 selected P0/P1 rows across multiple Skia-relevant families, keep unsupported breadth explicit, and update PM readiness without introducing tracked gaps or failing support claims. |
| M49 MEP Readiness Gate Toward 60% | Promote the dashboard from local evidence into release-oriented readiness gates and PM packaging. | Add gate invariant spec, CI validation task, portable PM artifact bundle, adapter-backed expansion to at least six rows, non-blocking performance trend gate contract, MEP release checklist, and sprint review. The 60% readiness score may be claimed only if all lanes land with merged evidence. |

## M41 Seed Tickets

- Define generated scene result schema compatible with
  `reports/wgsl-pipeline/scenes/data/scenes.json`.
- Add an exporter that materializes image/diff/route/stats artifacts from test
  outputs.
- Convert `bitmap-rect-nearest`, `crop-image-filter-nonnull-prepass`, and one
  M39 P1 row to generated evidence.
- Keep the static dashboard readable when generated artifacts are absent.

## M42 Seed Tickets

- Capture adapter-backed GPU output for `solid-rect`.
- Capture adapter-backed GPU output for `analytic-aa-convex`.
- Update P0 closeout evidence so tracked gaps either close or remain explicitly
  justified.

## M43 Seed Tickets

- Define benchmark JSON payload and environment metadata.
- Add CPU measured metrics for two stable P1 rows.
- Add GPU measured metrics for two stable P1 rows.
- Add regression label policy and non-gating CI budget note.

## M44 Seed Tickets

- Select one Path AA family from the M37 ranking.
- Implement or route the selected family without raising the global edge budget
  by default.
- Add dashboard row and inventory before/after report.
- Keep broad Path AA suites expected unsupported until scoped.

## M45 Seed Tickets

- Specify the bounded image-filter DAG subset.
- Implement explicit intermediate texture/layer ownership.
- Add CPU/GPU/reference dashboard rows.
- Keep unsupported DAG shapes visible through stable diagnostics.

## M46 Seed Tickets

- Convert `solid-rect` to generated evidence using the adapter-backed P0
  capture path.
- Convert `analytic-aa-convex` to generated evidence while preserving the
  composited `SrcOver` AA oracle contract.
- Convert `path-aa-stroke-primitive` to generated evidence and preserve the M44
  inventory delta evidence.
- Convert `image-filter-compose-cf-matrix-transform` to generated evidence and
  preserve pre-pass/intermediate texture route diagnostics.
- Convert one measured-performance scene, preferably `src-over-stack` or
  `bitmap-shader-local-matrix`, without losing M43 measured payload links.
- Publish a sprint review that reports generated/static counts, remaining
  static rows, tag aggregates, support status counts, and validation commands.

## Execution Rules

- Do not reopen MVP acceptance criteria.
- Do not treat static seed metrics as measured performance.
- Do not mark a scene pass without rendered artifacts and route diagnostics.
- Do not hide expected unsupported rows from dashboard or inventory evidence.
- Keep Linear tickets linked to specs, reports, validation commands, and raw
  artifacts.

## M41 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m41-generated-dashboard-closeout.md`.

Final M41 dashboard evidence:

| Signal | Count |
|---|---:|
| Total scene rows | 11 |
| Generated rows | 3 |
| Static rows | 8 |
| `pass` | 7 |
| `tracked-gap` | 2 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

Generated rows:

- `bitmap-rect-nearest`
- `crop-image-filter-nonnull-prepass`
- `linear-gradient-rect`

M42 starts from two explicit P0 tracked gaps: `solid-rect` and `analytic-aa-convex`.

## M42 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m42-adapter-backed-p0-capture-closeout.md`.

M42 attempted both P0 adapter-backed captures:

| Scene | M41/M42 start | M42 result | Follow-up |
|---|---|---|---|
| `solid-rect` | `tracked-gap` | `pass` with adapter-backed WebGPU capture on Apple M2 Max, 100.0% similarity, `fallbackReason=none`. | None. |
| `analytic-aa-convex` | `tracked-gap` | Initially `tracked-gap` with adapter-backed WebGPU capture on Apple M2 Max, 90.6% similarity, `fallbackReason=none`, `edgeBudgetReason=not coverage.edge-count-exceeded`; resolved by GRA-222 to `pass` after regenerating the CPU oracle as composited `SrcOver` AA edge pixels with 100.0% similarity. | None. |

Final M42 dashboard state after GRA-222 follow-up resolution:

| Signal | Count |
|---|---:|
| `pass` | 6 static rows, plus generated rows merged at export time |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

GRA-222 resolved the `analytic-aa-convex` oracle/policy gap by regenerating the
CPU oracle as composited `SrcOver` AA edge pixels. The row remains distinct from
broad Path AA edge-budget refusals: `edgeBudgetReason=not
coverage.edge-count-exceeded`.

## M43 Policy Note

GRA-209 defines the M43 baseline and regression policy in
`reports/wgsl-pipeline/2026-05-28-m43-baseline-regression-policy.md` and
`.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`.

The measured CPU and GPU/cache rows created by GRA-207 and GRA-208 remain
`reporting-only`. No required CI performance gate exists until a follow-up ticket
adds explicit budget, host/JDK/backend/adapter eligibility, variance threshold,
flake/quarantine handling, rollback rules, and a baseline owner.

## M43 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m43-real-benchmark-harness-closeout.md`.

M43 produced measured benchmark payloads for two stable rows:

| Scene | CPU | GPU/cache |
|---|---|---|
| `src-over-stack` | `measured`, baseline `m43-cpu-measured-local` | `measured`, baseline `m43-gpu-cache-measured-local`, adapter `Apple M2 Max` |
| `bitmap-shader-local-matrix` | `measured`, baseline `m43-cpu-measured-local` | `measured`, baseline `m43-gpu-cache-measured-local`, adapter `Apple M2 Max` |

All M43 measured metrics remain `reporting-only`; no required CI performance
gate was added. Future gate activation requires explicit CI budget,
host/JDK/backend/adapter eligibility, variance threshold, flake/quarantine
handling, rollback rules, and a baseline owner.

## M44 Selection Note

GRA-211 selects `StrokeRectGM` and `StrokeCircleGM` as the first real Path AA
family promotion target. Expected inventory effect is four rows removed from
`coverage.edge-count-exceeded` if the bounded primitive-stroke route succeeds;
all broad Path AA suites remain expected unsupported until separately scoped.

## M44 Inventory Note

GRA-214 confirms the M44 primitive-stroke promotion reduced Path AA expected
unsupported inventory from 50 to 46 rows. The selected `StrokeRectGM` and
`StrokeCircleGM` WebGPU/cross-backend rows now render through
`webgpu.coverage.path-aa-stroke-primitive`; broad Path AA suites remain visible
as `coverage.edge-count-exceeded` with zero unexpected exceptions and zero
similarity regressions.

## M44 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m44-path-aa-family-promotion-closeout.md`.

M44 promoted the selected primitive-stroke Path AA family (`StrokeRectGM` and
`StrokeCircleGM`) through `webgpu.coverage.path-aa-stroke-primitive` with
adapter-backed rendered evidence and dashboard scene `path-aa-stroke-primitive`.
The expected unsupported Path AA inventory moved from 50 to 46 rows with zero
unexpected exceptions and zero similarity regressions. Remaining broad Path AA
families stay explicitly expected unsupported until separately scoped.

## M45 Selection Note

GRA-216 selects `Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine))` as
M45's bounded image-filter DAG subset. The planned dashboard scene is
`image-filter-compose-cf-matrix-transform`; it requires one MatrixTransform
materialise scratch followed by a final ColorFilter composite. Broader DAG
shapes remain explicitly out of scope until separately scoped.

## M45 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m45-image-filter-dag-subset-closeout.md`.

M45 promoted the bounded two-node image-filter DAG subset
`Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine))` with dashboard
scene `image-filter-compose-cf-matrix-transform`. The route materialises the
affine MatrixTransform into `LayerCompositeDraw.materializeTargetTexture`, then
applies the ColorFilter during final composite. No full Skia image-filter DAG
support is claimed; broader DAG shapes remain explicitly scoped with stable
refusal policy.

## M46 Plan

M46 should harden evidence for already-supported rows before adding new feature
scope. Starting point after M45/GRA-221:

| Signal | Count |
|---|---:|
| Merged scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| `maturity.generated-evidence` | 3 |
| `maturity.static-evidence` | 10 |
| `maturity.adapter-backed` | 2 |

M46 target:

| Signal | Target |
|---|---:|
| `maturity.generated-evidence` | >= 8 |
| `maturity.static-evidence` | <= 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |

M46 should not change runtime support claims unless the generated artifacts
prove the same route, threshold, fallback, and tag semantics as the static row
being replaced.

M46 closed on 2026-05-30 by
`reports/wgsl-pipeline/2026-05-30-m46-generated-evidence-expansion-closeout.md`.
Final counters: 13 rows, 11 pass, 0 tracked-gap, 2 expected-unsupported, 0
fail, 8 generated-evidence rows, and 5 static-evidence rows. Remaining static
rows are `runtime-effect-simple`, `clip-rect-difference`,
`bitmap-shader-local-matrix`, `path-aa-stroke-outline-fallback`, and
`path-aa-edge-budget-boundary`.


## M47 Closeout

M47 closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md`.
Final counters: 13 rows, 11 pass, 0 tracked-gap, 2 expected-unsupported, 0
fail, 11 generated-evidence rows, and 2 static-evidence rows. Remaining static
rows are `path-aa-stroke-outline-fallback` and `path-aa-edge-budget-boundary`;
both are intentional Path AA policy sentinels with stable fallback reasons.

## M48 Closeout

M48 closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m48-sprint-review.md`.
Final counters: 23 rows, 18 pass, 0 tracked-gap, 5 expected-unsupported, 0
fail, 21 generated-evidence rows, and 2 static-evidence rows.

M48 added 10 selected P0/P1 rows:

- 7 generated support rows across paint, clip, transform, bitmap, gradient,
  blend, and Path AA-adjacent coverage;
- 3 generated expected-unsupported breadth rows for hard Path AA and
  image-filter planning boundaries.

The Post-MVP Big Target readiness moved from 35% to 40%. The Skia integration
coverage sub-score moved from 15% to 35% because M48 broadened representative
scene evidence while preserving 0 tracked-gap and 0 fail. The score does not
move higher because CI/release gates, performance thresholds, broader
adapter-backed coverage, text/font/codec coverage, and deployable PM reporting
remain outside M48.

Recommended next milestone: M49 should focus on CI and release gates for the
generated scene dashboard, including required invariants, CI-friendly validation,
portable PM artifact bundles, release readiness checklist, and performance gate
design.

## M49 Plan

M49 starts from the M48 dashboard state: 23 rows, 18 pass, 5
expected-unsupported, 0 tracked-gap, 0 fail, 21 generated-evidence rows, 2
static policy rows, and 2 adapter-backed rows.

The sprint target is ambitious: move Post-MVP Big Target readiness from 40% to
about 60% if, and only if, the milestone lands multiple release-relevant lanes:

| PM area | Start | M49 target | Required evidence |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Preserve 0 tracked-gap / 0 fail and stable generated dashboard semantics. |
| Skia integration coverage | 35% | 45% | Add adapter-backed proof for selected high-value pass rows, reaching at least 6 adapter-backed rows. |
| CI and release gates | 10% | 60% | Add CI-friendly dashboard validation and release-gate policy. |
| Performance readiness | 15% | 35% | Define and report a non-blocking measured-performance trend gate. |
| PM demo and reporting workflow | 15% | 45% | Generate a portable PM bundle with manifest, counters, artifacts, known limitations, and serve instructions. |

Planned tickets:

- M49-A gate invariant spec;
- M49-B CI validation task;
- M49-C portable PM artifact bundle;
- M49-D adapter-backed expansion;
- M49-E performance trend gate contract;
- M49-F MEP release readiness checklist;
- M49-G sprint review and score update.

The detailed sprint plan is
`reports/wgsl-pipeline/2026-05-31-m49-60-readiness-sprint-plan.md`.
