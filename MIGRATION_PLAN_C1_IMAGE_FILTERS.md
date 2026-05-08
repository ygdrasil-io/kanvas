# MIGRATION_PLAN_C1_IMAGE_FILTERS.md — mini plan for C1 image filters extras

This is a **slimmed-down, audited replacement** for the original C1
entry in [MIGRATION_PLAN_RASTER_COMPLETION.md § C1](MIGRATION_PLAN_RASTER_COMPLETION.md).
That entry estimated ~1800 LOC across 4 sub-slices ; the audit
below shows the surface is **22 missing factories** (vs the 11 the
original plan listed) and the right LOC budget is **~2750 main +
~1500 test**, decomposed into 7 sub-slices that ship independently.

## Audit (2026-05-08)

### What's already shipped

[`SkImageFilters`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt)
exposes 6 factories today (Group A core) :

| Factory | Algorithm |
|---|---|
| [`Offset(dx, dy, input)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | shift sampled coords by `(-dx, -dy)` |
| [`ColorFilter(cf, input)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | apply a [SkColorFilter] per pixel |
| [`Compose(outer, inner)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | `outer(inner(input))` |
| [`Blur(σx, σy, …)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | 2D Gaussian blur |
| [`MatrixTransform(M, …, input)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | resample input through `M` |
| [`DropShadow(dx, dy, σx, σy, color, input)`](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt) | offset + blur + colour-tint composited under input |

### What's missing (22)

Cross-referenced against
[`include/effects/SkImageFilters.h`](https://github.com/google/skia/blob/main/include/effects/SkImageFilters.h)
on the same Skia version :

| Factory | Algorithm | Upstream GM(s) using it |
|---|---|---|
| `Image(image, src, dst, sampling)` | sample a static [SkImage] as input | many (any GM that builds a filter chain over a stored image) |
| `Picture(pic, targetRect)` | replay an [SkPicture] into a bitmap input | `pictureshader.cpp`, `imagemakewithfilter.cpp` |
| `Shader(shader, dither)` | use any [SkShader] as input | `imagefilters.cpp`, `colorfilterimagefilter.cpp` |
| `Empty()` | placeholder ; transparent black | `imagefiltersgraph.cpp` |
| `Crop(rect, [tileMode], input)` | constrain `input` to `rect`, optionally tile | `crop_imagefilter.cpp` (16.4 KB GM), `animatedimageblurs.cpp`, `imageblurrepeatmode.cpp`, `matrixconvolution.cpp` |
| `Tile(srcRect, dstRect, input)` | tile `input` from `srcRect` over `dstRect` | `bigtileimagefilter.cpp`, `tileimagefilter.cpp`, `filterfastbounds.cpp`, `imagemakewithfilter.cpp` |
| `Magnifier(lensBounds, zoom, inset, sampling, input)` | radial scale around lens centre | `imagemagnifier.cpp` (8.3 KB GM) |
| `Arithmetic(k1, k2, k3, k4, …, bg, fg)` | `k1·src·dst + k2·src + k3·dst + k4` per channel | `arithmode.cpp`, `crbug_905548.cpp`, `imagefiltersgraph.cpp`, `imagemakewithfilter.cpp`, `xfermodeimagefilter.cpp` |
| `Blend(mode, bg, fg)` / `Blend(blender, bg, fg)` | composite two filter inputs via [SkBlendMode] | `arithmode.cpp`, `colorfilterimagefilter.cpp` (and many) |
| `Merge(filters[N])` | concat N inputs via per-pixel max-alpha | `imagefiltersgraph.cpp`, `imagefilterscropped.cpp`, `imagemakewithfilter.cpp` |
| `DropShadowOnly(dx, dy, σx, σy, color, input)` | drop-shadow without the original | (combined with `DropShadow` GMs) |
| `Erode(rx, ry, input)` | morphological erosion (per-pixel min over disk) | `morphology.cpp`, `imagefilters.cpp`, `imagefiltersclipped.cpp`, `imagefilterscropexpand.cpp`, `imagefilterscropped.cpp` |
| `Dilate(rx, ry, input)` | morphological dilation (per-pixel max) | same cluster as `Erode` |
| `DisplacementMap(xCh, yCh, scale, displacement, color)` | sample `color` via offsets read from `displacement.{xCh, yCh} - 0.5` | `displacement.cpp` (8.2 KB GM), `imagefiltersclipped.cpp`, `imagefilterscropexpand.cpp`, `imagefiltersscaled.cpp`, `imagefilterstransformed.cpp` |
| `MatrixConvolution(kSize, kernel[], gain, bias, kCenter, tileMode, convolveAlpha, input)` | 2D kernel convolution | `matrixconvolution.cpp`, `imagefilters.cpp`, `imagefiltersclipped.cpp`, `imagefiltersgraph.cpp`, `imagemakewithfilter.cpp` |
| `DistantLitDiffuse(direction, color, surfaceScale, kd, input)` | normal-from-height Phong diffuse, parallel light | `lighting.cpp` (7.8 KB GM), `imagefiltersclipped.cpp`, `imagemakewithfilter.cpp` |
| `PointLitDiffuse(location, color, surfaceScale, kd, input)` | … point light | same cluster |
| `SpotLitDiffuse(location, target, falloffExp, cutoffAngle, color, surfaceScale, kd, input)` | … cone spot light | same cluster |
| `DistantLitSpecular(direction, color, surfaceScale, ks, shininess, input)` | Phong specular, parallel | same cluster |
| `PointLitSpecular(location, color, surfaceScale, ks, shininess, input)` | … point | same cluster |
| `SpotLitSpecular(location, target, falloffExp, cutoffAngle, color, surfaceScale, ks, shininess, input)` | … spot | same cluster |
| `RuntimeShader(builder, scaleFactor, childrenMap, …)` | run a [SkRuntimeEffect] over the filter input | `runtimeimagefilter.cpp` |

### Original C1 plan accuracy

The existing
[MIGRATION_PLAN_RASTER_COMPLETION.md § C1](MIGRATION_PLAN_RASTER_COMPLETION.md)
covered **11 of the 22** missing factories :

- ✅ in plan : Erode, Dilate, DisplacementMap, Magnifier, Tile,
  Arithmetic, Merge, Image, Picture, Shader, Lighting (3
  variants).
- ❌ missing from plan : Blend, Crop, DropShadowOnly, Empty,
  MatrixConvolution, **3 specular lighting variants**,
  RuntimeShader.
- LOC undersize : "Lighting (point / distant / spot) ~600 LOC
  ensemble" assumed only 3 lighting filters ; upstream actually
  ships 6 (3 diffuse + 3 specular). Realistic doubles the
  estimate.

### Ported GMs that need C1 missing filters

**None.** Every ported GM in
[`kanvas-skia/src/main/kotlin/org/skia/tests/`](kanvas-skia/src/main/kotlin/org/skia/tests/)
that uses image filters relies only on the 6 already-shipped
factories. The 22 missing factories are GM-unblockers for new
ports (the upstream GMs in the right column of the table above)
rather than fixes for existing ratchet failures.

This puts C1 in the same conceptual bucket as B2 SVG / D3.4 WEBP :
**widen surface, not unblock existing GMs**. Justifies a
mini-plan rather than an outright descope because :

- Every additional factory unlocks a self-contained set of
  upstream-only GMs.
- The 6 already-shipped factories prove the
  [SkImageFilter](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilter.kt)
  base class + `filter(input, ctx)` contract is sound — adding
  more is decomposed work, not invention.

## Goal

Match upstream's `SkImageFilters` factory surface so future GM
ports can use the missing filters. **Iso-fidelity** with upstream
Skia's filter outputs is the goal where the algorithm is well-
defined (e.g. morphology, arithmetic, convolution) ; **best-effort
visual match** is the goal where Skia's algorithm uses platform-
specific GPU shaders we can't mirror cheaply (e.g. lighting's Phong
shading on F16 — sub-pixel drift acceptable).

## Scope

### In

- All 22 missing factories listed above except those marked **out**
  below.
- Pure-Kotlin algorithms running over the existing
  `kRGBA_8888` / `kRGBA_F16Norm` raster path. No GPU. No
  threading.

### Out (descoped, with revival path)

| Filter | Reason | Revive when |
|---|---|---|
| `RuntimeShader(builder, …)` | depends on D2 SkRuntimeEffect (currently doc-only ; iso-fidelity exception) | D2 ships an executable shim, not before |

That's the only descope — every other missing factory is in scope.

## Slices

Sized so each ships independently, fits a single PR, and unlocks a
named GM cluster. Order picks "smallest first" plus "dependencies
first" — the trivial wrappers (C1.1) feed the fancier composers
(C1.3 / C1.7).

### C1.1 — Source / passthrough wrappers ✏️ ~250 LOC

Filters whose "algorithm" is "reuse an existing canvas op as the
filter input". No new pixel arithmetic.

- **`Image(image, srcRect, dstRect, sampling)`** — wrap an
  [SkImage] sampled from `srcRect` into a filter input lying at
  `dstRect`. Implementation : draw `image` into a filter-result
  bitmap of `dstRect` size via the existing
  `SkBitmapDevice.drawImageRect` path.
- **`Picture(pic, targetRect)`** — same with [SkPicture] : replay
  the picture into a `targetRect`-sized bitmap, return as the
  filter input.
- **`Shader(shader, dither)`** — fill a filter-result bitmap with
  the shader. Re-uses
  [SkBitmapDevice.drawPaint](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt).
  `dither` ignored on F16 (already 16-bit per channel) ; honoured
  on 8888 by routing through the existing dither path.
- **`Empty()`** — return a transparent-black input. ~10 LOC ;
  trivial subclass that always reports an empty bbox.
- **`Crop(rect, [tileMode], input)`** — clip `input.eval(ctx)` to
  `rect`. Implementation : run `input.filter`, then for each
  output pixel either return the inside value, the tile-mode
  fallback (clamp / repeat / mirror), or transparent for
  `kDecal`. ~80 LOC.

**Tests** : unit-test each factory with a known input bitmap +
expected output bytes. Per-filter ~20 LOC test ; ~150 LOC test
total.

### C1.2 — Tile + Magnifier ✏️ ~250 LOC

- **`Tile(srcRect, dstRect, input)`** — like `Crop` with
  `kRepeat`, but the source rect is sampled in `srcRect` units,
  scaled into `dstRect`. ~150 LOC.
- **`Magnifier(lensBounds, zoom, inset, sampling, input)`** —
  inside `lensBounds`, sample `input` at the centre of the lens
  scaled by `zoom` ; outside, pass through ; with a smooth fade
  in the `inset` band. ~150 LOC.

**GMs unblocked** : `tileimagefilter.cpp`, `bigtileimagefilter.cpp`,
`imagemagnifier.cpp`.

### C1.3 — Arithmetic family ✏️ ~300 LOC

Filters that combine two input filters per pixel.

- **`Arithmetic(k1, k2, k3, k4, enforcePMColor, bg, fg)`** —
  `k1·src·dst + k2·src + k3·dst + k4` channel-wise, optionally
  pinned to premul-valid range. ~120 LOC.
- **`Blend(mode, bg, fg)`** — apply [SkBlendMode] per pixel.
  Implementation : run both inputs, then walk the result with the
  existing
  [SkBlendModeDispatch](kanvas-skia/src/main/kotlin/org/skia/foundation/SkBlendMode.kt)
  table. ~80 LOC.
- **`Merge(filters[])`** — N inputs composited via per-pixel
  max-alpha (visually : "show whichever input has more coverage
  here"). ~60 LOC.
- **`DropShadowOnly(dx, dy, σx, σy, color, input)`** — variant of
  the existing `DropShadow` that returns just the shadow without
  compositing the original. ~40 LOC (factor out the shadow-build
  code from
  [SkImageFilters.DropShadow](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt)).

**GMs unblocked** : `arithmode.cpp`, `xfermodeimagefilter.cpp`,
`imagefiltersgraph.cpp`.

### C1.4 — Morphology ✏️ ~300 LOC

- **`Erode(rx, ry, input)`** — replace each pixel with the per-channel
  minimum over a disk of radius `(rx, ry)`. Implementation :
  separable horizontal + vertical pass with a sliding-window
  min-deque (van Herk / Gil-Werman algorithm), `O(N)` per pass.
  ~150 LOC.
- **`Dilate(rx, ry, input)`** — same with max instead of min.
  Sharing the deque kernel as `MorphologyOp.kErode` /
  `MorphologyOp.kDilate`, the actual delta is ~30 LOC.

**GMs unblocked** : `morphology.cpp` (~3 GMs), and ~5 more in the
`imagefilters*` cluster that compose Erode / Dilate.

### C1.5 — DisplacementMap ✏️ ~200 LOC

- **`DisplacementMap(xCh, yCh, scale, displacement, color)`** —
  for each pixel `(x, y)`, read `displacement(x, y).{xCh, yCh}`
  as floats in `[0, 1]`, subtract `0.5` to centre, multiply by
  `scale` to get an offset `(dx, dy)`, then sample
  `color(x + dx, y + dy)` with the colour image's tile mode.

Algorithm is straightforward but allocates a temporary for the
displacement run. ~200 LOC.

**GMs unblocked** : `displacement.cpp` and 4 cross-cluster GMs.

### C1.6 — MatrixConvolution ✏️ ~250 LOC

- **`MatrixConvolution(kernelSize, kernel[], gain, bias, kCenter, tileMode, convolveAlpha, input)`** —
  general 2D kernel : for each pixel, `gain * Σ kernel[i,j] *
  input[x + i - kCenter.x, y + j - kCenter.y] + bias`. `tileMode`
  dictates edge sampling. `convolveAlpha = false` skips the alpha
  channel (per-channel premul-aware path).

Hot path : a doubly-nested loop in scalar Kotlin is plenty fast
for the kernel sizes upstream uses (3×3 to 9×9). No SIMD needed.
~250 LOC.

**GMs unblocked** : `matrixconvolution.cpp`, plus 4 GMs in the
`imagefilters*` cluster.

### C1.7 — Lighting (full surface : 6 variants) ✏️ ~1200 LOC

The biggest sub-slice. Six factories sharing a normal-from-height
extraction + Phong reflection model :

- **`DistantLitDiffuse(direction, color, surfaceScale, kd, input)`** —
  parallel light + Lambertian (`max(0, n · L)`) shading. ~150 LOC.
- **`PointLitDiffuse(location, color, surfaceScale, kd, input)`** —
  point light (per-pixel `L = normalize(location - p)`). ~150 LOC.
- **`SpotLitDiffuse(location, target, falloffExp, cutoffAngle, …)`**
  — point light + cosine cutoff cone. ~250 LOC.
- **`DistantLitSpecular(direction, …, ks, shininess, input)`** —
  Blinn-Phong specular `(n · h)^shininess`. ~150 LOC.
- **`PointLitSpecular(location, …, ks, shininess, input)`** —
  point light specular. ~150 LOC.
- **`SpotLitSpecular(location, target, …, ks, shininess, input)`**
  — spot light specular. ~250 LOC.

**Implementation note** — the 6 variants share :

1. A normal-from-height kernel : sample `input.alpha` at four
   neighbours, compute Sobel gradient, normalise to unit normal.
2. A Phong evaluator : compute `L`, `H = normalize(L + V)`, then
   either `kd · max(0, n · L)` (diffuse) or `ks · max(0, n · H) ^
   shininess` (specular).
3. A spot-cone cutoff (Spot variants only) : `max(0, cos(angle) -
   cos(cutoff))^falloffExp` modulation.

Factor `LightingCommon.kt` for the shared kernel + Phong
evaluator, then 6 thin factory subclasses. The shared kernel
should be ~400 LOC ; each factory ~150 LOC ; ~1200 total.

**Iso-fidelity caveat** — the height-from-alpha kernel uses a
specific Sobel weighting upstream ; sub-pixel drift on the
generated normal is normal. The lighting GM tolerance is loose
upstream (similarity ≥ 95%). Match those weights bit-for-bit
where possible.

**GMs unblocked** : `lighting.cpp` (~6 GMs), `imagefiltersclipped.cpp` lighting branches.

## Total LOC

| Slice | Main | Test | GMs unblocked |
|---|---:|---:|---|
| C1.1 source / passthrough | ~250 | ~150 | foundation for higher slices |
| C1.2 Tile + Magnifier | ~250 | ~120 | 3 |
| C1.3 Arithmetic family | ~300 | ~150 | 3 |
| C1.4 Morphology | ~300 | ~150 | ~8 |
| C1.5 DisplacementMap | ~200 | ~120 | 5 |
| C1.6 MatrixConvolution | ~250 | ~150 | 5 |
| C1.7 Lighting (6 variants) | ~1200 | ~600 | ~6 |
| **Total** | **~2750** | **~1440** | **~30 GM ports unblocked** |

vs. the original C1 estimate of `~1800 main` (which only covered
11 of 22 factories and undersized the lighting cluster by ~600
LOC).

## Validation

- **Per-filter unit tests** — synthetic input bitmap (e.g. a
  4×4 gradient) → known output bytes. The morphology and
  convolution slices are the most amenable to bit-exact
  validation ; lighting is approximate (loose tolerance).
- **End-to-end GM ports** — porting the upstream GMs in the
  "unblocks" column is the natural integration test. Each slice's
  PR adds 1-2 ports as smoke test ; full-cluster ports happen in
  follow-up PRs (C1.x.GM-port).
- **No similarity-ratchet entry until per-filter tests pass** —
  Phase F GMs trip the ratchet before this work shipped, so we
  can't use the existing scoreboard. Each slice declares its own
  bar in the matching test class.

## Sequencing notes

- **Independent of D1 / pathops** — image filters operate on
  rasterised bitmaps, not paths.
- **Depends on**
  [SkBitmapDevice](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt) ✅
  (already shipped) for the per-filter raster path,
  [SkImageFilter](kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilter.kt) ✅
  base class.
- **C1.1 ships first** — every other slice composes over `Image` /
  `Picture` / `Shader` / `Empty` / `Crop`.
- **C1.7 ships last** — biggest, highest risk, depends on the
  shared lighting kernel.
- **Mergeable in parallel with D1** — no SkPathOps dependency at
  all.

## Status

📋 **planned** — slices defined, scope agreed, no code yet.
Pickup-ready when the schedule allows ; not blocking any other
open chantier.

The original [§ C1 entry](MIGRATION_PLAN_RASTER_COMPLETION.md)
will be slimmed to point here, mirroring how
[MIGRATION_PLAN_SVG.md](MIGRATION_PLAN_SVG.md) replaced the
original B2 entry.
