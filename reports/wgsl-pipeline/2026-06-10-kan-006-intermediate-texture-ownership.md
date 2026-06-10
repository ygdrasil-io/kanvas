# KAN-006 Intermediate Texture Ownership

Date: 2026-06-10

## Scope

KAN-006 closes the ownership evidence gap for the bounded M61
`Compose(ColorFilter, MatrixTransform affine)` image-filter DAG row. It does
not add runtime support. It makes the existing resource lifecycle explicit for
PM/dev review.

## Selected Row

| Field | Value |
|---|---|
| Scene | `m61-compose-cf-matrix-transform-dag-v2` |
| Base artifact | `image-filter-compose-cf-matrix-transform` |
| CPU route | `cpu.image-filter.compose.cf-matrix-transform-oracle` |
| GPU route | `webgpu.image-filter.compose.cf-matrix-transform.dag-v2` |
| Prepass route | `webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix` |
| Final route | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` |
| Fallback | `none` |
| Intermediate textures | `1 / 4` |
| Estimated intermediate bytes | `4096` |

## Ownership Contract

```text
gpuSrc.intermediateView
  -> matrix-transform-prepass
     allocates LayerCompositeDraw.materializeTargetTexture
     writes through LayerCompositeDraw.materializeTargetView
  -> color-filter-final-composite
     samples the scratch as source layer input
  -> DrawResources.closeDrawResources
     closes materializeTargetView, then materializeTargetTexture
```

The scratch is allocated by
`SkWebGpuDevice.enqueueMaterializeMatrixTransformToScratch` with
`RenderAttachment | TextureBinding` usage and label
`SkWebGpuDevice.materializeMatrixTransformTarget`.

The prepass writes into `LayerCompositeDraw.materializeTargetView` with
`loadOp=Clear` and materialise-mode `kSrc` semantics. The final ColorFilter
composite consumes that scratch through the normal layer-composite source
binding. Ownership is then transferred into `DrawResources`, so the view and
texture are closed by `closeDrawResources` after command submission.

## Evidence

- Contract:
  `reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json`
- Route:
  `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json`
- Prepass:
  `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-prepass.json`
- Stats:
  `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/stats.json`
- Runtime owner code:
  `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- Test route diagnostics:
  `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SaveLayerImageFilterTest.kt`

## Validation

```bash
rtk ./gradlew --no-daemon :validateKan006IntermediateTextureOwnership
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
```

## Non-Claims

- No arbitrary image-filter DAG scheduler is claimed.
- No broad `ImageFiltersGraphGM` parity is claimed.
- No picture/layer prepass breadth is claimed.
- No perspective MatrixTransform support is claimed.
- No new resource manager, runtime cache lane, CPU readback fallback, or
  unbounded intermediate texture chain is claimed.
