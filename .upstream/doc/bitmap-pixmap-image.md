# Bitmap, Pixmap & Image

Skia's pixel-data hierarchy spans four levels of abstraction, ordered from the
lightest "stub of metadata" up to the heaviest "draw-ready, possibly GPU
resident image":

| Type | Owns pixels? | Mutable? | Where |
|---|---|---|---|
| `SkImageInfo` | no — describes only | n/a | `include/core/SkImageInfo.h` |
| `SkPixmap` | no — non-owning view | yes (writable\_addr) | `include/core/SkPixmap.h` |
| `SkBitmap` | yes — through `SkPixelRef` | yes | `include/core/SkBitmap.h` |
| `SkImage` | yes — immutable, raster or GPU | no | `include/core/SkImage.h` |

```
                                  SkImageInfo
                            (W,H + ColorType + AlphaType + ColorSpace)
                                       ^
                                       │ uses
                                       │
   +--------------+   addr,rowBytes   +-+--------+   wraps   +------------+
   | client memory|<------------------+ SkPixmap +-----------> SkBitmap   |
   +--------------+                   +----------+    in     | (sk_sp<    |
                                                             |  SkPixelRef|
                                                             |  > +       |
                                                             |  SkPixmap  |
                                                             |  cache)    |
                                                             +-----+------+
                                                                   |  asImage
                                                                   v
                                                              +-----------+
                                                              |  SkImage  |
                                                              | (raster   |
                                                              |  or GPU,  |
                                                              |  immut.)  |
                                                              +-----------+
```

---

## SkImageInfo

`SkImageInfo` is a value type that combines pixel dimensions with a
dimensionless `SkColorInfo` (color type + alpha type + optional color space).
Every other type in this hierarchy embeds or refers to one.

### SkColorType (28 values)

Each value names channel order from least-significant to most-significant on a
little-endian system. Defined in `include/core/SkColorType.h`.

| Value | Bits per pixel | Layout (LE) | Notes |
|---|---:|---|---|
| `kUnknown_SkColorType` | 0 | — | sentinel for invalid info |
| `kAlpha_8_SkColorType` | 8 | `[A:7..0]` | RGB are 0; alpha-only mask |
| `kRGB_565_SkColorType` | 16 | `[R:15..11 G:10..5 B:4..0]` | always opaque |
| `kARGB_4444_SkColorType` | 16 | `[R:15..12 G:11..8 B:7..4 A:3..0]` | rare today |
| `kRGBA_8888_SkColorType` | 32 | `[A:31..24 B:23..16 G:15..8 R:7..0]` | render-target; canonical 32-bit |
| `kRGB_888x_SkColorType` | 32 | `[x:31..24 B:23..16 G:15..8 R:7..0]` | alpha forced opaque |
| `kBGRA_8888_SkColorType` | 32 | `[A:31..24 R:23..16 G:15..8 B:7..0]` | swizzle of RGBA8888 |
| `kRGBA_1010102_SkColorType` | 32 | `[A:31..30 B:29..20 G:19..10 R:9..0]` | wide-gamut SDR |
| `kBGRA_1010102_SkColorType` | 32 | `[A:31..30 R:29..20 G:19..10 B:9..0]` | swizzle |
| `kRGB_101010x_SkColorType` | 32 | `[x:31..30 B:29..20 G:19..10 R:9..0]` | alpha forced opaque |
| `kBGR_101010x_SkColorType` | 32 | `[x:31..30 R:29..20 G:19..10 B:9..0]` | swizzle |
| `kBGR_101010x_XR_SkColorType` | 32 | extended-range BGR | maps to `[-0.752941, 1.25098]`; matches `MTLPixelFormatBGR10_XR` |
| `kBGRA_10101010_XR_SkColorType` | 64 | extended-range BGRA, 6 padding bits per channel | matches `MTLPixelFormatBGRA10_XR` |
| `kRGBA_10x6_SkColorType` | 64 | RGBA10 with 6 padding bits per channel | |
| `kGray_8_SkColorType` | 8 | `[G:7..0]` | replicated to RGB at decode |
| `kRGBA_F16Norm_SkColorType` | 64 | half-float per channel, range `[0,1]` | |
| `kRGBA_F16_SkColorType` | 64 | half-float per channel, extended range | HDR working space |
| `kRGB_F16F16F16x_SkColorType` | 64 | half-float RGB + ignored 16 bits | alpha forced opaque |
| `kRGBA_F32_SkColorType` | 128 | full float per channel | |
| **Read-only formats (cannot be a render target):** | | | |
| `kR8G8_unorm_SkColorType` | 16 | `[G:15..8 R:7..0]`; B=0, A=opaque | |
| `kA16_float_SkColorType` | 16 | half-float alpha; RGB=0 | |
| `kR16G16_float_SkColorType` | 32 | half-float RG; B=0, A=opaque | |
| `kA16_unorm_SkColorType` | 16 | unorm alpha; RGB=0 | |
| `kR16_unorm_SkColorType` | 16 | unorm red; GB=0, A=opaque | |
| `kR16G16_unorm_SkColorType` | 32 | unorm RG; B=0, A=opaque | |
| `kR16G16B16A16_unorm_SkColorType` | 64 | unorm RGBA | |
| `kSRGBA_8888_SkColorType` | 32 | RGBA8 with sRGB transfer | hardware-decoded by GPU |
| `kR8_unorm_SkColorType` | 8 | unorm red; GB=0, A=opaque | |

`kN32_SkColorType` resolves at compile time to either `kBGRA_8888` or
`kRGBA_8888` depending on `SK_PMCOLOR_BYTE_ORDER`. `SkColorTypeBytesPerPixel(ct)`
and `SkColorTypeIsAlwaysOpaque(ct)` (in `SkImageInfo.h`) are utility queries.
`kSkColorTypeCnt` is the count of valid values.

### SkAlphaType (4 values)

Defined in `include/core/SkAlphaType.h`.

| Value | Meaning |
|---|---|
| `kUnknown_SkAlphaType` | Uninitialized / invalid. |
| `kOpaque_SkAlphaType` | Hint that all alpha values are 1.0. Skia may take fast paths. Drawing a non-opaque pixel through opaque info is undefined. |
| `kPremul_SkAlphaType` | RGB has been multiplied by alpha. The standard internal representation. |
| `kUnpremul_SkAlphaType` | RGB independent of alpha. Used at I/O boundaries (encoders, getColor). |

`SkAlphaTypeIsOpaque(at)` is a free function. `SkColorTypeValidateAlphaType(ct,
at, &canonical)` (in `SkImageInfo.h`) returns whether a `(colorType,
alphaType)` pair is valid and writes the canonical alpha type.

### SkColorInfo

Aggregates color type + alpha type + (refcounted) color space, without
dimensions. Methods:

- `colorSpace()`, `refColorSpace()`, `colorType()`, `alphaType()`.
- `isOpaque()` — true when the alpha type is opaque or the color type is
  always-opaque.
- `gammaCloseToSRGB()`.
- `makeAlphaType(SkAlphaType)`, `makeColorType(SkColorType)`,
  `makeColorSpace(sk_sp<SkColorSpace>)` — return new `SkColorInfo`.
- `bytesPerPixel()`, `shiftPerPixel()` — derived from color type.
- `operator==`, `operator!=`.

### SkImageInfo

`SkImageInfo` = `SkColorInfo` + `SkISize` dimensions. It is not refcounted; it
is cheap to copy.

Factories:

- `SkImageInfo::Make(w, h, ct, at[, cs])` — explicit color type. Variants
  taking `SkISize` and `SkColorInfo` exist.
- `SkImageInfo::MakeN32(w, h, at[, cs])` — picks `kN32_SkColorType` (the
  platform's optimal 32-bit format).
- `SkImageInfo::MakeS32(w, h, at)` — N32 with sRGB color space.
- `SkImageInfo::MakeN32Premul(w, h[, cs])` / `MakeN32Premul(SkISize[, cs])`.
- `SkImageInfo::MakeA8(w, h)` / `MakeA8(SkISize)` — alpha-only premul.
- `SkImageInfo::MakeUnknown(w, h)` / `MakeUnknown()` — placeholder; can not be
  drawn from or to.

Accessors: `width()`, `height()`, `dimensions()`, `bounds()`, `colorType()`,
`alphaType()`, `colorSpace()`, `refColorSpace()`, `colorInfo()`,
`bytesPerPixel()`, `shiftPerPixel()`, `gammaCloseToSRGB()`, `isOpaque()`,
`isEmpty()`, `validRowBytes(rb)`.

Mutators (return new `SkImageInfo`): `makeWH(w, h)`, `makeDimensions(SkISize)`,
`makeAlphaType(at)`, `makeColorType(ct)`, `makeColorSpace(cs)`. `reset()`
clears in place.

Size queries:

- `minRowBytes64()` returns `width * bytesPerPixel` as `uint64_t`. Use this
  before allocating to detect overflow.
- `minRowBytes()` returns the same as `size_t`, or 0 if it overflows int32_t —
  Skia caps row bytes at 31 bits to keep `SkBitmap`'s pixel-ref offsets in
  range.
- `computeOffset(x, y, rowBytes)` returns the byte offset into a pixel buffer.
- `computeByteSize(rowBytes)` returns the buffer size; `SIZE_MAX` on overflow.
  Check with the static helper `SkImageInfo::ByteSizeOverflowed(byteSize)`.
- `computeMinByteSize()` is `computeByteSize(minRowBytes())`.

### SkYUVColorSpace

Although declared in `SkImageInfo.h`, `SkYUVColorSpace` is logically separate
from `SkImageInfo`'s color space. It identifies the YUV→RGB conversion matrix.
Values include JPEG-Full, Rec601-Limited, Rec709-Full/Limited,
BT2020-(8/10/12/16)bit-Full/Limited, FCC-Full/Limited, SMPTE240-Full/Limited,
YDZDX-Full/Limited, GBR-Full/Limited, YCgCo-(8/10/12/16)bit-Full/Limited, and
`kIdentity_SkYUVColorSpace` (no transform — used to visualize planes
directly). Legacy aliases `kJPEG_SkYUVColorSpace`, `kRec601_SkYUVColorSpace`,
`kRec709_SkYUVColorSpace`, `kBT2020_SkYUVColorSpace` map to the *Limited*
variants of their Full/Limited pair (except JPEG which is Full).
`SkYUVColorSpaceIsLimitedRange(cs)` returns true for limited-range members.

---

## SkPixmap

`SkPixmap` is a tuple `(SkImageInfo, const void* pixels, size_t rowBytes)`. It
does **not** own pixels; the caller controls lifetime. It is the "lowest level"
type for accessing or manipulating raster pixel memory and is used as the
input/output type for almost every Skia API that takes raw pixels.

### Construction & lifetime

- Default constructor → empty pixmap (unknown info, null pixels, rowBytes 0).
- `SkPixmap(info, addr, rowBytes)` — direct.
- `reset()` → empty.
- `reset(info, addr, rowBytes)` — re-target to new memory.
- `setColorSpace(sk_sp<SkColorSpace>)` — replace just the color space without
  touching pixels.

### Geometry

`info()`, `rowBytes()`, `addr()`, `width()`, `height()`, `dimensions()`,
`colorType()`, `alphaType()`, `colorSpace()`, `refColorSpace()`, `bounds()`,
`isOpaque()`, `rowBytesAsPixels()` (= `rowBytes >> shiftPerPixel`),
`shiftPerPixel()`, `computeByteSize()`, `computeIsOpaque()`.

`extractSubset(SkPixmap* subset, const SkIRect& area)` returns true if the
intersection is non-empty and writes a pixmap that points at the same backing
memory.

### Single-pixel access

- `getColor(x, y)` → `SkColor` (sRGB, unpremul). Color space in info is
  ignored; conversion may lose precision.
- `getColor4f(x, y)` → `SkColor4f`. Higher precision; otherwise same as above.
- `getAlphaf(x, y)` → `float` in `[0, 1]`.

### Typed pointer access

`addr(x, y)` is the generic form. The typed variants assert on color type and
return raw pointers — both readable (`addr8`, `addr16`, `addr32`, `addr64`,
`addrF16`, plus `(x, y)` overloads) and writable (`writable_addr*` variants
plus the type-erased `writable_addr()` and `writable_addr(x, y)`). Used by
blitters and pixel-walking code that needs minimum overhead.

Pixel-size assertions:
- `addr8` ⇔ `kAlpha_8` or `kGray_8` (1 BPP)
- `addr16` ⇔ `kRGB_565` or `kARGB_4444` (2 BPP)
- `addr32` ⇔ `kRGBA_8888` or `kBGRA_8888` (4 BPP)
- `addr64` ⇔ `kRGBA_F16` (8 BPP)
- `addrF16` is `addr64` reinterpreted as `uint16_t*`.

### Bulk pixel transfer

`readPixels` copies and converts pixels. Five overloads:

- `readPixels(dstInfo, dstPixels, dstRowBytes)` — full pixmap, origin (0, 0).
- `readPixels(dstInfo, dstPixels, dstRowBytes, srcX, srcY)` — with source
  offset; `srcX`/`srcY` may be negative to copy only a top/left slice.
- `readPixels(const SkPixmap& dst, srcX, srcY)`.
- `readPixels(const SkPixmap& dst)`.

Conversion is allowed unless one of the strict matches kicks in: `kGray_8`
must round-trip (color type and color space match), `kAlpha_8` color type must
match, an opaque alpha type forces destination to also be opaque, and a null
color space forces destination color space to be null too.

`scalePixels(const SkPixmap& dst, const SkSamplingOptions&)` — copies and
resamples, honoring the color-type/space/alpha rules above.

`erase(SkColor color, const SkIRect& subset)` and `erase(SkColor color)`
overwrite pixel data, treating `color` as sRGB unpremul. The `SkColor4f`
overload optionally takes a `const SkIRect*` subset (null = all pixels).

---

## SkBitmap

`SkBitmap` is the heap-managed, mutable raster container. It pairs an
`SkPixmap` with an owning `sk_sp<SkPixelRef>` (which is what actually holds
the storage; multiple `SkBitmap` may share one).

`SkBitmap` is **not thread-safe** — each thread should hold its own copy of
the metadata, even if they share the underlying `SkPixelRef` (which itself is
thread-safe).

### Creation & ownership

```
+--------------+   sk_sp<SkPixelRef> shared    +------------+
|  SkBitmap A  +------+                  +---->| SkPixelRef |
+--------------+      |                  |     |  pixels[]  |
                      v                  |     +------------+
                +-----+----+             |
                | SkPixmap |  rowBytes,  |
                | (cached) |  origin (in |
                +----------+   pixelRef) |
+--------------+                         |
|  SkBitmap B  +-------------------------+
+--------------+
```

- Default ctor → empty bitmap (no pixels).
- Copy ctor / `operator=` — shares the same `SkPixelRef` (pixel storage is
  shared, not copied).
- Move ctor / `operator=`.
- `swap(SkBitmap& other)`.
- Destructor releases the `SkPixelRef`.

### Configuration

- `setInfo(info, rowBytes = 0)` — sets `SkImageInfo` and frees pixels. Returns
  true on success. Color-type-specific alpha-type fixups are applied
  (e.g. `kRGB_565` forces `kOpaque`, `kAlpha_8` upgrades unpremul to premul).
- `setAlphaType(at)` — alters the alpha type if compatible. Mutates the
  shared `SkPixelRef`'s view of alpha type.
- `setColorSpace(sk_sp<SkColorSpace>)` — likewise, no pixel conversion.
- `reset()` — release pixels and zero out everything.

### Allocation

Both "try" (returns bool) and abort-on-failure overloads are provided:

| Try | Abort | Purpose |
|---|---|---|
| `tryAllocPixelsFlags(info, flags)` | `allocPixelsFlags(info, flags)` | flags = `kZeroPixels_AllocFlag` (or 0; pixels are *always* zeroed regardless — flag is obsolete) |
| `tryAllocPixels(info, rowBytes)` | `allocPixels(info, rowBytes)` | rowBytes 0 → minimum |
| `tryAllocPixels(info)` | `allocPixels(info)` | calls minRowBytes |
| `tryAllocN32Pixels(w, h, isOpaque)` | `allocN32Pixels(w, h, isOpaque)` | `kN32_SkColorType` |
| `tryAllocPixels()` | `allocPixels()` | uses default `HeapAllocator` |
| `tryAllocPixels(Allocator*)` | `allocPixels(Allocator*)` | custom allocator |

`SkBitmap::Allocator` is a refcounted abstract base with one virtual method,
`bool allocPixelRef(SkBitmap*)`. `SkBitmap::HeapAllocator` is the default
implementation (heap-backed `SkPixelRef`).

### Wrapping external memory

`installPixels(info, pixels, rowBytes, releaseProc, context)` builds a new
`SkPixelRef` referring to caller-owned memory. `releaseProc(addr, context)`
fires when the last bitmap referring to those pixels goes away. Convenience
overloads with no release-proc and a `(SkPixmap)` overload exist.

`setPixels(void* pixels)` replaces the `SkPixelRef`'s pixels in place,
preserving `SkImageInfo` and `rowBytes`.

`setPixelRef(sk_sp<SkPixelRef>, dx, dy)` swaps in a different `SkPixelRef`,
optionally with the bitmap origin moved by `(dx, dy)` *inside* that pixel
ref's coordinate space — this is how `extractSubset` produces a bitmap that
shares storage with the original but has different bounds.

### Pixel access

- `getPixels()` — base pointer.
- `getAddr(x, y)`, `getAddr8(x, y)`, `getAddr16(x, y)`, `getAddr32(x, y)` —
  generic / 1-byte / 2-byte / 4-byte writable typed pointers.
- `getColor(x, y)`, `getColor4f(x, y)`, `getAlphaf(x, y)` — pixel reads,
  forwarded to the underlying `SkPixmap`.
- `peekPixels(SkPixmap*)` — copies the cached pixmap if pixels are directly
  addressable; returns false otherwise (e.g. lazy/empty bitmaps).
- `eraseColor(SkColor4f)`, `eraseColor(SkColor)`, `eraseARGB(a, r, g, b)`,
  `erase(SkColor4f, SkIRect)`, `erase(SkColor, SkIRect)`,
  `eraseArea(SkIRect, SkColor)` (deprecated).
- `extractAlpha(dst[, paint, allocator, offset])` — produces an alpha-only
  bitmap, optionally applying a `SkMaskFilter` from `paint`.
- `extractSubset(dst, subset)` — points `dst` at the same `SkPixelRef`, with
  bounds intersected with `subset`.

### Bulk transfer

- `readPixels(dstInfo, dstPixels, dstRowBytes, srcX, srcY)` and
  `readPixels(SkPixmap, srcX, srcY)` / `readPixels(SkPixmap)` — same
  conversion rules as `SkPixmap::readPixels`.
- `writePixels(SkPixmap src, dstX, dstY)` and `writePixels(SkPixmap)`.

### Identity & immutability

- `getGenerationID()` is a unique value derived from the underlying
  `SkPixelRef`. Calling `notifyPixelsChanged()` increments it; this
  invalidates caches keyed on the generation ID.
- `isImmutable()` / `setImmutable()` — once set, can never be cleared. All
  bitmaps sharing the same `SkPixelRef` see the change. Writing to immutable
  pixels asserts in debug builds.
- `isOpaque()` is a query of the alpha-type hint; `ComputeIsOpaque(bm)` walks
  every pixel.

### Dimensions

`width()`, `height()`, `dimensions()`, `bounds()`, `getBounds(SkRect*)`,
`getBounds(SkIRect*)`, `getSubset()` (offset by `pixelRefOrigin()`),
`pixelRef()`, `pixelRefOrigin()`, `info()`, `pixmap()`, `rowBytes()`,
`bytesPerPixel()`, `rowBytesAsPixels()`, `shiftPerPixel()`, `computeByteSize()`,
`empty()`, `isNull()` (no pixel ref), `drawsNothing()` (`empty() ||
isNull()`), `readyToDraw()` (`getPixels() != nullptr`).

### Conversion helpers

- `makeShader(tmx, tmy, sampling, lm)` — and overloads with `lm` as
  `SkMatrix*` or default `kClamp` tile mode.
- `asImage()` — creates a CPU `SkImage`. If the bitmap is immutable, the
  pixel buffer is shared; otherwise pixels are copied.

### Mipmaps

`SkBitmap` carries an optional `sk_sp<SkMipmap>` (`fMips`) that is populated
by GPU upload paths via friend access. There is no public API to set mipmaps
on a `SkBitmap`; use `SkImage::withDefaultMipmaps()` for the public path.

---

## SkPixelRef

`SkPixelRef` is the refcounted, thread-safe holder of pixel memory.

```c++
SkPixelRef(int width, int height, void* addr, size_t rowBytes);
```

- Owns nothing by default — pure subclasses such as `SkMallocPixelRef` add
  storage management.
- `dimensions()`, `width()`, `height()`, `pixels()`, `rowBytes()`.
- `getGenerationID()` — non-zero unique id; changes when
  `notifyPixelsChanged()` is called.
- `isImmutable()` / `setImmutable()` — once set, never reverts.
- `addGenIDChangeListener(sk_sp<SkIDChangeListener>)` — fires (at most once)
  on the next gen-ID change. Used to invalidate Skia's internal pixel-ref
  caches.
- `notifyAddedToCache()` — marks that something in the resource cache depends
  on these pixels, enabling proactive purging when the pixel ref changes or
  goes away.
- `diagnostic_only_getDiscardable()` returns the underlying
  `SkDiscardableMemory*` if the subclass uses one — otherwise null. Not for
  production use.

Internally `fMutability` has three states: `kMutable`, `kTemporarilyImmutable`
(used by raster surfaces while a snapshot is outstanding), and `kImmutable`.

### SkMallocPixelRef

`include/core/SkMallocPixelRef.h` defines two factories in namespace
`SkMallocPixelRef`:

- `MakeAllocate(info, rowBytes)` — allocates `info.height() * rowBytes` bytes
  with the same allocator `SkMask` uses; pixels are zeroed.
- `MakeWithData(info, rowBytes, sk_sp<SkData>)` — refs the data and uses it as
  pixel storage.

This is the same allocator used internally by `SkBitmap::HeapAllocator`.

---

## SkImageGenerator

`SkImageGenerator` (in `include/core/SkImageGenerator.h`) represents a deferred
source of pixels: a codec, a scaled image, an external service. Subclasses
implement how those pixels appear when first asked for.

API:

- Construction: `SkImageGenerator(info, uniqueID = kNeedNewImageUniqueID)`.
- `uniqueID()`, `getInfo()`, `isValid(SkRecorder*)`, `isProtected()`,
  `isTextureGenerator()`.
- `refEncodedData()` returns the original encoded bytes if available
  (subclasses override `onRefEncodedData`).
- `getPixels(info, pixels, rowBytes)` / `getPixels(SkPixmap)` — synchronous
  decode; repeated calls must produce identical results so the generator can
  back an immutable `SkPixelRef`.
- `queryYUVAInfo(supportedDataTypes, SkYUVAPixmapInfo*)` — fast metadata
  lookup; returns false if YUVA decode isn't possible.
- `getYUVAPlanes(const SkYUVAPixmaps&)` — full YUVA decode.

A key client is `SkImages::DeferredFromGenerator(...)`, which wraps a
generator into a lazy `SkImage` that decodes on first draw.

`SkCodecImageGenerator` (in `src/codec/`) is the standard subclass that bridges
`SkCodec` into the generator interface.

---

## SkColorTable

`include/core/SkColorTable.h`. Holds four 256-entry per-channel lookup tables
for `SkColorFilters::Table`.

- `Make(table[256])` — same table for ARGB.
- `Make(tableA, tableR, tableG, tableB)` — per-channel; null channels mean
  identity.
- `alphaTable()`, `redTable()`, `greenTable()`, `blueTable()`.
- `flatten(SkWriteBuffer&)` / `Deserialize(SkReadBuffer&)`.

Internally a 256×4 `kAlpha_8` `SkBitmap`. Once made, immutable.

---

## SkImage

`SkImage` is an immutable, refcounted, possibly-GPU-resident image. Public
construction goes through factories in the `SkImages` namespace
(`include/core/SkImage.h`) plus backend-specific factories in
`include/gpu/ganesh/SkImageGanesh.h` and `include/gpu/graphite/Image.h`.

### Identity

- `imageInfo()`, `width()`, `height()`, `dimensions()`, `bounds()`,
  `uniqueID()` — every new `SkImage` instance gets a fresh ID.
- `alphaType()`, `colorType()`, `colorSpace()`, `refColorSpace()`,
  `isAlphaOnly()`, `isOpaque()`.

### Backend probes

- `isTextureBacked()` — true if pixels live on GPU.
- `textureSize()` — approximate VRAM use (0 if not GPU or external format).
- `isValid(SkRecorder*)` — null recorder tests raster validity; non-null tests
  GPU validity in that backend.
- `isLazyGenerated()` — true if backed by an `SkImageGenerator`.
- `isProtected()`.
- `peekPixels(SkPixmap*)` — true only for raster, eagerly-realized images.

### CPU-side factories (`SkImages` namespace)

| Factory | Behavior |
|---|---|
| `RasterFromBitmap(SkBitmap)` | Shares storage if the bitmap is immutable; copies otherwise. |
| `RasterFromPixmap(pixmap, releaseProc, releaseContext)` | Wraps caller memory; release proc fires when image dies. |
| `RasterFromPixmapCopy(pixmap)` | Always copies. |
| `RasterFromData(info, sk_sp<SkData>, rowBytes)` | Wraps refcounted bytes (no copy). |
| `RasterFromCompressedTextureData(data, w, h, SkTextureCompressionType)` | Decompresses now and wraps. |
| `DeferredFromEncodedData(sk_sp<const SkData>, std::optional<SkAlphaType>)` | Lazy decode — recommended path is now `SkCodecs::DeferredImage`, which lets you choose the decoder. |
| `DeferredFromGenerator(unique_ptr<SkImageGenerator>)` | Lazy from generator. |
| `DeferredFromPicture(picture, dim, mat, paint, BitDepth, cs[, props])` | Lazy raster of an `SkPicture`. `BitDepth::kU8` or `kF16`. |
| `MakeWithFilter(src, filter, subset, clipBounds, *outSubset, *offset)` | CPU-side image-filter result. |

`RasterReleaseProc` and `ReleaseContext` (typedef of `void*`) parameterize
who frees the memory.

### GPU factories (Ganesh — `include/gpu/ganesh/SkImageGanesh.h`)

- `AdoptTextureFrom(GrRecordingContext*, ...)` (3 overloads) — takes
  ownership of an existing `GrBackendTexture`.
- `BorrowTextureFrom(GrRecordingContext*, ...)` — does not take ownership;
  caller frees the backend texture later.
- `CrossContextTextureFromPixmap(GrDirectContext*, pixmap, ...)` — uploads
  pixels in a way that's safe to use from another GPU context.
- `TextureFromCompressedTexture(...)` /
  `TextureFromCompressedTextureData(...)` — wrap or upload compressed texture
  formats (`SkTextureCompressionType`).
- `TextureFromImage(GrDirectContext*, image, ...)` — promote a raster/lazy
  image to a Ganesh texture.
- `TextureFromYUVAPixmaps(...)` (2 overloads) — upload YUVA pixmaps to a
  texture-backed image; the Y/U/V/(A) planes can be in shared or separate
  textures.
- `TextureFromYUVATextures(...)` (2 overloads) — wrap existing YUVA backend
  textures.
- `GetBackendTextureFromImage(image, ...)` — read out the backend handle if
  available.
- `MakeBackendTextureFromImage(GrDirectContext*, image, ...)` — flatten any
  image to a freshly created `GrBackendTexture` you then own.
- `SubsetTextureFrom(GrDirectContext*, image, subset)`,
  `MakeWithFilter(GrRecordingContext*, ...)`.

### GPU factories (Graphite — `include/gpu/graphite/Image.h`)

The matching factory family takes a `skgpu::graphite::Recorder*` instead of a
`GrRecordingContext*`. Notable differences:

- `WrapTexture(Recorder*, ...)` — the equivalent of `BorrowTextureFrom`.
- `PromiseTextureFrom(...)` and `PromiseTextureFromYUVA(...)` — fulfill a
  promise texture later via a callback. Used to record without having a real
  texture yet.
- `TextureFromYUVAImages(Recorder*, ...)` — assemble a YUVA image from
  existing single-plane `SkImage` references (Graphite-specific).
- Otherwise the names mirror Ganesh: `TextureFromImage`,
  `TextureFromYUVAPixmaps`, `TextureFromYUVATextures`, `SubsetTextureFrom`,
  `MakeWithFilter`.

### Shaders from images

Both color and "raw" forms exist:

- `makeShader(tmx, tmy, SkSamplingOptions, SkMatrix*|SkMatrix&)` — color-managed
  shader. Defaults to `kClamp` in both axes when omitted.
- `makeRawShader(tmx, tmy, SkSamplingOptions, ...)` — same shape, but **no
  color-space transform**, **no auto-premultiply** for `kUnpremul`, and
  **bicubic sampling not supported** (returns null if requested).
  Use for normal maps, heightmaps, and other non-color data feeding
  `SkRuntimeEffect`.

### Read-back (CPU↔GPU transfer)

```
SkImage (raster or texture)
       │
       │  readPixels (sync)              asyncRescaleAndReadPixels (async, GPU)
       │      ──────────►            ─────────────────────────────────►
       │                                                  callback(ctx, AsyncReadResult)
       │
       │  scalePixels (sync, with sampling)
       ▼
   destination SkPixmap / dst buffer (CPU)
```

- `readPixels(GrDirectContext*, dstInfo, dstPixels, dstRowBytes, srcX, srcY,
  CachingHint)` and the `SkPixmap` overload — synchronous read. **Deprecated
  for Graphite**; use `skgpu::graphite::Context::asyncRescaleAndReadPixels`.
- Legacy non-context overloads exist if `SK_IMAGE_READ_PIXELS_DISABLE_LEGACY_API`
  is not defined.
- `asyncRescaleAndReadPixels(info, srcRect, RescaleGamma, RescaleMode,
  callback, context)` — async on Ganesh GPU when the underlying API supports
  transfer buffers; falls back to sync otherwise.
  - `RescaleGamma::kSrc` — rescale in source gamma.
  - `RescaleGamma::kLinear` — convert to linear, rescale, convert back.
  - `RescaleMode::kNearest`, `kLinear`, `kRepeatedLinear`, `kRepeatedCubic`.
- `asyncRescaleAndReadPixelsYUV420(...)` — produces 3 planes (Y, U, V), U/V at
  half size. Result is `count() == 3`.
- `asyncRescaleAndReadPixelsYUVA420(...)` — adds a 4th plane (A) at full
  resolution. Result is `count() == 4`.

`AsyncReadResult` is a non-copyable, non-movable interface returned via
`unique_ptr<const AsyncReadResult>`. Methods: `count()`, `data(int i)`,
`rowBytes(int i)`. Data lifetime tied to the result; on GPU images the data
is invalidated immediately if the GPU context is abandoned.

`scalePixels(SkPixmap dst, SkSamplingOptions, CachingHint)` — synchronous
copy + resample.

### Caching hint

```c++
enum CachingHint { kAllow_CachingHint, kDisallow_CachingHint };
```

`kDisallow` is appropriate for one-shot reads or pixels that already live in
a cache outside Skia.

### Mipmaps

- `hasMipmaps()` — true if attached.
- `withDefaultMipmaps()` — returns a new image whose mipmaps are auto-built.
- `RequiredProperties { bool fMipmapped }` — passed to `makeSubset`,
  `makeColorSpace`, and `makeColorTypeAndColorSpace` to demand mipmaps on the
  result.

### Color-space conversion

- `makeColorSpace(SkRecorder*, sk_sp<SkColorSpace>, RequiredProperties)` —
  convert pixels into the target space. Returns `this` if already matching.
  Recorder required for Graphite-backed images.
- `makeColorTypeAndColorSpace(SkRecorder*, SkColorType, sk_sp<SkColorSpace>,
  RequiredProperties)` — also converts color type. Experimental.
- `reinterpretColorSpace(sk_sp<SkColorSpace>)` — relabels the color space
  *without* converting pixels. Will draw differently.

### Subsetting

- `makeSubset(SkRecorder*, SkIRect subset, RequiredProperties)` — returns a
  subset image. GPU-backed input → GPU-backed output (recorder must match);
  raster input → raster output. Returns null on out-of-bounds, empty, or
  pixel-read failure.

### Scaling

- `makeScaled(SkRecorder*, SkImageInfo, SkSamplingOptions[, SurfaceProps])` and
  `makeScaled(SkImageInfo, SkSamplingOptions)` — copy and resample, retaining
  the source's backend (raster→raster, GPU→GPU). Recorder required if the
  source is on a Graphite recorder.

### Backend conversion

- `makeNonTextureImage(GrDirectContext*)` — copy to CPU if currently on GPU;
  return self otherwise. Returns null on copy failure.
- `makeRasterImage(GrDirectContext*, CachingHint = kDisallow)` — like
  `makeNonTextureImage`, but also forces lazy/encoded images to decode.
  Legacy no-context overload exists when
  `SK_IMAGE_READ_PIXELS_DISABLE_LEGACY_API` is not defined.

### Encoded data

- `refEncodedData()` returns the original encoded bytes (`sk_sp<SkData>` or
  `sk_sp<const SkData>` depending on `SK_DISABLE_LEGACY_NONCONST_ENCODED_IMAGE_DATA`)
  for images created from encoded sources. Returns null otherwise.

### Legacy bitmap conversion (deprecated)

- `LegacyBitmapMode { kRO_LegacyBitmapMode }`.
- `asLegacyBitmap(SkBitmap*, LegacyBitmapMode)` — populates a bitmap that
  shares storage and is marked immutable.

### Internal hierarchy

`SkImage` is abstract; subclasses include `SkImage_Raster` (CPU), `SkImage_Lazy`
(generator-backed), `SkImage_Ganesh` (Ganesh GPU), `SkImage_Graphite` (Graphite
GPU). Clients should not subclass it.

---

## SkTiledImageUtils

`include/core/SkTiledImageUtils.h`. Drop-in replacements for
`SkCanvas::drawImage*` that automatically tile a CPU-only `SkBitmap`-backed
image when it would be too large for a single GPU upload. If the image is
already GPU-resident, or fits, calls fall through to `SkCanvas`.

- `DrawImage(canvas, image, x, y, sampling, paint, constraint)` — and the
  `sk_sp<SkImage>` overload.
- `DrawImageRect(canvas, image, src, dst, sampling, paint, constraint)` — and
  the `(canvas, image, dst, ...)` and `sk_sp<SkImage>` overloads.
- `GetImageKeyValues(image, uint32_t keyValues[kNumImageKeyValues])` — fills
  `kNumImageKeyValues` (= 6) words suitable as a cache key. Captures
  `SkBitmap` generation id + subset for bitmap-backed images, picture
  identity + transform for picture-backed images, and falls back to
  `image->uniqueID()` otherwise.

---

## CPU↔GPU transfer summary

```
                                upload
   raster SkImage            ───────────►   GPU SkImage (Ganesh / Graphite)
       │                                            │
       │ makeRasterImage,                           │ makeRasterImage,
       │ makeNonTextureImage,                       │ makeNonTextureImage,
       │ readPixels                                 │ readPixels (sync, deprecated for Graphite),
       │                                            │ asyncRescaleAndReadPixels
       │                                            │ asyncRescaleAndReadPixelsYUV[A]420
       ▼                                            ▼
   CPU pixel buffer  ◄───────────────────────  CPU pixel buffer
                              readback
```

Upload paths are spelled `SkImages::TextureFromImage` (Ganesh and Graphite
both), `SkImages::CrossContextTextureFromPixmap` (Ganesh), and the
`WrapTexture` / `BorrowTextureFrom` / `AdoptTextureFrom` family for
zero-copy wraps around backend textures.
