# Android Integration

`include/android/` and `src/android/` form the Android-specific public
surface of Skia. They are layered on top of the cross-platform core but
expose Android-only types — `AHardwareBuffer`, `AImageDecoder`, the
NDK font enumerator — and a private surface used by the AOSP `hwui`
renderer (the system view hierarchy).

This page covers what those headers contain. The font side of the
Android port (`SkFontMgr_android`, `SkFontMgr_android_ndk`) and the
NDK image generator/encoder live with the other per-platform glue in
[Platform Ports](platform-ports.md). The cross-platform animated-image
machinery lives in [Animated Images](animated-images.md); this page
focuses on the Android-shaped wrapper around it.

## Source map

| Subject | Header | Source |
| --- | --- | --- |
| Animated drawable | `include/android/SkAnimatedImage.h` | `src/android/SkAnimatedImage.cpp` |
| Framework escape hatches | `include/android/SkAndroidFrameworkUtils.h` | `src/android/SkAndroidFrameworkUtils.cpp` |
| `AHardwareBuffer` ↔ `SkColorType` | `include/android/AHardwareBufferUtils.h` | (header-only inline; helpers used by Ganesh and Graphite) |
| Ganesh `AHardwareBuffer` import | `include/android/GrAHardwareBufferUtils.h` | `src/gpu/ganesh/GrAHardwareBufferUtils.cpp` |
| Image factories | `include/android/SkImageAndroid.h` | (forwarded to Ganesh / Graphite implementations) |
| Surface factories | `include/android/SkSurfaceAndroid.h` | (forwarded to Ganesh / Graphite implementations) |
| Canvas helpers | `include/android/SkCanvasAndroid.h` | (forwarded to core) |
| Graphite/AHB helpers | `include/android/graphite/` | `src/gpu/graphite/AndroidHardwareBuffer*.cpp` |
| Vulkan/AHB helpers | `include/android/vk/` | `src/gpu/ganesh/vk/`, `src/gpu/graphite/vk/` |
| Perfetto static storage | — | `src/android/SkAndroidFrameworkPerfettoStaticStorage.cpp` |

The headers compile out cleanly when `__ANDROID_API__` is undefined or
below the required level (typically 26 for AHB, 30 for the NDK image
codec), so cross-platform code can include them unconditionally.

---

## `AHardwareBuffer` import / export

`AHardwareBuffer` is the Android primitive that backs SurfaceFlinger
buffers, camera frames, and HAL outputs. Skia consumes them in two
roles:

- **as a texture source** — wrap an existing buffer as an `SkImage` so
  it can be used in a paint or drawn directly;
- **as a render target** — wrap a writable buffer as an `SkSurface`
  that the GPU backend renders into.

`SkImageAndroid.h` exposes the import factories
(`SkImages::DeferredFromAHardwareBuffer`,
`SkImages::TextureFromAHardwareBufferWithData`,
`skia-main/include/android/SkImageAndroid.h:31`). The first form is
deferred — Ganesh or Graphite imports the buffer as an external texture
on first GPU use, which is the right shape for camera/SurfaceFlinger
streams whose backing memory may rotate. The second uploads pixmap data
into a buffer the caller already owns and returns the matching image,
useful when the caller wants the AHB itself for IPC. A `colorSpace`
argument is optional; if omitted, sRGB is assumed.

`SkSurfaceAndroid.h` exposes `SkSurfaces::WrapAndroidHardwareBuffer`,
the render-target counterpart (`skia-main/include/android/SkSurfaceAndroid.h:41`).
The buffer must have both `GPU_COLOR_OUTPUT` and `GPU_SAMPLED_IMAGE`
usage bits, and the `fromWindow` flag tells the Vulkan backend whether
the buffer is part of an `ANativeWindow` swapchain (which controls how
the backing image's tiling and synchronisation are set up). Surface
ownership keeps the AHB ref alive until pending GPU work completes.

`AHardwareBufferUtils.h` is a tiny helper namespace (just one mapping
function and a constant) that translates `AHARDWAREBUFFER_FORMAT_*`
codes to `SkColorType`. External buffers — the ones that need an
external sampler because their format is not natively renderable — fall
through to `kRGBA_8888_SkColorType`
(`skia-main/include/android/AHardwareBufferUtils.h:18`).

`GrAHardwareBufferUtils.h` is the lower-level Ganesh API that the
public headers above call into. It packages the per-backend code for
creating a `GrBackendTexture` / `GrBackendRenderTarget` from a buffer:
EGL image import for OpenGL ES, `VkImage` import via
`VK_ANDROID_external_memory_android_hardware_buffer` for Vulkan, and
metadata extraction (`AHardwareBuffer_describe`,
`AHardwareBuffer_getId`). Graphite has the equivalent under
`include/android/graphite/` and `src/gpu/graphite/`.

The whole AHB stack is the single mechanism by which Skia interoperates
with other Android producers/consumers without copying — a frame can go
camera → AHB → `SkImage` → drawn into an AHB → SurfaceFlinger composite
without leaving GPU memory. The hwui renderer in AOSP is the largest
consumer; Android Studio's compose preview is another.

---

## SkAnimatedImage — `include/android/SkAnimatedImage.h`

`SkAnimatedImage` is a single-threaded `SkDrawable` wrapper around
`SkAndroidCodec` for animated formats (GIF, WebP, AVIF, animated PNG).
It is the engine behind Android's `AnimatedImageDrawable`.

Construction takes a decoder plus optional scale/crop/post-process
parameters:

```cpp
static sk_sp<SkAnimatedImage> Make(std::unique_ptr<SkAndroidCodec>,
        const SkImageInfo& info, SkIRect cropRect, sk_sp<SkPicture> postProcess);
static sk_sp<SkAnimatedImage> Make(std::unique_ptr<SkAndroidCodec>);
```

The first form lets callers downscale (`info` chooses the destination
dimensions, the codec picks the best native sample size), crop, and
overlay an arbitrary `SkPicture` on each frame — this is how Android
implements the "post-process" hook in `ImageDecoder.OnHeaderDecoded`.

Playback is pull-based:

- `decodeNextFrame()` advances to the next frame and returns the new
  frame's display duration in milliseconds, or
  `SkAnimatedImage::kFinished == -1` once all repetitions are done.
- `currentFrameDuration()` returns the same number without decoding.
- `getCurrentFrame()` snapshots the current state into an `SkImage`.
- `reset()` rewinds; `setRepetitionCount(int)` overrides the file's
  built-in count (use `SkCodec::kRepetitionCountInfinite` for endless
  playback, 0 to play once and stop).
- `getFrameCount()`, `setFilterMode(SkFilterMode)`,
  `isFinished()` round out the surface
  (`skia-main/include/android/SkAnimatedImage.h:46-119`).

Because it is an `SkDrawable`, `SkAnimatedImage` can be inserted into
an `SkPicture` or replayed onto any canvas; the drawable's `onDraw`
just paints `getCurrentFrame()` with the configured filter.

For the underlying codec API see [Animated Images](animated-images.md).

---

## SkAndroidFrameworkUtils — `include/android/SkAndroidFrameworkUtils.h`

This header is the AOSP-private escape hatch. It exposes operations
that the public Skia API deliberately keeps off-limits but that hwui
needs to implement Android Canvas semantics.

Highlights (`skia-main/include/android/SkAndroidFrameworkUtils.h`):

- `clipWithStencil(SkCanvas*)` — under Ganesh, encodes the current clip
  into the stencil buffer with reference value 1. Used to implement
  `Canvas.clipPath` semantics that exceed what `SkCanvas::clipPath`
  can express on the GPU.
- `getSurfaceFromCanvas(SkCanvas*)` — recovers the underlying
  `SkSurface` from a canvas (the public `SkCanvas` API hides it).
- `SaveBehind(SkCanvas*, const SkRect*)` — implements
  `Canvas.saveLayerAlpha` with `SAVE_FLAG_HAS_ALPHA_LAYER` semantics
  by saving and later compositing the *behind* content of a region.
- `ResetClip(SkCanvas*)` — wipes the clip stack down to the device clip
  restriction; the public API only allows shrinking the clip.
- `getBaseWrappedCanvas(SkCanvas*)` — unrolls a chain of
  `SkPaintFilterCanvas`es to the innermost target; useful when hwui
  needs to introspect the real surface beneath text-color filters.
- `ShaderAsALinearGradient(SkShader*, LinearGradientInfo*)` — gradient
  introspection (export + reflection) used by the framework to emulate
  legacy gradient APIs.
- `SafetyNetLog(const char*)` — bridges Skia warnings to Android's
  SafetyNet so the platform can audit suspicious draw operations.

Everything here is `SK_API`-exported but `@VisibleForTesting`-style:
nothing in the regular public API depends on these calls, and they
typically reach into `SkCanvas`/`SkSurface` private internals through
`friend` declarations. They are guaranteed only against the version of
Skia shipped in the matching AOSP branch.

`src/android/SkAndroidFrameworkPerfettoStaticStorage.cpp` provides the
single static storage slot Skia uses for its Perfetto trace category
when integrated into AOSP's Perfetto build. It is empty when Perfetto
is disabled, but the AOSP build needs the symbol to exist.

---

## Image and surface factory headers

`SkImageAndroid.h`, `SkSurfaceAndroid.h`, and `SkCanvasAndroid.h` are
the Android-public split of cross-platform factory APIs. Anything that
takes an `AHardwareBuffer*` or implies the Android framework lives
here, while the platform-neutral parts stay in `SkImage.h`,
`SkSurface.h`, and `SkCanvas.h`. Notable extras that come along for
the ride:

- `SkImages::PinnableRasterFromBitmap(const SkBitmap&)` — returns an
  `SkImage` whose pixels share the bitmap's storage and that can be
  pinned as a GPU texture without copying. Pair with
  `skgpu::ganesh::PinAsTexture` / `UnpinTexture` to keep the texture
  warm while the underlying mutable bitmap may still change
  (`skia-main/include/android/SkImageAndroid.h:58-96`).
- `SkImages::RasterFromBitmapNoCopy(const SkBitmap&)` — same no-copy
  semantics, no pinning. Useful for short-lived snapshots in
  framework-allocated bitmaps.

These no-copy variants exist because hwui needs to submit framework
`Bitmap` objects directly to Skia without doubling memory. The split
also lets `SkCanvas::makeSurface` produce the right backend for an
incoming AHB without dragging the AHB type into the cross-platform
header.

---

## Cross-references

- [Platform Ports](platform-ports.md) — Android font managers,
  `SkImageGeneratorNDK`, `SkImageEncoder_NDK`, log routing.
- [Animated Images](animated-images.md) — `SkAndroidCodec` and
  `SkCodec`'s frame model, which `SkAnimatedImage` wraps.
- [Image Decoders](image-decoders.md) — the broader decode stack the
  animated path is built on.
- [Ganesh Backend](ganesh-backend.md) and
  [Graphite Backend](graphite-backend.md) — backend-specific AHB
  import code that the headers above forward to.
