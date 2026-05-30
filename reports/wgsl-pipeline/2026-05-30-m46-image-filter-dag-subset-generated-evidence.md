# M46 Image Filter DAG Subset Generated Evidence

Date: 2026-05-30
Issue: GRA-228

## Outcome

`image-filter-compose-cf-matrix-transform` was converted from static dashboard
evidence to generated evidence through `pipelineGeneratedSceneExport` while
preserving the M45 bounded image-filter DAG subset and intermediate texture
ownership contract.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `image-filter-compose-cf-matrix-transform`, so the merged
dashboard keeps the same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `test-oracle` |
| Selected subset | `Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine, input=null))` |
| Representative | `SaveLayerImageFilterTest Compose(ColorFilter, MatrixTransform)` |
| CPU route | `cpu.image-filter.compose.cf-matrix-transform-oracle` |
| GPU route | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` |
| Pre-pass route | `webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix` |
| Fallback reason | `none` |
| Scratch owner | `LayerCompositeDraw.materializeTargetTexture` |
| Scratch lifetime | `per-composite-dispatch` |
| Materialise stages | `1` |
| Threshold | `99.0` |
| CPU/GPU similarity | `100.0%` |
| Matching pixels | `1024 / 1024` |
| Max channel delta | `0` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`.

## Image Filter Boundary

This row remains the bounded M45 DAG subset only:
`Compose(outer = ColorFilter(Matrix|Blend), inner = MatrixTransform(affine,
input=null))`. It does not claim general Skia image-filter DAG support. Broader
DAG shapes remain governed by their stable fallback diagnostics and future
promotion criteria.

The generated evidence keeps `LayerCompositeDraw.materializeTargetTexture` and
`per-composite-dispatch` visible in both the dashboard row and the closeout.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-prepass.json`
- `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/stats.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
