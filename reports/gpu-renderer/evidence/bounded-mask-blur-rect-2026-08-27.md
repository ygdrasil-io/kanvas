# Bounded mask-blur rectangle — WebGPU (2026-08-27)

`bounded-mask-blur-rect-v1` is the already-representable filter DAG: opaque
`FillRect(8,8,17,17)` → local mask → horizontal Gaussian blur → vertical
Gaussian blur → blur style → scene composite. It uses a finite `sigma = 2`,
five active taps in the fixed 25-weight ABI, four frame-local RGBA8
intermediates, and exactly one visual native dispatch. The WebGPU lane remains
headless/offscreen.

The CPU oracle is `TopLevelMaskBlurPixelOracle`, which evaluates mask coverage,
the kernel, style and `SrcOver` independently of the WebGPU materializer. The
32×32 result compares all 4,096 RGBA channels to the WebGPU readback under the
existing per-channel tolerance 24; that tolerance and every performance or GM
threshold are unchanged.

The task intentionally promotes no GM. Fresh Surface attempts record the real
terminal boundaries: `blurrects` is stopped by its unsupported material source,
`offsetimagefilter` by its missing native prepared-image binding, and
`blurquickreject` by the prior hairline stroke. The latter is not reclassified
as blur support.

The machine-readable CPU, GPU, diff, stats, route and refusal artifacts are in
this directory. Reproduce with:

```sh
rtk ./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUMaskBlurSurfaceTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUMaskBlurDispatchTest
rtk ./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SimpleFilterGmSurfaceRefusalEvidenceTest
```
