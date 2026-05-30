# M48 P0/P1 MEP Scene Pack Selection

Date: 2026-05-31
Linear: GRA-281
Parent epic: GRA-279
Depends on: GRA-280
Milestone: M48 -- Skia Scene Coverage Expansion

## Decision

M48 selects 10 new P0/P1 dashboard rows:

- 7 generated support evidence targets;
- 3 explicit expected-unsupported evidence targets;
- 0 tracked-gap targets.

This is enough breadth to move Skia integration readiness from 15% toward the
30-35% range if implementation preserves 0 `tracked-gap` / 0 `fail` and the
support rows are generated from the named owner commands.

## Selection Summary

| Group | Count | Owning ticket |
|---|---:|---|
| Paint / blend / transform generated support | 4 | GRA-282 / M48-C |
| Bitmap / gradient generated support | 3 | GRA-283 / M48-D |
| Hard Path AA / image-filter expected unsupported | 3 | GRA-284 / M48-E |
| Deferred with reason | 3 families | Planning note only |

## Selected Rows

| Proposed scene id | Classification | Family | Priority | Owner |
|---|---|---|---|---|
| `draw-paint-full-clip` | generated support evidence target | paint | P0 | GRA-282 |
| `draw-paint-clipped-rect` | generated support evidence target | paint / clip | P0 | GRA-282 |
| `scaled-rects-transform-stack` | generated support evidence target | transform / blend | P1 | GRA-282 |
| `gradient-color-filter-linear-kplus` | generated support evidence target | blend / paint | P1 | GRA-282 |
| `bitmap-shader-repeat-tile` | generated support evidence target | bitmap | P0 | GRA-283 |
| `bitmap-subset-local-matrix-repeat` | generated support evidence target | bitmap / transform | P1 | GRA-283 |
| `sweep-gradient-path-clamp` | generated support evidence target | gradient / path-aa | P1 | GRA-283 |
| `path-aa-convexpaths-edge-budget` | expected-unsupported evidence target | path-aa | P1 | GRA-284 |
| `path-aa-dashing-edge-budget` | expected-unsupported evidence target | path-aa | P1 | GRA-284 |
| `image-filter-crop-nonnull-prepass-required` | expected-unsupported evidence target | image-filter | P1 | GRA-284 |

## Row Contracts

### `draw-paint-full-clip`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.DrawPaintTest#drawPaint fills the full clip with opaque source` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest` |
| Reference kind | `test-oracle` |
| Expected CPU route | `cpu.paint.draw-paint.full-clip-oracle` |
| Expected GPU route | `webgpu.paint.draw-paint.full-clip` |
| Fallback reason | `none` |
| Threshold policy | 99.95 dashboard similarity floor or exact sampled-pixel oracle if artifacts are generated from the test |
| Artifact root | `artifacts/draw-paint-full-clip` |
| Required tags | `source.generated`, `feature.paint`, `feature.coverage.analytic-rect`, `route.gpu.webgpu`, `reference.test-oracle`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim all paint effects, color filters, color spaces, or blend modes. |

### `draw-paint-clipped-rect`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.DrawPaintTest#drawPaint after clipRect only fills inside the clip` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawPaintTest` |
| Reference kind | `test-oracle` |
| Expected CPU route | `cpu.paint.draw-paint.clip-rect-oracle` |
| Expected GPU route | `webgpu.paint.draw-paint.clip-rect` |
| Fallback reason | `none` |
| Threshold policy | 99.95 dashboard similarity floor or exact sampled-pixel oracle if artifacts are generated from the test |
| Artifact root | `artifacts/draw-paint-clipped-rect` |
| Required tags | `source.generated`, `feature.paint`, `feature.clip`, `feature.coverage.analytic-rect`, `route.gpu.webgpu`, `reference.test-oracle`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim arbitrary clip stacks, AA clips, or inverse clips. |

### `scaled-rects-transform-stack`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.ScaledRectsWebGpuTest#ScaledRectsGM renders close to reference PNG on the GPU backend` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ScaledRectsWebGpuTest` |
| Reference kind | `skia-upstream` |
| Expected CPU route | `cpu.gm.scaled-rects.reference-oracle` |
| Expected GPU route | `webgpu.transform.scaled-rects.convex-polygon` |
| Fallback reason | `none` |
| Threshold policy | Preserve the test floor: 99.99 GPU similarity |
| Artifact root | `artifacts/scaled-rects-transform-stack` |
| Required tags | `source.generated`, `feature.matrix-transform`, `feature.blend.plus`, `feature.coverage.path`, `route.gpu.webgpu`, `reference.skia-upstream`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim perspective, 3D transform, or all polygon AA edge behavior. |

### `gradient-color-filter-linear-kplus`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.GradientColorFilterTest#linear gradient with Blend(red, kPlus) colorFilter tints green to yellow` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GradientColorFilterTest` |
| Reference kind | `test-oracle` |
| Expected CPU route | `cpu.shader.linear-gradient.color-filter.blend-kplus-oracle` |
| Expected GPU route | `webgpu.generated.linear-gradient.color-filter.blend-kplus` |
| Fallback reason | `none` |
| Threshold policy | 99.95 dashboard similarity floor or sampled-pixel tolerance from the owner test |
| Artifact root | `artifacts/gradient-color-filter-linear-kplus` |
| Required tags | `source.generated`, `feature.gradient.linear`, `feature.color-filter`, `feature.blend.plus`, `route.gpu.webgpu`, `reference.test-oracle`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim all color filters, all blend modes, or color-space parity beyond this linear-gradient kPlus contract. |

### `bitmap-shader-repeat-tile`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.BitmapShaderPaintRectTest#paint shader kRepeat on oversized rect tiles the image` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest` |
| Reference kind | `test-oracle` |
| Expected CPU route | `cpu.shader.bitmap.repeat-tile-oracle` |
| Expected GPU route | `webgpu.shader.bitmap.repeat-tile` |
| Fallback reason | `none` |
| Threshold policy | 99.95 dashboard similarity floor or exact sampled-pixel oracle if artifacts are generated from the test |
| Artifact root | `artifacts/bitmap-shader-repeat-tile` |
| Required tags | `source.generated`, `feature.image.bitmap`, `feature.sampling.nearest`, `feature.tile.repeat`, `route.gpu.webgpu`, `reference.test-oracle`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim linear filtering, mipmaps, codecs, color management, or all tile-mode combinations. |

### `bitmap-subset-local-matrix-repeat`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest#BitmapSubsetShaderGM matches reference on raster and GPU backends` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.BitmapSubsetShaderCrossBackendTest` |
| Reference kind | `skia-upstream` |
| Expected CPU route | `cpu.shader.bitmap.subset-local-matrix-repeat` |
| Expected GPU route | `webgpu.shader.bitmap.subset-local-matrix-repeat` |
| Fallback reason | `none` |
| Threshold policy | Preserve cross-backend floors: raster >= 97.40, GPU >= 99.94 |
| Artifact root | `artifacts/bitmap-subset-local-matrix-repeat` |
| Required tags | `source.generated`, `feature.image.bitmap`, `feature.shader.local-matrix`, `feature.tile.repeat`, `feature.matrix-transform`, `route.gpu.webgpu`, `reference.skia-upstream`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim every subset sampling, perspective, all filtering modes, or codec-backed bitmap coverage. |

### `sweep-gradient-path-clamp`

| Field | Value |
|---|---|
| Classification | generated support evidence target |
| Source owner | `org.skia.gpu.webgpu.SweepGradientPathTest#kClamp sweep gradient on a circle path interpolates around the center` |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SweepGradientPathTest` |
| Reference kind | `test-oracle` |
| Expected CPU route | `cpu.shader.sweep-gradient.path-aa-oracle` |
| Expected GPU route | `webgpu.generated.sweep-gradient.path-aa` |
| Fallback reason | `none` |
| Threshold policy | 99.95 dashboard similarity floor or sampled-pixel tolerance from the owner test |
| Artifact root | `artifacts/sweep-gradient-path-clamp` |
| Required tags | `source.generated`, `feature.gradient.sweep`, `feature.path-aa`, `feature.coverage.aa`, `route.gpu.webgpu`, `reference.test-oracle`, `maturity.generated-evidence`, `risk.none` |
| Non-claim | Does not claim all sweep-gradient tile modes, all gradient families, or broad Path AA support. |

### `path-aa-convexpaths-edge-budget`

| Field | Value |
|---|---|
| Classification | expected-unsupported evidence target |
| Source owner | `org.skia.gpu.webgpu.ConvexPathsWebGpuTest#ConvexPathsGM renders close to reference PNG on the GPU backend` and M37 Path AA breadth inventory |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` or targeted `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ConvexPathsWebGpuTest` |
| Reference kind | `cpu-oracle` |
| Expected CPU route | `cpu.path-coverage.convexpaths-oracle` |
| Expected GPU route | `webgpu.coverage.refuse` |
| Stable fallback reason | `coverage.edge-count-exceeded` |
| Threshold policy | N/A for GPU lane because row is `expected-unsupported`; CPU/reference evidence should remain visible when possible |
| Artifact root | `artifacts/path-aa-convexpaths-edge-budget` |
| Required tags | `source.generated`, `feature.path-aa`, `feature.coverage.aa`, `route.gpu.expected-unsupported`, `reference.cpu-oracle`, `maturity.generated-evidence`, `risk.expected-unsupported`, `risk.edge-budget` |
| Non-claim | Does not claim ConvexPathsGM support or broad Path AA support; it documents the stable refusal. |

### `path-aa-dashing-edge-budget`

| Field | Value |
|---|---|
| Classification | expected-unsupported evidence target |
| Source owner | `org.skia.gpu.webgpu.crossbackend.DashingCrossBackendTest#DashingGM matches reference on raster and GPU backends` and M37 Path AA breadth inventory |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` or targeted `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.DashingCrossBackendTest` |
| Reference kind | `cpu-oracle` |
| Expected CPU route | `cpu.path-coverage.dashing-oracle` |
| Expected GPU route | `webgpu.coverage.refuse` |
| Stable fallback reason | `coverage.edge-count-exceeded` |
| Threshold policy | N/A for GPU lane because row is `expected-unsupported`; CPU/reference evidence should remain visible when possible |
| Artifact root | `artifacts/path-aa-dashing-edge-budget` |
| Required tags | `source.generated`, `feature.path-aa`, `feature.stroke`, `feature.dash`, `route.gpu.expected-unsupported`, `reference.cpu-oracle`, `maturity.generated-evidence`, `risk.expected-unsupported`, `risk.edge-budget` |
| Non-claim | Does not claim dash/cap/join support, broad stroke support, or edge-budget removal. |

### `image-filter-crop-nonnull-prepass-required`

| Field | Value |
|---|---|
| Classification | expected-unsupported evidence target |
| Source owner | Synthetic unsupported crop fixtures in `org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest` plus M34/M38 image-filter policy reports |
| Owner command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest` and, if implementation needs live inventory evidence, `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` |
| Reference kind | `cpu-oracle` |
| Expected CPU route | `cpu.image-filter.crop-nonnull-reference` |
| Expected GPU route | `webgpu.image-filter.refuse` |
| Stable fallback reason | `image-filter.crop-input-nonnull-prepass-required` |
| Threshold policy | N/A for GPU lane because row is `expected-unsupported`; row must cite the policy source and stable classifier reason |
| Artifact root | `artifacts/image-filter-crop-nonnull-prepass-required` |
| Required tags | `source.generated`, `feature.image-filter`, `feature.crop`, `route.gpu.expected-unsupported`, `reference.cpu-oracle`, `maturity.generated-evidence`, `risk.expected-unsupported` |
| Non-claim | Does not claim arbitrary image-filter DAG support, recursive crop pre-passes, or general layer scheduling. |

## Deferred Families And Reasons

| Family / candidate | Reason deferred from M48 implementation |
|---|---|
| Broad runtime-effect / arbitrary SkSL | M47 only proves registered descriptor-backed `runtime-effect-simple`; arbitrary SkSL would violate the no-SkSL-compiler target. Keep for a future registered-effect matrix if needed. |
| Text, glyph masks, emoji, codecs | AGENTS policy treats font/codec gaps as dependency-gated. Do not add substitutes just to raise scene counts. |
| Perspective / 3D transform | Current M48 taxonomy needs representative 2D MEP coverage first; perspective would require separate threshold and route diagnostics. |

## Execution Notes For M48-C/D/E

- M48-C must not add bitmap/gradient-only rows beyond the four rows assigned above.
- M48-D must not add unselected bitmap or gradient rows even if nearby tests exist.
- M48-E must keep unsupported rows explicit and must not convert them to `tracked-gap`.
- If a support row cannot produce generated artifacts, the owning ticket must either add the missing artifact writer or document a concrete blocker instead of adding a static support row.
- If an expected-unsupported row cannot preserve its stable fallback reason, do not add it; document the blocker because unknown diagnostics must stay fail-closed.

## Validation

Selection-only validation for GRA-281:

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Implementation tickets must additionally run their owner commands listed above.
