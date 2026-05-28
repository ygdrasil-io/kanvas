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
| `analytic-aa-convex` | `tracked-gap` | `tracked-gap` with adapter-backed WebGPU capture on Apple M2 Max, 90.6% similarity, `fallbackReason=none`, `edgeBudgetReason=not coverage.edge-count-exceeded`. | `GRA-222` owns the AA edge alpha oracle/render contract mismatch. |

Final M42 dashboard state:

| Signal | Count |
|---|---:|
| `pass` | 5 static rows, plus generated rows merged at export time |
| `tracked-gap` | 1 static P0 row: `analytic-aa-convex` |
| `expected-unsupported` | 2 |
| `fail` | 0 |

M43 can start measured benchmark work with `solid-rect` no longer blocked on an adapter-backed GPU capture. M44 must treat `analytic-aa-convex` as a tracked P0 oracle/policy gap until `GRA-222` resolves whether to regenerate the CPU oracle or adjust GPU AA compositing semantics.

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
