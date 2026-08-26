# Task 2 — bounded image shader local sampling

## Delivered

- `GPUMaterialDescriptor.ImageDraw` now owns immutable local-matrix facts.
  The direct prepared image route applies accepted matrices while forming image
  UVs; no unconsumed material ABI/WGSL extension is claimed.
- The prepared mapper accepts only finite translation/positive-scale matrices
  inside explicit bounds, and preserves nearest/linear filtering.
- `DrawimagerectFilterGm` reaches the prepared WebGPU image route for its
  local-matrix shader fill.  No GM source was changed.
- The frame source inventory recognizes only the axis-aligned rectangle path
  emitted by `GmCanvas`; arbitrary paths remain outside this task.
- Paint tint and alpha are propagated to image materials.  An executed native
  nearest + alpha-tint pixel oracle covers the bounded route.

## Evidence

See `reports/gpu-renderer/evidence/image-local-sampling-2026-08-27.md`.
`drawimagerect_filter` ran natively with four dispatches, zero refusals and an
empty diagnostic set.  The CPU reference, WebGPU render and `ComparisonUtils`
diff/stat authority are recorded there, with independent `diff.json`,
`stats.json`, `route.json`, and `diagnostics.json` artifacts.  Its score is
28.74074074074074% against the unchanged 0.0% GM threshold.

## Verification

```sh
./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramTest \
  :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedDrawImageLowererTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceImagePixelTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUMaterialMapperTest \
  :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.gm.image.DrawimagerectFilterGmTest \
  --tests org.graphiks.kanvas.skia.gm.image.NearestHalfPixelImageGmTest

./gradlew --no-daemon :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=drawimagerect_filter
./gradlew --no-daemon :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=nearest_half_pixel_image

./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=drawimagerect_filter \
  -Dkanvas.render.debugLevel=PIXEL
```

All commands passed.

## Non-claims / concern

No repeat/mirror/decal tile modes, perspective, skew, or negative image local
scale, mipmapping, anisotropy, cubic sampling, codec fallback, arbitrary-path
image shader fill, Ganesh, Graphite or dynamic SkSL is implemented.  The
`nearest_half_pixel_image` regeneration is valid and checked in: it renders
with two dispatches, zero refusals, and 73.44938749194068% similarity against
its unchanged 0.0% threshold.  Its mirror/negative-scale variants are Canvas
geometry transforms inherited by `Shader.Image`, not negative image local
matrices.  Consequently this GM does not broaden the bounded local-matrix
contract; negative image local matrices remain refused.  Its independent
CPU/GPU diff/stat/route/diagnostic artifacts sit under
`reports/gpu-renderer/evidence/nearest-half-pixel-image-2026-08-27/`.
