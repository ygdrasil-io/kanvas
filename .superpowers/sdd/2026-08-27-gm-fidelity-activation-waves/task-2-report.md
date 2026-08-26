# Task 2 — bounded image shader local sampling

## Delivered

- `GPUMaterialDescriptor.ImageDraw` now owns immutable local-matrix facts.
  The material ABI packs two affine rows and WGSL evaluates them before image
  sampling.
- The prepared mapper accepts only finite translation/positive-scale matrices
  inside explicit bounds, and preserves nearest/linear filtering.
- `DrawimagerectFilterGm` reaches the prepared WebGPU image route for its
  local-matrix shader fill.  No GM source was changed.
- The frame source inventory recognizes only the axis-aligned rectangle path
  emitted by `GmCanvas`; arbitrary paths remain outside this task.

## Evidence

See `reports/gpu-renderer/evidence/image-local-sampling-2026-08-27.md`.
`drawimagerect_filter` ran natively with four dispatches, zero refusals and an
empty diagnostic set.  The CPU reference, WebGPU render and `ComparisonUtils`
diff/stat authority are recorded there.  Its score is
28.74074074074074% against the unchanged 0.0% GM threshold.

## Verification

```sh
./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramTest \
  :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUMaterialMapperTest \
  :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.gm.image.DrawimagerectFilterGmTest

./gradlew --no-daemon :integration-tests:skia:generateSkiaRendersFor \
  -Pgm.name=drawimagerect_filter

./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=drawimagerect_filter \
  -Dkanvas.render.debugLevel=PIXEL
```

All commands passed.

## Non-claims / concern

No repeat/mirror/decal tile modes, perspective, skew, negative scale,
mipmapping, anisotropy, cubic sampling, codec fallback, arbitrary-path image
shader fill, Ganesh, Graphite or dynamic SkSL is implemented.  The attempted
`nearest_half_pixel_image` regeneration remains outside this bounded contract:
its mirror/negative-scale variants fail the existing prepared image-lowerer
authority check, so its generated reference and score were not updated.
