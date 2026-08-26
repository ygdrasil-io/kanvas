# Mask blur rect translation evidence

Date: 2026-08-26

## Scope

The prepared native WebGPU mask-blur route for `FillRect` now accepts an
identity or translation transform only. Its local mask is built from the
translated device bounds, with the localized command reset to identity. This
keeps the existing one blur node and its existing intermediate-budget policy;
it adds no filter DAG, texture class, similarity threshold, or reference PNG.

Scale, rotation, skew, perspective, and singular transforms are explicitly
refused with
`unsupported.core_primitive.mask_blur.unsupported.mask-filter.blur.transform`.

## Evidence

The red test initially failed with a maximum channel delta of 255 because the
local mask used source-space bounds after `translate(4, 5)`.

After the correction, the following native WebGPU pixel-oracle tests passed:

```
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUMaskBlurSurfaceTest
```

This includes the independent CPU oracle comparison for the translated rect,
and the stable bounded-route refusal for scale.

## Registered GM replay

```
./gradlew --no-daemon :integration-tests:skia:test \\
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \\
  -Dkanvas.gm.name=blurquickreject -Dkanvas.gm.includeBlocking=true
```

`blurquickreject` remains terminal at
`unsupported.stroke.width_invalid`: the GM draws a hairline stroke outline
before its mask-blur rectangles. No fidelity or promotion claim is made for
that GM.
