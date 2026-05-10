# Shaders

`SkShader` is the abstract source of *premultiplied* color in Skia's drawing
pipeline. Whenever an `SkPaint` carries a shader, the paint's solid `SkColor`
is ignored and the shader is asked, per pixel, "what colour should appear
here?" Shaders are pure functions over local-space coordinates: they are
immutable once constructed (every constructor returns a `sk_sp<SkShader>`),
they are reentrant, and they participate in the SKP serialisation format via
`SkFlattenable`. The paint's alpha still modulates the shader's output, which
makes it cheap to vary opacity without rebuilding a gradient or image
shader.

Shaders are composable. `SkShader::makeWithLocalMatrix`,
`makeWithColorFilter` and `makeWithWorkingColorSpace` (`include/core/SkShader.h`)
each wrap the receiver with a thin shim, and the `SkShaders::Blend` /
`SkShaders::CoordClamp` factories combine two shaders into one. The factory
namespace `SkShaders` (also declared in `SkShader.h`) is the modern entry
point — `Color`, `Image`, `RawImage`, `Empty`, `Blend`, `CoordClamp`. The
gradient and Perlin-noise factories live in
`include/effects/SkGradient.h` and
`include/effects/SkPerlinNoiseShader.h` and add to the same namespace.

## Pipeline at a glance

```
                     ┌────────────────┐
   SkPaint ────►     │  SkShader      │  appendStages(SkStageRec, MatrixRec)
   (paint.alpha)     │  (immutable)   │  ────────────────────────────────────►
                     └────────┬───────┘
                              │ walks the shader tree, accumulating
                              │ local matrices in a MatrixRec
                              ▼
                    ┌──────────────────┐
                    │ SkRasterPipeline │  one stage per node
                    │ (raster path)    │  child shaders inline as sub-stages
                    └────────┬─────────┘
                             │
                             ▼  on GPU the same tree is converted to
                    ┌──────────────────┐  a `GrFragmentProcessor` (Ganesh)
                    │ blitter / fragment│ or a `Recorder` SkSL fragment
                    │ shader            │ (Graphite) by SkShaderBase::asFP
                    └──────────────────┘
```

Every concrete shader extends `SkShaderBase`
(`src/shaders/SkShaderBase.h`) — a non-public refinement that tags the
implementation with a `ShaderType` enum, exposes `appendStages` for the CPU
raster pipeline, and lets the framework introspect a shader as either a
gradient (`asGradient`) or as a single image (`onIsAImage`). The full set of
internal types is enumerated by the `SK_ALL_SHADERS` X-macro:

```cpp
#define SK_ALL_SHADERS(M) \
    M(Blend) M(CTM) M(Color) M(ColorFilter) M(CoordClamp) \
    M(Empty) M(GradientBase) M(Image) M(LocalMatrix)      \
    M(PerlinNoise) M(Picture) M(Runtime) M(Transform)     \
    M(TriColor) M(WorkingColorSpace)
```

with the gradient family further enumerated by `SK_ALL_GRADIENTS`:
`Conical`, `Linear`, `Radial`, `Sweep`. Every entry corresponds to a single
file under `src/shaders/` (or `src/shaders/gradients/` for the four
gradients).

## Public API surface

| Header | Purpose |
|---|---|
| `include/core/SkShader.h` | Abstract base + `SkShaders` factory for color, image, blend, coord-clamp, raw-image, and the `makeWithLocalMatrix` / `makeWithColorFilter` / `makeWithWorkingColorSpace` wrappers. |
| `include/effects/SkGradient.h` | `SkGradient`, `SkGradient::Colors`, `SkGradient::Interpolation`, plus `SkShaders::LinearGradient` / `RadialGradient` / `TwoPointConicalGradient` / `SweepGradient`. |
| `include/effects/SkPerlinNoiseShader.h` | `SkShaders::MakeFractalNoise`, `MakeTurbulence`. |
| `include/private/SkGainmapShader.h` | `SkGainmapShader::Make` — used internally by HDR rendering. |
| `include/core/SkPicture.h` (`SkPicture::makeShader`) | Wraps a recorded `SkPicture` as a tileable shader. |
| `include/core/SkImage.h` (`SkImage::makeShader` / `makeRawShader`) | Convenience constructors that delegate to `SkImageShader::Make` / `MakeRaw`. |
| `include/effects/SkRuntimeEffect.h` (`SkRuntimeEffect::makeShader`) | User-authored SkSL shaders — see [Runtime Effects](runtime-effects.md). |

## SkShaderBase — `src/shaders/SkShaderBase.h`

`SkShaderBase` declares the contract every implementation honours:

- `ShaderType type() const = 0` — identifies the concrete subclass without
  needing RTTI.
- `bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) = 0`
  — emits raster-pipeline stages for the CPU backend. The `SkStageRec`
  carries the destination color space / type, an arena allocator and the
  `SkRasterPipeline` being built; the `MatrixRec` accumulates pending local
  matrices so a child shader can collapse them into a single multiply.
- `bool isOpaque() = 0` — used by the blitter to skip src-over compositing.
- `GradientType asGradient(GradientInfo*, SkMatrix*)` — defaults to
  `kNone`. Gradient shaders override it so the PDF / SVG canvases can emit
  native gradient primitives.
- `SkImage* onIsAImage(SkMatrix*, SkTileMode*)` — `SkImageShader` returns
  itself; everything else returns null. Lets callers (e.g. nine-patch
  drawing) recover the underlying image without rasterising the shader.
- `SkRuntimeEffect* asRuntimeEffect()` — `SkRuntimeShader` returns its
  effect.
- `bool onAsLuminanceColor(SkColor4f*)` — single-color summary used by
  emboss-and-shadow heuristics.

`makeInvertAlpha()` and `makeWithCTM()` are utility wrappers used by Skia
internally (the latter is `SkCTMShader`, a sibling of `SkLocalMatrixShader`
that bakes in a *parent* CTM rather than a *local* matrix).

`SkShaders::MatrixRec` is the linchpin of shader-tree traversal. It tracks
three things:

1. The CTM — applied at the root and propagated unchanged to children.
2. The total local matrix — the concatenation of every `SkLocalMatrixShader`
   between the root and the current node.
3. A pending local matrix — local matrices that have not yet been folded
   into the raster pipeline.

`MatrixRec::concat`, `apply`, and `applyForFragmentProcessor` flush the
pending matrix into the appropriate stage at the right moment. The
Android-framework backwards-local-matrix concatenation hack lives here too
(`SkShaderBase::ConcatLocalMatrices` swaps argument order under
`SK_BUILD_FOR_ANDROID_FRAMEWORK`, see b/256873449).

The base class also retains a legacy `Context` / `shadeSpan` API guarded by
`SK_ENABLE_LEGACY_SHADERCONTEXT`, used by Android's HWUI for the per-span
blitters that predate the raster pipeline.

---

## SkColorShader — `src/shaders/SkColorShader.{h,cpp}`

The simplest shader: a single sRGB color stored as `SkColor4f`. Created via
`SkShaders::Color(SkColor)` or `SkShaders::Color(SkColor4f, sk_sp<SkColorSpace>)`.
`SkShaders::Color` always converts the supplied color into sRGB before
storing it, so `SkColorShader` itself only ever needs to perform one
`SkColorSpaceXformSteps` transform at draw time (sRGB → destination CS).
`isOpaque()` returns true iff the alpha channel is exactly 1.0.

`SkColorShader::appendStages` is a single
`SkRasterPipelineOp::appendConstantColor`. Two flattenable names are
registered for backwards compatibility with old SKPs: the historical
`SkColorShader4` and the modern `SkColorShader`.

## SkEmptyShader — `src/shaders/SkEmptyShader.{h,cpp}`

Returns `transparent_black` everywhere. `SkShaders::Empty()` returns the
canonical instance — used by the higher-level factories as a sentinel when
their inputs are invalid (e.g. `SkPictureShader::Make` with a null
picture).

## SkBlendShader — `src/shaders/SkBlendShader.{h,cpp}`

`SkShaders::Blend(SkBlendMode|sk_sp<SkBlender>, sk_sp<SkShader> dst,
sk_sp<SkShader> src)` evaluates two child shaders and combines them with a
blend mode. The CPU pipeline is straight-forward: emit the `dst` stages
storing the result in scratch storage, emit the `src` stages, then load the
saved `dst` into the dst register and append the blend stages
(`SkBlendMode_AppendStages`).

A few short-circuits keep the tree small: `kClear` returns
`Color(0)`, `kSrc` returns the src shader, `kDst` returns the dst shader.
For non-built-in `SkBlender`s a *runtime effect* is conscripted from the
`SkKnownRuntimeEffects::StableKey::kBlend` slot — the runtime effect is
parameterised with the two child shaders and the blender, so any custom
SkSL blend mode can be used.

`isOpaque()` walks the children's alpha and the blend mode's coefficients
(`SkBlendMode_AsCoeff`) to determine when the result is provably opaque —
this is conservative; non-coeff blends report false.

## SkColorFilterShader — `src/shaders/SkColorFilterShader.{h,cpp}`

Wraps a base shader plus a `SkColorFilter`, with an optional alpha
multiplier. Constructed by `SkShader::makeWithColorFilter`. The CPU
implementation appends the base shader's stages, then the colour filter's
`appendStages`, then a constant alpha multiply when the alpha is non-unit.
This is the workhorse for `SkColorFilters::Lerp`, image tints applied via
`SkPaint::setColorFilter` while drawing through an image shader, and so on.

## SkLocalMatrixShader / SkCTMShader — `src/shaders/SkLocalMatrixShader.{h,cpp}`

`SkLocalMatrixShader` wraps another shader and a local matrix. Created by
`SkShader::makeWithLocalMatrix`. It collapses redundant wraps: wrapping an
already-wrapped shader composes the matrices. It also forwards
`isConstant`, `asGradient`, `onIsAImage`, and `onAsLuminanceColor` to the
inner shader so introspection survives the wrap.

`MakeWrapped<Subclass>(localMatrix, ...)` is the canonical constructor used
by `SkPictureShader::Make` and gradient builders to attach a local matrix
without forcing every concrete subclass to handle one.

`SkCTMShader` is the parent CTM analogue — it stores an explicit CTM and
calls `appendRootStages(rec, fCTM)` to seed the pipeline as if that CTM had
been the canvas's own. It is not registered for serialisation (its
`CreateProc` always asserts).

## SkTransformShader — `src/shaders/SkTransformShader.{h,cpp}`

A specialised shader used internally by the `drawAtlas` / `drawVertices`
fast-paths: it applies a per-vertex `SkRSXform` (rotate-scale + translate)
to the local coordinates before forwarding to a child shader. Not exposed
as a public factory.

## SkTriColorShader — `src/shaders/SkTriColorShader.{h,cpp}`

Used by `SkCanvas::drawVertices` to shade triangles with per-vertex colors
(barycentric interpolation) and optionally per-vertex texture coordinates
plus a child shader.

## SkCoordClampShader — `src/shaders/SkCoordClampShader.{h,cpp}`

`SkShaders::CoordClamp(child, subset)` clamps the local coordinates to a
rectangle before forwarding to `child`. Useful with image shaders to pad a
sub-rectangle without sampling the surrounding pixels (when sampling could
otherwise leak across an atlas boundary).

## SkWorkingColorSpaceShader — `src/shaders/SkWorkingColorSpaceShader.{h,cpp}`

Implements `SkShader::makeWithWorkingColorSpace(inputCS, outputCS)`. The
shader transforms the destination color space into `inputCS` for the child,
then assumes the child returns colors in `outputCS` and converts those back
to the destination. This is how Skia threads custom color management
through a shader graph — see [Color Management](color-management.md). The
factory short-circuits when both spaces equal the destination.

---

## SkImageShader — `src/shaders/SkImageShader.{h,cpp}`

`SkShaders::Image(image, tmx, tmy, sampling, lm)` and
`SkImage::makeShader` create an `SkImageShader`. This is the most-used
shader in any Skia program, because every `drawImage` / `drawImageRect`
ultimately funnels through it.

Inputs:

- `sk_sp<SkImage> image` — pixel source (raster, lazy, or texture-backed).
- `SkTileMode tmx`, `tmy` — `kClamp`, `kRepeat`, `kMirror`, `kDecal` for
  outside the image.
- `SkSamplingOptions` — nearest, linear, mip-mapped, or cubic
  (Mitchell/Catmull-Rom) sampling. The cubic variant uses the
  `CubicResamplerMatrix(B, C)` static helper to derive the resampling
  matrix from the two B/C coefficients.
- Optional local matrix — folded into a wrapping `SkLocalMatrixShader`.
- `bool raw` — `MakeRaw` skips premultiplication, alpha conversion, and
  color-space transforms; useful for sampling alpha or tag images. Cubic
  sampling is rejected for raw shaders because of how clamping interacts
  with non-premul data.
- `bool clampAsIfUnpremul` — used by Android Framework for legacy
  N32 → linear paths.

`MakeSubset` (texture-backed only) restricts sampling to a sub-rect — the
returned shader can only be used on GPU surfaces. `MakeForDrawRect` is the
fast-path used by `SkCanvas::drawImageRect` to convert the call into
`drawRect(paint with SkImageShader)` while preserving subset semantics.

The `optimize(SkTileMode, dimension)` helper rewrites `kRepeat`/`kMirror` to
`kClamp` when sampling a 1-pixel axis (they're equivalent there and clamp
is the fastest path); `kDecal` is preserved so it still produces
transparent border pixels.

`isOpaque()` consults the image's alpha type and the tile modes — `kDecal`
forces non-opaque because the outside is transparent black.
`onIsAImage` returns the image pointer plus the tile modes, letting the
canvas treat the shader as a plain image when possible (e.g.
`drawImageRect` paths in PDF).

`appendStages` defers most work to `SkBitmapProcState` (CPU) and
`SkMipmapAccessor` for mip selection. The actual filtering kernels live in
`src/core/` (`SkBitmapProcState_*`, `SkMipmapAccessor`, the SIMD opts in
`src/opts/`). For full pipeline details see the
[CPU Rendering Pipeline](cpu-rendering-pipeline.md) doc; for the underlying
image objects see [Bitmap, Pixmap & Image](bitmap-pixmap-image.md).

## SkBitmapProcShader — `src/shaders/SkBitmapProcShader.{h,cpp}`

Legacy compatibility shim used only when `SK_ENABLE_LEGACY_SHADERCONTEXT`
is defined; it adapts `SkBitmapProcState` to the old `Context::shadeSpan`
API. New code should never construct one directly — `SkImageShader` is
the public surface.

---

## SkPictureShader — `src/shaders/SkPictureShader.{h,cpp}`

Wraps an `SkPicture` so it can be used as a tile pattern. `SkPicture::makeShader`
constructs one; `SkPictureShader::Make` then optionally wraps it in an
`SkLocalMatrixShader`. Empty pictures or empty tile rects collapse to
`SkShaders::Empty()`.

The implementation lazily rasterises the picture into an
`SkImage`-backed shader at the right scale for the current draw:

- `CachedImageInfo::Make(bounds, totalM, dstColorType, dstCS, maxTextureSize, props)`
  decomposes the CTM into a rotation-invariant scale, computes a tile size
  capped at ~4 M pixels (2048×2048), and clamps it to the GPU's max texture
  size when applicable.
- `rasterShader` looks up the resulting image in the global
  `SkResourceCache` (keyed on color space, color type, picture ID, subset,
  scale, and surface props). On a miss, it makes a new raster surface,
  draws the picture into it, and stores the image in the cache.
- The cached image is then wrapped as an `SkImage` shader with the user's
  tile modes plus a scale matrix that maps the rasterised tile back to the
  original picture size.

This means `SkPictureShader` essentially adapts vector content into the
image-shader fast paths, at the cost of one rasterisation per unique scale.

The cache key uses both the XYZ-D50 hash and the transfer-function hash of
the destination color space, so different CS's yield different cached
tiles.

---

## SkPerlinNoiseShader — `src/shaders/SkPerlinNoiseShaderImpl.{h,cpp}`

Implements the SVG 1.1 `feTurbulence` filter primitive
(http://www.w3.org/TR/SVG/filters.html#feTurbulenceElement). Two variants:

- `SkShaders::MakeFractalNoise(baseFx, baseFy, numOctaves, seed, tileSize)`
- `SkShaders::MakeTurbulence  (baseFx, baseFy, numOctaves, seed, tileSize)`

`baseFrequency` is in (0..1] per axis; `numOctaves` is capped at 255 (each
octave doubles the frequency). When `tileSize` is non-empty the
implementation tweaks the frequencies to make the resulting pattern
seamlessly tileable (`PaintingData::stitch()`).

Internally, `SkPerlinNoiseShader` precomputes a lattice-selector permutation
table and a per-channel gradient table at construction (256 entries each;
generated by an LCG matching the SVG spec exactly). Two helper bitmaps
(`fPermutationsBitmap`, `fNoiseBitmap`) are bound as alpha-only and 8888
images respectively when the noise is evaluated through the raster
pipeline.

`isOpaque()` is always false — turbulence and fractal noise both have a
varying alpha channel.

---

## Gradients — `src/shaders/gradients/`

All four gradient kinds derive from `SkGradientBaseShader`
(`SkGradientBaseShader.{h,cpp}`). A gradient is parameterised by
`SkGradient::Colors` (the color stops + tile mode + colour space) and
`SkGradient::Interpolation` (premul / colour-space / hue-method). The base
class:

- Owns the color and position arrays (interleaved when stop positions are
  provided, evenly distributed otherwise).
- Validates the inputs (`SkGradientBaseShader::ValidGradient`) and degrades
  pathological inputs to a single-colour shader
  (`MakeDegenerateGradient`).
- Computes a `fPtsToUnit` matrix mapping local coordinates to the canonical
  gradient parameter space (e.g. (0,0)→(1,0) for linear, the unit circle
  for radial), so each subclass can specialise its `appendGradientStages`
  to evaluate `t` once.

`SkGradient::Interpolation` (defined in `include/effects/SkGradient.h`) is
the modern, CSS-style interpolation control:

| Field | Effect |
|---|---|
| `InPremul` | Multiply colors by alpha before interpolation. |
| `ColorSpace` | One of `kDestination`, `kSRGBLinear`, `kLab`, `kOKLab` (and gamut-mapped variant), `kLCH`, `kOKLCH` (and gamut-mapped variant), `kSRGB`, `kHSL`, `kHWB`, `kDisplayP3`, `kRec2020`, `kProphotoRGB`, `kA98RGB`. |
| `HueMethod` | `kShorter`, `kLonger`, `kIncreasing`, `kDecreasing` — only applies to LCH/OKLCH/HSL/HWB. |

The interpolation pipeline is `AppendInterpolatedToDstStages`: it first
converts each stop colour into the requested intermediate colour space,
linearly interpolates, then converts back into the destination space. For
`kDestination` (the default) Skia interpolates directly in the dst space —
this is what users get for the legacy `flags` argument
(`Interpolation::FromFlags`).

Each subclass adds the geometry-specific `t` evaluation:

| Type | File | `t` formula |
|---|---|---|
| Linear | `SkLinearGradient.cpp` | `t = x` after `fPtsToUnit` maps `pts[0]→(0,0)`, `pts[1]→(1,0)`. |
| Radial | `SkRadialGradient.cpp` | `t = length(local)` after mapping center→origin and radius→1. |
| Sweep | `SkSweepGradient.cpp` | `t = atan2(y, x) / (2π)` re-mapped to `[startAngle, endAngle]`. |
| Two-point conical | `SkConicalGradient.cpp` | Solves the quadratic for the parameterised circle blend; degenerates to radial / strip / focal cases. |

All four are registered through `SkRegisterLinearGradientShaderFlattenable` /
etc. The factories in `SkShaders` namespace (`LinearGradient`,
`RadialGradient`, `SweepGradient`, `TwoPointConicalGradient`) validate the
arguments — invalid input returns nullptr — and then construct the
appropriate subclass wrapped in an optional `SkLocalMatrixShader`.

The legacy four-argument `MakeLinear(SkPoint pts[2], SkColor[], …)` style
factories survive in the source tree for backwards-compatibility with
older Skia clients and SKP files; the modern API uses `SkGradient::Colors`
which is also what PDF / SVG / Skottie consume.

---

## SkRuntimeShader — `src/shaders/SkRuntimeShader.{h,cpp}`

Holds an `SkRuntimeEffect` plus uniform data and child effect bindings.
Created via `SkRuntimeEffect::makeShader`. This is the user-facing way to
plug arbitrary SkSL into the shader tree — see
[Runtime Effects](runtime-effects.md) for the complete picture.

`appendStages` defers to the runtime effect's compiled raster-pipeline
program (`SkSL::RP::Program`), passing a `RuntimeEffectRPCallbacks` object
that knows how to recursively call `appendStages` on every child shader /
color filter / blender at the right point.

`makeTracedClone(coord)` is the entry point Skia's debugger uses to capture
an execution trace at a single pixel; it forks the effect through
`makeUnoptimizedClone()` so the SkSL is preserved for stepping.

The flattened form of a runtime shader is either a stable-key reference
(when the effect is one of the built-in `SkKnownRuntimeEffects`) or the
SkSL source string plus uniform bytes plus child-effect descriptors. Older
SKPs that included a local matrix are read through a legacy code path that
re-wraps the resulting shader in an `SkLocalMatrixShader`.

---

## SkGainmapShader — `src/shaders/SkGainmapShader.{cpp}`, header in `include/private/SkGainmapShader.h`

Internal shader used by the HDR rendering pipeline to combine a base SDR
image with a per-pixel gain map and a target HDR ratio. Implemented as a
single-purpose `SkRuntimeEffect` (the SkSL is embedded verbatim at the top
of `SkGainmapShader.cpp`) with two child image shaders (base + gainmap),
plus uniform data describing the log-ratio range, gamma, epsilon, gain
weight, and the special Apple-style HDR adjustment used by their gainmap
encoding.

`SkGainmapShader::Make` does several short-circuits:
- If the dst HDR ratio equals the SDR display ratio, returns the base
  image's shader unchanged (no gainmap effect needed).
- Otherwise builds the runtime shader and wraps it in
  `makeWithWorkingColorSpace(linearGamma)` so the math runs in linear
  light.

For the wider gainmap pipeline, see [HDR & Gainmaps](hdr-and-gainmaps.md).

---

## Putting it together — a typical shader graph

A reasonably-complex paint might end up with a shader tree like:

```
LocalMatrix(M_user)
└── ColorFilter(tint)
    └── Blend(kSrcOver, src=Image(photo), dst=LinearGradient(...))
```

Every node is an `SkShaderBase`. At paint time, `SkShaderBase::appendStages`
walks this tree once, accumulating the local matrix, then emitting the
right raster-pipeline ops in order. The blend node generates two
sub-pipelines for `src` and `dst` and joins them via the blend stages; the
colour filter appends its own stages between the blend output and the
output store; the local matrix wraps the blend's coordinates with a
matrix multiply.

For shaders inside a paint that draws through an image filter or runs on
GPU, see [Image Filters & Mask Filters](image-filters-and-mask-filters.md)
for the DAG that wraps the paint, and [Runtime Effects](runtime-effects.md)
for the SkSL bridge.
