# M36 Solid Rect Scene Evidence

Date: 2026-05-28
Linear: GRA-169
Scene: `solid-rect`

## Scope

This report backs the first P0 scene dashboard row. It proves the CPU solid
rectangle path uses descriptor-driven analytic rect coverage and records the
current GPU evidence boundary without fabricating a render artifact.

## CPU Evidence

The CPU route is the production descriptor route exercised by
`org.skia.core.SkBitmapDescriptorCoverageOracleTest#production bitmap device selects descriptor route for non aa filled rect by default`.

Required route facts:

- selected route: `cpu.descriptor.coverage-plan.solid-rect`
- fallback route retained for rollback: `kanvas-skia.current.draw-rect`
- coverage plan: `AnalyticRect(2.0,1.0,7.0,6.0,aa=false)`
- lowering result: `CoverageModel.AnalyticRect(2.0,1.0,7.0,6.0,aa=false)`
- execution evidence: `lowering-consumed:CoverageModel.AnalyticRect;kernel=cpu.scalar.analytic_rect_coverage;touchedPixels=25`
- fallback reason: `none`

The static CPU oracle image is an 8x8 fixture matching those bounds. The CPU
image is byte-identical to the oracle for this dashboard seed, so the CPU diff
is transparent and similarity is `100.0` against the explicit `99.95` threshold.

## GPU Evidence Boundary

The GPU route selector supports the analytic rect coverage lane through
`org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest#analytic rect selection adds only coverage kind code axis`, which selects `webgpu.coverage.analytic-rect` and the `coverageKind=analyticRect` pipeline-key axis.

No adapter-backed GPU render artifact is attached in this local M36-C row. The
scene keeps the GPU section visible with status `expected-unsupported` and the
stable diagnostic `gpu.adapter-backed-solid-rect-capture-not-produced-in-m36-c-local-run` instead of claiming a generated render.

## Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/stats.json`
