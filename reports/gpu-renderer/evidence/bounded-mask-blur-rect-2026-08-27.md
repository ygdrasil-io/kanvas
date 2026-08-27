# Bounded mask-blur rectangle — WebGPU (2026-08-27)

`bounded-mask-blur-rect-v1` is the already-representable filter DAG: opaque
`FillRect(8,8,17,17)` → local mask → horizontal Gaussian blur → vertical
Gaussian blur → blur style → scene composite. It uses a finite `sigma = 2`,
five active taps in the fixed 25-weight ABI, four frame-local RGBA8
intermediates, and one logical filter operation expanded into five ordered
native render passes. The WebGPU lane remains headless/offscreen.

The CPU oracle is `BoundedMaskBlurRectCpuOracle`, a fixture-specific formula
with no `MaskBlurPlanner`, production-kernel, payload, or WebGPU dependency. It
computes every pixel of the 32×32 RGBA8 expected buffer, including the RGBA8
quantization of each intermediate. CPU and GPU SHA-256 are both
`9735248adde7e8e966a03d90fe43ea70c468be2ddd748384985c2b9706dd1bae`;
`differentChannels=0`, `maxDelta=0`, and `meanDelta=0.0`. This fixture is
byte-exact—there is no tolerance.

Admission is bounded before any intermediate allocation: `sigma` must be
finite and in `0..12`; `sigma=12` consumes the explicit 25-tap kernel and
values such as `200`, `NaN`, and infinity refuse with
`unsupported.mask-filter.blur.sigma`.

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
