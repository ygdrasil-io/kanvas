# Image Decoders

Skia's encoded-image input lives under two roots:

- `include/codec/` — public headers (`SkCodec`, the per-format decoder
  namespaces, `SkAndroidCodec`, `SkCodecAnimation`, `SkEncodedImageFormat`,
  `SkEncodedOrigin`, `SkPngChunkReader`, `SkPixmapUtils`).
- `src/codec/` — implementations: `SkJpegCodec`, `SkPngCodec`,
  `SkPngRustCodec`, `SkWebpCodec`, `SkBmpCodec` (with `Standard`, `Mask`, and
  `RLE` variants), `SkIcoCodec`, `SkWbmpCodec`, `SkGifCodec`,
  `SkAvifCodec` / `SkCrabbyAvifCodec`, `SkJpegxlCodec`, `SkRawCodec`,
  plus shared infrastructure (`SkFrameHolder`, `SkSampler`, `SkSwizzler`,
  `SkExif`, `SkGainmapInfo.cpp`).

```
                ┌───────────────────────────────┐
                │   encoded bytes (SkData /     │
                │     SkStream)                 │
                └──────────────┬────────────────┘
                               │
                  format peek (32 B minimum)
                               │
                       ┌───────┴────────┐
                       ▼                ▼
                ┌─────────────┐   ┌──────────────┐
                │ SkCodecs::  │   │ per-format   │
                │ Decoder list│   │ ::Decode()   │
                └──────┬──────┘   └───────┬──────┘
                       │                  │
                       └────────┬─────────┘
                                ▼
                         ┌────────────┐    ┌────────────────┐
                         │  SkCodec   │───►│ SkAndroidCodec │
                         └─────┬──────┘    └────────┬───────┘
                               │ getPixels /         │ getAndroidPixels
                               │ scanline /          │ (subset, sampleSize)
                               │ incremental         │
                               ▼                     ▼
                         destination SkPixmap / SkBitmap
```

---

## SkCodec

`include/codec/SkCodec.h` defines the abstract codec base used by every
built-in decoder (and any third-party decoder registered via
`SkCodecs::Register`).

### Construction

The minimum amount of data Skia must be able to peek or read to identify a
format is `SkCodec::MinBufferedBytesNeeded()` (= 32). Streams that can't
peek that much must be rewindable.

| Factory | Notes |
|---|---|
| `MakeFromStream(unique_ptr<SkStream>, SkSpan<const SkCodecs::Decoder>, Result*, SkPngChunkReader*, SelectionPolicy)` | Modern: caller supplies the decoder list. |
| `MakeFromStream(unique_ptr<SkStream>, Result*, SkPngChunkReader*, SelectionPolicy)` | Deprecated form using globally-registered decoders. |
| `MakeFromData(sk_sp<const SkData>, SkSpan<const SkCodecs::Decoder>, SkPngChunkReader*)` | Same, for an already-buffered blob. |
| `MakeFromData(sk_sp<const SkData>, SkPngChunkReader*)` | Deprecated. |

`SelectionPolicy` controls multi-image containers (HEIF, AVIF, JPEG-XL):

- `kPreferStillImage` (default) — pick a still frame; may prevent
  animation-feature use if the input isn't rewindable.
- `kPreferAnimation` — pick the animated image sequence.

`SkPngChunkReader` (in `include/codec/SkPngChunkReader.h`) is an optional
ref-counted callback for handling unknown PNG chunks. Its sole virtual is
`bool readChunk(const char tag[], const void* data, size_t length)`. Skia
calls it during factory construction, `getPixels`, `startScanlineDecode`, or
the first `getScanlines` / `skipScanlines`. Callbacks may run on a different
thread and may run multiple times.

The codec **takes ownership** of the stream when construction succeeds; it
deletes the stream immediately on failure.

### Result enum

| Value | Meaning |
|---|---|
| `kSuccess` | Done. |
| `kIncompleteInput` | Image not fully decoded; partial pixels were produced. Can be returned again from incremental decode after more data is fed. |
| `kErrorInInput` | Like `kIncompleteInput`, but stream is corrupt. From incremental decode this means **stop** — more data won't help. |
| `kInvalidConversion` | Cannot output the requested pixel/color/alpha combination, regardless of dimensions. |
| `kInvalidScale` | Cannot scale to the requested dimensions. |
| `kInvalidParameters` | Pixels null, rowBytes too small, etc. |
| `kInvalidInput` | Bytes don't form a valid image. |
| `kCouldNotRewind` | Stream rewind failed; required for re-decoding the same source. |
| `kInternalError` | Including OOM. |
| `kUnimplemented` | Feature not supported by this codec (e.g. scanline decode). |
| `kOutOfMemory` | Decode would exceed `Options::fMaxDecodeMemory`. |

`SkCodec::ResultToString(Result)` returns a human-readable label.

### Image metadata

- `getInfo()` — a sensible default `SkImageInfo` for decode. ICC profiles that
  cannot map to an `SkColorSpace` are reported as sRGB.
- `dimensions()`, `bounds()`.
- `getICCProfile()` — `const skcms_ICCProfile*` from the encoded data.
- `getHdrMetadata()` — `const skhdr::Metadata&` (CLLI, MDCV, AGTM payload —
  even SDR images can carry HDR metadata indicating how to inverse-tone-map).
- `hasHighBitDepthEncodedData()` — true when the source uses ≥16 bits per
  component.
- `getOrigin()` — `SkEncodedOrigin` from EXIF (`include/codec/SkEncodedOrigin.h`).
  Values mirror the EXIF orientation enum: `kTopLeft` (default), `kTopRight`
  (mirror), `kBottomRight` (180°), `kBottomLeft` (vflip), `kLeftTop`,
  `kRightTop` (90° CW), `kRightBottom`, `kLeftBottom` (90° CCW).
  `SkEncodedOriginToMatrix(origin, w, h)` and
  `SkEncodedOriginToMatrixInverse(...)` produce the `SkMatrix` that maps the
  raw image to display orientation. `SkEncodedOriginSwapsWidthHeight(origin)`
  is true for the four 90°-rotation values.
- `getEncodedFormat()` — `SkEncodedImageFormat`
  (`include/codec/SkEncodedImageFormat.h`): `kBMP`, `kGIF`, `kICO`, `kJPEG`,
  `kPNG`, `kWBMP`, `kWEBP`, `kPKM`, `kKTX`, `kASTC`, `kDNG`, `kHEIF`, `kAVIF`,
  `kJPEGXL`.
- `getEncodedData()` — re-acquires the underlying `SkStream` as a copy
  (returns `nullptr` if the stream is not duplicable).
- `getScaledDimensions(float desiredScale)` — closest size the codec can
  decode to natively. Upscale (≥1.0) returns the original size; values ≤ 0
  are errors and asserted in debug.
- `getValidSubset(SkIRect* desiredSubset)` — returns true if a (possibly
  modified) subset can be decoded. WebP (the only built-in subset-capable
  codec) requires top/left to be even.
- `isAnimated()` — `IsAnimated::kYes`, `kNo`, or `kUnknown`. PNG/AVIF/WebP can
  answer immediately; GIF may not know until enough bytes have arrived.

### Decode options

```c++
struct SkCodec::Options {
    ZeroInitialized fZeroInitialized = kNo_ZeroInitialized;
    const SkIRect*  fSubset          = nullptr;
    int             fFrameIndex      = 0;
    int             fPriorFrame      = kNoFrame;
    size_t          fMaxDecodeMemory = 0;
};
```

- `fZeroInitialized` — `kYes` lets the codec skip zero-fill writes. Default
  `kNo`.
- `fSubset` — only respected by WebP for `getPixels`/incremental decode. In
  scanline decode, only `left`/`width` are honored; `top` must be 0 and
  `height` must equal full height.
- `fFrameIndex` — which frame to decode in animated images.
- `fPriorFrame` — when set to a frame index `[fRequiredFrame, fFrameIndex)`
  whose `fDisposalMethod` is not `kRestorePrevious`, indicates that frame
  is already in the destination buffer; the codec composites on top of it
  and ignores `fZeroInitialized`. `kNoFrame` (= -1) means decode prior
  frames as needed.
- `fMaxDecodeMemory` — cumulative byte budget. Decode aborts with
  `kOutOfMemory` once exceeded.

`ZeroInitialized` is just `{ kYes_ZeroInitialized, kNo_ZeroInitialized }`.

### Full-image decode

- `getPixels(info, pixels, rowBytes, const Options*)` — synchronous decode.
  Mismatched dimensions imply scaling (returns `kInvalidScale` if not
  supported). `info.colorSpace()` controls color conversion: passing the
  ICC-derived space is a no-op; passing null skips conversion entirely.
  Repeated calls must produce identical pixels (codec is referentially
  transparent).
- Convenience overloads: `getPixels(info, pixels, rowBytes)` and
  `getPixels(SkPixmap, const Options* = nullptr)`.
- `getImage([info, opts])` and zero-arg `getImage()` — return
  `tuple<sk_sp<SkImage>, SkCodec::Result>`. Honors `getOrigin()` by rotating
  the output.

A scanline decode in progress is terminated by `getPixels`. To decode
scanlines again afterwards, restart with `startScanlineDecode`.

### Incremental decode

For partial / streaming decodes:

```c++
Result startIncrementalDecode(dstInfo, dst, rowBytes [, opts]);
Result incrementalDecode(int* rowsDecoded = nullptr);
```

- `startIncrementalDecode` may rewind. May return `kIncompleteInput` and be
  retried after more data arrives.
- `incrementalDecode` continues. `kSuccess` means all rows decoded.
  `kIncompleteInput` lets the caller wait for more data, optionally inspecting
  `*rowsDecoded`. Note that "rows initialized" ≠ "rows finished" for
  interlaced PNG.
- Unlike `getPixels`/`getScanlines`, incremental decode does **not** fill
  uninitialized rows; the caller decides whether to pre-clear or post-clear.

### Scanline decode

```c++
Result startScanlineDecode(dstInfo [, opts]);
int    getScanlines(void* dst, int countLines, size_t rowBytes);
bool   skipScanlines(int countLines);
SkScanlineOrder getScanlineOrder() const;
int    nextScanline() const;
int    outputScanline(int inputScanline) const;
```

- `getScanlines` returns the number of lines actually written; if it's less
  than `countLines`, the remainder are filled with a default value (the
  codec's choice).
- `SkScanlineOrder` values: `kTopDown_SkScanlineOrder` (overwhelming default)
  and `kBottomUp_SkScanlineOrder` (e.g. classic upside-down BMPs). For
  bottom-up codecs, `nextScanline()` reports the *output* y for the next
  row; clients can use it or just keep their own counter.
- `outputScanline(inputScanline)` maps from "row index in the encoded data"
  to "y-coordinate in the output image" (only differs for bottom-up BMP and
  interlaced GIF).
- Not all codecs support scanline mode (notably WebP and AVIF return
  `kUnimplemented`).

### YUVA decode

```c++
bool   queryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes&,
                     SkYUVAPixmapInfo*) const;
Result getYUVAPlanes(const SkYUVAPixmaps& yuvaPixmaps);
```

`queryYUVAInfo` reports plane configuration without decoding; pass the
`SupportedDataTypes` bitset of what the caller can consume, get back the
specific `SkYUVAPixmapInfo` describing planes/subsampling/types/row-bytes.
`getYUVAPlanes` always performs a full decode — call `queryYUVAInfo` first if
you only want metadata.

### Animation

- `getFrameCount()` — number of frames. May read through the stream; for
  partially-received GIF/APNG the returned count grows as more bytes arrive.
- `getRepetitionCount()` — number of *additional* loops past the first play
  through (i.e. count 4 means each frame plays 5 times). Returns
  `kRepetitionCountInfinite` (= -1) for forever-looping. Returns 0 for both
  still images and play-once animations; disambiguate via `isAnimated()`.
- `kNoFrame` (= -1) and `kRepetitionCountInfinite` (= -1) are sentinel
  constants.

`SkCodec::FrameInfo` (struct):

| Field | Meaning |
|---|---|
| `int fRequiredFrame` | Earliest frame that must be drawn before this one for blending. `kNoFrame` if independent. |
| `int fDuration` | Display duration in milliseconds. |
| `bool fFullyReceived` | End marker for this frame is in the stream (≠ "decode would succeed"). |
| `SkAlphaType fAlphaType` | Conservative — may say non-opaque even if all alphas would resolve to opaque. |
| `bool fHasAlphaWithinBounds` | Conservative same as above. |
| `SkCodecAnimation::DisposalMethod fDisposalMethod` | How to clean up before the next frame. |
| `SkCodecAnimation::Blend fBlend` | `kSrcOver` or `kSrc` blend with prior frame. |
| `SkIRect fFrameRect` | Updated rect; may be empty. Always `⊆ dimensions()`. |

`getFrameInfo(int index, FrameInfo*)` is metadata-only and never reads
through the stream — call `getFrameCount()` first if necessary.
`getFrameInfo()` returns `std::vector<FrameInfo>` by reading through the
stream; future decodes may need a rewind afterwards.

### Custom decoder registration

```c++
namespace SkCodecs {
    using DecodeContext         = void*;
    using IsFormatCallback      = bool (*)(const void*, size_t);
    using MakeFromStreamCallback = std::unique_ptr<SkCodec> (*)(
        std::unique_ptr<SkStream>, SkCodec::Result*, DecodeContext);

    struct Decoder {
        std::string_view id;             // e.g. "png", "jpg"
        IsFormatCallback isFormat;
        MakeFromStreamCallback makeFromStream;
    };

    void Register(Decoder);
    sk_sp<SkImage> DeferredImage(unique_ptr<SkCodec>,
                                 std::optional<SkAlphaType> = nullopt);
}
```

`Register` adds (or replaces by `id`) a decoder in a linked list. Not
thread-safe — call before the first `MakeFromStream`/`MakeFromData`.

`SkCodecs::DeferredImage(codec, alphaType)` is the preferred way to create a
lazy `SkImage` from a codec — it lets you choose the decoder explicitly,
unlike `SkImages::DeferredFromEncodedData`. Forcing
`alphaType = kOpaque_SkAlphaType` is rejected (returns null).

The legacy `SkCodec::Register(peek, make)` still exists for backwards
compatibility.

### Subclass API

The protected/virtual surface includes `onGetEncodedFormat`, `onGetPixels`,
`onQueryYUVAInfo`/`onGetYUVAPlanes`, `onGetValidSubset`, `onRewind`,
`onGetScanlineOrder`, `onOutputScanline`, `conversionSupported`,
`usesColorXform`, `onGetFrameCount`/`onGetFrameInfo`/`onGetRepetitionCount`,
`onIsAnimated`, the scanline/incremental hooks, `onGetGainmapCodec`, and
`onGetGainmapInfo` (with a `SkStream*` overload retained for JPEG until
[issues.skia.org/363544350](https://issues.skia.org/issues/363544350)
finishes deprecation). `applyColorXform` and `allocateFromBudget` are helpers
for the subclass to call.

---

## Per-format decoders

Every built-in decoder lives in its own namespace, exporting a uniform
`Is<Format>(const void*, size_t)`, `Decode(stream/data, Result*,
DecodeContext = nullptr)`, and a `Decoder()` constexpr that returns a
`SkCodecs::Decoder` registration record.

The registration shape (e.g. `SkPngDecoder::Decoder()` returns
`{ "png", IsPng, Decode }`) lets clients build a `SkSpan<const Decoder>` once
and pass it to `SkCodec::MakeFromStream`/`MakeFromData` to control which
formats are accepted. Not supplying a decoder list (the deprecated overloads)
falls back on whatever `SkCodecs::Register` has accumulated.

### JPEG — `SkJpegDecoder` (`SkJpegCodec` in src/codec)

- Decoder id `"jpeg"`.
- DecodeContext **ignored**.
- Backed by libjpeg-turbo (`third_party/libjpeg-turbo/`).
- Supports scanline decode, scaling (factors of 1/2, 1/4, 1/8), YUVA decode
  (the most complete YUVA support of any built-in codec), EXIF orientation,
  ICC profiles, gainmap extraction (multi-picture JPEG and ISO 21496-1
  metadata).
- Subclass implementation `SkJpegCodec` lives in `src/codec/SkJpegCodec.cpp`.
  Companion files: `SkJpegDecoderMgr.{h,cpp}` (libjpeg setup),
  `SkJpegMetadataDecoderImpl.{h,cpp}` (EXIF/XMP/ICC/MPF parse),
  `SkJpegSegmentScan.{h,cpp}` (segment iterator),
  `SkJpegMultiPicture.{h,cpp}` (MPF gainmap extraction).

### PNG — `SkPngDecoder` (libpng, `SkPngCodec`)

- Decoder id `"png"`.
- DecodeContext, if non-null, is expected to be `SkPngChunkReader*`.
- Backed by libpng (`third_party/libpng/`). Supports scanline, incremental,
  interlaced (Adam7), color profiles, tRNS, animation (APNG).
- Common base `SkPngCodecBase.{h,cpp}` shared with the Rust decoder for
  format helpers.
- `SkPngCompositeChunkReader.{h,cpp}` is the chunk reader used internally for
  gainmap-bearing PNGs.

### PNG — `SkPngRustDecoder` (`SkPngRustCodec`)

- Decoder id `"png"`. Drop-in replacement for libpng, written in Rust
  (`third_party/rust/png/`).
- DecodeContext **not** used (no `SkPngChunkReader` plumbing).
- Implementation in `src/codec/SkPngRustCodec.cpp` (~50 KB), adapter in
  `SkPngRustDecoder.cpp`. Sits on top of `SkPngCodecBase`.

### WebP — `SkWebpDecoder` (`SkWebpCodec`)

- Decoder id `"webp"`. Backed by libwebp.
- DecodeContext ignored.
- Supports lossy + lossless, animation, alpha, ICC, EXIF/XMP, subset decode
  (with even top/left), scaled decode.
- No scanline mode (`kUnimplemented`).

### GIF — `SkGifDecoder` (`SkGifCodec`)

- Decoder id `"gif"`. Backed by `third_party/wuffs` (formally verified, safer
  than libgif).
- DecodeContext ignored.
- Animation + per-frame disposal/blend.

### BMP — `SkBmpDecoder` (`SkBmpCodec` and friends)

- Decoder id `"bmp"`.
- DecodeContext ignored.
- Internally dispatches to `SkBmpStandardCodec`, `SkBmpMaskCodec`,
  `SkBmpRLECodec` based on header type. `SkBmpBaseCodec` shares helpers.
- Supports kBottomUp scanline order.

### ICO — `SkIcoDecoder` (`SkIcoCodec`)

- Decoder id `"ico"`. Container format that wraps inner BMP or PNG entries;
  picks the largest matching entry.
- DecodeContext ignored.

### WBMP — `SkWbmpDecoder` (`SkWbmpCodec`)

- Decoder id `"wbmp"`. Wireless Application Protocol Bitmap (1-bit B/W).
- DecodeContext ignored. No color transform (`usesColorXform()` returns
  false).

### AVIF — `SkAvifDecoder`

`SkAvifDecoder` is a *meta* namespace: it exposes two implementations and
auto-routes the bare `Decoder()` call to `LibAvif`.

- `SkAvifDecoder::LibAvif::IsAvif`, `Decode`, `Decoder()` — backed by
  libavif (`third_party/libavif/`); decoder id `"avif"`.
- `SkAvifDecoder::CrabbyAvif::IsAvif`, `Decode`, `Decoder()` — backed by the
  Rust **CrabbyAvif** crate (`third_party/crabbyavif/`); decoder id
  `"avif"`. Implementation in `src/codec/SkCrabbyAvifCodec.{h,cpp}`.
- The bare `SkAvifDecoder::Decoder()` is shorthand for `LibAvif::Decoder()`,
  preserved for backwards compatibility.
- DecodeContext ignored.

### JPEG-XL — `SkJpegxlDecoder` (`SkJpegxlCodec`)

- Decoder id `"jpegxl"`. Backed by libjxl (`third_party/libjxl/`).
- DecodeContext ignored.
- Supports HDR, animation, lossless and lossy modes.

### RAW — `SkRawDecoder` (`SkRawCodec`)

- Decoder id `"raw"`. Backed by piex + dng-sdk (`third_party/piex/`,
  `third_party/dng_sdk/`).
- `IsRaw` is hard-coded to `return true` because raw signatures cannot be
  identified from the first 32 bytes (Sony ARW alone needs ~10 KB). To still
  let other formats win, callers should register the raw decoder *last* in
  the list — `SkCodecs::Register` documents that the raw decoder is always
  considered last regardless of registration order.
- DecodeContext ignored.

### Static-only headers

- `include/codec/SkPixmapUtils.h` — small helpers for orientation:
  - `SkPixmapUtils::Orient(dst, src, SkEncodedOrigin)` — apply EXIF
    transformation when copying.
  - `SkPixmapUtils::SwapWidthHeight(SkImageInfo)` — for the four 90°
    orientations.

---

## SkAndroidCodec

`include/codec/SkAndroidCodec.h` adds Android-specific features over a
plain `SkCodec`: integer downscale (`fSampleSize`), subset decoding,
gainmap extraction tied to the codec, and color-output negotiation
helpers used by the Android framework.

### Construction

- `MakeFromCodec(unique_ptr<SkCodec>)` — wraps an existing codec.
- `MakeFromStream(unique_ptr<SkStream>, SkPngChunkReader* = nullptr)`.
- `MakeFromData(sk_sp<const SkData>, SkPngChunkReader* = nullptr)`.

### Output negotiation

- `getInfo()` — cached `SkImageInfo` of the underlying codec.
- `getICCProfile()` — forwards to `SkCodec`'s `SkEncodedInfo`.
- `getEncodedFormat()`.
- `computeOutputColorType(SkColorType requested)` — may upgrade to
  `kRGBA_F16` for HDR content. Otherwise returns the request if supported, a
  best match otherwise.
- `computeOutputAlphaType(bool requestedUnpremul)` — honors
  `requestedUnpremul` if the image has alpha.
- `computeOutputColorSpace(SkColorType outputColorType,
  sk_sp<SkColorSpace> prefColorSpace = nullptr)`.

### Subset & sample-size

- `computeSampleSize(SkISize* size)` — input is desired output size; output
  is the actual rounded-up size achievable with that sample size; returns
  the `fSampleSize` to use.
- `getSampledDimensions(int sampleSize)` — codec's preferred output for that
  sample size. Always ≥ 1×1.
- `getSupportedSubset(SkIRect* desiredSubset)` — like `SkCodec::getValidSubset`
  but valid Android-side.
- `getSampledSubsetDimensions(int sampleSize, const SkIRect& subset)` — size
  after both sample-size and subset are applied.

### Decode

```c++
struct AndroidOptions : public SkCodec::Options {
    int fSampleSize = 1;     // integer downscale factor
};

Result getAndroidPixels(const SkImageInfo&, void*, size_t,
                        const AndroidOptions*);
Result getAndroidPixels(const SkImageInfo&, void*, size_t);  // defaults
Result getPixels(const SkImageInfo&, void*, size_t);          // shim
```

### Gainmap

- `getGainmapAndroidCodec(SkGainmapInfo*, unique_ptr<SkAndroidCodec>*)` —
  populates the info and (optionally) the gainmap codec. Available for any
  format whose `SkCodec` reports a gainmap (HEIC, AVIF, JPEG, etc.).
- `getAndroidGainmap(SkGainmapInfo*, unique_ptr<SkStream>*)` — JPEG-only
  legacy path; will be removed once
  [issues.skia.org/363544350](https://issues.skia.org/issues/363544350)
  resolves.

`codec()` returns the underlying `SkCodec*`. The protected virtuals
subclasses must implement are `onGetSampledDimensions`,
`onGetSupportedSubset`, and `onGetAndroidPixels`. The default subclass is
`SkAndroidCodecAdapter` (in `src/codec/SkAndroidCodecAdapter.{h,cpp}`),
which routes everything to the wrapped `SkCodec` and applies sampling via
`SkSampledCodec`.

---

## SkCodecAnimation

`include/codec/SkCodecAnimation.h` is a tiny header that defines two enums
shared across animated codecs and `SkAnimatedImage`:

```c++
enum class DisposalMethod {
    kKeep            = 1,  // overlay next frame on this one
    kRestoreBGColor  = 2,  // clear this frame's rect to BG before next
    kRestorePrevious = 3,  // discard this frame; next blends on the one before
};

enum class Blend {
    kSrcOver,  // standard alpha-over
    kSrc,      // overwrite destination
};
```

Numbers match GIF89a values. GIF disposal value 0 is treated as `kKeep`;
value 4 is treated as `kRestorePrevious`.

The internal `SkFrame` (`src/codec/SkFrameHolder.h`) carries a superset of
this metadata: `id`, `rect`, `hasAlpha`, `requiredFrame`, `disposalMethod`,
`duration`, `blend`. `SkFrameHolder` collects per-frame `SkFrame`s and
computes `setAlphaAndRequiredFrame` — which finds the earliest frame this
one needs for compositing and whether the resulting frame is opaque.

---

## Decode pipeline flow

```
              ┌──────────────────┐
              │ SkStream / SkData│
              └────────┬─────────┘
                       ▼ (peek 32 B)
              ┌────────┴──────────┐
              │ format dispatch   │ ── unmatched ──► nullptr + Result
              │ (Decoders[].isFmt)│
              └────────┬──────────┘
                       ▼
              ┌────────┴─────────┐
              │ Decoders[].make  │  reads more, parses headers
              │ (e.g. SkPngCodec)│
              └────────┬─────────┘
                       ▼
              ┌──────────────────┐
              │     SkCodec      │ ◄── orientation, ICC, hdr metadata,
              │                  │     repetition count, frame count
              └────┬─────────┬───┘
                   │         │
                   │         └──────► SkAndroidCodec (downscale, subset, gainmap)
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   getPixels  startScanline  startIncremental
   (sync,    Decode +        Decode + incrementalDecode
   one-shot) getScanlines
                   │
                   ▼
          colorXform (skcms)  ──► dst SkImageInfo (CT/AT/CS)
                   │
                   ▼
            destination buffer
```

Color transformation goes through skcms (`modules/skcms/skcms.h`). The codec
records `fXformTime` ∈ `{kNo, kPalette, kDecodeRow}` to choose where to apply
it (palette decode applies once into the LUT, scanline decode applies
per-row, full decode is internal). `applyColorXform(dst, src, count)` is the
helper subclasses call.

`fillIncompleteImage` fills uninitialized rows on `kIncompleteInput` with a
default value when `getPixels` / `getScanlines` returns short.
`SkSampledCodec` (in `src/codec/SkSampledCodec.{h,cpp}`) wraps a codec to
provide arbitrary integer downscale via `SkSampler`.

---

## The decode budget

`Options::fMaxDecodeMemory` is enforced by `SkCodec::allocateFromBudget` —
each subclass calls it before allocating large internal buffers (palette
tables, intermediate planes, color-xform staging). Exceeding the budget
returns `kOutOfMemory`. 0 (the default) disables the cap.
