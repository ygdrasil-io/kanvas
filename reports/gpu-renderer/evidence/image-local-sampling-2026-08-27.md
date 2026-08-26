# Bounded image local sampling — 2026-08-27

## Scope

This evidence covers exactly the `DrawimagerectFilterGm` image-shader fill:
the prepared RGBA8 image artifact is sampled with clamp-to-edge `nearest` or
`linear`, and a finite bounded translation/positive-scale local matrix.  The
GPU remains headless/offscreen WebGPU.

The native lowerer recognizes the four-edge, axis-aligned rectangle path that
`GmCanvas` emits for this GM's translated fill.  It is not a general path
image-shader implementation.  Its UV source rectangle carries the bounded
local transform and may extend outside the image only because the sampler is
clamp-to-edge.

## CPU / GPU / diff / stats

| Evidence | Location or result |
| --- | --- |
| CPU/Skia reference | `integration-tests/skia/src/test/resources/reference/drawimagerect_filter.png` |
| WebGPU generated PNG | `integration-tests/skia/src/test/resources/generated-renders/image/drawimagerect_filter.png` |
| Diff authority | `ComparisonUtils.compareRgba`, executed by `SkiaGmRunner` with `DebugLevel.PIXEL` |
| Pixels | 1,552 / 5,400 exact (28.74074074074074% similarity) |
| Native route | 4 dispatches, 0 refusals, empty diagnostics |

The comparison runs with the GM's existing `0.0%` threshold; no threshold was
changed.  The non-identical diff is retained as a measured fidelity result,
not promoted to a broader image-shader claim.

## Stable refusals

The mapper/compiler tests retain terminal, typed refusals for:

- non-clamp tile modes: `unsupported.material.mapping.image_tile_mode`;
- perspective local matrices: `unsupported.material.mapping.image_local_matrix_perspective`;
- non-finite, skew, negative/zero, or unbounded affine matrices:
  `unsupported.material.mapping.image_local_matrix_affine`;
- cubic/mipmap/anisotropic sampling and codec/image formats outside the
  pre-existing prepared RGBA8 contract.

## Reproduction

```sh
./gradlew --no-daemon :integration-tests:skia:generateSkiaRendersFor -Pgm.name=drawimagerect_filter
./gradlew --no-daemon :integration-tests:skia:test \
  --tests org.graphiks.kanvas.skia.SkiaGmRunner \
  -Dkanvas.gm.name=drawimagerect_filter \
  -Dkanvas.render.debugLevel=PIXEL
```

`nearest_half_pixel_image` was deliberately not regenerated: it exercises
mirror/negative-scale variants beyond this bounded positive-scale contract and
currently hits the pre-existing prepared image-lowerer authority invariant.
