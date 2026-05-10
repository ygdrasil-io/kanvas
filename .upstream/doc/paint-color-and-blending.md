# Paint, Color & Blending

`SkPaint` is the configuration object passed to almost every draw call. It
collects everything *outside* the canvas's matrix and clip — color, stroke
parameters, antialiasing, and a stack of effects (`SkShader`, `SkColorFilter`,
`SkPathEffect`, `SkMaskFilter`, `SkImageFilter`, `SkBlender`) that are
applied to the rasterized geometry.

```
            geometry         clip          matrix
                │              │             │
                └──────────────┼─────────────┘
                               │
                               ▼
              ┌──────────── SkCanvas::draw* ────────────┐
              │                                          │
              │  SkPaint                                 │
              │  ├── color (SkColor4f, SkColorSpace*)    │
              │  ├── style (Fill / Stroke / both)        │
              │  ├── stroke width, miter, cap, join      │
              │  ├── antialias, dither                   │
              │  ├── sk_sp<SkPathEffect>   (geometry)    │
              │  ├── sk_sp<SkShader>       (color src)   │
              │  ├── sk_sp<SkMaskFilter>   (mask)        │
              │  ├── sk_sp<SkColorFilter>  (per-pixel)   │
              │  ├── sk_sp<SkImageFilter>  (CPU/GPU fx)  │
              │  └── sk_sp<SkBlender>      (compose)     │
              └──────────────────────────────────────────┘

   geometry → (PathEffect) → (Stroke) → coverage mask
                                     │
   color     → (Shader)              ▼
            → (ColorFilter)   →  multiply by coverage
            → (Blender → dst)
                ↓
   (ImageFilter wraps the whole layer)
```

Every effect slot is a `sk_sp<...>` to a refcounted, flattenable object;
`nullptr` means "no effect" and is the fast path. None of the effects are
mutable after creation, so `SkPaint`'s copy semantics are intentionally
shallow.

---

## Header / source map

| Header | Implementation | Purpose |
|---|---|---|
| `include/core/SkPaint.h` | `src/core/SkPaint.cpp`, `SkPaintPriv.cpp` | The paint itself. |
| `include/core/SkColor.h` | `src/core/SkColor.cpp` | `SkColor` (8-bit unpremul ARGB), `SkColor4f` (float unpremul RGBA), `SkPMColor` (premul, byte-order matches `kBGRA_8888`), HSV helpers, `SkAlpha`. |
| `include/core/SkColorType.h` | (header-only enum) | Pixel layouts; covered in [bitmap-pixmap-image.md](bitmap-pixmap-image.md). |
| `include/core/SkAlphaType.h` | (header-only enum) | `kOpaque_SkAlphaType`, `kPremul_SkAlphaType`, `kUnpremul_SkAlphaType`, `kUnknown_SkAlphaType`. |
| `include/core/SkColorFilter.h` | `src/core/SkColorFilter.cpp`, `SkColorFilter_Matrix.cpp`, `src/effects/SkColorMatrix.cpp` | Color-filter base + `SkColorFilters` factories. |
| `include/core/SkBlendMode.h` | `src/core/SkBlendMode.cpp` | Porter-Duff and HSL blend modes. |
| `include/core/SkBlender.h` | `src/core/SkBlendModeBlender.cpp`, `src/core/SkBlenderBase.h` | Pluggable blender — wraps a blend mode or a runtime effect. |
| `include/core/SkCoverageMode.h` | header-only enum | Mask-on-mask combinator (Union, Intersect, Difference, ReverseDifference, Xor). |
| `include/core/SkUnPreMultiply.h` | `src/core/SkColor.cpp` | Lookup table for fast unpremultiplication. |

---

## SkPaint

### Construction and copy semantics

```c++
SkPaint p;                               // default white, fill, no AA
SkPaint p2(SkColor4f{1,0,0,1}, nullptr); // sRGB red
SkPaint p3 = p;                          // shallow copy; effect refs share
```

`SkPaint` is `sk_is_trivially_relocatable`. Copy and move constructors are
provided; the copy bumps the refcount on each effect, the move steals them.
`operator==` is reference-equality on the effect pointers (not deep
content equality) — distinct shaders that compute identical results will
compare unequal.

`reset()` returns the paint to its default state (alpha 1, white,
fill style, no effects, no AA, no dither).

### Drawing flags

| Flag | Default | Setter / Getter |
|---|---|---|
| Antialiasing | off | `setAntiAlias(bool)` / `isAntiAlias()` |
| Dithering | off | `setDither(bool)` / `isDither()` |

Anti-aliasing makes edge pixels partially transparent; dithering distributes
quantization error across nearby pixels (only effective on low-bit-depth
targets, primarily `kRGB_565` and `kARGB_4444`).

### Style: fill, stroke, or both

```c++
enum Style : uint8_t {
    kFill_Style,           // fill the geometry
    kStroke_Style,         // outline only
    kStrokeAndFill_Style,  // both — single pass; saves one rasterization
};
static constexpr int kStyleCount = kStrokeAndFill_Style + 1;
```

`getStyle()`, `setStyle(Style)`, and the convenience `setStroke(bool)`
(which selects `kStroke_Style` or `kFill_Style`).

### Stroke parameters

| Property | Default | Setter / Getter |
|---|---|---|
| Stroke width | 0 (hairline) | `setStrokeWidth(SkScalar)` / `getStrokeWidth()` |
| Miter limit | 4 | `setStrokeMiter(SkScalar)` / `getStrokeMiter()` |
| Cap | `kButt_Cap` | `setStrokeCap(Cap)` / `getStrokeCap()` |
| Join | `kMiter_Join` | `setStrokeJoin(Join)` / `getStrokeJoin()` |

`Cap` (drawn at open path-contour endpoints) and `Join` (drawn at corners
between segments) enums:

| `Cap` | Drawing |
|---|---|
| `kButt_Cap` | No extension. |
| `kRound_Cap` | Half-circle of stroke width. |
| `kSquare_Cap` | Square of side = stroke width. |

| `Join` | Drawing |
|---|---|
| `kMiter_Join` | Sharp extension of the two edges; clamped by the miter limit (`miterLength = 1 / sin(angle/2)`). Above the limit it falls back to bevel. |
| `kRound_Join` | Quarter-circle. |
| `kBevel_Join` | Straight connector across the outside of the corner. |

A *zero* stroke width is "hairline" — exactly one device pixel wide,
unaffected by the canvas matrix. Negative widths are silently rejected.

### Color

A paint stores its color as `SkColor4f` (float RGBA, unpremultiplied) plus
an internal `SkColorSpace` pointer (held externally; the color in the paint
is interpreted in this space).

```c++
SkPaint p;
p.setColor(SK_ColorRED);                              // sRGB unpremul ARGB byte
p.setColor(SkColor4f{0, 1, 0, 1}, nullptr);           // sRGB float (cs=null → sRGB)
p.setColor(SkColor4f{1, 0, 0, 1}, displayP3.get());   // P3 float
SkColor c4 = p.getColor();                            // 32-bit unpremul ARGB
SkColor4f c4f = p.getColor4f();                       // float unpremul
float a = p.getAlphaf();
p.setAlphaf(0.5f);
p.setARGB(0xff, 0, 128, 255);
```

`setAlpha(U8CPU)` is a `setAlphaf(a / 255.0f)` convenience.

If a `SkShader` is set, the paint color is overridden — except its alpha,
which still scales the shader output.

### Effect slots

All six effect slots follow the same pattern:

```c++
SkBlender*       getBlender() const;                  // raw, no ref
sk_sp<SkBlender> refBlender() const;                  // bumps ref
void             setBlender(sk_sp<SkBlender>);        // takes ownership
```

For path effects, shaders, mask filters, color filters, and image filters
the corresponding methods are `getPathEffect`/`refPathEffect`/`setPathEffect`,
etc.

| Slot | Type | What it does | When applied |
|---|---|---|---|
| `SkPathEffect` | `sk_sp<SkPathEffect>` | Mutates the geometry (dashing, corner rounding, trim, 1-D pattern, 2-D pattern). | Before stroke conversion. |
| `SkShader` | `sk_sp<SkShader>` | Source of color (gradient, image, runtime SkSL). | Per fragment, replaces paint color. |
| `SkMaskFilter` | `sk_sp<SkMaskFilter>` | Operates on coverage mask (blur, table, emboss, gradient, shader-mask). | After geometry → mask conversion. |
| `SkColorFilter` | `sk_sp<SkColorFilter>` | Pixel-by-pixel color rewrite (matrix, blend, table, runtime). | After shader, before blender. |
| `SkImageFilter` | `sk_sp<SkImageFilter>` | Wraps the whole draw in an off-screen pass (drop shadow, blur, displacement, runtime). | Whole-layer; forces an implicit `saveLayer`. |
| `SkBlender` | `sk_sp<SkBlender>` | How the paint's pixels combine with the destination. | Last; overrides the implicit `kSrcOver`. |

Each is documented in its own page:

- [`SkShader`](shaders.md) — gradients, image shaders, runtime shaders.
- [`SkPathEffect`](path-effects.md).
- [`SkMaskFilter`, `SkImageFilter`](image-filters-and-mask-filters.md).
- [`SkColorFilter` and `SkBlender`](#skcolorfilter--skblender) — see below.

### `nothingToDraw`, `canComputeFastBounds`, `computeFastBounds`

- `nothingToDraw()` — true if the paint provably contributes nothing
  (alpha 0 + `kSrcOver`-equivalent blender, etc.). Skia uses this to early-out
  draws that would be entirely transparent.
- `canComputeFastBounds()` — false when a `SkPathEffect` (or otherwise
  expensive effect) is set; bounds need a full geometry walk in that case.
- `computeFastBounds(orig, *storage)` and `doComputeFastBounds(orig,
  *storage, style)` — adjust `orig` for stroke width, mask filter, image
  filter expansion. Pass the result to `SkCanvas::quickReject` to skip
  no-op draws cheaply.

These are flagged "to be made private" in the public header; treat them as
implementation hooks for the rendering pipeline.

### Storage layout

Paint is a small POD-like (modulo refcounted pointers). Layout (from
`SkPaint.h`):

```c++
sk_sp<SkPathEffect>   fPathEffect;
sk_sp<SkShader>       fShader;
sk_sp<SkMaskFilter>   fMaskFilter;
sk_sp<SkColorFilter>  fColorFilter;
sk_sp<SkImageFilter>  fImageFilter;
sk_sp<SkBlender>      fBlender;
SkColor4f             fColor4f;
SkScalar              fWidth;
SkScalar              fMiterLimit;
union {
    struct {
        unsigned fAntiAlias : 1;
        unsigned fDither    : 1;
        unsigned fCapType   : 2;
        unsigned fJoinType  : 2;
        unsigned fStyle     : 2;
        unsigned fPadding   : 24;
    } fBitfields;
    uint32_t fBitfieldsUInt;
};
```

All `sk_sp` members are statically asserted to be trivially relocatable, so
`SkPaint` itself is — making `std::vector<SkPaint>` (and Skia's `SkTArray`)
re-allocations cheap.

---

## Color types

### SkAlpha

8-bit alpha: `SK_AlphaTRANSPARENT = 0x00`, `SK_AlphaOPAQUE = 0xFF`.

### SkColor — 32-bit unpremultiplied ARGB

```c++
typedef uint32_t SkColor;
static constexpr SkColor SkColorSetARGB(U8CPU a, U8CPU r, U8CPU g, U8CPU b);
#define SkColorSetRGB(r, g, b)   SkColorSetARGB(0xFF, r, g, b)
#define SkColorGetA(c)           (((c) >> 24) & 0xFF)
#define SkColorGetR(c)           (((c) >> 16) & 0xFF)
#define SkColorGetG(c)           (((c) >>  8) & 0xFF)
#define SkColorGetB(c)           (((c) >>  0) & 0xFF)
SkColor SkColorSetA(SkColor c, U8CPU a);
```

`SkColor` is **always** in the same byte order (A R G B from bits 31–0), is
**always** unpremultiplied, and is implicitly in **sRGB** unless paired with
a `SkColorSpace`. `SkColor` is the type for paint and gradient stops.

Common constants: `SK_ColorTRANSPARENT`, `SK_ColorBLACK`, `SK_ColorDKGRAY`,
`SK_ColorGRAY`, `SK_ColorLTGRAY`, `SK_ColorWHITE`, `SK_ColorRED`,
`SK_ColorGREEN`, `SK_ColorBLUE`, `SK_ColorYELLOW`, `SK_ColorCYAN`,
`SK_ColorMAGENTA`.

### SkPMColor — 32-bit premultiplied

```c++
typedef uint32_t SkPMColor;
SK_API SkPMColor SkPreMultiplyARGB(U8CPU a, U8CPU r, U8CPU g, U8CPU b);
SK_API SkPMColor SkPreMultiplyColor(SkColor c);
```

The byte order of `SkPMColor` matches `kN32_SkColorType`, so on little-endian
systems with `kBGRA_8888` it's `[A R G B]` from the low byte up; on systems
configured for `kRGBA_8888` it's `[A B G R]`. Always premultiplied. The
bridge between unpremultiplied `SkColor` and premultiplied pixels.

`SkUnPreMultiply` (`include/core/SkUnPreMultiply.h`) is the reverse:

```c++
const SkUnPreMultiply::Scale* table = SkUnPreMultiply::GetScaleTable();
unsigned a = SkColorGetA(pmcolor);
SkUnPreMultiply::Scale scale = table[a];
unsigned r = SkUnPreMultiply::ApplyScale(scale, redByte);
SkColor c = SkUnPreMultiply::PMColorToColor(pmcolor);
```

### SkRGBA4f / SkColor4f — float per channel

```c++
template <SkAlphaType kAT>
struct SkRGBA4f {
    float fR, fG, fB, fA;
    // … operators, vec(), array(), isOpaque(), fitsInBytes(),
    // pinAlpha(), withAlpha(a), withAlphaByte(byte), makeOpaque(),
    // operator*(scale), operator==/!=
    SkRGBA4f<kPremul_SkAlphaType>   premul()   const;     // requires kUnpremul
    SkRGBA4f<kUnpremul_SkAlphaType> unpremul() const;     // requires kPremul
    static SkRGBA4f FromColor(SkColor);
    SkColor          toSkColor() const;
    static SkRGBA4f FromPMColor(SkPMColor);
    uint32_t         toBytes_RGBA() const;
    static SkRGBA4f FromBytes_RGBA(uint32_t);
};
using SkColor4f = SkRGBA4f<kUnpremul_SkAlphaType>;
```

Skia's *public* color type is `SkColor4f` — the unpremultiplied
specialization. Premultiplied colors only show up internally (as
`SkRGBA4f<kPremul_SkAlphaType>`).

The compile-time `kAT` distinction prevents accidentally premultiplying a
color twice (`premul()` requires `kUnpremul`, `unpremul()` requires
`kPremul`).

The `SkColors::` namespace provides float constants matching the SkColor
constants: `kTransparent`, `kBlack`, `kDkGray`, `kGray`, `kLtGray`, `kWhite`,
`kRed`, `kGreen`, `kBlue`, `kYellow`, `kCyan`, `kMagenta`.

### SkColorChannel / SkColorChannelFlag

```c++
enum class SkColorChannel { kR, kG, kB, kA, kLastEnum = kA };

enum SkColorChannelFlag : uint32_t {
    kRed_SkColorChannelFlag    = 1 << kR,
    kGreen_SkColorChannelFlag  = 1 << kG,
    kBlue_SkColorChannelFlag   = 1 << kB,
    kAlpha_SkColorChannelFlag  = 1 << kA,
    kGray_SkColorChannelFlag   = 0x10,
    kGrayAlpha_SkColorChannelFlags = kGray  | kAlpha,
    kRG_SkColorChannelFlags        = kRed   | kGreen,
    kRGB_SkColorChannelFlags       = kRG    | kBlue,
    kRGBA_SkColorChannelFlags      = kRGB   | kAlpha,
};
```

Used by `SkColorType` and texture-format introspection (e.g. which channels
are present in `kR8_unorm_SkColorType`).

### HSV conversion

```c++
void SkRGBToHSV(U8CPU r, U8CPU g, U8CPU b, SkScalar hsv[3]);
void SkColorToHSV(SkColor color, SkScalar hsv[3]);
SkColor SkHSVToColor(U8CPU alpha, const SkScalar hsv[3]);
SkColor SkHSVToColor(const SkScalar hsv[3]);   // alpha = 0xFF
```

`hsv[0]` is `[0, 360)`, `hsv[1]` and `hsv[2]` are `[0, 1]`. Out-of-range
values are pinned.

### SkAlphaType

`include/core/SkAlphaType.h`:

| Value | Meaning |
|---|---|
| `kUnknown_SkAlphaType` | Uninitialized / invalid. |
| `kOpaque_SkAlphaType` | Hint that all alpha values are 1.0; allows fast paths. Drawing a non-opaque pixel through opaque info is undefined. |
| `kPremul_SkAlphaType` | RGB has been multiplied by alpha (Skia's standard internal representation). |
| `kUnpremul_SkAlphaType` | RGB is independent of alpha. Used at I/O boundaries (encoders, `SkPixmap::getColor`). |

`SkAlphaTypeIsOpaque(at)` returns `at == kOpaque_SkAlphaType`.

For details on how `SkAlphaType` interacts with `SkColorType` and
`SkImageInfo`, see [bitmap-pixmap-image.md](bitmap-pixmap-image.md).

---

## SkColorFilter & SkBlender

### SkColorFilter

`SkColorFilter` operates on the source color (the result of the paint's
shader, or the paint's solid color) and emits a new color, before the
blender combines it with the destination.

Subclasses are reentrant-safe — Skia can share one filter across threads.

Public methods on `SkColorFilter`:

- `asAColorMode(SkColor* color, SkBlendMode* mode)` — true if equivalent to
  a single source-color blend.
- `asAColorMatrix(float[20])` — true if expressible as a 5×4 matrix.
- `isAlphaUnchanged()` — true if the filter never modifies alpha.
- `filterColor4f(srcColor, srcCS, dstCS)` — convert + filter + return in
  destination color space.
- `makeComposed(inner)` — `outer(inner(...))`.
- `makeWithWorkingColorSpace(cs)` — run the filter math in a specific color
  space, regardless of the destination surface's color space. Useful for
  matrix and runtime filters that depend on a known working space.
- `Deserialize(data, size, procs)` — deserialize a flattened color filter.

Built-in factories live in `SkColorFilters` (final, all-static):

| Factory | Behavior |
|---|---|
| `Compose(outer, inner)` | Same as `outer->makeComposed(inner)`, with a null-handling shortcut. |
| `Blend(SkColor4f c, sk_sp<SkColorSpace> cs, SkBlendMode m)` / `Blend(SkColor c, SkBlendMode m)` | Blend a constant source color over the input. |
| `Matrix(SkColorMatrix[, Clamp])` / `Matrix(float[20], [, Clamp])` | 5×4 RGBA matrix. `Clamp::kYes` (default) clips the output to `[0,1]`. |
| `HSLAMatrix(SkColorMatrix)` / `HSLAMatrix(float[20])` | Same matrix in HSLA space (`HSLA → matrix → RGBA`). |
| `LinearToSRGBGamma()` / `SRGBToLinearGamma()` | Pure gamma conversion. |
| `Lerp(t, dst, src)` | Per-channel `mix(dst, src, t)`. |
| `Table(uint8_t[256])` | Single 256-entry lookup applied to all four channels (in unpremul space). |
| `TableARGB(tA[256], tR[256], tG[256], tB[256])` | Per-channel; null channels are identity. |
| `Table(sk_sp<SkColorTable>)` | Reference-counted shared table (see [bitmap-pixmap-image.md](bitmap-pixmap-image.md#skcolortable)). |
| `Lighting(SkColor mul, SkColor add)` | Multiply RGB by `mul`, add `add`, pin to `[0, 255]`. Alpha of the args is ignored. |

`SkColorMatrix` (`include/effects/SkColorMatrix.h`) is the canonical 5×4
matrix value type — `setIdentity`, `setSaturation(s)`, `setRowMajor(...)`,
`postConcat(...)`, etc.

For pixel-shader-style runtime color filters, see
[runtime-effects.md](runtime-effects.md) (`SkRuntimeEffect::ColorFilter`).

### SkBlender

`SkBlender` is the pluggable substitute for `SkBlendMode` in
`SkPaint::setBlender`. The default (null blender) is equivalent to
`SkBlendMode::kSrcOver`.

```c++
class SK_API SkBlender : public SkFlattenable {
public:
    static sk_sp<SkBlender> Mode(SkBlendMode mode);  // wrap a built-in mode
private:
    SkBlender() = default;
    friend class SkBlenderBase;
};
```

`SkBlender::Mode(SkBlendMode)` produces a flattenable wrapper around a
built-in mode. Runtime SkSL blenders come from
`SkRuntimeEffect::Blender::makeBlender` (see
[runtime-effects.md](runtime-effects.md)).

`SkPaint::asBlendMode()` returns `std::optional<SkBlendMode>` if the current
blender simplifies to a built-in mode; `getBlendMode_or(default)` returns
that value or the supplied fallback. `isSrcOver()` is true for null and for
explicitly `kSrcOver` blenders. `setBlendMode(mode)` is a convenience for
`setBlender(SkBlender::Mode(mode))`.

---

## SkBlendMode

`include/core/SkBlendMode.h`. The 30 built-in compositing modes Skia
supports. Documentation uses the abbreviations `s` = source, `d` =
destination, `sa` = source alpha, `da` = destination alpha; `r` = result
when all four channels share the formula, `rc` = color channels, `ra` =
alpha.

### Porter-Duff (coefficient-based)

These are the modes for which `SkBlendMode_AsCoeff(mode, *src, *dst)` returns
true; their coefficient pair fits into the GPU blend hardware directly.

| Mode | Formula |
|---|---|
| `kClear` | `r = 0` |
| `kSrc` | `r = s` |
| `kDst` | `r = d` |
| `kSrcOver` | `r = s + (1−sa) * d` (the default) |
| `kDstOver` | `r = d + (1−da) * s` |
| `kSrcIn` | `r = s * da` |
| `kDstIn` | `r = d * sa` |
| `kSrcOut` | `r = s * (1−da)` |
| `kDstOut` | `r = d * (1−sa)` |
| `kSrcATop` | `r = s * da + d * (1−sa)` |
| `kDstATop` | `r = d * sa + s * (1−da)` |
| `kXor` | `r = s * (1−da) + d * (1−sa)` |
| `kPlus` | `r = min(s + d, 1)` |
| `kModulate` | `r = s * d` |
| `kScreen` | `r = s + d − s*d` *(also the last `kLastCoeffMode`)* |

`SkBlendModeCoeff` enumerates the possible coefficient values: `kZero`,
`kOne`, `kSC`, `kISC`, `kDC`, `kIDC`, `kSA`, `kISA`, `kDA`, `kIDA`. The
free function `SkBlendMode_AsCoeff(mode, *src, *dst)` writes the coefficient
pair for any Porter-Duff mode.

### Separable advanced modes

These operate per-channel but with non-coefficient formulas (typically the
SVG/W3C compositing modes). `kLastSeparableMode = kMultiply`.

| Mode | Notes |
|---|---|
| `kOverlay` | Multiply or screen, depending on destination luminance. |
| `kDarken` | `rc = s + d − max(s*da, d*sa)`, `ra = SrcOver`. |
| `kLighten` | `rc = s + d − min(s*da, d*sa)`, `ra = SrcOver`. |
| `kColorDodge` | Brighten destination toward source. |
| `kColorBurn` | Darken destination toward source. |
| `kHardLight` | Multiply or screen, depending on source luminance. |
| `kSoftLight` | Lighten or darken, depending on source luminance. |
| `kDifference` | `rc = s + d − 2*min(s*da, d*sa)`, `ra = SrcOver`. |
| `kExclusion` | `rc = s + d − two(s*d)`, `ra = SrcOver`. |
| `kMultiply` | `r = s*(1−da) + d*(1−sa) + s*d`. |

### Non-separable HSL modes

Operate on the entire color triple (treating it as HSL); alpha is computed
as `kSrcOver`.

| Mode | Notes |
|---|---|
| `kHue` | Source hue, destination saturation + luminosity. |
| `kSaturation` | Source saturation, destination hue + luminosity. |
| `kColor` | Source hue + saturation, destination luminosity. |
| `kLuminosity` | Source luminosity, destination hue + saturation. |

`kLastMode = kLuminosity`. `kSkBlendModeCount` is the count of valid modes.

`SkBlendMode_Name(mode)` returns the C string name (used by debug printers).

---

## SkCoverageMode

`include/core/SkCoverageMode.h`. Operates only on alpha — i.e. on coverage
masks rather than full RGBA pixels. Used by `SkMaskFilter` composition and
some runtime-effect mask paths.

| Mode | Formula |
|---|---|
| `kUnion` | `A ∪ B = A + B − A*B` |
| `kIntersect` | `A ∩ B = A * B` |
| `kDifference` | `A − B = A * (1 − B)` |
| `kReverseDifference` | `B − A = B * (1 − A)` |
| `kXor` | `A ⊕ B = A + B − 2*A*B` |

---

## How a draw uses everything

For the simple `canvas->drawRect(rect, paint)`:

1. The geometry (rect) is transformed by the canvas matrix.
2. If `paint.getPathEffect()` is set, the rect → path → effected path.
3. If `paint.getStyle() != kFill_Style`, the path is converted to a
   stroked-fill outline using the cap, join, miter, and width.
4. The result is rasterized into a per-pixel coverage value `α ∈ [0, 1]`.
5. If `paint.getMaskFilter()` is set, it transforms the coverage mask
   (e.g. blur).
6. For each output pixel:
   1. Source color `s` ← shader evaluation, or `paint.fColor4f` if no shader.
       The paint's alpha multiplies the result either way.
   2. `s ← colorFilter.filterColor4f(s, ...)` if a `SkColorFilter` is set.
   3. `s' ← s * α` (apply coverage).
   4. `dst[xy] ← blender(s', dst[xy])` — default `SkBlendMode::kSrcOver`,
      otherwise the specified blender.
7. If `paint.getImageFilter()` is set, the entire above pipeline is wrapped
   in an implicit `saveLayer` so the image filter can postprocess the result
   off-screen.

For text and images the geometry-step is different (glyphs are coverage
masks, images are textures), but the color/coverage/blend stages are the
same.

---

## Cross-references

- [`SkColorSpace`](color-management.md) — every paint color and shader is
  interpreted in a color space; `SkColorFilter::makeWithWorkingColorSpace`
  is the API entry to the topic.
- [`SkShader`](shaders.md), [`SkPathEffect`](path-effects.md),
  [`SkMaskFilter` and `SkImageFilter`](image-filters-and-mask-filters.md),
  [`SkRuntimeEffect`](runtime-effects.md) — the effect ecosystem.
- [`SkCanvas`](canvas-and-recording.md) — the consumer of `SkPaint`.
- [`SkBitmap` / `SkPixmap`](bitmap-pixmap-image.md) — for `SkColorType` and
  `SkAlphaType`.
- [`SkSurface`](surface-and-output.md) — the destination, including its
  color space.
