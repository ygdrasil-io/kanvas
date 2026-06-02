# M60 Target-Colorspace Blend Evidence - 2026-06-02

Linear: `FOR-232`

## Decision

`m60-bounded-stroke-cap-join` remains `expected-unsupported`.

The target-colorspace blend pilot fixes the isolated neutral AA mismatch, but
the full M60 stroke cap/join scene still misses the exact `99.95%` support
threshold. No readiness percentage is increased.

## Working-Space Convention

Current default WebGPU comparison path:

1. solid and coverage shaders emit premul sRGB-coded values;
2. fixed-function blending happens in the sRGB-coded intermediate;
3. `present_pass.wgsl` converts the final blended result to the DM Rec.2020
   reference space.

FOR-232 target-colorspace pilot:

1. supported solid-colour coverage shaders convert unpremul sRGB source colour
   to DM Rec.2020 encoded values before premultiplication;
2. fixed-function blending happens in the target encoded intermediate;
3. the present pass uses identity copy because the intermediate is already in
   the comparison space;
4. unsupported draw families refuse with
   `color-space.target-blend-unsupported-draw-kind:<kind>`.

Covered families in this slice:

- `solid_color.wgsl` / `RectDraw`;
- `aa_stencil_cover.wgsl` / `StencilCoverAaPolygonDraw`.

Non-covered families include gradients, bitmap shaders, runtime effects, image
filters, layers, non-native blend composites, text masks, and blur/drop-shadow
paths. Those must not silently share the target-colorspace mode.

## Isolated Fixture

`m60-target-colorspace-neutral-aa` renders a half-covered black sample over
white. It demonstrates the blocker named by `FOR-231`.

| Pixel | CPU reference | Default WebGPU | Target-blend WebGPU |
|---|---:|---:|---:|
| neutral AA red channel | `128` | `115` | `128` |

Artifacts:

- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/gpu-post-present.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/gpu-target-blend.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/gpu-post-present-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/gpu-target-blend-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/stats.json`

## M60 Scene Result

| Metric | Before FOR-232 | After target-blend pilot | Support threshold |
|---|---:|---:|---:|
| Exact similarity | `89.60%` | `95.37%` | `99.95%` |
| Similarity at tolerance 8 | `92.93%` | `99.96%` | `99.95%` |
| Similarity at tolerance 16 | `99.37%` | `99.96%` | `99.95%` |
| Similarity at tolerance 32 | `99.97%` | `99.98%` | `99.95%` |
| Max channel delta | `39` | `49` | n/a |

The target-colorspace pilot removes the dominant neutral-AA darkening but
reveals residual stroke cap/join AA differences, especially in the
`round-round` region. The normal route therefore remains
`webgpu.coverage.refuse` with
`coverage.stroke-cap-join-visual-parity-below-threshold`.

## BlendPlan And Pipeline Impact

- No fixed-function blend mode is added or removed.
- `kSrcOver` still uses the existing fixed-function `BlendPlan`.
- The new axis is not part of `PipelineKey`; it is a device working-space mode
  and a uniform-only shader branch for the covered solid-colour paths.
- The present shader selection changes only when the device explicitly enables
  target-colorspace blending.
- The mode refuses unsupported draw kinds before encoding GPU resources.

## Non-Claims

- No broad colour-space parity is claimed.
- No gradient, bitmap, runtime-effect, saveLayer, image-filter, text, blur, or
  drop-shadow target-blend support is claimed.
- No M60 support promotion is claimed.
- No global threshold is lowered.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM is introduced.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```
