# M45-C Image-filter DAG route implementation

GRA-218 hardens the selected M45 image-filter DAG subset with deterministic GPU
route diagnostics:

```text
Compose(
  outer = ColorFilter(Matrix|Blend, input = null),
  inner = MatrixTransform(affine 2x3, input = null)
)
```

The existing renderer already materialises the inner MatrixTransform into a
layer-sized scratch and runs the outer ColorFilter during the final composite.
This ticket makes that support auditable by exposing a test-only route diagnostic
record and adding a focused assertion for the selected M45 shape.

## Implementation

- Added `SkWebGpuDevice.ImageFilterRouteDiagnostics`.
- Added `SkWebGpuDevice.imageFilterRouteDiagnosticsForTests()`.
- Records the selected M45 route only when the resolved plan is exactly:
  - one materialise stage;
  - terminal leaf is `MatrixTransform`;
  - no BlurCF prefix;
  - no blur;
  - one effective ColorFilter;
  - no accumulated Offset.
- Added `SaveLayerImageFilterTest#saveLayer with Compose ColorFilter MatrixTransform exposes M45 route diagnostics`.

## Route evidence

Selected GPU route:

```text
webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite
```

Pre-pass route:

```text
webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix
```

Ownership evidence:

```text
scratchOwner=LayerCompositeDraw.materializeTargetTexture
scratchLifetime=per-composite-dispatch
materialiseStages=1
fallbackReason=none
```

The existing render test remains the CPU/GPU visual evidence source:

```text
SaveLayerImageFilterTest#saveLayer with Compose(ColorFilter, MatrixTransform) grayscales the transformed pixels()
```

## Unsupported scope

No broad image-filter DAG support claim is made. The selected diagnostic is
recorded only for the M45 V1 shape. Other DAGs continue through existing routes
or stable refusal behavior, including perspective MatrixTransform, non-null child
filters outside selected shapes, duplicate ColorFilter or Blur chains, and
arbitrary Crop/Tile/Magnifier/Image/Blend/morphology/lighting/displacement DAGs.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
```

Result: `SaveLayerImageFilterTest` passed, including the selected M45 route
diagnostic assertion.
