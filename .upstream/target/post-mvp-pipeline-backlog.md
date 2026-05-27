# Post-MVP Pipeline Backlog

Status: Proposed
Date: 2026-05-28
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Input: `reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md`

## Purpose

Define the next pipeline milestones after the WGSL/WebGPU MVP closeout. The
MVP is complete, so this backlog should not reopen MVP acceptance criteria.
It should convert the remaining visible limitations and observability gaps into
scoped post-MVP work.

## Current Baseline

Final MVP inventory:

| Category | Count | Interpretation |
|---|---:|---|
| `expected-unsupported-diagnostic` | 50 | Path AA / coverage breadth under `coverage.edge-count-exceeded`. |
| `unsupported-image-filter` | 2 | `Crop(input = nonNull)` child pre-pass limitation. |
| `adapter-skip` | 48 | Adapter/dependency placeholder families outside required smoke. |
| `similarity-regression` | 0 | No unresolved similarity regression. |
| `adapter-missing` | 0 | Adapter-backed evidence exists for required smoke. |
| `unexpected-exception` | 0 | No unowned blocker category remains. |

## Proposed Milestones

| Milestone | Name | Goal |
|---|---|---|
| M36 | Scene Evidence Dashboard | Build a static scene dashboard with CPU/GPU renders, diffs, diagnostics, and stats. |
| M37 | Path AA Breadth Strategy | Reduce or better route `coverage.edge-count-exceeded` inventory rows without hiding breadth. |
| M38 | Image-filter Child Pre-pass | Implement or prototype render-to-texture child pre-pass for `Crop(input = nonNull)`. |
| M39 | Pipeline Route Convergence | Move more `kanvas-skia` integration scenes through explicit CoveragePlan/PipelineIR diagnostics. |
| M40 | Performance And Regression Dashboard | Add benchmark and trend evidence for promoted CPU/GPU pipeline routes. |

## M36 Backlog

### M36-A: Scene Evidence Dashboard Spec And Export

Definition of Done:

- `.upstream/specs/wgsl-pipeline/10-scene-evidence-dashboard.md` is merged.
- A Gradle export task writes `build/reports/wgsl-pipeline-scenes/index.html`.
- The export fails when a referenced image, diff, JSON, or report is missing.
- The dashboard source is static and reviewable in the repository.

References:

- `.upstream/specs/wgsl-pipeline/10-scene-evidence-dashboard.md`
- `reports/wgsl-pipeline/demo/mvp/index.html`

### M36-B: Scene Data Contract And Validator

Definition of Done:

- `scenes.json` schema is documented and validated.
- CPU and GPU sections are required for every scene.
- GPU unsupported rows require stable `fallbackReason`.
- Validation reports missing artifacts, invalid status values, and absent stats.

References:

- `.upstream/specs/wgsl-pipeline/10-scene-evidence-dashboard.md#scene-record-contract`

### M36-C: P0 Solid Rect Scene

Definition of Done:

- Scene includes Skia/upstream reference or CPU oracle image.
- CPU render image, diff, route diagnostics, and stats are captured.
- GPU render or stable unsupported diagnostic is captured.
- CPU route proves `CoveragePlan.AnalyticRect` and
  `cpu.descriptor.coverage-plan.solid-rect`.

References:

- `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`
- `kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDescriptorCoverageOracleTest.kt`

### M36-D: P0 Bitmap Rect Nearest Scene

Definition of Done:

- Scene reuses or regenerates M32 image-rect reference/actual/diff evidence.
- CPU and GPU sections exist with similarity stats.
- GPU route links to the M32 smoke-promoted image-rect evidence.
- No floor lowering is introduced.

References:

- `.upstream/specs/wgsl-pipeline/08-bitmap-image-rect-sampling.md`
- `reports/wgsl-pipeline/artifacts/m32-bitmap-imagerect/`

### M36-E: P0 Analytic AA Convex Scene

Definition of Done:

- Scene captures CPU AA oracle and GPU analytic AA output or stable route data.
- GPU evidence links to `AnalyticAntialiasConvexWebGpuTest`.
- Scene is not confused with `coverage.edge-count-exceeded` breadth rows.
- Stats and diagnostics are visible in the dashboard.

References:

- `.upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md`
- `reports/wgsl-pipeline/2026-05-27-m33-path-aa-smoke-promotion.md`

### M36-F: Dashboard PM Closeout

Definition of Done:

- Dashboard links all P0 scenes, raw artifacts, and route JSON.
- A PM report explains how to read CPU/GPU/reference/diff panels.
- Linear comments link the exported dashboard and validation command output.
- Follow-up P1 scene tickets are created or explicitly deferred.

References:

- `reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md`

## M37 Backlog Seed

- Audit all `coverage.edge-count-exceeded` rows by primitive family and edge
  count distribution.
- Select one path family for promotion or better fallback evidence.
- Keep the 256-edge budget stable unless a profiling ADR justifies a change.
- Add scene dashboard rows for one passing AA family and one expected
  unsupported breadth family.

## M38 Backlog Seed

- Design the render-to-texture child pre-pass for `Crop(input = nonNull)`.
- Define intermediate surface format, bounds, tile mode, lifetime, and fallback
  diagnostics.
- Promote one image-filter scene only after CPU/GPU/reference evidence exists.

## M39 Backlog Seed

- Add integration scenes that prove more `skia-integration-tests` routes emit
  CoveragePlan/PipelineIR diagnostics.
- Prioritize linear gradient rect, `SrcOver` stack, local matrix shader, and
  color-filter-over-bitmap.
- Keep text/glyph scenes dependency-gated unless font infrastructure has landed.

## M40 Backlog Seed

- Add trendable benchmark outputs for promoted CPU/GPU routes.
- Track warm/cold GPU pipeline cache behavior.
- Keep Java 25 Vector as optional acceleration until benchmark gates justify
  promotion.
- Publish performance stats in the same dashboard model used by M36.

## Execution Policy

- Keep each milestone measurable and evidence-driven.
- Do not use archived root plans as active backlog.
- Do not broaden required GPU smoke without adapter-backed evidence.
- Do not mark expected unsupported rows as fixed without a rendered scene,
  diagnostics, and report evidence.
