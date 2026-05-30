# M46 Analytic AA Convex Generated Evidence

Date: 2026-05-30
Issue: GRA-226

## Outcome

`analytic-aa-convex` was converted from static dashboard evidence to generated
evidence through `pipelineGeneratedSceneExport` while preserving the GRA-222
composited `SrcOver` AA oracle reconciliation.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `analytic-aa-convex`, so the merged dashboard keeps the
same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P0` |
| Reference kind | `cpu-oracle` |
| CPU route | `cpu.path-coverage.analytic-aa-convex-composited-oracle` |
| CPU oracle contract | composited `SrcOver` AA edge over opaque background |
| GPU route | `webgpu.coverage.path-convex-fan` |
| GPU fallback reason | `none` |
| Edge budget reason | `not coverage.edge-count-exceeded` |
| Threshold | `99.85` |
| GPU similarity | `100.0%` |
| Matching pixels | `256 / 256` |
| Max channel delta | `0` |
| Adapter | `Apple M2 Max` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. `maturity.adapter-backed`
was retained because `gpu.stats.adapter` is concrete.

## Not A Broad Path AA Claim

This conversion does not promote broad Path AA coverage. The row remains the
narrow single-contour convex fan route with `edgeBudgetReason=not
coverage.edge-count-exceeded`. The expected unsupported Path AA edge-budget rows
remain static and visible in the dashboard.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
```

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
