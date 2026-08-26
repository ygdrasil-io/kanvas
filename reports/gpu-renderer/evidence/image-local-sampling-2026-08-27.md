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

This direct image route applies the bounded local transform while preparing
image UVs.  It does not consume `GPUPreparedMaterialProgram`'s WGSL ABI; the
unconsumed ABI extension was removed rather than being represented as executed
native behavior.  The generic material compiler refuses non-identity image
local matrices, so this specialized lowerer is the sole accepted route.

## CPU / GPU / diff / stats

| Evidence | Location or result |
| --- | --- |
| CPU/Skia reference | `integration-tests/skia/src/test/resources/reference/drawimagerect_filter.png` |
| WebGPU generated PNG | `integration-tests/skia/src/test/resources/generated-renders/image/drawimagerect_filter.png` |
| Diff authority | `ComparisonUtils.compareRgba`, executed by `SkiaGmRunner` with `DebugLevel.PIXEL` |
| Pixels | 3,104 / 10,800 exact (28.74074074074074% similarity) |
| Native route | 4 dispatches, 0 refusals, empty diagnostics |

The comparison runs with the GM's existing `0.0%` threshold; no threshold was
changed.  The non-identical diff is retained as a measured fidelity result,
not promoted to a broader image-shader claim.

Independent machine-readable evidence is checked in beside this report:
[`diff.json`](image-local-sampling-2026-08-27/diff.json),
[`stats.json`](image-local-sampling-2026-08-27/stats.json),
[`route.json`](image-local-sampling-2026-08-27/route.json), and
[`diagnostics.json`](image-local-sampling-2026-08-27/diagnostics.json).

`GPUPreparedSurfaceImagePixelTest` additionally executes a bounded
translation + nearest + alpha-tint case against an exact WebGPU readback
oracle (`[137, 0, 0, 64]` for a half-alpha red paint over a half-alpha A8
image).  This is the representative nearest/alpha route proof.

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

`nearest_half_pixel_image` was regenerated and compared independently at
73.44938749194068% similarity, with two dispatches and zero refusals.  Its
`mirror` cases are inherited Canvas geometry transforms: the GM constructs
`Shader.Image` directly, without a `Shader.WithLocalMatrix`.  This successful
render therefore does not broaden the bounded image local-matrix contract:
negative *local matrices* remain a stable refusal.  Its independent artifacts
are [`diff.json`](nearest-half-pixel-image-2026-08-27/diff.json),
[`stats.json`](nearest-half-pixel-image-2026-08-27/stats.json),
[`route.json`](nearest-half-pixel-image-2026-08-27/route.json), and
[`diagnostics.json`](nearest-half-pixel-image-2026-08-27/diagnostics.json).
