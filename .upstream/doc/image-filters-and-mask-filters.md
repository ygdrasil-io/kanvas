# Image Filters & Mask Filters

This document covers two parallel filter families that operate at different
points in the drawing pipeline:

- **`SkImageFilter`** — a *post-rasterisation* effect that consumes a
  rendered image (typically a saved layer) and produces a new image. Image
  filters compose into arbitrary directed-acyclic graphs (DAGs) and are
  the backbone of CSS-style filter effects, drop shadows, blurs, lighting,
  displacement, and filter-graph composition. Created via the static
  factories in `include/effects/SkImageFilters.h`.

- **`SkMaskFilter`** — an *alpha-coverage* transform that runs on the 1-bit
  or 8-bit mask the rasteriser produces from a path or text. Mask filters
  alter the coverage values before they are blended through the paint;
  blur, emboss, and shader-mask are the three remaining families. Created
  via `SkMaskFilter::MakeBlur`, `SkEmbossMaskFilter::Make`,
  `SkShaderMaskFilter::Make`, and the deprecated `SkTableMaskFilter`.

The two families are increasingly converging — every modern `SkMaskFilter`
exposes an `asImageFilter(ctm, paint)` method, and `SkCanvas` will prefer
the image-filter representation when restoring layers — but they are still
shipped as distinct hierarchies because mask filters can be expressed as
an ahead-of-time transform on the *coverage mask*, while image filters
must always go through a real RGBA image buffer.

## Pipeline at a glance

```
                                   ┌────────────┐
   SkPaint ────► (rasteriser) ────►│ A8 coverage │──► SkMaskFilter (blur/emboss/...)
                                   └─────┬──────┘                │
                                         │ filtered coverage     │ asImageFilter
                                         ▼                       ▼
                                   ┌──────────────────────────────────┐
   SkCanvas::saveLayer(paint) ────►│ offscreen RGBA buffer (the layer)│
                                   └─────────────┬────────────────────┘
                                                 │
                                                 ▼
                                       ┌──────────────────┐
                                       │ SkImageFilter DAG│  blur, drop-shadow,
                                       │ (filterImage)    │  lighting, blend,
                                       └────────┬─────────┘  matrix-conv, ...
                                                │
                                                ▼
                                       ┌──────────────────┐
                                       │ skif::FilterResult│ ─► drawn into the
                                       │  (image + offset) │   parent surface
                                       └──────────────────┘
```

## Public API surface

| Header | Purpose |
|---|---|
| `include/core/SkImageFilter.h` | Base `SkImageFilter` — flattenable, `filterBounds`, `countInputs`, `getInput`, `makeWithLocalMatrix`, `isColorFilterNode`. |
| `include/effects/SkImageFilters.h` | Static factory `SkImageFilters::*` for every built-in image-filter primitive (blur, drop shadow, displacement, lighting, morphology, matrix-conv, magnifier, blend, merge, compose, crop, offset, picture, image, runtime-shader, shader, color-filter). |
| `include/core/SkMaskFilter.h` | Base `SkMaskFilter` + `MakeBlur` factory. |
| `include/effects/SkBlurMaskFilter.h` | Legacy `MakeEmboss` factory (deprecated). |
| `include/effects/SkShaderMaskFilter.h` | Deprecated factory wrapping a shader as a mask. |
| `include/effects/SkTableMaskFilter.h` | Deprecated 256-entry per-channel alpha table. |

The `src/` implementations are split:

| Source area | Contents |
|---|---|
| `src/core/SkImageFilter.{cpp}` + `src/core/SkImageFilter_Base.h` | `SkImageFilter` and the internal `SkImageFilter_Base` that every implementation extends. |
| `src/core/SkImageFilterTypes.{h,cpp}` | `skif::Mapping`, `skif::ParameterSpace`, `skif::LayerSpace`, `skif::DeviceSpace`, `skif::Context`, `skif::FilterResult` — the type-safe coordinate spaces and the per-DAG-node context. |
| `src/core/SkImageFilterCache.{h,cpp}` | LRU cache keyed on `<filter-id, mapping, input-bounds>` so identical sub-trees aren't recomputed. |
| `src/effects/imagefilters/Sk*ImageFilter.cpp` | One file per image-filter primitive (Blur, DropShadow, DisplacementMap, Lighting, Morphology, MatrixConvolution, MatrixTransform, Crop, Compose, Merge, Magnifier, Blend, ColorFilter, Image, Picture, Runtime, Shader). |
| `src/core/SkMaskFilter.cpp` + `src/core/SkMaskFilterBase.{h,cpp}` | Base + plumbing into the rasteriser's `filterPath` / `filterRRect` paths. |
| `src/core/SkBlurMaskFilterImpl.{h,cpp}` | The blur mask filter implementation. |
| `src/core/SkBlurEngine.{h,cpp}` + `src/core/SkBlurMask.{h,cpp}` | Shared Gaussian blur kernel used by both the mask filter and image filter. |
| `src/effects/SkEmbossMaskFilter.{h,cpp}` + `src/effects/SkEmbossMask.{h,cpp}` | 3D emboss highlight + ambient lighting. |
| `src/effects/SkShaderMaskFilterImpl.{h,cpp}` | Wraps a shader as the alpha channel. |
| `src/effects/SkTableMaskFilter.cpp` | 256-entry alpha lookup. |

---

## SkImageFilter — `include/core/SkImageFilter.h`

`SkImageFilter` is a `SkFlattenable` and is otherwise an opaque
identifier. Its public methods are introspection only:

```cpp
SkIRect filterBounds(const SkIRect& src, const SkMatrix& ctm,
                     MapDirection, const SkIRect* inputRect = nullptr) const;
bool   isColorFilterNode(SkColorFilter** out) const;
bool   asAColorFilter   (SkColorFilter** out) const;
int    countInputs() const;
const SkImageFilter* getInput(int i) const;
SkRect computeFastBounds(const SkRect& bounds) const;
bool   canComputeFastBounds() const;
sk_sp<SkImageFilter> makeWithLocalMatrix(const SkMatrix&) const;
```

`MapDirection::kForward` answers "what device-space pixels does this
src region affect?" — used to compute the conservative bounds for clipping
the output. `kReverse` answers "what input pixels do I need to fill this
desired output?" — used to size the offscreen buffer the canvas allocates
for the layer that feeds the filter. The default impl walks the input
DAG and unions / intersects accordingly; primitives override to model
their specific kernel reach (blur radius, displacement, etc.).

The actual filter-image dispatch lives on the internal subclass
`SkImageFilter_Base` (`src/core/SkImageFilter_Base.h`). Every concrete
filter overrides:

```cpp
skif::FilterResult onFilterImage(const skif::Context& context) const;

skif::LayerSpace<SkIRect> onGetInputLayerBounds(
        const skif::Mapping&, const skif::LayerSpace<SkIRect>& desiredOutput,
        std::optional<skif::LayerSpace<SkIRect>> contentBounds) const;

std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
        const skif::Mapping&,
        std::optional<skif::LayerSpace<SkIRect>> contentBounds) const;
```

`skif::Context` (in `src/core/SkImageFilterTypes.h`) carries the input
backend, the source image, the desired-output region, the destination
colour space and surface props, the shared `SkImageFilterCache`, and
helpers for spawning child filters in their own coordinate spaces. The
strongly-typed `ParameterSpace<T>` / `LayerSpace<T>` / `DeviceSpace<T>`
wrappers prevent the common bug of feeding a layer-space rect into a
parameter-space API by mistake; conversions go through the `Mapping`
object.

The `skif::FilterResult` return type wraps a single special image plus a
device-space origin; it can also be empty (transparent black) which is
how short-circuits propagate.

### `filterBounds` & friends — bounds propagation

Every primitive must answer two questions:
1. *Backward bounds* — given the desired output rect, what input
   rect do I need? Drives the offscreen buffer that the canvas allocates
   when restoring a saveLayer with a filter on the paint.
2. *Forward bounds* — given the input rect that will exist, what output
   rect can I produce? Drives clip / damage tracking.

For most filters the two functions are duals: blur outsets by `~3*sigma`
in both directions; offset translates by ±`(dx, dy)`; matrix-transform
maps through the matrix; crop intersects with the crop rect (decal mode).
Picture / Image leaves report a fixed input bounds (their own size) and
ignore the input bounds in their reverse computation.

### Composition helpers

`SkImageFilters::Compose(outer, inner)` — pipe `inner`'s output into
`outer`'s input.
`SkImageFilters::Merge(filters[])` — draw each filter's output in order
with `kSrcOver`. The `Merge` factory has both a 2-arg overload and an
`N`-arg overload.
`SkImageFilters::Blend(mode|blender, background, foreground)` and the older
`Arithmetic(k1..k4, enforcePM, …)` combine two child filter results.
`SkImageFilters::Crop(rect, tileMode, input)` constrains an intermediate
result to a rectangle with one of the four `SkTileMode` semantics for
out-of-rect pixels — historically every filter accepted a bare crop rect,
but modern factories prefer an explicit `Crop` filter so the tiling
behaviour is unambiguous.

### `Empty` / `Image` / `Picture` / `Shader`

These four are the *leaf* image filters — they have no children and
produce content from a constant or stored source.
- `Empty()` — always transparent black.
- `Image(image, srcRect, dstRect, sampling)` — equivalent to drawing the
  image into the layer with `drawImageRect`.
- `Picture(picture, targetRect)` — equivalent to playing back the picture
  into the layer.
- `Shader(shader, dither, cropRect)` — fills the layer using a shader's
  per-pixel evaluation in the local coordinate system; useful as a
  paintable input to a filter graph.

---

## Built-in primitives

### Blur — `src/effects/imagefilters/SkBlurImageFilter.cpp`

`SkImageFilters::Blur(sigmaX, sigmaY, tileMode, input, cropRect)` builds a
filter that applies a separable Gaussian blur. Implementation specifics:

- The kernel half-width is `~3 * sigma` in each direction, and the
  outset is added to the input bounds.
- The tile mode controls what happens at the edge of the input image.
  `kDecal` (the default) treats outside as transparent; `kClamp`,
  `kRepeat`, `kMirror` (still gated; some backends may not support kMirror)
  reuse the edge pixels.
- Tiling-with-no-crop is preserved temporarily for backwards
  compatibility, but new code should pass an explicit `cropRect` when
  using non-decal tile modes.
- The blur kernel itself runs through the `SkBlurEngine`
  (`src/core/SkBlurEngine.h`), which selects between fast box-blur
  approximation, raster two-pass separable Gaussian, or GPU implementations
  depending on backend and sigma. The same engine is used by
  `SkBlurMaskFilterImpl`.

### DropShadow / DropShadowOnly — `SkDropShadowImageFilter.cpp`

These are convenience composites — `make_drop_shadow_graph` builds:

```
crop(merge_or_blend(
    matrix_transform(translate(dx,dy),
        color_filter(blend(color, kSrcIn),
            blur(sigmaX, sigmaY, input))),
    input))
```

For `DropShadowOnly` the merge step is skipped so only the offset blur is
emitted. With `SK_LEGACY_BLEND_FOR_DROP_SHADOWS` the shadow is composited
onto the input via `kSrcOver`; otherwise `Merge` is used for slightly
better performance because it avoids evaluating the blend shader for the
full bounding union. Drop shadow is therefore a *constructed graph*, not
a dedicated `SkImageFilter` subclass — old SKPs still deserialise the
legacy `SkDropShadowImageFilter` flattenable through
`legacy_drop_shadow_create_proc`, which then re-builds the same DAG.

### DisplacementMap — `SkDisplacementMapImageFilter.cpp`

`SkImageFilters::DisplacementMap(xCh, yCh, scale, displacement, color, crop)`
moves each pixel of the *color* input by an offset read from the
*displacement* input. The two channel selectors pick which RGBA channel
encodes X and Y; the offset is `scale * (sample[xCh] - 0.5, sample[yCh] - 0.5)`.

The required-input rect for `color` is outset by `±scale/2` to cover the
maximum possible displacement; `displacement` is required at full
resolution over the desired output. Displacement uses
`SkSamplingOptions{SkFilterMode::kNearest}` for the displacement map (to
match historical behaviour) but allows any sampling for the color input.
Internally the implementation is a runtime effect (the SkSL is held in
`SkKnownRuntimeEffects`) parameterised by the channel selectors and scale.

### Lighting — `SkLightingImageFilter.cpp`

The four `*LitDiffuse` / `*LitSpecular` factories produce filters that
treat the input alpha as a height map and shade with one of three light
sources:

- `Distant` — directional light, parameterised by a 3D direction vector.
- `Point` — light at a 3D location.
- `Spot` — point light constrained to a cone defined by `target`,
  `cutoffAngle`, and `falloffExponent`.

Diffuse uses Lambertian shading (`kd * (N · L)`), specular uses Phong
(`ks * (N · H)^shininess`). The `surfaceScale` parameter converts the
0..1 alpha into world-space heights before sampling normals via finite
differencing. Z coordinates are mapped through the CTM scale (see the
`ZValue` `LayerSpace` specialisation at the top of the file). All six
factories share an internal `SkLightingImageFilter` template
parameterised on light type and shading model. The rendering uses a
known runtime effect under the hood; the same code is reused by
`SkEmbossMaskFilter::asImageFilter`.

### Morphology — `SkMorphologyImageFilter.cpp`

`SkImageFilters::Dilate(rx, ry, input, crop)` and `Erode(rx, ry, ...)`
implement separable max/min filters of half-radius `(rx, ry)` per axis.
Radius is capped at 256 pixels (crbug.com/1123035); larger requests would
otherwise produce extremely slow draws. The implementation is a known
runtime effect that reads neighbouring pixels and accumulates a min or
max per channel.

### MatrixConvolution — `SkMatrixConvolutionImageFilter.cpp`

`SkImageFilters::MatrixConvolution(kernelSize, kernel[], gain, bias,
kernelOffset, tileMode, convolveAlpha, input, crop)` applies an arbitrary
NxM image-processing kernel — used for sharpen, edge-detection, and
custom filters. `convolveAlpha=false` keeps alpha untouched (only RGB is
convolved), which matches the SVG `feConvolveMatrix` semantics. Kernels
larger than `MatrixConvolutionImageFilter::kMaxKernelSize` (currently
121 entries) are split via cooperative tiling.

### MatrixTransform — `SkMatrixTransformImageFilter.cpp`

`SkImageFilters::MatrixTransform(matrix, sampling, input)` applies an
arbitrary `SkMatrix` to the local space *before* the canvas's own
transform. Equivalent to wrapping the input in a layer that pre-applies
the matrix; used by drop shadow's offset step and by SVG `feOffset`.

### Magnifier — `SkMagnifierImageFilter.cpp`

`SkImageFilters::Magnifier(lensBounds, zoomAmount, inset, sampling, input, crop)`
fills `lensBounds` with a magnified version of the input, with a fish-eye
falloff zone of width `inset` around the edge. Used to implement the
`-webkit-magnifier` style and certain accessibility magnifiers.

### Offset — `SkBlurImageFilter.cpp` / shared helpers

`SkImageFilters::Offset(dx, dy, input)` is a thin wrapper that wraps its
input in a `MatrixTransform(SkMatrix::Translate(dx, dy), kLinear, input)`.
Linear sampling is used to hide nearest-neighbour artifacts at fractional
offsets, especially when the offset is post-blur in a drop-shadow graph.

### ColorFilter — `SkColorFilterImageFilter.cpp`

`SkImageFilters::ColorFilter(cf, input, crop)` runs an `SkColorFilter`
over each pixel of the input. The factory short-circuits when the input
itself is a colour-filter node and the two filters compose into one
(`isColorFilterNode` returns the inner filter; the two are folded into a
single `SkColorFilter::makeComposed`). This is the canonical way to
inject a colour-filter into an image-filter DAG and is the building block
of `DropShadow` (a blur + a colour-filter that drops the rgb to the
shadow colour).

### Crop / Compose / Merge / Blend / Arithmetic

These graph-shape primitives have already been described under
"Composition helpers" above. Their implementations are correspondingly
small — they exist mostly to model the right bounds and to forward
`onFilterImage` to the appropriate child or composed result.

### RuntimeShader — `SkRuntimeImageFilter.cpp`

`SkImageFilters::RuntimeShader(builder, sampleRadius, childShaderName(s),
input(s))` evaluates a user-authored SkSL shader pixel-by-pixel,
substituting each named child shader with the corresponding `input`
filter (or the layer source if null). Used to inject custom SkSL into the
filter graph; see [Runtime Effects](runtime-effects.md). The
`maxSampleRadius` informs the bounds machinery how far the SkSL might
sample from its output coordinate.

The implementation forces `MatrixCapability::kTranslate` because there is
currently no way to declare which uniforms are geometric and so should
respond to canvas scaling — this is documented as skbug.com/40044507.

---

## SkMaskFilter — `include/core/SkMaskFilter.h`

`SkMaskFilter` operates on the rasteriser's `SkMask` directly. The base
class declares only one factory:

```cpp
static sk_sp<SkMaskFilter> MakeBlur(SkBlurStyle style, SkScalar sigma,
                                    bool respectCTM = true);
```

`SkBlurStyle` (in `include/core/SkBlurTypes.h`) selects how the blurred
mask is combined with the original:

| Style | Effect |
|---|---|
| `kNormal_SkBlurStyle` | Fuzzy inside and outside (a true Gaussian blur). |
| `kSolid_SkBlurStyle`  | Solid inside, fuzzy outside (drop-shadow look with the source on top). |
| `kOuter_SkBlurStyle`  | Nothing inside, fuzzy outside (the halo only). |
| `kInner_SkBlurStyle`  | Fuzzy inside, nothing outside (an inset glow). |

`respectCTM=true` (the default) scales `sigma` by the CTM so the blur is
in *local* space and looks the same regardless of how the canvas is
zoomed. `respectCTM=false` keeps `sigma` in device pixels — useful for
fixed-pixel shadows in zoomable UIs.

### SkMaskFilterBase — `src/core/SkMaskFilterBase.h`

The internal subclass adds:

- `SkMask::Format getFormat()` — `kA8`, `k3D`, `kLCD16`, etc.
- `bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix& ctm,
                   SkIPoint* margin)` — produce the filtered mask. `margin`
  reports how much the filter dilates the bounds.
- `Type type()` — one of `{kBlur, kEmboss, kSDF, kShader, kTable}`.
- `bool asABlur(BlurRec*)` — does this filter look like a single Gaussian
  blur, and if so what are the sigma and style? Used by `SkPaint::asABlur`
  and certain GPU fast-paths.
- `std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(ctm, paint)` —
  return an equivalent image-filter DAG. The boolean reports whether the
  image-filter representation also applies the paint's shading.

`filterPath`, `filterRRect`, and `filterRects` are the internal entry
points called by the rasteriser (`skcpu::Draw`) to apply the mask
filter to a freshly-rasterised mask. Implementations may also override
`filterRectsToNine` / `filterRRectToNine` to produce a small ninepatch
mask instead of a full rectangle, since stretched ninepatches are both
faster to render and easier to cache.

### Blur — `src/core/SkBlurMaskFilterImpl.{h,cpp}`

`SkMaskFilter::MakeBlur` returns a `SkBlurMaskFilterImpl` (the public
constructor accepts only `SkBlurStyle`, `sigma`, and the CTM-respecting
flag). The implementation:

- Uses the shared `SkBlurMask` / `SkBlurEngine` to actually blur the A8
  mask. `SkBlurMask::ComputeBlurredScanline` and
  `BoxBlur` provide the CPU implementation; the GPU has a Gaussian-shader
  fast-path.
- Implements the four blur styles by combining the blurred mask with the
  unblurred mask (or vice versa) using simple dst-in / src-over /
  src-out / dst-out compositing — see `asImageFilter` for the exact
  compositions.
- Supports `BlurRec` (`asABlur`) so callers can detect the blur and
  potentially merge it with another effect or take a different code path.
- `asImageFilter` constructs an equivalent
  `SkImageFilters::Blur(sigma, sigma, nullptr) + Blend(...)` graph; this
  is what `SkCanvas::saveLayer` uses when restoring a paint that has
  both a blur mask filter and a shader, so the blur is applied on the
  shaded image rather than on the bare coverage mask.

### Emboss — `src/effects/SkEmbossMaskFilter.{h,cpp}` + `src/effects/SkEmbossMask.{h,cpp}`

Creates a 3D embossed-look effect by treating the alpha mask as a height
field, computing surface normals, and applying ambient + specular
highlights from a directional light. Constructed via
`SkEmbossMaskFilter::Make(blurSigma, Light{direction[3], ambient, specular})`.
The light direction is normalised in the factory; `ambient` is in [0, 1]
(stored as 8.0 fixed) and `specular` is the Phong exponent in 4.4
fixed-point.

The filter blurs the input mask with `SkBlurMask::Blur` (the same engine
as the blur mask filter), then walks the blurred image computing per-pixel
normals via central differences and writing both the original alpha and
the lighting result into a `kSk3D_Format` mask (one channel for alpha,
three for the multiplier RGB). The blitter is responsible for combining
the multiplier with the paint's color.

`SkBlurMaskFilter::MakeEmboss` (in `include/effects/SkBlurMaskFilter.h`)
is a deprecated convenience wrapper kept behind
`SK_SUPPORT_LEGACY_EMBOSSMASKFILTER`; it converts the `direction[3] +
ambient + specular` parameters into the `Light` struct.

### Shader-mask — `src/effects/SkShaderMaskFilterImpl.{h,cpp}`

`SkShaderMaskFilter::Make(shader)` (deprecated) wraps an `SkShader` as a
mask: each pixel of the mask is multiplied by the shader's *alpha* at
that location. Implementation-wise, `filterMask` iterates the source
mask, allocates a new A8 destination, and asks the shader to produce
PMColor4f values whose alpha is then multiplied in. `asImageFilter`
returns a `Shader` image filter combined with the original coverage via
`kDstIn`. This is how (e.g.) you can clip text rendering by an arbitrary
shader. Marked deprecated because the same effect is cleaner to express
as a shader on a layer.

### Table — `src/effects/SkTableMaskFilter.cpp`

Deprecated. Three factories:

- `Create(table)` — arbitrary 256-entry alpha lookup (`a' = table[a]`).
- `CreateGamma(gamma)` — pre-fills `table[i] = pow(i/255, 1/gamma) * 255`.
- `CreateClip(min, max)` — sets values below `min` to 0, values above
  `max` to 255, and rescales the rest into [0, 255].

`MakeGammaTable` / `MakeClipTable` are exposed as utilities so callers
can build the table once and pass it to `Create`. Marked deprecated
because the same effect is achievable with a shader-based color filter.

---

## How the canvas wires things together

For a paint with both a mask filter and an image filter on a draw call:

1. The rasteriser produces an A8 coverage mask for the path / glyph / rect.
2. If `SkMaskFilter` is present, `filterMask` rewrites the coverage.
3. The blitter combines the (possibly filtered) coverage with the paint's
   shader to produce a temporary RGBA image — *unless* the paint has an
   image filter, in which case the rasteriser draws into a
   `SkCanvas::saveLayer`-equivalent offscreen buffer.
4. If `SkImageFilter` is present, `filterImage` is invoked on the layer's
   image and the resulting `skif::FilterResult` is composited onto the
   parent surface using the paint's blend mode.

The conversion `SkMaskFilterBase::asImageFilter` lets step 2 sometimes be
folded into step 4 — particularly for blurs, since blurring the coverage
mask alone gives a different (typically wrong) result when the paint has
a shader. See [Paint, Color & Blending](paint-color-and-blending.md) for
the paint-side view of this pipeline.

For per-backend specifics, see [CPU Rendering Pipeline](cpu-rendering-pipeline.md),
[Ganesh Backend](ganesh-backend.md) and [Graphite Backend](graphite-backend.md).
