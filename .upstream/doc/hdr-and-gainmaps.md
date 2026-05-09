# HDR & Gainmaps

Skia handles high-dynamic-range content through three cooperating systems:

1. **HDR transfer functions and color spaces** — PQ and HLG transfer
   functions plus the BT.2020 / Rec.2100 gamut, expressed as ordinary
   `SkColorSpace` values.
2. **HDR metadata** (`skhdr::Metadata`) — CLLI, MDCV, and AGTM payloads
   carried through codecs and encoded into PNG / JPEG output.
3. **Gainmaps** (`SkGainmapInfo`, `SkGainmapShader`) — the *Adobe Gain Map* /
   *UltraHDR* / *ISO 21496-1* technology that pairs an SDR (or HDR) base
   image with a per-pixel gain that produces an HDR (or SDR) rendition for
   the display's actual headroom.

Headers:

- `include/private/SkGainmapInfo.h` — gainmap rendering parameters.
- `include/private/SkGainmapShader.h` — runtime shader that combines a base
  image and a gainmap.
- `include/private/SkHdrMetadata.h` — `skhdr::ContentLightLevelInformation`,
  `skhdr::MasteringDisplayColorVolume`, `skhdr::Agtm`, and `skhdr::Metadata`.
- `include/private/SkJpegGainmapEncoder.h` — encoder for UltraHDR JPEGs.
- `include/codec/SkCodec.h` — `getHdrMetadata()`, gainmap virtuals.
- `include/core/SkColorSpace.h` — `SkNamedTransferFn::kPQ`, `kHLG`,
  `SkNamedGamut::kRec2020`.

Implementation:

- `src/codec/SkGainmapInfo.cpp` (~9 KB) — ISO 21496-1 parse/serialize.
- `src/codec/SkHdrMetadata.cpp`, `SkHdrAgtm.cpp` (~21 KB),
  `SkHdrAgtmParse.cpp` (~25 KB) — `skhdr::Agtm` implementation.
- `src/codec/SkHdrAgtmPriv.h` — internal AGTM data model
  (`AgtmImpl`).
- `src/encode/SkJpegGainmapEncoder.cpp` (~16 KB).
- `src/shaders/` — runtime SkSL backing `SkGainmapShader::Make`.

---

## HDR color spaces

HDR transfer functions are first-class `skcms_TransferFunction` values
exposed in `SkNamedTransferFn`:

```c++
namespace SkNamedTransferFn {
    extern const skcms_TransferFunction kSRGB;
    extern const skcms_TransferFunction kRec2020;
    extern const skcms_TransferFunction kPQ;       // ITU-R BT.2100 perceptual quantization
    extern const skcms_TransferFunction kHLG;      // ITU-R BT.2100 hybrid log-gamma
    // ...plus Rec.709, Linear, sRGB synonyms, etc.
}

namespace SkNamedGamut {
    extern const skcms_Matrix3x3 kSRGB;
    extern const skcms_Matrix3x3 kRec2020;
    // ...
}
```

Construct an HDR `SkColorSpace` with `SkColorSpace::MakeRGB(transferFn,
gamut)`:

```c++
auto pqRec2020  = SkColorSpace::MakeRGB(SkNamedTransferFn::kPQ,
                                        SkNamedGamut::kRec2020);
auto hlgRec2020 = SkColorSpace::MakeRGB(SkNamedTransferFn::kHLG,
                                        SkNamedGamut::kRec2020);
```

`SkColorSpace::isNumericalTransferFn(skcms_TransferFunction*)` returns false
for non-parametric transfer functions like PQ and HLG (they are
non-analytic), so callers shouldn't assume every color space exposes a
single closed-form transfer function. CICP support is exposed via the CICP
ID-based factory (`MakeCICP(...)`), and IDs include `kRec2020 = 9`,
`kPQ = 16`, `kHLG = 18`.

`SkImageInfo` carries the color space directly — there is no separate "HDR
color type". HDR pixel data lives in floating-point (`kRGBA_F16`) or
extended-range (`kRGBA_1010102`, `kBGRA_10101010_XR`) formats.

---

## skhdr::Metadata

`include/private/SkHdrMetadata.h` defines the `skhdr` namespace with three
content-metadata structures wrapped by a `Metadata` aggregate that
`SkCodec` and the PNG encoder thread through.

### ContentLightLevelInformation (CLLI)

Per-frame brightness summary defined by ANSI/CTA-861-H Annex P (semantically
shared by ITU-T H.265 D.2.35 and PNG cLLI).

```c++
struct ContentLightLevelInformation {
    float fMaxCLL  = 0.f;   // maximum content light level (cd/m²)
    float fMaxFALL = 0.f;   // maximum frame-average light level

    bool         parse(const SkData*);             // AV1 / H.265 SEI encoding
    sk_sp<SkData> serialize() const;
    bool         parsePngChunk(const SkData*);     // PNG cLLI encoding
    sk_sp<SkData> serializePngChunk() const;
    SkString     toString() const;
    bool         operator==(const ContentLightLevelInformation&) const;
};
```

The PNG cLLI encoding is *not* equivalent to the AV1/H.265 SEI encoding,
which is why the two `parse`/`serialize` pairs exist. Both round-trip
losslessly within their respective formats.

### MasteringDisplayColorVolume (MDCV)

Per-content reference about the display the master was graded on, defined
by SMPTE ST 2086:2018.

```c++
struct MasteringDisplayColorVolume {
    SkColorSpacePrimaries fDisplayPrimaries{};   // .fRX, fRY, fGX, fGY, fBX, fBY, fWX, fWY
    float fMaximumDisplayMasteringLuminance = 0.f;   // cd/m²
    float fMinimumDisplayMasteringLuminance = 0.f;

    bool         parse(const SkData*);
    sk_sp<SkData> serialize() const;
    SkString     toString() const;
    bool         operator==(const MasteringDisplayColorVolume&) const;
};
```

The single encoding works for AV1, H.265, and PNG mDCV (the three specs
align on this one).

### skhdr::Agtm — Adaptive Global Tone Mapping

Defined in **SMPTE ST 2094-50** (Application #5; under development at
[github.com/SMPTE/st2094-50](https://github.com/SMPTE/st2094-50)). AGTM
specifies how to *dynamically* tone-map between SDR and HDR renditions
based on the display's actual headroom.

```c++
class Agtm {
public:
    // Parse a serialized SMPTE ST 2094-50 blob.
    static std::unique_ptr<Agtm> Make(const SkData*);

    // Pre-baked variants: Reference White Tone Mapping Operator (RWTMO).
    static std::unique_ptr<Agtm> MakeReferenceWhite(float hdrReferenceWhite,
                                                    float baselineHdrHeadroom);
    // Pre-baked variant: clamp-only (no tone mapping; the consumer just clamps).
    static std::unique_ptr<Agtm> MakeClamp(float hdrReferenceWhite,
                                            float baselineHdrHeadroom);

    static constexpr float kDefaultHdrReferenceWhite = 203.f;  // cd/m²

    virtual sk_sp<SkData> serialize() const = 0;
    virtual float         getHdrReferenceWhite() const = 0;
    virtual bool          hasBaselineHdrHeadroom() const = 0;
    virtual float         getBaselineHdrHeadroom() const = 0;
    virtual bool          isClamp() const = 0;
    virtual sk_sp<SkColorFilter>
                          makeColorFilter(float targetedHdrHeadroom) const = 0;
    virtual SkString      toString() const = 0;
};
```

`makeColorFilter(targetedHdrHeadroom)` returns an `SkColorFilter` that
applies the chosen gain function for the given display headroom — feed it
to a `SkPaint` to render at any HDR level between SDR and the metadata's
`baselineHdrHeadroom`.

### skhdr::Agtm internals (`AgtmImpl`)

`src/codec/SkHdrAgtmPriv.h` documents the private model. ST 2094-50 stores
HDR variants as a discrete list of "alternate images" with associated gain
functions; the runtime computes a weighted blend of the closest two.

```c++
struct PiecewiseCubicFunction {
    static constexpr uint8_t kMaxNumControlPoints = 32;
    uint8_t fNumControlPoints;
    float   fX[kMaxNumControlPoints];   // GainCurveControlPointX
    float   fY[kMaxNumControlPoints];   // GainCurveControlPointY
    float   fM[kMaxNumControlPoints];   // GainCurveControlPointM (slopes)
    void    populateSlopeFromPCHIP();   // Hermite slope fill (PCHIP, Clause 6.1.3)
    float   evaluate(float x) const;    // Clause 5.1.3
};

struct ComponentMixingFunction {
    float fRed, fGreen, fBlue, fMax, fMin, fComponent;  // Clause 5.2
    SkColor4f evaluate(const SkColor4f&) const;
};

struct GainFunction {                                   // Clause 5.3
    ComponentMixingFunction fComponentMixing;
    PiecewiseCubicFunction  fPiecewiseCubic;
    SkColor4f evaluate(const SkColor4f&) const;
};

class AgtmImpl : public Agtm {
    enum class Type { kNone, kReferenceWhite, kCustom };
    Type     fType;
    float    fHdrReferenceWhite        = kDefaultHdrReferenceWhite;
    float    fBaselineHdrHeadroom      = 0.f;
    SkColorSpacePrimaries fGainApplicationSpacePrimaries;
    static constexpr uint8_t kMaxNumAlternateImages = 4;
    uint8_t  fNumAlternateImages       = 0;
    float    fAlternateHdrHeadroom[kMaxNumAlternateImages];
    GainFunction fGainFunction[kMaxNumAlternateImages];
    sk_sp<SkImage> fGainCurvesXYM;                 // baked into a texture for SkSL
    void  populateGainCurvesXYM();                 // bake LUT
    void  populateUsingRwtmo();                    // RWTMO defaults
    bool  parse(const SkData*);                    // smpte_st_2094_50_application_info_v0()

    struct Weighting {
        static constexpr uint8_t kInvalidIndex = 255;
        uint8_t fAlternateImageIndex[2] = {kInvalidIndex, kInvalidIndex};
        float   fWeight[2]              = {0.f, 0.f};
    };
    Weighting computeWeighting(float targetedHdrHeadroom) const;  // Clause 5.4.5
    void      applyGain(SkSpan<SkColor4f>, float targetedHdrHeadroom) const;
    sk_sp<SkColorSpace> getGainApplicationSpace() const;
};
```

`makeColorFilter(targetedHdrHeadroom)` produces a `SkColorFilter` that:

1. Transforms input pixels to the `GainApplicationSpace` (whose primaries
   are stored in `fGainApplicationSpacePrimaries`).
2. Computes per-image weights for the targeted headroom via
   `computeWeighting`.
3. Samples the per-alternate gain curves (LUT in `fGainCurvesXYM`) using
   the component-mixing function as the curve abscissa.
4. Blends the two nearest alternates by weight and re-applies the resulting
   gain to the input.

### skhdr::Metadata aggregate

```c++
class Metadata {
public:
    static Metadata MakeEmpty();

    bool                getContentLightLevelInformation(ContentLightLevelInformation*) const;
    void                setContentLightLevelInformation(const ContentLightLevelInformation&);
    bool                getMasteringDisplayColorVolume(MasteringDisplayColorVolume*) const;
    void                setMasteringDisplayColorVolume(const MasteringDisplayColorVolume&);
    sk_sp<const SkData> getSerializedAgtm() const;
    void                setSerializedAgtm(sk_sp<const SkData>);

    SkString toString() const;
    bool     operator==(const Metadata&) const;
private:
    std::optional<ContentLightLevelInformation> fContentLightLevelInformation;
    std::optional<MasteringDisplayColorVolume>  fMasteringDisplayColorVolume;
    sk_sp<const SkData>                         fAgtm;     // serialized; parse via Agtm::Make
};
```

`SkCodec::getHdrMetadata()` returns a `const skhdr::Metadata&` populated
from the encoded source. The PNG encoder (`SkPngEncoder::Options::fHdrMetadata`)
writes any present fields back as `cLLI` / `mDCV` / AGTM chunks. AGTM is
stored serialized so multiple decoders (AV1, AVIF, JPEG-XL, PNG) can pass
the blob through without parsing.

---

## SkGainmapInfo

`include/private/SkGainmapInfo.h`. Pure data structure describing how to
combine a base image with a gainmap to produce a tone-mapped output for a
given display.

> **Licensing**: Skia's gainmap support uses Adobe's *Gain Map technology*
> under license; see the source-file header.

```c++
struct SkGainmapInfo {
    // Per-channel parameters for converting gainmap pixels to log space.
    SkColor4f fGainmapRatioMin = {1.f, 1.f, 1.f, 1.f};
    SkColor4f fGainmapRatioMax = {2.f, 2.f, 2.f, 1.f};
    SkColor4f fGainmapGamma    = {1.f, 1.f, 1.f, 1.f};

    // Numerical-stability fudge factors.
    SkColor4f fEpsilonSdr = {0.f, 0.f, 0.f, 1.f};
    SkColor4f fEpsilonHdr = {0.f, 0.f, 0.f, 1.f};

    // Display headroom (HDR-to-SDR luminance ratio) bounds.
    float fDisplayRatioSdr = 1.f;   // ≤ this → show pure SDR
    float fDisplayRatioHdr = 2.f;   // ≥ this → show pure HDR

    enum class BaseImageType { kSDR, kHDR };
    BaseImageType fBaseImageType = BaseImageType::kSDR;

    enum class Type { kDefault, kApple };
    Type fType = Type::kDefault;

    // If non-null, only the primaries are used (transfer function ignored).
    sk_sp<SkColorSpace> fGainmapMathColorSpace = nullptr;

    // Static helpers
    bool isUltraHDRv1Compatible() const;
    static bool          ParseVersion(const SkData*);
    static bool          Parse(const SkData*, SkGainmapInfo&);
    static sk_sp<SkData> SerializeVersion();
    sk_sp<SkData>        serialize() const;

    bool operator==(const SkGainmapInfo&) const;
};
```

### Field semantics

- `fGainmapRatioMin`, `fGainmapRatioMax` — multiplicative gain range
  expressed in linear HDR-to-SDR ratio. Per-channel so monochromatic
  gainmaps still work; the alpha component of the `SkColor4f` is unused.
- `fGainmapGamma` — power applied to the sampled gainmap value before
  log interpolation.
- `fEpsilonSdr`, `fEpsilonHdr` — small additive constants that prevent the
  multiplicative math from blowing up at black (when the SDR or HDR
  rendition has a zero pixel).
- `fDisplayRatioSdr`, `fDisplayRatioHdr` — define the linear interpolation
  region. A display headroom of `fDisplayRatioSdr` (or less) produces pure
  SDR; `fDisplayRatioHdr` (or greater) produces full HDR. Headrooms in
  between produce a partial gainmap application.
- `fBaseImageType` — whether the base image is the SDR rendition (gainmap
  *adds* HDR) or the HDR rendition (gainmap *attenuates* to SDR).
- `fType`:
  - `kDefault` — ISO 21496-1 / Adobe Gain Map encoding.
  - `kApple` — Apple's HDR Effect encoding from
    [developer.apple.com/documentation/appkit/images_and_pdf/applying_apple_hdr_effect_to_your_photos](https://developer.apple.com/documentation/appkit/images_and_pdf/applying_apple_hdr_effect_to_your_photos).
    Convertible to `kDefault` per
    [docs.google.com/.../1iUpYAThVV_FuDdeiO3t0vnlfoA1ryq0WfGS9FuydwKc](https://docs.google.com/document/d/1iUpYAThVV_FuDdeiO3t0vnlfoA1ryq0WfGS9FuydwKc).
- `fGainmapMathColorSpace` — color space whose **primaries only** define
  the space the gainmap math is applied in. If null, the base image's color
  space is used.

### Tone mapping math

Let:

- `H` = the display's HDR-to-SDR ratio.
- `B` = a base-image pixel in *linear* primaries-of-the-base-image space.
- `G` = the corresponding gainmap pixel in `[0, 1]`.
- `D` = the output pixel.

Then:

```
W = clamp((log H - log fDisplayRatioSdr) /
          (log fDisplayRatioHdr - log fDisplayRatioSdr), 0, 1)

L = mix(log fGainmapRatioMin,
        log fGainmapRatioMax,
        pow(G, fGainmapGamma))

if (fBaseImageType == kSDR):
    D = (B + fEpsilonSdr) * exp(L * W)       - fEpsilonHdr
else if (fBaseImageType == kHDR):
    D = (B + fEpsilonHdr) * exp(L * (W - 1)) - fEpsilonSdr
```

The base of `log` and `exp` is irrelevant as long as it is consistent —
implementations use natural log/exp. The math is performed per-channel.

### Tone mapping pipeline

```
                         display HDR→SDR ratio H
                                  │
  ┌──────────┐        ┌───────────▼───────────┐    ┌──────────┐
  │ base img │        │      compute W        │    │ gainmap  │
  │  (linear │        │ W = clamp( (log H -   │    │  image   │
  │  in base │        │  log Sdr) / (log Hdr -│    │  (G in   │
  │  primar.)│        │  log Sdr), 0, 1 )     │    │  [0,1])  │
  └────┬─────┘        └───────────────┬───────┘    └────┬─────┘
       │                              │                 │
       │                              │                 ▼
       │                              │       ┌─────────────────┐
       │                              │       │ L = mix(        │
       │                              │       │  log Ratio Min, │
       │                              │       │  log Ratio Max, │
       │                              │       │  pow(G, Gamma) )│
       │                              │       └────────┬────────┘
       │                              └────────┐       │
       │                                       │       │
       │                                       ▼       ▼
       │                              ┌─────────────────────┐
       │                              │ Effective gain =    │
       │                              │   exp(L * W)        │   (kSDR base)
       │                              │   exp(L * (W - 1))  │   (kHDR base)
       │                              └────────┬────────────┘
       └────► (B + ε_src) × ───────────────────┘
                                              │
                                              ▼
                              D = (...) − ε_dst
                              [output linear in
                               same primaries as B]
                                              │
                                              ▼
                              transfer function for
                              destination color space
                                              │
                                              ▼
                                      displayed pixel
```

When `H ≤ fDisplayRatioSdr` ⇒ `W = 0` ⇒ `exp(0) = 1` ⇒ output equals base
(SDR rendition). When `H ≥ fDisplayRatioHdr` ⇒ `W = 1` ⇒ full gain applied
(HDR rendition). Between those, a logarithmic blend.

For `kHDR`-based images, when `W = 1` the gain factor is `exp(0) = 1`
(unchanged HDR); when `W = 0` it becomes `exp(-L)`, the inverse of the SDR
blend. The two formulae are intentionally symmetric.

### Serialization

ISO 21496-1 (the standardized container format adopted by the W3C UltraHDR
work) is the on-the-wire format:

- `SkGainmapInfo::ParseVersion(SkData*)` returns true if the blob's version
  byte is supported.
- `SkGainmapInfo::Parse(SkData*, SkGainmapInfo&)` populates an info; sets
  `fGainmapMathColorSpace` to sRGB by default (overwritten downstream by
  the decoder if the actual space is known) or to nullptr if the metadata
  asks for the base image's space.
- `serialize()` emits a version-0 blob with the current parameters.
- `SerializeVersion()` is a static convenience that emits a header-only
  blob (used by tools and tests).
- `isUltraHDRv1Compatible()` checks whether the parameters are within the
  subset UltraHDR v1 requires.

Implementation: `src/codec/SkGainmapInfo.cpp`.

---

## SkGainmapShader

`include/private/SkGainmapShader.h`. Builds an `SkShader` that performs the
above tone-mapping math at draw time.

```c++
class SkGainmapShader {
public:
    // Variant 1: math performed in the destination color space implicitly
    // (whatever surface receives the draw).
    static sk_sp<SkShader> Make(const sk_sp<const SkImage>& baseImage,
                                const SkRect& baseRect,
                                const SkSamplingOptions& baseSamplingOptions,
                                const sk_sp<const SkImage>& gainmapImage,
                                const SkRect& gainmapRect,
                                const SkSamplingOptions& gainmapSamplingOptions,
                                const SkGainmapInfo& gainmapInfo,
                                const SkRect& dstRect,
                                float dstHdrRatio);

    // Variant 2: explicit destination color space.
    static sk_sp<SkShader> Make(const sk_sp<const SkImage>& baseImage,
                                const SkRect& baseRect,
                                const SkSamplingOptions& baseSamplingOptions,
                                const sk_sp<const SkImage>& gainmapImage,
                                const SkRect& gainmapRect,
                                const SkSamplingOptions& gainmapSamplingOptions,
                                const SkGainmapInfo& gainmapInfo,
                                const SkRect& dstRect,
                                float dstHdrRatio,
                                sk_sp<SkColorSpace> dstColorSpace);
};
```

Parameters:

- `baseImage` + `baseRect` — sample area inside the base image; mapped onto
  `dstRect`.
- `gainmapImage` + `gainmapRect` — sample area inside the gainmap (which
  may be a different size than the base — gainmaps are commonly stored at
  half resolution).
- `baseSamplingOptions` / `gainmapSamplingOptions` — per-source filter
  control.
- `gainmapInfo` — the rendering parameters from above.
- `dstRect` — the destination rectangle in canvas-space; both `baseRect`
  and `gainmapRect` map to this.
- `dstHdrRatio` — the *display's* current HDR-to-SDR ratio (`H` in the
  math). Drives the `W` weight.
- `dstColorSpace` (variant 2) — color space the shader's output is in.

The shader is implemented as an `SkRuntimeEffect` (SkSL); see
`src/shaders/SkGainmapShader.cpp` for the SkSL source.

Typical use:

```c++
SkPaint paint;
paint.setShader(SkGainmapShader::Make(base, baseRect, sampling,
                                       gainmap, gainmapRect, sampling,
                                       info, dstRect,
                                       displayHdrRatio,
                                       dstColorSpace));
canvas->drawRect(dstRect, paint);
```

---

## JPEG gainmap encode / decode pipeline

### Encode — `SkJpegGainmapEncoder::EncodeHDRGM`

```
caller
  │  base SkPixmap, base SkJpegEncoder::Options
  │  gainmap SkPixmap, gainmap SkJpegEncoder::Options
  │  SkGainmapInfo
  ▼
SkJpegGainmapEncoder::EncodeHDRGM(dst, base, baseOpts,
                                  gainmap, gainmapOpts, info)
  │
  ├─ encode base via SkJpegEncoder::Make → SkData (base.jpg)
  │     ↳ ICC from base.colorSpace()
  │     ↳ XMP overwritten with HDRGM descriptor (gainmap params per ISO 21496-1)
  │
  ├─ encode gainmap via SkJpegEncoder::Make → SkData (gainmap.jpg)
  │     ↳ ICC from gainmap.colorSpace()
  │     ↳ XMP overwritten with the gainmap-side descriptor
  │
  └─ MakeMPF([&base, &gainmap], 2) → wraps both in a Multi Picture Format
                                     container per CIPA DC-007
                                     │
                                     ▼ writes to dst
```

`MakeMPF(SkWStream* dst, const SkData** images, size_t imageCount)` is
exposed publicly so callers can build other multi-image bundles (depth
maps, disparity maps, etc.).

`baseOptions` and `gainmapOptions` are standard
`SkJpegEncoder::Options` — but **`xmpMetadata` is overwritten** by the
encoder, since the XMP must describe the gainmap layout.

Implementation: `src/encode/SkJpegGainmapEncoder.cpp`.

### Decode

The decode side is split between `SkCodec`'s gainmap virtuals and the
JPEG-specific machinery in `src/codec/SkJpegCodec.cpp` plus
`SkJpegMetadataDecoderImpl`/`SkJpegMultiPicture`:

```c++
// In SkCodec:
virtual bool onGetGainmapCodec(SkGainmapInfo*, std::unique_ptr<SkCodec>*);
virtual bool onGetGainmapInfo (SkGainmapInfo*);
// JPEG-only legacy path (TODO: remove per issues.skia.org/363544350)
virtual bool onGetGainmapInfo (SkGainmapInfo*, std::unique_ptr<SkStream>*);
```

`SkAndroidCodec::getGainmapAndroidCodec(SkGainmapInfo*,
unique_ptr<SkAndroidCodec>*)` is the public, format-agnostic entry point;
it delegates to `SkCodec::onGetGainmapCodec`. The decode flow:

```
encoded JPEG bytes
  │
  ▼
SkJpegCodec parses APP1 (XMP) and APP2 (MPF) segments
  │  detects HDRGM XMP / Apple HDR Effect XMP
  │
  ├─► fills out caller's SkGainmapInfo
  │
  └─► extracts the gainmap sub-image's bytes from the MPF index and
      constructs an inner SkJpegCodec for it
                                  │
                                  ▼
                  base SkCodec ─── pair ─── gainmap SkCodec
                                  │
                                  ▼
            Caller decodes both and feeds them to SkGainmapShader
            (or to SkAnimatedImage if a gainmap is per-frame).
```

PNG decoders (libpng-backed `SkPngCodec` and Rust-backed `SkPngRustCodec`)
implement the same gainmap virtuals using the `gmAP` / `gdAT` chunks
written by `SkPngEncoder::Options::fGainmap` + `fGainmapInfo`.

---

## HDR metadata in SkImageInfo

`SkImageInfo` itself does **not** carry HDR metadata — only the
`SkColorSpace` (which encodes the transfer function and primaries) and the
pixel format (which dictates whether you have the dynamic range to store
HDR values: `kRGBA_F16`, `kRGBA_1010102`, `kBGR_101010x_XR`,
`kBGRA_10101010_XR`).

HDR metadata travels separately:

| Source / sink | Location |
|---|---|
| codec → caller | `SkCodec::getHdrMetadata()` returns `const skhdr::Metadata&`. |
| caller → PNG encoder | `SkPngEncoder::Options::fHdrMetadata`. |
| caller → JPEG encoder | indirectly via `SkJpegEncoder::Options::xmpMetadata` for XMP-encoded HDR gain info. |
| caller → JPEG gainmap encoder | `SkJpegGainmapEncoder::EncodeHDRGM` parameter `gainmapInfo` (typed `SkGainmapInfo&`); per-channel HDR metadata not directly encoded — embed in XMP or encode AGTM into a sidecar. |
| display | `SkGainmapShader::Make`'s `dstHdrRatio` parameter. |

For full HDR display, callers typically:

1. Decode the encoded image with `SkCodec::MakeFromData`.
2. Read `getHdrMetadata()` to get CLLI / MDCV / AGTM payloads.
3. Decode pixels into `kRGBA_F16` with the source's HDR color space.
4. If a gainmap is available, also decode the gainmap codec and use
   `SkGainmapShader::Make`. Otherwise apply
   `Agtm::makeColorFilter(targetedHdrHeadroom)` as an `SkColorFilter` in the
   `SkPaint`.
5. Draw onto a surface configured with the display's HDR color space (e.g.
   PQ Rec.2020) and the display's headroom communicated via the platform
   API (Android `WindowSurface`, macOS `EDR` displays, etc.).

---

## Where to look

| File | Purpose |
|---|---|
| `include/private/SkGainmapInfo.h` | Gainmap rendering parameters + ISO 21496-1 parse/serialize |
| `include/private/SkGainmapShader.h` | Shader factory |
| `include/private/SkHdrMetadata.h` | `skhdr::ContentLightLevelInformation`, `MasteringDisplayColorVolume`, `Agtm`, `Metadata` |
| `include/private/SkJpegGainmapEncoder.h` | UltraHDR JPEG encoder |
| `include/encode/SkPngEncoder.h` | `Options::fHdrMetadata`, `fGainmap`, `fGainmapInfo` |
| `include/codec/SkCodec.h` | `getHdrMetadata`, gainmap virtuals |
| `src/codec/SkGainmapInfo.cpp` | ISO 21496-1 implementation |
| `src/codec/SkHdrAgtmPriv.h` | `skhdr::AgtmImpl` model |
| `src/codec/SkHdrAgtm.cpp` | AGTM evaluation, weighting, color filter |
| `src/codec/SkHdrAgtmParse.cpp` | SMPTE ST 2094-50 bitstream parser |
| `src/codec/SkHdrMetadata.cpp` | `skhdr::Metadata` implementation |
| `src/codec/SkJpegMultiPicture.{h,cpp}` | MPF parse/build for gainmap JPEGs |
| `src/codec/SkJpegMetadataDecoderImpl.{h,cpp}` | EXIF/XMP/ICC/MPF parse |
| `src/encode/SkJpegGainmapEncoder.cpp` | UltraHDR JPEG encode |
| `src/shaders/SkGainmapShader.cpp` | SkSL gainmap shader implementation |
