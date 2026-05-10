# Color Management

Skia is a color-managed renderer. Every `SkImage`, `SkSurface`, and `SkPaint`
color carries (or implicitly assumes) an `SkColorSpace`, and Skia automatically
converts between spaces during draws so the output looks correct on a wide-gamut
or HDR target. This page documents the public color-management types
(`SkColorSpace`, gamut, transfer function), the internal pipeline that
threads color spaces through draws (`SkColorSpaceXformSteps`), and where to
find related material.

The actual ICC and transfer-function math lives in a standalone library,
`modules/skcms/`, documented separately in [skcms.md](skcms.md). This page
covers how Skia *uses* those primitives.

```
   client supplies                  Skia decides per-draw         pixels written
   ┌──────────────────┐    ┌──────────────────────────────┐    ┌────────────────┐
   │ SkPaint          │───▶│ SkColorSpaceXformSteps:      │───▶│ surface pixels │
   │  fColor4f + cs   │    │   unpremul / linearize /     │    │ in surface CS  │
   │ SkShader         │    │   src OOTF / gamut transform │    └────────────────┘
   │  carries cs      │    │   / dst OOTF / encode /      │
   │ SkImage          │    │   premul                     │
   │  carries cs      │    └──────────────────────────────┘
   │ SkSurface        │
   │  carries cs      │
   └──────────────────┘
```

---

## Header / source map

| Header / file | Purpose |
|---|---|
| `include/core/SkColorSpace.h` | Public `SkColorSpace`, `SkColorSpacePrimaries`, `SkNamedPrimaries`, `SkNamedTransferFn`, `SkNamedGamut`. |
| `src/core/SkColorSpace.cpp` | Implementation, including `SkColorSpaceSingletonFactory` for sRGB / sRGB-linear singletons. |
| `src/core/SkColorSpacePriv.h` | Internal helpers (predicates, matrix math). |
| `src/core/SkColorSpaceXformSteps.{h,cpp}` | The internal "what conversion sequence does (src, dst) require" planner. |
| `modules/skcms/skcms.h`, `skcms.cc` | Standalone ICC parser + transfer-function evaluator + transform compiler. See [skcms.md](skcms.md). |
| `include/private/SkExif.h`, `SkGainmapInfo.h`, `SkHdrMetadata.h` | Color-related metadata for HDR / gainmap workflows; see [hdr-and-gainmaps.md](hdr-and-gainmaps.md). |
| `include/core/SkColorType.h` | Pixel layouts. Documented in [bitmap-pixmap-image.md](bitmap-pixmap-image.md). |
| `include/core/SkAlphaType.h` | Alpha conventions. Documented in [paint-color-and-blending.md](paint-color-and-blending.md#skalphatype). |

---

## What is a color space, in Skia?

A Skia `SkColorSpace` describes how to interpret RGB values:

1. **Transfer function (TF)** — the per-channel function mapping encoded
   non-linear values to linear-light radiance. Encoded as the standard ICC
   7-parameter equation
   ```
       linear = sign(encoded) *
                ((|encoded|       <  d) ? c * |encoded| + f
                                        : (a * |encoded| + b)^g + e)
   ```
   plus three "named" non-numerical functions: PQ, HLG, and "linear" (gamma 1).
2. **Gamut** — a 3×3 matrix mapping linear RGB into XYZ-D50 (Skia's
   profile-connection space). Equivalently, the chromaticities of the R, G,
   B primaries plus a white point.

Whenever a color crosses a color-space boundary (paint → surface, image →
paint, layer → parent layer), Skia applies the source's TF inverse to
linearize, multiplies by `srcToDst` (with chromatic adaptation already baked
in), then applies the destination's TF to encode.

Skia does *not* attempt rendering-intent conversions (relative or absolute
colorimetric, perceptual, saturation). Out-of-gamut destination colors are
simply clipped to `[0, 1]` (or, for HDR working spaces, left extended).

---

## SkColorSpacePrimaries

A struct of eight `float`s — RX, RY, GX, GY, BX, BY, WX, WY — naming the
chromaticity coordinates of the three primaries and the white point.
Conversion to a 3×3 toXYZD50 matrix happens via
`bool toXYZD50(skcms_Matrix3x3* outMatrix) const`.

`SkNamedPrimaries::` provides constants for every entry in ITU-T H.273 Table 2
plus ProPhoto:

| Constant | CICP id | Notes |
|---|---:|---|
| `kRec709` | 1 | sRGB / Rec.709 / Display-P3 (the latter has same primaries with different TF). |
| `kRec470SystemM` | 4 | Old NTSC. |
| `kRec470SystemBG` | 5 | PAL/SECAM. |
| `kRec601` | 6 | SMPTE 170M (also `kSMPTE_ST_240`). |
| `kSMPTE_ST_240` | 7 | (alias of kRec601) |
| `kGenericFilm` | 8 | Generic film with Illuminant C. |
| `kRec2020` | 9 | UHD / wide gamut. |
| `kSMPTE_ST_428_1` | 10 | Identity matrix; cinema XYZ. |
| `kSMPTE_RP_431_2` | 11 | DCI-P3. |
| `kSMPTE_EG_432_1` | 12 | Display-P3 white point. |
| `kITU_T_H273_Value22` | 22 | Sometimes called EBU 3213-E. |
| `kProPhotoRGB` | n/a | CSS Color 4. |

The matching `SkNamedPrimaries::CicpId` enum spells the integer id per
the H.273 spec.

---

## SkNamedTransferFn

`skcms_TransferFunction` is a tuple `{ g, a, b, c, d, e, f }`. The header
defines:

| Constant | CICP id | Notes |
|---|---:|---|
| `kSRGB` | 13 | The sRGB curve (`g≈2.4` plus a linear toe). |
| `k2Dot2` | — | Pure gamma 2.2; used by AdobeRGB. |
| `kRec2020` | — | Used as a generic Rec.2020 SDR curve. |
| `kRec709` | 1 | Rec.709 OETF. |
| `kRec470SystemM` | 4 | Gamma 2.2. |
| `kRec470SystemBG` | 5 | Gamma 2.8. |
| `kRec601` | 6 | Same as Rec.709. |
| `kSMPTE_ST_240` | 7 | SMPTE 240M. |
| `kLinear` | 8 | Identity; gamma = 1. |
| `kIEC61966_2_4` | 11 | Same as Rec.709 (extended-range). |
| `kIEC61966_2_1` (`= kSRGB`) | 13 | sRGB. |
| `kRec2020_10bit` | 14 | Same as Rec.709. |
| `kRec2020_12bit` | 15 | Same as Rec.709. |
| `kPQ` | 16 | Rec.2100 PQ (HDR). Encoded as `{−5, 203, 0, 0, 0, 0, 0}` — non-numerical. |
| `kSMPTE_ST_428_1` | 17 | Cinema. |
| `kHLG` | 18 | Rec.2100 HLG (HDR). Encoded as `{−6, 203, 1000, 1.2, 0, 0, 0}`. |
| `kProPhotoRGB` | — | Gamma 1.8. |
| `kA98RGB` (= `k2Dot2`) | — | AdobeRGB / opRGB. |

The matching `SkNamedTransferFn::CicpId` enum names the integer id; some
H.273 values are intentionally *not* representable
(e.g. log-gamma rows 9 and 10).

PQ and HLG live as non-numerical encodings (negative `g` values selected as
sentinels); `SkColorSpace::isNumericalTransferFn(*fn)` returns false for
them, and Skia routes them through `skcms` rather than the inline
analytical evaluator.

---

## SkNamedGamut

3×3 D50 matrices for the four most common gamuts:

| Constant | Gamut |
|---|---|
| `kSRGB` | sRGB / Rec.709. |
| `kAdobeRGB` | AdobeRGB. |
| `kDisplayP3` | Apple Display-P3. |
| `kRec2020` | Rec.2020. |
| `kXYZ` | Identity (XYZ-D50). |

The sRGB and AdobeRGB matrices are stored as ICC fixed-point (16.16) values
to round-trip exactly with the rest of skcms.

---

## SkColorSpace

`include/core/SkColorSpace.h`. Refcounted via `SkNVRefCnt<SkColorSpace>` —
final, no virtual destructor.

### Construction

| Factory | Behavior |
|---|---|
| `SkColorSpace::MakeSRGB()` | Process-wide singleton. |
| `SkColorSpace::MakeSRGBLinear()` | sRGB primaries, linear gamma. Singleton. |
| `SkColorSpace::MakeRGB(transferFn, toXYZD50Matrix)` | Custom RGB color space. |
| `SkColorSpace::MakeCICP(primariesCicp, tfCicp)` | Build from two H.273 code points. Returns null for unsupported combinations or where matrix coefficients ≠ 0 (only RGB is supported). |
| `SkColorSpace::Make(skcms_ICCProfile)` | Adopt a parsed ICC profile (caller can use `skcms_Parse` or `skcms_ParseWithA2BPriority`). |
| `SkColorSpace::Deserialize(data, length)` | Parse a serialized color space. |

The sRGB and sRGB-linear factories are designed to be cheap — internally
they bottom out in `SkColorSpaceSingletonFactory` and never allocate.

### Identity

- `gammaCloseToSRGB()` — true if the transfer function is "close enough" to
  sRGB to be approximated as such (used for fast paths).
- `gammaIsLinear()` — true if `g == 1`.
- `isSRGB()` — true only if both gamut and TF *exactly* match sRGB
  (with tolerance for ICC fixed-point and D50 chromatic adaptation rounding).
  A 2.2-gamma color space is *not* sRGB even though they look similar.
- `transferFnHash()`, `toXYZD50Hash()`, `hash()` — fast equality keys
  combining 32-bit hashes of the TF and gamut matrix.
- `Equals(a, b)` — null-safe deep comparison.

### Querying internal state

```c++
bool isNumericalTransferFn(skcms_TransferFunction* out) const;
bool toXYZD50(skcms_Matrix3x3* out) const;
void transferFn(skcms_TransferFunction* out) const;
void invTransferFn(skcms_TransferFunction* out) const;
void gamutTransformTo(const SkColorSpace* dst, skcms_Matrix3x3* src_to_dst) const;
void toProfile(skcms_ICCProfile* out) const;
sk_sp<SkData> serialize() const;
size_t        writeToMemory(void* memory) const;
```

`gamutTransformTo` is the workhorse for chromatic adaptation: given two color
spaces, it composes `dst.fromXYZD50 * src.toXYZD50` to produce the 3×3
linear-RGB → linear-RGB matrix that takes source values to destination
values (the chromatic adaptation has already been baked in by the D50
intermediary).

### Derived spaces

- `makeLinearGamma()` — same gamut, linear gamma.
- `makeSRGBGamma()` — same gamut, sRGB gamma.
- `makeColorSpin()` — same gamut and TF but with the primary axes rotated
  (R → G, G → B, B → R). Used by tests to construct intentionally-perverse
  color spaces.

### Internal lazy fields

`SkColorSpace` lazily computes its inverse transfer function (`fInvTransferFn`)
and inverse gamut matrix (`fFromXYZD50`) on first need, guarded by
`SkOnce fLazyDstFieldsOnce`. This makes color-space creation cheap (no
matrix inversion at construction) but `gamutTransformTo`, `invTransferFn`,
and the destination-side of any conversion fast.

---

## How draws thread color through

Skia normalizes everything to a single conceptual pipeline that
`SkColorSpaceXformSteps` (see below) compiles for each `(src, dst)` pair.

### Source colors and shader output

- Paint colors set via `SkPaint::setColor(SkColor4f, SkColorSpace*)` are
  tagged with their color space at insertion time. A `SkColor` (32-bit
  integer) is implicitly sRGB.
- Shaders carry their own color space — gradients store stop colors in a
  caller-supplied space; image shaders inherit from the image; runtime
  shaders run in their *working* color space (linear by default).
- A `SkColorFilter` may be wrapped via
  `SkColorFilter::makeWithWorkingColorSpace(cs)` to force its math into a
  specific space regardless of the destination — this is critical for any
  filter whose output depends on the perceptual interpretation of its
  inputs (the matrix and lighting filters in particular).

### Destination

The destination color space comes from the `SkSurface`'s `SkImageInfo` (or
its layer's overridden color space, set via `SaveLayerRec::fColorSpace`).
A surface created with no color space is *legacy / untagged*: Skia treats
inputs and outputs as opaque pixels and skips color management entirely.
This is the "Android framework legacy" mode and the only place where mixing
color-managed and untagged drawing is safe.

### Layer color spaces

`SkCanvas::saveLayer(SaveLayerRec)` accepts an optional
`fColorSpace` pointer. When set:

- The layer is allocated in `fColorSpace`.
- All draws into the layer convert from their source space into
  `fColorSpace`.
- `fBackdrop` (if any) and `fPaint`'s effects run in `fColorSpace`.
- On `restore()`, the layer is converted from `fColorSpace` to the parent
  layer's color space.

This is the standard mechanism for switching to a wide-gamut or linear
working space for a sub-tree of drawing. Combined with
`kF16ColorType` in `fSaveLayerFlags`, it gives high-precision intermediate
storage for filters that benefit from extra range.

---

## SkColorSpaceXformSteps — the internal planner

`src/core/SkColorSpaceXformSteps.h`. Compiles a `(srcCS, srcAT, dstCS, dstAT)`
tuple into a list of seven possible substeps:

```c++
struct Flags {
    bool unpremul         = false;   // 1
    bool linearize        = false;   // 2
    bool src_ootf         = false;   // 32 (HDR PQ/HLG opto-optical transfer fn)
    bool gamut_transform  = false;   // 4
    bool dst_ootf         = false;   // 64
    bool encode           = false;   // 8
    bool premul           = false;   // 16
};
```

Plus the data needed for each:

```c++
skcms_TransferFunction fSrcTF;       // for linearize
skcms_TransferFunction fDstTFInv;    // for encode
float fSrcToDstMatrix[9];            // 3×3 column-major
float fSrcOotf[4];                   // r, g, b coefficients + gamma
float fDstOotf[4];
```

The planner activates only the steps that actually differ. For the common
case of `(sRGB premul → sRGB premul)`, every flag is false and `apply()` is
a no-op. For `(P3 unpremul → sRGB premul)` the ordered sequence is:
`linearize → gamut_transform → encode → premul` (no premul/unpremul roundtrip
because the gamut transform is linear-RGB only).

`apply(float rgba[4])` runs the steps on a single pixel; `apply(SkRasterPipeline*)`
appends raster-pipeline stages for batched processing — the latter is what
the CPU and GPU backends actually use.

Invocation sites: `SkColorFilter`, the raster blitter setup (`SkBlitter::Choose`),
the GPU blitter pipeline, and `SkImage::makeColorSpace`.

---

## ICC profiles

ICC profiles enter Skia in three places:

1. **PNG/JPEG/HEIF/WebP/AVIF decoders** — see [image-decoders.md](image-decoders.md).
   The codec parses the embedded `iCCP`/`APP2`/`colr` profile via
   `skcms_Parse` and constructs an `SkColorSpace` via `SkColorSpace::Make`.
   The resulting `SkImage` then carries that color space.
2. **Encoders** — see [image-encoders.md](image-encoders.md). The encoder
   serializes the source `SkImage`'s color space to ICC bytes via
   `SkColorSpace::toProfile(*profile)` followed by
   `skcms_ApproximatelyEqualProfiles` to find an exact-match named profile or
   `skcms_Write_ICC` to emit a fresh one.
3. **Direct construction** — clients with their own ICC bytes call
   `skcms_Parse` themselves and pass the parsed `skcms_ICCProfile` to
   `SkColorSpace::Make`.

Skia does not support all ICC profile shapes — only RGB profiles whose A2B
or matrix-based forward transform reduces to a 3×3 matrix and a per-channel
TF. Lookup-table-only profiles (perceptual rendering intents on CMYK
profiles, complex multi-channel A2Bs) are rejected by `skcms` and produce a
null `SkColorSpace`.

---

## Special concerns

### YUV color spaces

`SkYUVColorSpace` (declared in `include/core/SkImageInfo.h`) is *separate*
from `SkColorSpace` — it identifies the YUV → RGB conversion matrix only.
Once `SkYUVAImage` decoding produces RGB, the resulting `SkImage` carries
its own RGB `SkColorSpace`. See [bitmap-pixmap-image.md](bitmap-pixmap-image.md#skyuvcolorspace).

### HDR

PQ (`kPQ`, CICP 16) and HLG (`kHLG`, CICP 18) are both non-numerical
transfer functions. Skia treats values above 1.0 as legitimate HDR signal:
the `kRGBA_F16` and `kRGBA_F32` color types preserve them, and the wider
unorm types (`kRGBA_1010102`, `kBGRA_1010102`) admit them via the extended
ranges of the matching color types (`kBGR_101010x_XR_SkColorType`,
`kBGRA_10101010_XR_SkColorType`). HDR also relies on the gainmap pipeline
documented in [hdr-and-gainmaps.md](hdr-and-gainmaps.md).

When a PQ/HLG color space crosses into a non-HDR destination, the
`SkColorSpaceXformSteps` planner enables `src_ootf` (and possibly
`dst_ootf`) to apply the signal's *opto-optical transfer function*
(reference white point of 203 cd/m², matching ITU-R BT.2408). Without OOTF
the PQ-encoded "diffuse white" would land at ~0.58 in the output rather
than 1.0.

### Untagged and "legacy" mode

If a `SkSurface` is created without a color space, Skia disables color
management for draws into it. Source color spaces are *ignored* in this
mode — the bytes flow through unchanged. This is the only safe way to
operate on pre-existing untagged pixel data; mixing tagged and untagged
draws into the same surface produces undefined results.

### Color-spin testing

`SkColorSpace::makeColorSpin()` (and `SK_ASSUME_COLOR_SPIN` builds) rotates
the primaries so any code that incorrectly skips color conversion produces
visibly wrong (cyan-instead-of-red) output. Used by Skia's regression tests.

---

## Cross-references

- [`SkCMS`](skcms.md) — the standalone library that does the parsing, ICC
  serialization, and the transfer-function math.
- [`SkPaint`, `SkColor4f`, `SkColorFilter`](paint-color-and-blending.md) —
  every paint color is tagged with a `SkColorSpace`; color filters can
  override their working space.
- [`SkBitmap` / `SkPixmap` / `SkImage`](bitmap-pixmap-image.md) — pixel
  containers all carry an `SkColorSpace`; `SkImage::makeColorSpace`,
  `SkImage::reinterpretColorSpace`, `SkImage::makeColorTypeAndColorSpace`
  are the conversion APIs.
- [`SkSurface`](surface-and-output.md) — surfaces created with a
  `SkImageInfo` whose color space is non-null are color-managed.
- [`SkCanvas` `SaveLayerRec::fColorSpace`](canvas-and-recording.md) — for
  per-layer color-space switches.
- [HDR and gainmaps](hdr-and-gainmaps.md) — PQ/HLG, gainmap shading, OOTF.
- [Image decoders](image-decoders.md) and
  [image encoders](image-encoders.md) — how ICC profiles cross the I/O
  boundary.
