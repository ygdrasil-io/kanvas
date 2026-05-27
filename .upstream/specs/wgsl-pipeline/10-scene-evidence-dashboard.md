# Scene Evidence Dashboard

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M36 -- Scene Evidence Dashboard

## Purpose

Create a static, data-driven visual dashboard that proves selected Skia-like
scenes exercise the intended Kanvas pipeline routes. The dashboard must show
the rendered output, Skia/upstream reference or CPU oracle comparison, diff,
route diagnostics, and stats for both CPU and GPU when the scene is
GPU-eligible.

This is the post-MVP bridge between release evidence and daily pipeline
development. It should make route regressions visible without requiring a
developer to inspect JUnit XML, Gradle logs, or ad hoc debug images.

## Non-Goals

- Do not turn this into a native live editor in M36.
- Do not claim full Skia parity from a small scene pack.
- Do not promote known unsupported breadth into required GPU smoke.
- Do not hide expected unsupported cases by deleting them from the dashboard.
- Do not introduce Ganesh, Graphite, SkSL compiler, SkSL IR, or SkVM.
- Do not add short-lived font/codec substitutes to make dashboard rows green.

## Source Layout

The static dashboard source should live under:

```text
reports/wgsl-pipeline/scenes/
  index.html
  data/scenes.json
  artifacts/
    <scene-id>/
      skia.png
      cpu.png
      gpu.png
      cpu-diff.png
      gpu-diff.png
      route.json
      stats.json
```

The generated export should live under:

```text
build/reports/wgsl-pipeline-scenes/
  index.html
  data/scenes.json
  artifacts/...
```

The initial implementation may be static and checked in. Later milestones can
replace hand-authored artifacts with generated outputs while preserving the
same `scenes.json` schema.

## Scene Record Contract

Each scene record must be deterministic and self-contained:

```json
{
  "id": "solid-rect",
  "title": "Solid filled rect",
  "status": "pass",
  "referenceKind": "skia-upstream",
  "reference": "artifacts/solid-rect/skia.png",
  "cpu": {
    "image": "artifacts/solid-rect/cpu.png",
    "diff": "artifacts/solid-rect/cpu-diff.png",
    "similarity": 100.0,
    "route": {
      "coveragePlan": "AnalyticRect(2.0,1.0,7.0,6.0,aa=false)",
      "selectedRoute": "cpu.descriptor.coverage-plan.solid-rect",
      "fallbackReason": "none"
    }
  },
  "gpu": {
    "image": "artifacts/solid-rect/gpu.png",
    "diff": "artifacts/solid-rect/gpu-diff.png",
    "similarity": 100.0,
    "route": {
      "pipelineKey": "pipeline.key ...",
      "coverageStrategy": "webgpu.analytic-rect",
      "fallbackReason": "none"
    }
  },
  "stats": {
    "pixels": 4096,
    "matchingPixels": 4096,
    "maxChannelDelta": 0,
    "threshold": 99.95
  },
  "evidence": [
    "reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md"
  ]
}
```

Rules:

- `reference` is the upstream Skia PNG when available.
- If no upstream PNG exists, `referenceKind` must state `cpu-oracle`, and the
  CPU route is the reference for GPU comparison.
- CPU and GPU sections are both required in the schema.
- A non-GPU-eligible scene must keep the `gpu` section with
  `status=expected-unsupported` and a stable `fallbackReason`.
- Missing artifacts must fail the export task.

## P0 Scene Pack

M36 starts with a small pack that proves the dashboard and route capture model:

| Scene | CPU requirement | GPU requirement | Expected status |
|---|---|---|---|
| `solid-rect` | `CoveragePlan.AnalyticRect` through CPU descriptor route. | WebGPU analytic rect or explicit unsupported diagnostic if not wired. | pass or tracked gap |
| `bitmap-rect-nearest` | Skia/upstream reference diff and CPU render. | M32 image-rect smoke-compatible route. | pass |
| `analytic-aa-convex` | CPU AA coverage oracle. | M33 promoted analytic AA smoke fixture. | pass |

These three scenes are enough to prove shape coverage, paint/image sampling,
CPU reference behavior, GPU route evidence, and dashboard generation.

## P1 Scene Pack

After P0 is stable, add breadth scenes:

| Scene | Purpose |
|---|---|
| `linear-gradient-rect` | Paint shader lowering and generated WGSL gradient route. |
| `src-over-stack` | Destination load, blending, and store semantics. |
| `runtime-effect-simple` | Registered runtime-effect descriptor with CPU/GPU implementations. |
| `clip-rect-difference` | Clip interaction and stable fallback/route diagnostics. |
| `crop-image-filter-nonnull` | M34 expected unsupported limitation row. |
| `path-aa-edge-budget` | M33 expected unsupported breadth row. |

## Diagnostics

Every scene must expose route diagnostics in a PM-readable and
machine-readable form:

- scene id and backend;
- selected route;
- compatibility fallback route, if any;
- `CoveragePlan` and lowering result;
- `PipelineIR` dump when the paint pipeline participates;
- `PipelineKey` preimage/hash when the GPU route participates;
- stable `fallbackReason`, or `none`;
- JUnit suite/test name when generated from a test;
- source report or Linear issue id.

The dashboard should show the important fields inline and link to the raw JSON.

## Stats

At minimum each rendered backend records:

- image dimensions;
- total pixels;
- matching pixels;
- similarity percentage;
- threshold;
- max channel delta;
- render backend;
- adapter name for GPU captures when available;
- command or test that produced the artifact.

Render time may be absent in M36. If present, it must include host, JDK, backend,
and warm/cold run context.

## Validation

M36 is complete when:

- `scenes.json` validates against the expected fields.
- All referenced source and exported artifacts exist.
- The static dashboard renders without missing images or links.
- P0 includes CPU and GPU sections for all three scenes.
- Unsupported GPU rows show stable diagnostics instead of blank cells.
- The export task writes `build/reports/wgsl-pipeline-scenes/index.html`.
- CI or local validation runs:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

## Future Native App Direction

The static dashboard is intentionally data-driven so a later native app can
consume the same scene registry. The future app may add:

- live CPU/WebGPU backend switching;
- editable paint, transform, threshold, and route flags;
- real-time route diagnostics;
- artifact export back to the static dashboard format;
- PR-ready scene evidence bundles.

The native app should not change the evidence schema without first migrating
the static dashboard and CI export.
