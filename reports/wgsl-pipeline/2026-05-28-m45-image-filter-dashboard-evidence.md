# M45-D Image-filter DAG dashboard evidence

GRA-219 adds dashboard evidence for the selected M45 image-filter DAG subset:

```text
Compose(outer = ColorFilter(Matrix|Blend), inner = MatrixTransform(affine))
```

## Dashboard row

| Field | Value |
| --- | --- |
| Scene id | `image-filter-compose-cf-matrix-transform` |
| Status | `pass` |
| Representative artifact | `SaveLayerImageFilterTest` Compose(ColorFilter, MatrixTransform) |
| GPU route | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` |
| Pre-pass route | `webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix` |
| Scratch owner | `LayerCompositeDraw.materializeTargetTexture` |
| Scratch lifetime | `per-composite-dispatch` |
| Fallback reason | `none` |

## Artifacts

- Reference: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/skia.png`
- CPU: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu.png`
- GPU: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu-diff.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-cpu.json`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json`
- Pre-pass route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-prepass.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/stats.json`

## Evidence

The row is tied to GRA-218's focused route diagnostic test:

```text
SaveLayerImageFilterTest#saveLayer with Compose ColorFilter MatrixTransform exposes M45 route diagnostics()
```

and the visual representative test:

```text
SaveLayerImageFilterTest#saveLayer with Compose(ColorFilter, MatrixTransform) grayscales the transformed pixels()
```

The representative artifact is a deterministic 32x32 rendering of the selected
semantics: a source red rect translated by the affine MatrixTransform, then
converted to grayscale by the outer ColorFilter. CPU/reference/GPU artifacts are
identical for the dashboard row, so the diff artifacts are empty and stats report
100.0% similarity.

## Unsupported scope

The dashboard row is a support claim only for the selected two-node DAG subset.
Perspective MatrixTransform, non-null child filters outside selected shapes,
duplicate ColorFilter or Blur chains, Crop/Tile/Magnifier/Image/Blend/morphology,
lighting, displacement, and arbitrary image-filter DAG compilation remain out of
scope with stable diagnostics.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
```
