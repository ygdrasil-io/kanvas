# Post-MVP Pipeline Backlog

Status: Active -- M38 closed, M39 next
Date: 2026-05-28
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Input: `reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md`

## Purpose

Define the next pipeline milestones after the WGSL/WebGPU MVP closeout. The
MVP is complete, so this backlog should not reopen MVP acceptance criteria.
It should convert the remaining visible limitations and observability gaps into
scoped post-MVP work.

## Current Baseline

Current post-M38 inventory:

| Category | Count | Interpretation |
|---|---:|---|
| `expected-unsupported-diagnostic` | 50 | Path AA / coverage breadth under `coverage.edge-count-exceeded` and `coverage.stroke-outline-edge-count-exceeded`. |
| `unsupported-image-filter` | 0 | Selected `Crop(kDecal, input = Offset(null))` SimpleOffset child pre-pass is implemented; no selected image-filter rows remain in this bucket. |
| `adapter-skip` | 48 | Adapter/dependency placeholder families outside required smoke. |
| `similarity-regression` | 0 | No unresolved similarity regression. |
| `adapter-missing` | 0 | Adapter-backed evidence exists for required smoke. |
| `unexpected-exception` | 0 | No unowned blocker category remains. |

M38 removed the two M34 `SimpleOffsetImageFilter*` rows from
`unsupported-image-filter`. The diagnostic
`image-filter.crop-input-nonnull-prepass-required` remains reserved for future
out-of-scope `Crop(input = nonNull)` graph shapes that are not covered by the
selected M38 pre-pass.

## Proposed Milestones

| Milestone | Name | Goal |
|---|---|---|
| M36 | Scene Evidence Dashboard | Done -- static dashboard with CPU/GPU renders, diffs, diagnostics, and stats. |
| M37 | Path AA Breadth Strategy | Done -- stroke-outline overflow split and dashboard evidence without hiding breadth. |
| M38 | Image-filter Child Pre-pass | Done -- selected render-to-texture child pre-pass for `Crop(kDecal, input = Offset(null))`. |
| M39 | Pipeline Route Convergence | Next -- move more `kanvas-skia` integration scenes through explicit CoveragePlan/PipelineIR diagnostics. |
| M40 | Performance And Regression Dashboard | Proposed -- add benchmark and trend evidence for promoted CPU/GPU pipeline routes. |

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

Status: Done through GRA-184.

Delivered:

- Designed the bounded render-to-texture child pre-pass for the selected
  `Crop(kDecal, input = Offset(null))` SimpleOffset shape.
- Implemented child materialisation into
  `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` and final crop
  composite sampling from scratch.
- Promoted `SimpleOffsetImageFilterWebGpuTest` to required GPU smoke.
- Moved selected image-filter inventory from `unsupported-image-filter=2` to
  `unsupported-image-filter=0`.
- Added dashboard scene `crop-image-filter-nonnull-prepass` with CPU/GPU
  artifacts, diffs, route diagnostics, pre-pass diagnostics, and stats.

Evidence:

- `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-design.md`
- `reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md`
- `reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md`
- `reports/wgsl-pipeline/2026-05-28-m38-image-filter-dashboard-scene.md`
- `reports/wgsl-pipeline/2026-05-28-m38-image-filter-closeout.md`

## M39 Backlog Seed

Status: Done through GRA-187.

Delivered:

- Selected five P1 route-convergence scenes:
  `linear-gradient-rect`, `src-over-stack`, `runtime-effect-simple`,
  `clip-rect-difference`, and `bitmap-shader-local-matrix`.
- Added dashboard artifacts, diffs, stats, and route diagnostics for gradient
  and `SrcOver` stack rows in GRA-185.
- Added dashboard artifacts, diffs, stats, and route diagnostics for registered
  runtime-effect, clip difference, and bitmap shader local-matrix rows in
  GRA-186.
- Closed M39 with an 11-row route matrix across P0, M37, M38, and M39 selected
  P1 scenes.
- Kept text/glyph scenes dependency-gated until real font infrastructure lands.

Evidence:

- `reports/wgsl-pipeline/2026-05-28-m39-p1-scene-selection.md`
- `reports/wgsl-pipeline/2026-05-28-m39-gradient-srcover-dashboard-scenes.md`
- `reports/wgsl-pipeline/2026-05-28-m39-runtime-clip-localmatrix-dashboard-scenes.md`
- `reports/wgsl-pipeline/2026-05-28-m39-route-convergence-closeout.md`
- `reports/wgsl-pipeline/scenes/data/scenes.json`

## M40 Backlog Seed

Status: Done through GRA-190.

Delivered:

- Added optional validated `performanceTrend` schema fields for CPU/GPU lanes.
- Updated the static dashboard to display performance as measured/estimated or
  `unavailable`.
- Populated CPU timing/counter metric seeds for five selected M39 P1 rows.
- Populated GPU timing/cache metric seeds for the same five selected M39 P1
  rows.
- Published the M40 performance/regression closeout report.
- Kept Java 25 Vector optional; no promotion without benchmark-gate evidence.

Evidence:

- `reports/wgsl-pipeline/2026-05-28-m40-performance-trend-schema.md`
- `reports/wgsl-pipeline/2026-05-28-m40-cpu-performance-metrics.md`
- `reports/wgsl-pipeline/2026-05-28-m40-gpu-cache-performance-metrics.md`
- `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md`
- `reports/wgsl-pipeline/scenes/data/scenes.json`

Future native/live benchmark writing remains a recommendation, not active
backlog, until a real benchmark harness is ready to own reproducibility and
baseline comparison.

## Execution Policy

- Keep each milestone measurable and evidence-driven.
- Do not use archived root plans as active backlog.
- Do not broaden required GPU smoke without adapter-backed evidence.
- Do not mark expected unsupported rows as fixed without a rendered scene,
  diagnostics, and report evidence.
