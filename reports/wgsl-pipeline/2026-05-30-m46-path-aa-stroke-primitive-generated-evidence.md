# M46 Path AA Stroke Primitive Generated Evidence

Date: 2026-05-30
Issue: GRA-227

## Outcome

`path-aa-stroke-primitive` was converted from static dashboard evidence to
generated evidence through `pipelineGeneratedSceneExport` while preserving the
M44 first Path AA family promotion scope.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `path-aa-stroke-primitive`, so the merged dashboard keeps
the same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `test-oracle` |
| Selected family | `StrokeRectGM + StrokeCircleGM primitive strokes` |
| Representative | `StrokeCircleGM` |
| CPU route | `cpu.raster.path-aa-stroke-primitive-oracle` |
| GPU route | `webgpu.coverage.path-aa-stroke-primitive` |
| GPU fallback reason | `none` |
| GPU pipeline key | `coverageKind=pathAaStrokePrimitive pathFillRule=winding topology=triangleList` |
| CPU threshold | `90.0` |
| GPU threshold | `91.76` |
| CPU similarity | `90.21%` |
| GPU similarity | `91.81%` |
| GPU matching pixels | `248258 / 270400` |
| GPU max channel delta | `169` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`.

## M44 Inventory Boundary

The generated row preserves the M44 inventory delta:
`coverage.edge-count-exceeded` moved from 50 to 46 after the targeted
`StrokeRectGM` / `StrokeCircleGM` primitive-stroke promotion.

This conversion does not promote broad Path AA coverage. Broad Path AA rows
remain visible as `expected-unsupported` with stable `coverage.edge-count-exceeded`
diagnostics; they were not hidden, relabelled, or folded into this narrow row.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/stats.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
