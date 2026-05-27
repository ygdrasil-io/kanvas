# M36 Analytic AA Convex Scene Evidence

Date: 2026-05-28
Linear: GRA-171
Scene: `analytic-aa-convex`

## Scope

This report backs the P0 analytic anti-aliasing scene dashboard row. The row is
only about the supported single-contour convex AA lane and must not be confused
with the M33 breadth inventory refused as `coverage.edge-count-exceeded`.

## Source Evidence

Primary source reports and specs:

- `.upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md`
- `reports/wgsl-pipeline/2026-05-27-m33-path-aa-smoke-promotion.md`
- `reports/wgsl-pipeline/2026-05-27-m33-path-aa-closeout.md`

Accepted route facts:

- promoted fixture: `org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest`
- supported GPU branch: `webgpu.coverage.path-convex-fan`
- scene boundary: single-contour convex AA path within edge budget
- explicit non-boundary: not `coverage.edge-count-exceeded`

## Dashboard Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`

## GPU Capture Boundary

The route/test is promoted by M33, but this M36-E local dashboard row does not
attach a GPU render artifact. The row therefore keeps the GPU section visible
as `expected-unsupported` with the stable diagnostic
`gpu.adapter-backed-analytic-aa-convex-capture-not-produced-in-m36-e-local-run`.
It does not claim a generated GPU image and does not hide the row.
