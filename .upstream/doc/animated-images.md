# Animated Images

Skia's animated-image stack has three layers:

1. **`SkCodec` + `SkCodecAnimation`** — every animated codec (GIF, APNG,
   animated WebP, animated AVIF, JPEG-XL) reports its frames through the
   common `SkCodec::FrameInfo` API. See [image-decoders.md](image-decoders.md)
   for `SkCodec` itself.
2. **`SkAnimatedImage`** — a thread-unsafe `SkDrawable` that drives an
   animation, holding decoded frames and applying the disposal/blend rules.
3. **YUVA pixmap support** (`SkYUVAInfo`, `SkYUVAPixmaps`,
   `SkYUVAPixmapInfo`, `SkYUVColorSpace`) — used by codecs that decode video
   frames into Y/U/V/(A) planes rather than RGB.

---

## SkCodecAnimation

`include/codec/SkCodecAnimation.h`. Two enums shared across all animated
codecs and `SkAnimatedImage`.

```c++
namespace SkCodecAnimation {
    enum class DisposalMethod {
        kKeep            = 1,  // overlay next frame on this frame
        kRestoreBGColor  = 2,  // clear this frame's rect to BG before next
        kRestorePrevious = 3,  // discard this frame; next blends on the prior one
    };

    enum class Blend {
        kSrcOver,  // standard alpha-over
        kSrc,      // overwrite destination
    };
}
```

The numeric values match GIF89a's graphic-control extension. GIF disposal
value 0 (unspecified) is interpreted as `kKeep`; value 4 is interpreted as
`kRestorePrevious`. Other formats (APNG, WebP, AVIF) advertise the same
methods natively.

---

## SkCodec::FrameInfo

(Defined in `include/codec/SkCodec.h`; included here for completeness.)

```c++
struct FrameInfo {
    int                              fRequiredFrame;        // earliest predecessor; kNoFrame = independent
    int                              fDuration;             // ms
    bool                             fFullyReceived;        // end marker is in stream
    SkAlphaType                      fAlphaType;            // conservative
    bool                             fHasAlphaWithinBounds; // conservative
    SkCodecAnimation::DisposalMethod fDisposalMethod;
    SkCodecAnimation::Blend          fBlend;                // vs. prior frame
    SkIRect                          fFrameRect;            // updated rect (⊆ codec dimensions)
};
```

Sentinels (members of `SkCodec`):

- `kNoFrame = -1` — used in both `FrameInfo::fRequiredFrame` (independent
  frame) and `Options::fPriorFrame` (no prior frame in dst).
- `kRepetitionCountInfinite = -1` — return value of
  `SkCodec::getRepetitionCount()` for forever-looping content.

`getFrameCount()` may grow over time as additional bytes arrive (true for
incremental GIF/APNG decode); `isAnimated()` may return `IsAnimated::kYes`,
`kNo`, or `kUnknown` depending on whether the codec can determine animation
status from currently-buffered bytes.

---

## SkFrameHolder (internal)

`src/codec/SkFrameHolder.h`. The internal data model behind every animated
`SkCodec` subclass.

```c++
class SkFrame {
    int     fId;
    bool    fHasAlpha;             // post-composite alpha
    int     fRequiredFrame;        // earliest predecessor; kUninitialized = -2
    SkIRect fRect;
    SkCodecAnimation::DisposalMethod fDisposalMethod;
    int     fDuration;
    SkCodecAnimation::Blend         fBlend;
    // ...
    virtual SkEncodedInfo::Alpha onReportedAlpha() const = 0;
};

class SkFrameHolder {
    int fScreenWidth, fScreenHeight;
    void setAlphaAndRequiredFrame(SkFrame*);
    virtual const SkFrame* onGetFrame(int i) const = 0;
};
```

`setAlphaAndRequiredFrame` walks predecessor frames to determine:

- whether the current frame, *after* compositing, is opaque
  (`fHasAlpha`).
- the earliest prior frame the current one depends on
  (`fRequiredFrame`) — propagated to `SkCodec::FrameInfo::fRequiredFrame`.
- accounting for each predecessor's `disposalMethod` and `frameRect`.

The implementing codecs (`SkGifCodec`, `SkPngCodec`/`SkPngRustCodec` for
APNG, `SkWebpCodec`, `SkAvifCodec`, `SkCrabbyAvifCodec`, `SkJpegxlCodec`)
all subclass `SkFrameHolder` and override `onGetFrame` and `getFrameHolder`
(the private virtual returned by `SkCodec::getFrameHolder`).

---

## SkAnimatedImage

`include/android/SkAnimatedImage.h`. A thread-unsafe `SkDrawable` that owns
an `SkAndroidCodec` and drives the animation: it decodes frames on demand,
manages disposal/blend state, and exposes the current frame as an
`SkImage`.

> Despite living under `include/android/`, `SkAnimatedImage` is portable —
> the Android directory is just where Android-flavored helpers were
> traditionally collected.

### Construction

```c++
static sk_sp<SkAnimatedImage> Make(std::unique_ptr<SkAndroidCodec>,
                                   const SkImageInfo& info,
                                   SkIRect cropRect,
                                   sk_sp<SkPicture> postProcess);

static sk_sp<SkAnimatedImage> Make(std::unique_ptr<SkAndroidCodec>);
```

- `info` — the requested decode size; the codec scales/pads as needed.
- `cropRect` — applied after scaling.
- `postProcess` — an arbitrary `SkPicture` drawn over each composited frame
  (for tinting, vignetting, watermarks, etc.).
- The simpler `Make(codec)` uses default size, no crop, no post-process.

The first frame is decoded synchronously during `Make`; null is returned if
allocation fails.

### Animation control

| Method | Purpose |
|---|---|
| `decodeNextFrame()` | Advance to the next frame. Returns the duration in ms of the *previous* frame, or `kFinished` (= -1) if the animation has ended. |
| `currentFrameDuration()` | Display time for the current frame in ms. Useful for the first frame, since `decodeNextFrame` returns the *outgoing* duration. |
| `getCurrentFrame()` | Snapshot the current frame as an `sk_sp<SkImage>`. The returned image is immutable (not affected by subsequent `decodeNextFrame`). Returns nullptr if there is no current frame. |
| `getFrameCount()` | Total frame count from the codec. |
| `getRepetitionCount()` | Currently set repetition count. |
| `setRepetitionCount(int count)` | Override the encoded repetition count. `SkCodec::kRepetitionCountInfinite` = forever; `0` = play through once and stop. |
| `isFinished()` | True after all repetitions complete or an error stops the animation. Cleared by `reset()`. |
| `reset()` | Restart the animation from frame 0. |
| `setFilterMode(SkFilterMode)` / `getFilterMode()` | Filter used when drawing through `onDraw`. Default `kLinear`. |

`onDraw(SkCanvas*)` (inherited from `SkDrawable`) renders
`getCurrentFrame()` into the canvas, applying `cropRect`, scaling matrix,
and `postProcess` if set. `onGetBounds()` returns the post-crop bounds.

### Internal frame storage

```c++
struct Frame {
    SkBitmap fBitmap;
    int      fIndex;
    SkCodecAnimation::DisposalMethod fDisposalMethod;
    enum class OnInit { kRestoreIfNecessary, kNoRestore };
    bool init(const SkImageInfo&, OnInit);
    bool copyTo(Frame*) const;
};

Frame fDisplayFrame;    // currently being shown
Frame fDecodingFrame;   // next, being decoded
Frame fRestoreFrame;    // saved copy for kRestorePrevious
```

The three `Frame` slots cover all needs:

- `fDisplayFrame` is what `getCurrentFrame()` returns.
- `fDecodingFrame` is the destination for `decodeNextFrame`. After a
  successful decode, the slots swap.
- `fRestoreFrame` is populated when the decoded frame's
  `disposalMethod == kRestorePrevious` — it caches the pre-decode pixels so
  the *next* frame can restore them.

`Frame::init` may need to allocate a new `SkPixelRef` if the existing one is
shared with an outstanding `SkImage` returned to the user. `OnInit` controls
whether to copy pixels from the old ref into the new one.

`computeNextFrame(int current, bool* animationEnded)` advances the index
honoring the repetition count.

### Disposal state diagram

```
                  decodeNextFrame()
                          │
                          ▼
       ┌─────────────────────────────────────┐
       │ Examine fDisplayFrame.disposalMethod│
       │ (the disposal of the OUTGOING frame)│
       └────────┬────────────┬───────────────┘
                │            │
        kKeep   │            │ kRestoreBGColor       kRestorePrevious
                │            │                              │
                ▼            ▼                              ▼
   leave display    clear display.frameRect        copy fRestoreFrame
   pixels intact    to transparent before          back into display
                    decode                         BEFORE decode
                          │
                          ▼
                  decode new frame's
                  fFrameRect into the
                  destination (using
                  fBlend semantics:
                  kSrcOver | kSrc)
                          │
                          ▼
            if next.disposalMethod ==
            kRestorePrevious:
                copy display → fRestoreFrame
                          │
                          ▼
                 swap display/decoding,
                 advance fCurrentFrameDuration
```

This is the implementation of GIF89a §23 ("Graphic Control Extension")
generalized across formats. The same compositing math is applied
internally by `SkCodec::handleFrameIndex` when `Options::fPriorFrame` is
unset, in case a caller decodes individual frames directly.

### Compositing impact of disposal methods

| Method | Effect on next frame |
|---|---|
| `kKeep` | Next frame is drawn on top of this one. Standard "no clean-up" path. Cheapest. |
| `kRestoreBGColor` | This frame's `fFrameRect` is cleared to transparent before the next frame draws. Effectively "this frame is temporary". |
| `kRestorePrevious` | This frame is discarded entirely; the next frame composites on the frame *before* this one. Used for short-lived overlays (e.g. a cursor) on top of a stable background. Requires the encoder to support an extra restore buffer; correspondingly requires `SkAnimatedImage` to keep `fRestoreFrame`. |

For `kRestorePrevious` to work on the decode side, `SkCodec::FrameInfo`'s
`fRequiredFrame` reports the predecessor *of the predecessor* — the frame
the next one will composite on. `SkAnimatedImage` and decoders both honor
this by skipping past the to-be-restored frame in their dependency walk.

---

## YUVA support

Animated codecs and video-frame producers can deliver pixels in YUV(A)
planes rather than RGB. The relevant types live in `include/core/`:

- `SkYUVAInfo` — describes plane layout, subsampling, color space, origin,
  and chroma siting.
- `SkYUVAPixmapInfo` — `SkYUVAInfo` + per-plane `SkColorType` + per-plane
  row bytes; fully specifies layout without holding pixels.
- `SkYUVAPixmaps` — owns or borrows pixel storage matching a
  `SkYUVAPixmapInfo`.

### SkYUVColorSpace

Declared in `SkImageInfo.h`. Identifies the YUV→RGB conversion matrix; not
to be confused with `SkColorSpace`. The 28 values cover the standard
broadcast/film matrices plus an identity:

| Family | Variants |
|---|---|
| JPEG | `kJPEG_Full_SkYUVColorSpace` (legacy alias `kJPEG_SkYUVColorSpace`) |
| Rec. 601 | `kRec601_Limited` (alias `kRec601`) |
| Rec. 709 | `kRec709_Full`, `kRec709_Limited` (alias `kRec709`) |
| BT.2020 | 8/10/12/16-bit × Full/Limited; alias `kBT2020 = kBT2020_8bit_Limited` |
| FCC | Full / Limited |
| SMPTE 240 | Full / Limited |
| YDZDX | Full / Limited |
| GBR | Full / Limited |
| YCgCo | 8/10/12/16-bit × Full / Limited |
| Identity | `kIdentity_SkYUVColorSpace` (Y→R, U→G, V→B passthrough) |

`SkYUVColorSpaceIsLimitedRange(cs)` reports whether the value is a
limited-range member.

### SkYUVAInfo

```c++
class SK_API SkYUVAInfo {
public:
    enum YUVAChannels { kY, kU, kV, kA, kLast = kA };
    static constexpr int kYUVAChannelCount = 4;
    static constexpr int kMaxPlanes        = 4;

    enum class PlaneConfig {
        kUnknown,
        kY_U_V, kY_V_U, kY_UV, kY_VU, kYUV,  kUYV,
        kY_U_V_A, kY_V_U_A, kY_UV_A, kY_VU_A, kYUVA, kUYVA,
        kLast = kUYVA,
    };

    enum class Subsampling {
        kUnknown,
        k444, k422, k420, k440, k411, k410,
        kLast = k410,
    };

    enum class Siting { kCentered };  // only value supported today

    // ...
};
```

- **`PlaneConfig`** — underscore-separated planes name the layout. `kY_U_V`
  = three planes (Y, U, V); `kY_UV` = two planes (Y followed by interleaved
  UV); `kYUV` = one plane with three interleaved channels; `kUYV` = one
  plane in `(U, Y, V)` order; the `_A` variants add alpha. Channel ordering
  inside a multi-channel plane follows the underlying `SkColorType`:
  Gray = [G], Gray+A = [G, A], RG = [R, G], RGB = [R, G, B], RGBA =
  [R, G, B, A]. For example `kY_UV` puts Y in plane 0 channel 0, U in
  plane 1 channel 0, V in plane 1 channel 1.
- **`Subsampling`** — uses J:a:b notation: `k444` = no subsampling, `k422`
  = horizontal halving, `k420` = both axes halved, etc. Only valid with
  `PlaneConfig` values that put U/V in different planes than Y; alpha is
  never subsampled.
- **`Siting`** — currently only `kCentered`.

Static helpers:

- `NumPlanes(PlaneConfig)` (constexpr) — 0 / 1 / 2 / 3 / 4.
- `NumChannelsInPlane(PlaneConfig, int i)` (constexpr) — 0 / 1 / 2 / 3 / 4.
- `SubsamplingFactors(Subsampling)` — `(xFactor, yFactor)` ratio of Y/A
  values to one U/V value. `(0, 0)` for `kUnknown`.
- `PlaneSubsamplingFactors(config, sub, planeIdx)` — like
  `SubsamplingFactors` but returns `(1, 1)` for non-UV planes; `(0, 0)` for
  invalid combinations.
- `PlaneDimensions(SkISize image, PlaneConfig, Subsampling, SkEncodedOrigin,
  SkISize planeDimensions[kMaxPlanes])` — fills per-plane dimensions
  *as stored in memory* (which may be rotated relative to display
  orientation, depending on `origin`). Returns the number of planes.
- `GetYUVALocations(PlaneConfig, const uint32_t* planeChannelFlags)` —
  computes a `YUVALocations` array (`{plane, channel}` for Y, U, V, A) from
  per-plane channel-presence bitmasks. Used by GPU backends to bind YUV
  textures whose channel layouts are not the canonical defaults.
- `HasAlpha(PlaneConfig)` — true for the `_A` variants.

Instance methods cover `planeConfig`, `subsampling`, `dimensions`, `width`,
`height`, `yuvColorSpace`, `siting{X,Y}`, `origin`, `originMatrix` /
`inverseOriginMatrix` (delegating to `SkEncodedOriginToMatrix` /
`SkEncodedOriginToMatrixInverse`), `hasAlpha`, `numPlanes`, and
`numChannelsInPlane`.

`computeTotalBytes(rowBytes[kMaxPlanes], optional planeSizes[kMaxPlanes])`
computes the total allocation. Returns `SIZE_MAX` and fills `planeSizes`
with `SIZE_MAX` on overflow.

`makeSubsampling(Subsampling)` and `makeDimensions(SkISize)` return modified
copies (invalid if the change is incompatible with the current
`PlaneConfig`).

### SkYUVAPixmapInfo

`include/core/SkYUVAPixmaps.h`.

```c++
class SK_API SkYUVAPixmapInfo {
    enum class DataType {
        kUnorm8,           // 8-bit unsigned normalized
        kUnorm16,          // 16-bit unsigned normalized
        kFloat16,          // 16-bit half float
        kUnorm10_Unorm2,   // 10/10/10 + 2-bit alpha
        kLast = kUnorm10_Unorm2,
    };

    class SupportedDataTypes {
        // bitset over (DataType × planeChannelCount) tuples
        constexpr SupportedDataTypes();
        static constexpr SupportedDataTypes All();
        constexpr bool supported(PlaneConfig, DataType) const;
        void enableDataType(DataType, int numChannels);
    };

    static constexpr SkColorType DefaultColorTypeForDataType(DataType,
                                                              int numChannels);
    static std::tuple<int, DataType> NumChannelsAndDataType(SkColorType);

    SkYUVAPixmapInfo(const SkYUVAInfo&,
                     const SkColorType[kMaxPlanes],
                     const size_t rowBytes[kMaxPlanes]);
    SkYUVAPixmapInfo(const SkYUVAInfo&, DataType,
                     const size_t rowBytes[kMaxPlanes]);

    bool isValid() const;
    bool isSupported(const SupportedDataTypes&) const;

    int numPlanes() const;
    DataType dataType() const;
    size_t rowBytes(int i) const;
    const SkImageInfo& planeInfo(int i) const;
    size_t computeTotalBytes(size_t planeSizes[kMaxPlanes] = nullptr) const;
    bool initPixmapsFromSingleAllocation(void* memory,
                                         SkPixmap pixmaps[kMaxPlanes]) const;
};
```

- All planes must share one `DataType`. Mixed types make the info invalid.
- `DefaultColorTypeForDataType` picks a sensible per-channel-count color
  type:

| numChannels | kUnorm8 | kUnorm16 | kFloat16 | kUnorm10_Unorm2 |
|---:|---|---|---|---|
| 1 | `kGray_8` | `kA16_unorm` | `kA16_float` | (unsupported) |
| 2 | `kR8G8_unorm` | `kR16G16_unorm` | `kR16G16_float` | (unsupported) |
| 3 | `kRGBA_8888` | `kR16G16B16A16_unorm` | `kRGBA_F16` | `kRGBA_1010102` |
| 4 | `kRGBA_8888` | `kR16G16B16A16_unorm` | `kRGBA_F16` | `kRGBA_1010102` |

  3-channel rows reuse the 4-channel formats (alpha forced to 1) because
  there is better GPU-backend support for the alpha-bearing variants.

- `SupportedDataTypes` is a small bitset clients use to advertise what
  combinations they can consume. Codecs query against it via
  `SkCodec::queryYUVAInfo(supportedDataTypes, &outInfo)`. `All()` returns
  every legal combo.

- `initPixmapsFromSingleAllocation(memory, pixmaps[])` divvies one big
  allocation among the planes and writes `numPlanes()` `SkPixmap`s.

### SkYUVAPixmaps

```c++
class SK_API SkYUVAPixmaps {
    using DataType = SkYUVAPixmapInfo::DataType;

    static SkColorType RecommendedRGBAColorType(DataType);

    static SkYUVAPixmaps Allocate(const SkYUVAPixmapInfo&);
    static SkYUVAPixmaps FromData(const SkYUVAPixmapInfo&, sk_sp<SkData>);
    static SkYUVAPixmaps FromExternalMemory(const SkYUVAPixmapInfo&, void*);
    static SkYUVAPixmaps FromExternalPixmaps(const SkYUVAInfo&,
                                             const SkPixmap[kMaxPlanes]);
    static SkYUVAPixmaps MakeCopy(const SkYUVAPixmaps& src);

    bool                                       isValid() const;
    int                                        numPlanes() const;
    const SkYUVAInfo&                          yuvaInfo() const;
    DataType                                   dataType() const;
    SkYUVAPixmapInfo                           pixmapsInfo() const;
    const std::array<SkPixmap, kMaxPlanes>&    planes() const;
    const SkPixmap&                            plane(int i) const;
    SkYUVAInfo::YUVALocations                  toYUVALocations() const;
    bool                                       ownsStorage() const;
};
```

- `Allocate` allocates one block big enough for all planes and stores it
  internally as an `sk_sp<SkData>`.
- `FromData` borrows pixel storage from existing refcounted data.
- `FromExternalMemory` uses a raw pointer; caller must keep the buffer
  alive.
- `FromExternalPixmaps` wraps already-set-up `SkPixmap`s.
- `MakeCopy` deep-copies the source.
- `ownsStorage()` is true when the instance was built via `Allocate`,
  `FromData`, or `MakeCopy`.

### YUVA decode and image creation

Producers (mostly the JPEG codec, plus the AVIF codecs):

```c++
// Query without decoding
bool ok = codec->queryYUVAInfo(SkYUVAPixmapInfo::SupportedDataTypes::All(),
                               &yuvaPixmapInfo);

// Decode planes
auto pixmaps = SkYUVAPixmaps::Allocate(yuvaPixmapInfo);
SkCodec::Result r = codec->getYUVAPlanes(pixmaps);
```

Consumers:

- CPU-side compositing of the planes into RGB is the caller's
  responsibility; `SkImage::MakeFromYUVAPixmaps` is **not** part of the
  generic API — the GPU factories below are the supported path.
- `SkImages::TextureFromYUVAPixmaps(GrRecordingContext*, ...)` (Ganesh) and
  `SkImages::TextureFromYUVAPixmaps(skgpu::graphite::Recorder*, ...)`
  (Graphite) upload the planes and produce a normal `SkImage`.
- `SkImages::TextureFromYUVATextures(...)` wraps existing GPU YUVA
  textures.
- Async readback from an RGB image into YUV planes:
  `SkImage::asyncRescaleAndReadPixelsYUV420(...)` (3 planes) or
  `asyncRescaleAndReadPixelsYUVA420(...)` (4 planes).

### Video-frame typical layout

| Source | Typical config |
|---|---|
| JPEG (4:2:0) | `kY_U_V`, `Subsampling::k420`, `kJPEG_Full` color space |
| MPEG-style decoded video (limited range) | `kY_U_V`, `k420`, `kRec709_Limited` |
| HEIC/AVIF | `kY_U_V` or `kY_UV`, `k420`–`k444`, `kBT2020_*` for HDR |
| Apple "biplanar 4:2:0" | `kY_UV`, `k420` |
| Identity (visualizing planes) | `kYUV`, `k444`, `kIdentity` |

---

## Frame disposal — full state diagram across frames

```
        frame 0 (kNoFrame required, independent)
        ┌───────────────────┐
        │  decode rect 0    │
        │  composite onto   │
        │  cleared canvas   │
        └─────────┬─────────┘
                  │
       ┌──────────┼──────────────────────────────┐
       │          │ disposalMethod[0]            │
       ▼          ▼                              ▼
   kKeep   kRestoreBGColor                kRestorePrevious
       │      │                                  │
       │      └─ clear rect 0 → transparent      │
       │                                         │
       │                                         └─ snapshot canvas → fRestoreFrame
       │                                            (only if NEXT frame's
       │                                             dependency requires it)
       ▼
        frame 1 (requiredFrame[1] = computed by SkFrameHolder)
        ┌───────────────────┐
        │  decode rect 1    │
        │  blend per Blend  │
        │  (kSrcOver | kSrc)│
        └─────────┬─────────┘
                  │
                ...
                  ▼
       ...continues with each frame's disposal applied
       BEFORE decoding the next frame into the canvas.
```

`SkFrameHolder::setAlphaAndRequiredFrame` is what fills in
`requiredFrame` and `hasAlpha` based on the chain of disposals. Codec
implementations rely on it so that callers using
`SkCodec::Options::fPriorFrame` can tell the codec "frame N is already in
the destination" and skip redundant predecessor decodes.

---

## Where to look

- Codec-side animation: `src/codec/SkFrameHolder.h`,
  `src/codec/SkGifCodec.{h,cpp}`, `src/codec/SkPngCodec.{h,cpp}` (APNG),
  `src/codec/SkPngRustCodec.{h,cpp}` (APNG via Rust),
  `src/codec/SkWebpCodec.{h,cpp}`, `src/codec/SkAvifCodec.{h,cpp}`,
  `src/codec/SkCrabbyAvifCodec.{h,cpp}`, `src/codec/SkJpegxlCodec.{h,cpp}`.
- Animated drawable: `include/android/SkAnimatedImage.h`,
  `src/android/SkAnimatedImage.cpp`.
- YUVA: `include/core/SkYUVAInfo.h`, `include/core/SkYUVAPixmaps.h`,
  `src/core/SkYUVAInfo.cpp`, `src/core/SkYUVAPixmaps.cpp`.
- GPU YUVA wrappers: `include/gpu/ganesh/GrYUVABackendTextures.h`,
  `include/gpu/graphite/YUVABackendTextures.h`.
- Animated WebP encode (output side): `SkWebpEncoder::EncodeAnimated`, see
  [image-encoders.md](image-encoders.md).
