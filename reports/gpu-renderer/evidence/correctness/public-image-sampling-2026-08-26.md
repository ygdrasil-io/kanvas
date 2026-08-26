# Public bounded image sampling — 2026-08-26

## Scope

The existing prepared-image route already owns an actual native WebGPU texture,
sampler, WGSL program and readback. This slice exposes its bounded sampler
choice through the public `Canvas.drawImage` API instead of requiring callers
to construct an image shader themselves.

Supported by this API route:

- decoded CPU pixels uploaded as the existing prepared RGBA8 image artifact;
- clamp-to-edge `nearest` and `linear` sampling;
- finite non-perspective image placement and the existing prepared-image
  paint/clip policy.

It does not add image-shader fills, codecs in the GPU backend, mipmaps,
anisotropic or cubic filtering, tile modes other than clamp, or a fallback.

## Native pixel and CPU oracle

`GPUPreparedSurfaceImagePixelTest.public drawImage nearest sampling reaches
native binding and matches the CPU source oracle` renders a 2×1 opaque black /
white source into one native offscreen pixel with `SamplingOptions.NEAREST`.
The independent CPU source oracle selects the right texel according to the
WebGPU nearest texel-centre rule. Native readback is exactly opaque white, with
one dispatched operation and zero refusals.

The adjacent public API test requests `SamplingOptions.Cubic.Mitchell` and
verifies the terminal diagnostic
`unsupported.image.sampling_cubic`; unsupported filtering is never silently
substituted.

## Codec and registered GM replay

The `:codec` runtime binding is available in the Skia integration test class
path, including JPEG. Registered GM `bitmap-image-srgb-legacy` decodes
`mandrill_512_q075.jpg` through both `Image.decode` and `Codec.MakeFromData`,
then renders the decoded images through the native prepared-image route. Its
replay passed.

This establishes that the selected route is real decoded-image sampling, not a
test-only bitmap substitute. It is not an image-shader, broad codec, or GM
promotion claim. No reference PNG, dashboard score, threshold, or
`gpu-renderer-scenes` file changed.

## Reproduction

```sh
./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceImagePixelTest

./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=bitmap-image-srgb-legacy \
  -Dkanvas.gm.includeBlocking=true
```
