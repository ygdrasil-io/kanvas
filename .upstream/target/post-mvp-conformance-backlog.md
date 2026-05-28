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
