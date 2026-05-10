# Surface & Output Targets

`SkSurface` is the destination half of every Skia draw. It owns the pixel
storage (or, for wrapped backends, references storage that someone else
owns), allocates the `SkCanvas` that renders into it, snapshots its content
as `SkImage`, supports synchronous and asynchronous read-back, and exposes
the platform handles (textures, render targets) the GPU backends use to
present.

```
                     +--------------------+
   client code --->  |     SkSurface      |
                     |  (refcounted)      |
                     +---------+----------+
                               |
                               | getCanvas()  (owned, do not delete)
                               ▼
                     +--------------------+
                     |     SkCanvas       | ── matrix / clip / save stack ──> draws into ───┐
                     +--------------------+                                                   │
                                                                                              ▼
                                                          +-----------------------------------+
                                                          | backend device                    |
                                                          |   raster: SkBitmapDevice          |
                                                          |   Ganesh GPU: SkGpuDevice         |
                                                          |   Graphite GPU: skgpu::graphite::Device |
                                                          |   PDF / SVG: backend-specific     |
                                                          +-----------------------------------+

   makeImageSnapshot()                       writePixels(SkBitmap, x, y) / writePixels(SkPixmap, x, y)
   makeTemporaryImage()                      readPixels(...) — three overloads
                                             asyncRescaleAndReadPixels[YUV[A]420]
                                             draw(canvas, x, y, sampling, paint)
                                             notifyContentWillChange(mode)
                                             generationID()
                                             wait(numSemaphores, GrBackendSemaphore[],
                                                  deleteSemaphoresAfterWait)
                                             characterize(GrSurfaceCharacterization*)
                                             props() / capabilities()
```

`SkSurface` is **not** a public subclassing point — `SkSurface_Base`
(`src/image/SkSurface_Base.h`) plus the per-backend subclasses
(`SkSurface_Raster`, `SkSurface_Ganesh`, `SkSurface_Graphite`,
`SkSurface_Null`) implement everything. Clients add new backends by
implementing a `SkDevice` and a `SkSurface_Base` rather than by subclassing
`SkSurface` itself.

---

## Header / source map

| Header | Implementation | Purpose |
|---|---|---|
| `include/core/SkSurface.h` | `src/image/SkSurface.cpp`, `SkSurface_Base.{h,cpp}` | The base class plus raster factories under `SkSurfaces::` namespace. |
| | `src/image/SkSurface_Raster.{h,cpp}` | Raster (CPU) implementation. |
| | `src/image/SkSurface_Null.cpp` | The null surface (`SkSurfaces::Null`). |
| `include/gpu/ganesh/SkSurfaceGanesh.h` | `src/gpu/ganesh/SkSurface_Ganesh.cpp`, `src/gpu/ganesh/surface/` | Ganesh GPU factories under `SkSurfaces::` namespace. |
| `include/gpu/graphite/Surface.h` | `src/gpu/graphite/Surface_Graphite.cpp` | Graphite factories under `SkSurfaces::` namespace. |
| `include/core/SkSurfaceProps.h` | (header-only / `src/core/`) | Per-surface knobs (LCD striping, font flags, dither, transparent draws). |
| `include/core/SkRasterHandleAllocator.h` | `src/core/SkRasterHandleAllocator.cpp` | Hook for embedders that own raster pixel storage and want to associate a per-layer "handle". |
| `include/core/SkCapabilities.h` | `src/core/SkCapabilities.cpp` | Backend feature reflection (e.g. SkSL version supported). |

---

## SkSurfaceProps

`include/core/SkSurfaceProps.h`. A small bag of surface-level rendering
hints, copied by value.

| Field | Type | Notes |
|---|---|---|
| `flags` | `uint32_t` | Bitmask of `Flags`. |
| `pixelGeometry` | `SkPixelGeometry` | Sub-pixel layout for LCD-rendered text. |
| `textContrast` | `SkScalar` | Mask-coverage contrast for text. `[0, 1]` inclusive. |
| `textGamma` | `SkScalar` | Text gamma. `[0, 4)` (exclusive upper bound). |

```c++
enum Flags {
    kDefault_Flag                    = 0,
    kUseDeviceIndependentFonts_Flag  = 1 << 0,   // shape on the CPU; render via paths
    kDynamicMSAA_Flag                = 1 << 1,   // GPU: synthesize MSAA on a non-MSAA RT
    kAlwaysDither_Flag               = 1 << 2,   // GPU: dither every draw
    kPreservesTransparentDraws_Flag  = 1 << 3,   // do not skip "fully transparent" draws
};

enum SkPixelGeometry {
    kUnknown_SkPixelGeometry,    // unknown / portable
    kRGB_H_SkPixelGeometry,      // sub-pixels arranged horizontally as R,G,B
    kBGR_H_SkPixelGeometry,
    kRGB_V_SkPixelGeometry,      // arranged vertically (rotated displays)
    kBGR_V_SkPixelGeometry,
};
```

Helpers: `SkPixelGeometryIsRGB(geo)`, `SkPixelGeometryIsBGR(geo)`,
`SkPixelGeometryIsH(geo)`, `SkPixelGeometryIsV(geo)`. The
`cloneWithPixelGeometry(newGeo)` member returns a new `SkSurfaceProps`
with the same flags / contrast / gamma but a different pixel geometry —
used by `SaveLayerRec::kPreserveLCDText_SaveLayerFlag`.

`SkSurface::props()` returns the surface's properties; `SkCanvas::getBaseProps()`
and `getTopProps()` are the canvas-side queries.

---

## SkSurface — the base type

### Identity

| Method | Notes |
|---|---|
| `width()`, `height()` | Pixel dimensions; immutable. |
| `imageInfo()` | Full `SkImageInfo` of the surface (color type, alpha type, color space). For raw / opaque-handle surfaces returns `SkImageInfo::MakeUnknown(w, h)`. |
| `generationID()` | Increments each time the contents change. |
| `notifyContentWillChange(mode)` | Tell Skia that the contents will be modified externally. `kDiscard_ContentChangeMode` allows the implementation to throw away the previous contents; `kRetain_ContentChangeMode` preserves them. |
| `props()` | Returns the `SkSurfaceProps`. |
| `capabilities()` | Returns `sk_sp<const SkCapabilities>` for the underlying device (used to query SkSL feature support, etc.). |

### Recording context queries

A surface bound to a backend exposes the recorder for that backend; the
others return `nullptr`:

| Method | Returns |
|---|---|
| `recordingContext()` | Ganesh `GrRecordingContext*`. |
| `recorder()` | Graphite `skgpu::graphite::Recorder*`. |
| `baseRecorder()` | Unified `SkRecorder*` (always non-null for usable surfaces). |

See [canvas-and-recording.md](canvas-and-recording.md#skrecorder--the-recorder-taxonomy)
for the recorder taxonomy.

### Canvas access

```c++
SkCanvas* getCanvas();
```

Returns the `SkCanvas` owned by the surface (do not delete it; it lives as
long as the surface). Subsequent calls return the same pointer. Drawing into
this canvas is the normal way to put pixels into the surface.

### Snapshots

```c++
sk_sp<SkImage> makeImageSnapshot();
sk_sp<SkImage> makeImageSnapshot(const SkIRect& bounds);
sk_sp<SkImage> makeTemporaryImage();
```

`makeImageSnapshot()` captures the current pixels as an immutable `SkImage`.
For raster surfaces it shares the storage (subsequent draws into the surface
will *not* be reflected in the image because Skia performs copy-on-write).
For Ganesh GPU surfaces the resulting image references the same render
target until a draw forces a CoW copy. For Graphite, the legacy snapshot
*always* makes a copy (Graphite explicitly removed CoW; clients that want
the old behavior call the new `SkSurfaces::AsImage` helper documented
below).

`makeImageSnapshot(SkIRect bounds)` clamps to the surface; null if the
intersection is empty.

`makeTemporaryImage()` returns an image that *aliases* the surface for as
long as the caller does not draw into the surface again (or destroy it
before the image). Faster than `makeImageSnapshot` because it never copies,
but UB if used incorrectly.

### Pixel transfer

Read:

```c++
bool readPixels(const SkPixmap& dst, int srcX, int srcY);
bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes,
                int srcX, int srcY);
bool readPixels(const SkBitmap& dst, int srcX, int srcY);
```

Synchronous; returns false for document-based surfaces or for any failed
allocation. **Deprecated for Graphite** — use
`skgpu::graphite::Context::asyncRescaleAndReadPixels` (with optional
explicit synchronization) instead.

Write:

```c++
void writePixels(const SkPixmap& src, int dstX, int dstY);
void writePixels(const SkBitmap& src, int dstX, int dstY);
```

Async read with optional rescale:

```c++
using AsyncReadResult     = SkImage::AsyncReadResult;
using ReadPixelsContext   = void*;
using ReadPixelsCallback  = void(ReadPixelsContext, std::unique_ptr<const AsyncReadResult>);
using RescaleGamma        = SkImage::RescaleGamma;   // kSrc | kLinear
using RescaleMode         = SkImage::RescaleMode;    // kNearest | kLinear | kRepeatedLinear | kRepeatedCubic

void asyncRescaleAndReadPixels(const SkImageInfo& info,
                               const SkIRect& srcRect,
                               RescaleGamma, RescaleMode,
                               ReadPixelsCallback, ReadPixelsContext);
void asyncRescaleAndReadPixelsYUV420(SkYUVColorSpace, sk_sp<SkColorSpace> dstCS,
                                     const SkIRect& srcRect, const SkISize& dstSize,
                                     RescaleGamma, RescaleMode,
                                     ReadPixelsCallback, ReadPixelsContext);
void asyncRescaleAndReadPixelsYUVA420(SkYUVColorSpace, sk_sp<SkColorSpace> dstCS,
                                      const SkIRect& srcRect, const SkISize& dstSize,
                                      RescaleGamma, RescaleMode,
                                      ReadPixelsCallback, ReadPixelsContext);
```

The async read variants are only truly asynchronous on a Ganesh GPU surface
whose underlying API supports transfer buffers and CPU/GPU synchronization
primitives; otherwise they fall through to a synchronous implementation and
invoke the callback immediately. For Graphite, these have been replaced
with the equivalent API on `skgpu::graphite::Context`.

`AsyncReadResult` returns one or more planes; the data is invalidated if a
GPU context is abandoned. See [bitmap-pixmap-image.md](bitmap-pixmap-image.md#read-back-cpugpu-transfer)
for the read-back model.

### Direct pixel access

```c++
bool peekPixels(SkPixmap* pixmap);
```

Returns true if the surface's pixels are CPU-addressable; the returned
pixmap is valid until any draw call.

### Drawing into another canvas

```c++
void draw(SkCanvas* canvas, SkScalar x, SkScalar y,
          const SkSamplingOptions& sampling, const SkPaint* paint);
void draw(SkCanvas* canvas, SkScalar x, SkScalar y, const SkPaint* paint = nullptr);
```

Equivalent to taking a snapshot and drawing it; the implementation may share
storage to avoid the copy when it knows it's safe.

### GPU semaphores

```c++
bool wait(int n, const GrBackendSemaphore* sems, bool deleteSemaphoresAfterWait = true);
```

Tells the GPU backend to block subsequent commands on the given semaphores
before any draw work continues. Returns false if the backend can't honor
the request. If `deleteSemaphoresAfterWait` is false, the caller must keep
the semaphores valid until they have actually been signalled.

### Backend-handle replacement

```c++
virtual bool replaceBackendTexture(const GrBackendTexture& backendTexture,
                                   GrSurfaceOrigin origin,
                                   ContentChangeMode mode = kRetain_ContentChangeMode,
                                   TextureReleaseProc = nullptr,
                                   ReleaseContext = nullptr) = 0;
```

For surfaces created from a backend texture (Ganesh), swap to a different
texture without recreating the surface. Dimensions and format must match;
contents are optionally copied per `mode`.

### Characterization

```c++
bool characterize(GrSurfaceCharacterization* characterization) const;
bool isCompatible(const GrSurfaceCharacterization& characterization) const;
```

`GrSurfaceCharacterization` is a value-type description of a surface that's
sufficient to record draws against it without holding the surface itself —
the basis for parallel multi-tile recording with `GrDeferredDisplayList` /
`SkDeferredDisplayListRecorder`. Raster surfaces don't support
characterization (returns false).

---

## Backends

All concrete factories live in the `SkSurfaces::` namespace (or a backend
sub-namespace). The historical `SkSurface::Make*` static methods have been
removed.

### Raster

`include/core/SkSurface.h`, `src/image/SkSurface_Raster.cpp`. Pixels live in
heap memory accessible to the CPU.

```c++
namespace SkSurfaces {

// Backed by Skia-allocated, zero-filled memory. rowBytes 0 → minRowBytes.
SK_API sk_sp<SkSurface> Raster(const SkImageInfo& info,
                               size_t rowBytes,
                               const SkSurfaceProps* props);
inline  sk_sp<SkSurface> Raster(const SkImageInfo& info,
                                const SkSurfaceProps* props = nullptr);

// Caller owns the pixels; lifetime must outlive the surface.
SK_API sk_sp<SkSurface> WrapPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
                                   const SkSurfaceProps* props = nullptr);
inline  sk_sp<SkSurface> WrapPixels(const SkPixmap& pm,
                                    const SkSurfaceProps* props = nullptr);

// Caller-owned pixels with a release callback fired on surface destruction.
using PixelsReleaseProc = void(void* pixels, void* context);
SK_API sk_sp<SkSurface> WrapPixels(const SkImageInfo& info, void* pixels, size_t rowBytes,
                                   PixelsReleaseProc, void* context,
                                   const SkSurfaceProps* props = nullptr);

// A surface that drops every draw — useful for tests.
SK_API sk_sp<SkSurface> Null(int width, int height);

}  // namespace SkSurfaces
```

`Raster()` allocates with `sk_calloc_throw` (zero-filled). `WrapPixels()`
shares caller-owned memory; the optional `PixelsReleaseProc` fires when
the surface is destroyed (good for `mmap`-backed buffers).

The base `SkSurface` constructor `SkCanvas::MakeRasterDirect(...)` short-cuts
the surface and returns a `unique_ptr<SkCanvas>` that draws directly into
caller pixels — see [canvas-and-recording.md](canvas-and-recording.md#construction).

### Ganesh GPU

`include/gpu/ganesh/SkSurfaceGanesh.h`. `RenderTarget()` allocates a fresh
backend texture; `WrapBackendTexture()` and `WrapBackendRenderTarget()`
adopt existing handles.

```c++
namespace SkSurfaces {

// Skia-allocated render target.
SK_API sk_sp<SkSurface> RenderTarget(GrRecordingContext*,
                                     skgpu::Budgeted,
                                     const SkImageInfo&,
                                     int sampleCount,
                                     GrSurfaceOrigin,
                                     const SkSurfaceProps*,
                                     bool shouldCreateWithMips = false,
                                     bool isProtected = false);
SK_API sk_sp<SkSurface> RenderTarget(GrRecordingContext*,
                                     const GrSurfaceCharacterization&,
                                     skgpu::Budgeted);

// Wrap a caller-owned backend texture.
SK_API sk_sp<SkSurface> WrapBackendTexture(GrRecordingContext*,
                                           const GrBackendTexture&,
                                           GrSurfaceOrigin,
                                           int sampleCnt,
                                           SkColorType,
                                           sk_sp<SkColorSpace>,
                                           const SkSurfaceProps*,
                                           TextureReleaseProc = nullptr,
                                           ReleaseContext = nullptr);

// Wrap a backend render target (e.g. a default framebuffer object on GL).
SK_API sk_sp<SkSurface> WrapBackendRenderTarget(GrRecordingContext*,
                                                const GrBackendRenderTarget&,
                                                GrSurfaceOrigin,
                                                SkColorType,
                                                sk_sp<SkColorSpace>,
                                                const SkSurfaceProps*,
                                                RenderTargetReleaseProc = nullptr,
                                                ReleaseContext = nullptr);

// Inspect the backing handles.
SK_API GrBackendTexture       GetBackendTexture(SkSurface*, BackendHandleAccess);
SK_API GrBackendRenderTarget  GetBackendRenderTarget(SkSurface*, BackendHandleAccess);

// Resolve MSAA into the resolve texture without flushing.
SK_API void ResolveMSAA(SkSurface*);
SK_API void ResolveMSAA(const sk_sp<SkSurface>&);

}  // namespace SkSurfaces

namespace skgpu::ganesh {
SK_API GrSemaphoresSubmitted Flush(SkSurface*);
SK_API GrSemaphoresSubmitted Flush(sk_sp<SkSurface>);
SK_API void                  FlushAndSubmit(SkSurface*);
SK_API void                  FlushAndSubmit(sk_sp<SkSurface>);
}
```

`BackendHandleAccess` is `SkSurface::BackendHandleAccess`:

| Value | Meaning |
|---|---|
| `kFlushRead` | The handle will be sampled — Skia must flush pending draws. |
| `kFlushWrite` | The handle will be modified — Skia flushes and the surface tracks the foreign write. |
| `kDiscardWrite` | The handle will be fully overwritten — Skia can skip flushing. |

`skgpu::Budgeted` (`include/gpu/GpuTypes.h`) is `enum class : bool { kNo,
kYes }` — whether the allocation counts against `GrDirectContext`'s
resource budget.

`GrSurfaceOrigin` selects whether `(0, 0)` is the top-left or
`bottom-left` corner of the texture (OpenGL's classic conundrum). Most
desktop / mobile platforms use `kTopLeft_GrSurfaceOrigin`; OpenGL default
framebuffers commonly want `kBottomLeft_GrSurfaceOrigin`.

`isProtected` selects DRM-protected memory on Android (Vulkan) where
content is hardware-secured against host read-back.

### Graphite

`include/gpu/graphite/Surface.h`. The Graphite factories require a
`skgpu::graphite::Recorder*` (the per-thread recording context).

```c++
namespace SkSurfaces {
SK_API sk_sp<SkSurface> RenderTarget(skgpu::graphite::Recorder*,
                                     const SkImageInfo&,
                                     skgpu::Mipmapped = skgpu::Mipmapped::kNo,
                                     const SkSurfaceProps* props = nullptr,
                                     std::string_view label = {});

// Two overloads. The deprecated one takes an explicit colorType; the modern
// form derives it from the backend texture's format.
SK_API sk_sp<SkSurface> WrapBackendTexture(skgpu::graphite::Recorder*,
                                           const skgpu::graphite::BackendTexture&,
                                           SkColorType,                       // deprecated
                                           sk_sp<SkColorSpace>,
                                           const SkSurfaceProps*,
                                           TextureReleaseProc = nullptr,
                                           ReleaseContext = nullptr,
                                           std::string_view label = {});
SK_API sk_sp<SkSurface> WrapBackendTexture(skgpu::graphite::Recorder*,
                                           const skgpu::graphite::BackendTexture&,
                                           sk_sp<SkColorSpace>,
                                           const SkSurfaceProps*,
                                           TextureReleaseProc = nullptr,
                                           ReleaseContext = nullptr,
                                           std::string_view label = {});

// Snapshot helpers (Graphite-specific).
SK_API sk_sp<SkImage> AsImage(sk_sp<const SkSurface>);
SK_API sk_sp<SkImage> AsImageCopy(sk_sp<const SkSurface>,
                                  const SkIRect* subset = nullptr,
                                  skgpu::Mipmapped = skgpu::Mipmapped::kNo);
}
```

Graphite's snapshot model:

- `makeImageSnapshot()` (the legacy API) **always copies** in Graphite — the
  CoW behavior of Ganesh and raster has been removed.
- `SkSurfaces::AsImage(surface)` returns an image that *shares* the
  surface's backing texture; the caller must ensure the image is consumed
  before the surface is mutated again. Returns null if the backing GPU
  buffer is not textureable. Mipmapping matches the source.
- `SkSurfaces::AsImageCopy(surface, subset, mipmapped)` copies, optionally
  with subset and a `Mipmapped::kYes`/`kNo` choice.

Resource lifetime: while the client holds a `sk_sp<SkSurface>`, the backing
GPU object does *not* count against the recorder's budget. After the
surface is freed, the GPU object may become a scratch (reusable) resource
— at which point it does count against the budget.

The `label` argument is a debug name surfaced in capture / Renderdoc /
Xcode profiles.

For Graphite-specific concepts (Recorders, Recordings, `Context`,
PromiseTextures), see [graphite-backend.md](graphite-backend.md).

### PDF, SVG, XPS

These are not exposed as `SkSurface` factories. PDF uses `SkDocument`
(`include/core/SkDocument.h`), which hands out `SkCanvas`es per page; SVG
uses `SkSVGCanvas::Make(bounds, SkXMLWriter*)` to produce an `SkCanvas`
that writes XML. XPS uses `SkXPSDocument`. All three bypass the `SkSurface`
abstraction because their output is a stream rather than a pixel grid.

See [pdf-backend.md](pdf-backend.md), [svg-canvas.md](svg-canvas.md),
[xps-backend.md](xps-backend.md).

---

## SkRasterHandleAllocator

`include/core/SkRasterHandleAllocator.h`. A hook that lets an embedder
provide its own raster-pixel allocator and attach a typed "handle" to each
saved layer that the canvas can later retrieve. Concretely: Android Skia
uses this to associate every layer with the corresponding Android `Bitmap`
header.

```c++
class SK_API SkRasterHandleAllocator {
public:
    using Handle = void*;

    struct Rec {
        void   (*fReleaseProc)(void* pixels, void* ctx);
        void*  fReleaseCtx;
        void*  fPixels;
        size_t fRowBytes;
        Handle fHandle;
    };

    virtual bool allocHandle(const SkImageInfo&, Rec*) = 0;
    virtual void updateHandle(Handle, const SkMatrix& ctm, const SkIRect& clipBounds) = 0;

    static std::unique_ptr<SkCanvas> MakeCanvas(
        std::unique_ptr<SkRasterHandleAllocator>,
        const SkImageInfo&,
        const Rec* rec = nullptr,
        const SkSurfaceProps* props = nullptr);
};
```

`MakeCanvas` returns a `unique_ptr<SkCanvas>` (note: not an `SkSurface`).
The allocator's `allocHandle` is invoked once for the base layer (unless
`rec` is supplied) and once per `saveLayer`. The canvas exposes the
top-of-stack handle via `SkCanvas::accessTopRasterHandle()` and calls the
allocator's `updateHandle(matrix, clipBounds)` when those change.

Pixel storage tracked by the allocator is freed by the per-`Rec`
`fReleaseProc` when the corresponding layer is restored (or the canvas is
destroyed).

This is the standard mechanism for embedders that want canvas-shaped output
*without* surrendering pixel ownership to Skia.

---

## SkCapabilities

`include/core/SkCapabilities.h`. A small refcounted struct exposed via
`SkSurface::capabilities()` and `skgpu::graphite::Recorder::capabilities()`.
Reports things like the maximum SkSL version supported by the backend
(`SkSL::Version::k100`, `k300`), so runtime-effect compilation knows what
features are usable on the destination.

---

## How a draw becomes pixels

For a raster surface created by `SkSurfaces::Raster(info, ...)`:

1. `Raster()` allocates the pixel block (`sk_calloc_throw`) and constructs
   a `SkSurface_Raster` wrapping a `SkBitmapDevice`.
2. The first `surface->getCanvas()` call returns the canvas the
   `SkSurface_Raster` constructed; the canvas's matrix and clip stack start
   at identity and the full surface bounds.
3. Each draw call goes through the canvas → device → blitter pipeline (see
   [cpu-rendering-pipeline.md](cpu-rendering-pipeline.md)).
4. `surface->makeImageSnapshot()` increments the device's pixel-ref
   immutability count; the resulting `SkImage_Raster` shares the storage.
   The next draw into the surface triggers copy-on-write inside the
   pixel ref before the actual blit, so the snapshot remains valid.

For a Ganesh GPU surface created by
`SkSurfaces::WrapBackendRenderTarget(...)`:

1. The surface holds a `GrBackendRenderTarget` (a thin handle around a
   GL FBO / Vulkan VkImage / Metal MTLTexture / Direct3D resource).
2. `getCanvas()` returns a `SkCanvas` whose device records into a
   `GrSurfaceFillContext`, which appends draw ops to a per-target op chain.
3. `surface->flushAndSubmit()` (via `skgpu::ganesh::FlushAndSubmit`) hands
   the op chain to `GrDirectContext`, which compiles it into actual GPU
   commands.
4. `getBackendRenderTarget(surface, kFlushRead_BackendHandleAccess)`
   forces the flush and returns the underlying handle, so the embedder can
   present the framebuffer (eg `eglSwapBuffers`).

For a Graphite surface created by `SkSurfaces::RenderTarget(recorder, ...)`:

1. The surface holds a Graphite `Texture` allocated by the recorder.
2. Drawing into the canvas appends to the recorder's task list.
3. `recorder->snap()` produces a `Recording`.
4. `context->insertRecording(...)` followed by `context->submit(...)`
   pushes commands to the GPU (or `Submission::kYes` to do both at once).
5. `SkSurfaces::AsImage(surface)` lets a follow-up draw sample the same
   texture without copying, provided the client doesn't write the surface
   again before the image is consumed.

---

## Cross-references

- [`SkCanvas`](canvas-and-recording.md) — the consumer of a surface; every
  `SkSurface::getCanvas()` returns a canvas.
- [`SkBitmap` / `SkPixmap` / `SkImage`](bitmap-pixmap-image.md) — the pixel
  containers for raster I/O and snapshot results.
- [`SkColorSpace`](color-management.md) — every surface's `SkImageInfo`
  carries a (possibly null) color space that drives color-managed drawing.
- [Ganesh backend](ganesh-backend.md) — the GPU op-list architecture behind
  `SkSurfaces::RenderTarget` (Ganesh).
- [Graphite backend](graphite-backend.md) — the GPU architecture behind the
  Graphite-namespace surface factories.
- [CPU rendering pipeline](cpu-rendering-pipeline.md) — what
  `SkSurface_Raster` does under the hood.
- [PDF backend](pdf-backend.md), [SVG canvas](svg-canvas.md),
  [XPS backend](xps-backend.md) — output targets that don't go through
  `SkSurface`.
