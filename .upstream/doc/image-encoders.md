# Image Encoders

Skia's encoded-image *output* lives in two trees:

- `include/encode/` — public headers: `SkEncoder`, `SkPngEncoder`,
  `SkPngRustEncoder`, `SkJpegEncoder`, `SkWebpEncoder`, `SkICC`.
- `src/encode/` — implementations: `SkPngEncoderImpl` /
  `SkPngEncoderBase`, `SkPngRustEncoderImpl`, `SkJpegEncoderImpl`,
  `SkWebpEncoderImpl`, `SkICC.cpp`, `SkJpegGainmapEncoder.cpp`. Each format
  also has a `*_none.cpp` stub used when its third-party dependency is
  excluded from the build (the `SK_ENCODE_<FMT>` build flags).

The HDR-related private headers `include/private/SkJpegGainmapEncoder.h`,
`include/private/SkGainmapInfo.h`, and `include/private/SkHdrMetadata.h` are
referenced from the encode flow and documented in detail in
[hdr-and-gainmaps.md](hdr-and-gainmaps.md).

---

## SkEncoder — incremental abstraction

`include/encode/SkEncoder.h`. Abstract base for *streaming* encoders.

```c++
class SK_API SkEncoder : SkNoncopyable {
public:
    struct SK_API Frame {
        SkPixmap pixmap;
        int      duration;     // milliseconds; for animated outputs
    };

    bool encodeRows(int numRows);   // encode at most `numRows` more rows
    virtual ~SkEncoder() = default;

protected:
    virtual bool onEncodeRows(int numRows) = 0;
    SkEncoder(const SkPixmap& src, size_t storageBytes);

    const SkPixmap&        fSrc;
    int                    fCurrRow;
    skia_private::AutoTMalloc<uint8_t> fStorage;   // scratch row buffer
};
```

The base captures a reference to the source `SkPixmap`, the current row
position (`fCurrRow`), and a scratch buffer. `encodeRows(n)` clamps `n` to
"remaining rows" before calling `onEncodeRows`. `numRows` must be > 0.

The `Frame` struct is the input granularity for animated-output encoders
(`SkWebpEncoder::EncodeAnimated`). Each frame's `pixmap` must match the
canvas size of the first frame; offsets are not yet supported (TODO
[skbug.com/40044793](https://issues.skia.org/issues/skbug.com/40044793)).

Concrete subclasses live in `src/encode/`: `SkPngEncoderImpl`,
`SkPngRustEncoderImpl`, `SkJpegEncoderImpl`, `SkWebpEncoderImpl`. Each format
also exposes top-level `Encode()` convenience functions that wrap a
`Make()` + `encodeRows(INT_MAX)` cycle.

---

## SkJpegEncoder

`include/encode/SkJpegEncoder.h`. Backed by libjpeg-turbo.

### Options

```c++
enum class AlphaOption  { kIgnore, kBlendOnBlack };
enum class Downsample   { k420, k422, k444 };

struct Options {
    int                 fQuality      = 100;             // [0, 100]
    Downsample          fDownsample   = Downsample::k420;// chroma subsampling
    AlphaOption         fAlphaOption  = AlphaOption::kIgnore;
    const SkData*       xmpMetadata   = nullptr;         // optional XMP block
    std::optional<SkEncodedOrigin> fOrigin;              // optional EXIF orient
};
```

- `fQuality` — libjpeg-turbo quality in [0, 100]; 100 is highest.
- `fDownsample`:
  - `k420` (default, libjpeg-turbo default) — UV halved in both axes.
  - `k422` — UV halved horizontally only.
  - `k444` — no subsampling.
  - Ignored when input is `kGray_8` (encoded as grayscale, no chroma) or when
    input is an `SkYUVAPixmaps` (the input's own subsampling is used).
- `fAlphaOption` — JPEGs must be opaque. `kIgnore` (default) drops alpha;
  `kBlendOnBlack` premultiplies onto a black background. The encoder picks
  linear vs legacy blending based on the source color space.
- `xmpMetadata` — raw XMP block written as an APP1 segment.
- `fOrigin` — when present, written to EXIF for the consumer to honor.
- Hidden HDR / gainmap fields: when constructed via `SkJpegGainmapEncoder`
  the `xmpMetadata` and ICC are overridden to encode the UltraHDR layout.

### Functions

```c++
namespace SkJpegEncoder {
    // Pixmap → stream
    SK_API bool Encode(SkWStream* dst, const SkPixmap& src, const Options&);
    // YUVA pixmaps → stream (with optional source color space)
    SK_API bool Encode(SkWStream* dst, const SkYUVAPixmaps& src,
                       const SkColorSpace* srcColorSpace, const Options&);

    // Pixmap → SkData
    SK_API sk_sp<SkData> Encode(const SkPixmap& src, const Options&);
    // SkImage → SkData (raster or GPU-backed; ctx required for GPU)
    SK_API sk_sp<SkData> Encode(GrDirectContext* ctx, const SkImage* img,
                                const Options&);

    // Streaming variants — Make() + encoder->encodeRows() pattern
    SK_API std::unique_ptr<SkEncoder> Make(SkWStream* dst, const SkPixmap& src,
                                           const Options&);
    SK_API std::unique_ptr<SkEncoder> Make(SkWStream* dst,
                                           const SkYUVAPixmaps& src,
                                           const SkColorSpace* srcColorSpace,
                                           const Options&);
}
```

`SkWStream` (the destination) is unowned but must remain valid for the
encoder's lifetime. `Make()` returns `nullptr` on unsupported input.

### Color management

`SkJpegEncoder` writes an ICC profile when the source `SkPixmap`/`SkImage` has
a non-null `SkColorSpace`. ICC bytes are produced via `SkICC::SkWriteICCProfile`
(see below).

### Huffman / progressive

The libjpeg-turbo wrapper uses optimized Huffman tables by default (the
"opt" path). Progressive scan can be selected only by editing the
implementation in `src/encode/SkJpegEncoderImpl.cpp` — there is no public
flag in `Options` for it as of this writing.

### Implementation files

- `src/encode/SkJpegEncoderImpl.{h,cpp}` — full encoder, `SkJpegEncoderImpl`
  subclass of `SkEncoder`.
- `src/encode/SkJPEGWriteUtility.{h,cpp}` — libjpeg-turbo destination
  manager bridging to `SkWStream`.
- `src/encode/SkJpegEncoder_none.cpp` — stub that fails everything when
  `SK_ENCODE_JPEG=0`.

---

## SkPngEncoder (libpng)

`include/encode/SkPngEncoder.h`. Backed by libpng + zlib.

### Filter flags

PNG row filters; combine with `|`.

```c++
enum class FilterFlag : int {
    kZero  = 0x00,
    kNone  = 0x08,
    kSub   = 0x10,
    kUp    = 0x20,
    kAvg   = 0x40,
    kPaeth = 0x80,
    kAll   = kNone | kSub | kUp | kAvg | kPaeth,
};
inline FilterFlag operator|(FilterFlag, FilterFlag);
```

- A single flag → libpng uses that filter for every row.
- Multiple flags → libpng's heuristic picks the best per row. Slower but
  often smaller. Default `kAll` matches libpng's default.
- `kZero` (= 0) is the "no filter selection" sentinel.

### Options

```c++
struct Options {
    FilterFlag           fFilterFlags = FilterFlag::kAll;
    int                  fZLibLevel   = 6;        // [0, 9]; 0 skips zlib
    sk_sp<SkDataTable>   fComments;               // tEXt: keyword/text pairs
    skhdr::Metadata      fHdrMetadata;            // CLLI, MDCV, AGTM
    const SkPixmap*      fGainmap     = nullptr;  // gainmap base pixmap
    const SkGainmapInfo* fGainmapInfo = nullptr;  // gainmap rendering info
};
```

- `fZLibLevel` — passed straight to zlib. 9 = max compression. 0 is a
  special case that creates dramatically larger PNGs (suitable for
  streaming or when downstream re-compresses).
- `fComments` — an `SkDataTable` of alternating keyword/text pairs:
  `[keyword_0, text_0, keyword_1, text_1, ...]`. Each pair becomes one tEXt
  chunk. Keywords are limited to 79 Latin-1 characters per the PNG spec.
- `fHdrMetadata` — `skhdr::Metadata` aggregate from
  `include/private/SkHdrMetadata.h`. CLLI is written via the cLLI chunk
  (`Portable Network Graphics (Third Edition) §11.3.2.8`); MDCV via mDCV
  (§11.3.2.7); the AGTM blob is written verbatim.
- `fGainmap` + `fGainmapInfo` — encode an UltraHDR-style gainmap. The
  gainmap is written as a full PNG inside a `gmAP` chunk, with its
  rendering parameters in a `gdAT` chunk inside that `gmAP`. This implements
  Option B of the [w3c/png#380 discussion](https://github.com/w3c/png/issues/380#issuecomment-2325163149).
  Both fields must be non-null together; supplying only one fails.

### Functions

```c++
namespace SkPngEncoder {
    SK_API bool Encode(SkWStream* dst, const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(GrDirectContext* ctx, const SkImage* img,
                                const Options&);
    SK_API std::unique_ptr<SkEncoder> Make(SkWStream* dst, const SkPixmap& src,
                                           const Options&);
}
```

The streaming `Make()` form is the recommended entry point for very large
images, since it allows back-pressure between row generation and PNG
encoding.

### Color management

ICC profiles attached to the source's `SkColorSpace` are written as iCCP
chunks via `SkICC::SkWriteICCProfile`. Non-color formats (e.g. `kAlpha_8`)
do not write ICC.

### Implementation

- `src/encode/SkPngEncoderImpl.{h,cpp}` — main subclass; sits on
  `SkPngEncoderBase`.
- `src/encode/SkPngEncoderBase.{h,cpp}` — pixel-format conversion,
  shared with `SkPngRustEncoderImpl`.
- `src/encode/SkPngEncoder_none.cpp` — stub for `SK_ENCODE_PNG=0`.

---

## SkPngRustEncoder

`include/encode/SkPngRustEncoder.h`. Drop-in PNG encoder backed by the Rust
[`png` crate](https://crates.io/crates/png) (`third_party/rust/png/`).

### Options

```c++
enum class CompressionLevel : uint8_t { kLow, kMedium, kHigh };

struct Options {
    CompressionLevel   fCompressionLevel = CompressionLevel::kMedium;
    sk_sp<SkDataTable> fComments;       // tEXt keyword/text pairs (Latin-1)
};
```

- `fCompressionLevel` — coarse Low/Medium/High knob (no per-zlib-level
  control, unlike libpng).
- `fComments` — same shape as `SkPngEncoder::Options::fComments`. Latin-1;
  keywords ≤ 79 chars and may not contain non-breaking space (per PNG
  spec). Trailing NULs are stripped.

Not yet supported (tracked at
[crbug.com/379312510](https://crbug.com/379312510)):
- Comments via the `iTXt`/`zTXt` chunks
- ICC profile (`iCCP`) writing
- HDR metadata
- Gainmap chunks

### Functions

```c++
namespace SkPngRustEncoder {
    SK_API bool Encode(SkWStream* dst, const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(GrDirectContext* ctx, const SkImage* img,
                                const Options&);
    SK_API std::unique_ptr<SkEncoder> Make(SkWStream* dst, const SkPixmap& src,
                                           const Options&);
}
```

### Implementation

- `src/encode/SkPngRustEncoder.cpp` — public-facing functions, FFI shim.
- `src/encode/SkPngRustEncoderImpl.{h,cpp}` — `SkEncoder` subclass.

---

## SkWebpEncoder

`include/encode/SkWebpEncoder.h`. Backed by libwebp.

### Options

```c++
enum class Compression { kLossy, kLossless };

struct SK_API Options {
    Compression fCompression = Compression::kLossy;
    float       fQuality     = 100.0f;   // [0, 100]
};
```

The `fQuality` parameter changes meaning based on `fCompression`:

- **`kLossy`** — visual quality. Lower `fQuality` → smaller files, more
  artifacts. 100 is highest.
- **`kLossless`** — encoder *effort*. Lower `fQuality` → faster, larger.
  Higher `fQuality` → slower, smaller. Matches libwebp's API convention.

### Functions

```c++
namespace SkWebpEncoder {
    SK_API bool Encode(SkWStream* dst, const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(const SkPixmap& src, const Options&);
    SK_API sk_sp<SkData> Encode(GrDirectContext* ctx, const SkImage* img,
                                const Options&);

    // Animated WebP
    SK_API bool EncodeAnimated(SkWStream* dst,
                               SkSpan<const SkEncoder::Frame> src,
                               const Options&);
}
```

### Animation

`EncodeAnimated` consumes a span of `SkEncoder::Frame { SkPixmap pixmap; int
duration; }`. The first frame's size is the canvas size; subsequent frames
must match (no offsets, no resizing — see TODO in `SkEncoder.h`). All frames
share one `Options` (no per-frame quality / compression overrides yet — but
the API note flags it as a future addition along with background color and
loop count).

### Implementation

- `src/encode/SkWebpEncoderImpl.cpp` — implementation.
- `src/encode/SkWebpEncoder_none.cpp` — stub for `SK_ENCODE_WEBP=0`.

There is no streaming `SkEncoder` subclass for WebP — libwebp's API does
not expose row-by-row encoding cleanly.

---

## SkICC — ICC profile serialization

`include/encode/SkICC.h`. Free functions and a couple of helpers used by
every encoder that writes color profiles, plus by callers building skcms
B2A/A2B tables.

```c++
SK_API sk_sp<SkData> SkWriteICCProfile(const skcms_TransferFunction&,
                                       const skcms_Matrix3x3& toXYZD50);
SK_API sk_sp<SkData> SkWriteICCProfile(const skcms_ICCProfile*,
                                       const char* description);

// Helpers for encoding skcms A2B / B2A grid_16 tables.
SK_API void SkICCFloatXYZD50ToGrid16Lab(const float* float_xyz,
                                        uint8_t*      grid16_lab);
SK_API void SkICCFloatToTable16(float f, uint8_t* table_16);
```

- The `(transfer_fn, matrix)` overload writes a parametric ICC v4 profile
  (mAB tags, cicp where applicable). Used to round-trip `SkColorSpace`
  values without a source ICC blob.
- The `(skcms_ICCProfile*, description)` overload writes a fully-specified
  profile, embedding `description` as the desc tag. Lossless re-encoding of
  the input profile.
- `SkICCFloatXYZD50ToGrid16Lab` writes 6 bytes; the encoded `grid_16_lab` is
  what skcms decodes (see [skbug.com/40044907](https://issues.skia.org/issues/skbug.com/40044907)
  for spec divergence).
- `SkICCFloatToTable16` writes 2 bytes for the `table_16` member of
  `skcms_Curve`.

The implementation in `src/encode/SkICC.cpp` (~30 KB) is self-contained and
written in plain C (no Skia type dependencies beyond skcms and `SkData`).
`src/encode/SkICCPriv.h` exposes the internal tag layouts to test code.

---

## SkJpegGainmapEncoder

`include/private/SkJpegGainmapEncoder.h`. Builds a JPEG container holding an
SDR base image, an HDR gainmap image, and the metadata to combine them
("UltraHDR" / Adobe Gain Map technology).

```c++
class SK_API SkJpegGainmapEncoder {
public:
    static bool EncodeHDRGM(SkWStream* dst,
                            const SkPixmap& base,
                            const SkJpegEncoder::Options& baseOptions,
                            const SkPixmap& gainmap,
                            const SkJpegEncoder::Options& gainmapOptions,
                            const SkGainmapInfo& gainmapInfo);

    static bool MakeMPF(SkWStream* dst, const SkData** images,
                        size_t imageCount);
};
```

### `EncodeHDRGM`

Produces an UltraHDR JPEG. Layout:

```
APP0 (JFIF) ──► APP1 (XMP, augmented to advertise HDRGM)
APP2 (ICC for base, if any) ──► APP2 (MPF index)
   ┌─ DCT'd base image (JPEG) ─────────────────┐
   │                                            │
   └─ MPF descriptor → individual image #2:     │
      ┌─ APP1 (XMP describing gainmap params)   │
      │  + APP2 (ICC for gainmap, if any)       │
      └─ DCT'd gainmap image (JPEG)             │
                                                 ▼
                                          end of file
```

- `base`/`gainmap` are arbitrary pixmaps (any opaque pixel format JPEG
  accepts; ICC profiles are written through their `SkColorSpace`).
- `baseOptions` / `gainmapOptions` are standard `SkJpegEncoder::Options` —
  but **`xmpMetadata` is overwritten** by the encoder, since it has to
  emit XMP describing the gainmap. Pre-existing XMP content is replaced.
- `gainmapInfo` holds all rendering parameters (gain ratios, gamma, epsilon,
  display ratios, base image type, gainmap math color space). Documented in
  detail in [hdr-and-gainmaps.md](hdr-and-gainmaps.md).

### `MakeMPF`

Concatenates `imageCount` already-encoded JPEG images into a Multi Picture
Format container (CIPA DC-007). Used by `EncodeHDRGM` internally and is
exposed for callers that want to bundle additional sub-images (depth maps,
disparity, etc.).

### Implementation

`src/encode/SkJpegGainmapEncoder.cpp` (~16 KB). Drives the base/gainmap
image encodes via `SkJpegEncoder::Make`, then weaves in MPF + XMP fix-up
chunks before writing to `dst`.

The decode side (extracting a gainmap from a JPEG) lives in `SkCodec`
(`onGetGainmapInfo`/`onGetGainmapCodec`) and is implemented in
`src/codec/SkJpegCodec.cpp` plus the helpers in `SkJpegMetadataDecoderImpl`
and `SkJpegMultiPicture`.

---

## Build flags

Each encoder can be excluded from the build by setting the corresponding
GN/Bazel flag — `skia_use_libjpeg_turbo_encode`, `skia_use_libpng_encode`,
`skia_use_libwebp_encode`, `skia_use_rust_png_encode`. The `*_none.cpp`
files are then linked instead and every `Encode`/`Make` call returns `false`
or `nullptr`. `SkICC` is part of `core` and is always available.

There is no top-level "encode any format from extension" entry point in
modern Skia — pick the namespace explicitly. Older code referenced
`SkEncodeImage(stream, src, format, quality)` (deprecated) which dispatched
based on `SkEncodedImageFormat`; new code should call the format-specific
namespace directly.
