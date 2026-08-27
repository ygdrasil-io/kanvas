# Bounded sRGB matrix ColorFilter evidence — 2026-08-27

The supported slice is `srgb-colorfilter-matrix-v1`: one uniform encoded-sRGB
straight RGBA color, one finite row-major 4×5 matrix, and one 4×4 headless
`rgba8unorm` target. Kotlin and WGSL both decode sRGB before applying the
matrix, re-encode the RGB result, then premultiply for storage.

The native `color-matrix-v1` WebGPU readback exactly matched the independent
hand-derived CPU pixel `[46, 32, 96, 128]` repeated over 16 pixels:
`differentChannels=0`, `maxDelta=0`, `meanDelta=0.0`, one submit and one
readback copy. The module is parser/reflection validated by `wgsl4k`.

The evidence is correctness-only and non-gating: it makes no frame-time,
general color-management, image codec, arbitrary filter-DAG, Ganesh, Graphite,
or dynamic SkSL claim. The image-backed GM `srgb_colorfilter` is still refused
at `unsupported.image.native_binding`; the image-filter GM is still refused at
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted`. Both are
preserved in `refusals.json`.

Reproduce the native fixture with:

```sh
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSrgbMatrixColorFilterSmokeTest \
  --tests org.graphiks.kanvas.gpu.renderer.filters.SrgbMatrixColorFilterTest \
  --tests org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgslTest
```
