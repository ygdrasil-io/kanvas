# Canvas & Recording API

`SkCanvas` is the central drawing interface in Skia. Every draw call goes
through one — `SkCanvas::drawRect`, `drawPath`, `drawImage`, `drawTextBlob`,
etc. — and every backend (raster, Ganesh, Graphite, PDF, SVG, picture
recording, debugging proxies) is exposed as either an `SkCanvas` instance or a
`SkSurface` from which you ask for one.

```
                                                +-> raster blitter pipeline (CPU)
                                                |
   client code  ---->  SkCanvas  ---->  SkDevice +-> Ganesh GPU op list
                          |       |              |
                          |       |              +-> Graphite Recorder
                          |       |
                          |       +--> SkRecordCanvas (SkPictureRecorder)
                          |       |
                          |       +--> PDF page state
                          |       |
                          |       +--> SVG canvas
                          |
                          +--> wrapper canvases (SkNoDrawCanvas,
                                SkNWayCanvas, SkOverdrawCanvas,
                                SkPaintFilterCanvas) forward / filter
                                draws before reaching the inner canvas
```

`SkCanvas` is *not* a drawable surface itself — it draws into an `SkDevice`
or, in the recording case, accumulates `SkRecord` ops. The public API exposes
no way to subclass `SkDevice` (it lives in `src/core/SkDevice.h`); to extend
Skia, override `SkCanvas` (or one of its wrapper subclasses) and implement
the on-draw virtual hooks via `SkCanvasVirtualEnforcer`.

---

## Header / source map

| Header | Implementation | Purpose |
|---|---|---|
| `include/core/SkCanvas.h` | `src/core/SkCanvas.cpp`, `SkCanvasPriv.cpp`, `SkCanvas_Raster.cpp` | The base canvas. |
| `include/core/SkCanvasVirtualEnforcer.h` | header-only | Forces subclasses to override every new `onDraw*` virtual. |
| `include/core/SkPicture.h` | `src/core/SkPicture.cpp`, `SkBigPicture.{h,cpp}`, `SkEmptyPicture` | Recorded, immutable draw stream. |
| `include/core/SkPictureRecorder.h` | `src/core/SkPictureRecorder.cpp` | Builder that emits `SkPicture` or `SkDrawable`. |
| `include/core/SkDrawable.h` | `src/core/SkDrawable.cpp` | Mutable, replayable drawing object. |
| `include/core/SkRecorder.h` | `src/core/SkCPURecorder.cpp`, plus per-backend recorder impls | Tagged base for "what kind of recorder is this canvas talking to" — CPU / Ganesh / Graphite. |
| `include/core/SkBBHFactory.h` | `src/core/SkBBHFactory.cpp` | Bounding-box hierarchies for picture playback. |
| `include/core/SkOverdrawCanvas.h` | `src/core/SkOverdrawCanvas.cpp` | Counts per-pixel draws into an `kAlpha_8` bitmap. |
| `include/utils/SkNoDrawCanvas.h` | `src/utils/SkNoDrawCanvas.cpp` | Records nothing; used for analysis. |
| `include/utils/SkNullCanvas.h` | `src/utils/SkNullCanvas.cpp` | Drops draws; perf testing. |
| `include/utils/SkNWayCanvas.h` | `src/utils/SkNWayCanvas.cpp` | Forwards draws to N inner canvases. |
| `include/utils/SkPaintFilterCanvas.h` | `src/utils/SkPaintFilterCanvas.cpp` | Intercepts and rewrites paints. |

The PDF and SVG canvases live in their own modules — see
[pdf-backend.md](pdf-backend.md) and [svg-canvas.md](svg-canvas.md).

---

## SkCanvas

### Construction

| Factory | Backing |
|---|---|
| `SkCanvas::MakeRasterDirect(info, pixels, rowBytes[, props])` | Caller-supplied pixel buffer. Returns `unique_ptr<SkCanvas>`. |
| `SkCanvas::MakeRasterDirectN32(w, h, SkPMColor*, rowBytes)` | Convenience wrapping `MakeRasterDirect` with `kN32_SkColorType` premul. |
| `SkCanvas()` | Empty 0×0 canvas — placeholder. |
| `SkCanvas(int w, int h[, props])` | Sized but no backing device. Subclasses fill in the rest. |
| `SkCanvas(sk_sp<SkDevice>)` | Private/internal wrapper. |
| `SkCanvas(const SkBitmap&[, props])` | Draws directly into a bitmap. May be deprecated. |
| `SkSurface::getCanvas()` | The standard path — owned by the surface. |
| `SkPictureRecorder::beginRecording(...)` | Returns a `SkRecordCanvas` (recording). |

### State stack

The canvas keeps a stack of (matrix, clip) pairs, plus optionally an
intermediate render target ("layer"). Stack operations:

| Call | Effect |
|---|---|
| `save()` | Push matrix+clip; returns the post-save count. |
| `saveLayer(bounds, paint)` | Push state and allocate a new layer (off-screen). On `restore()`, the layer is composed back into the previous one through `paint` (alpha, color filter, image filter, blend mode). |
| `saveLayer(SaveLayerRec)` | Full-form: bounds, paint, backdrop image filter, backdrop tile mode, layer color space, optional filter list (`fFilters`, up to `kMaxFiltersPerLayer = 16`), and `SaveLayerFlags` (`kPreserveLCDText_SaveLayerFlag`, `kInitWithPrevious_SaveLayerFlag`, `kF16ColorType`). |
| `saveLayerAlphaf(bounds, alpha)` / `saveLayerAlpha(bounds, U8CPU)` | Convenience for the alpha-only case. |
| `restore()` | Pop and (for layers) compose. No-op on an empty stack. |
| `restoreToCount(n)` | Pop until `getSaveCount() == n`. |
| `getSaveCount()` | Returns 1 immediately after construction. |

`SkAutoCanvasRestore` (in `SkCanvas.h`) is the standard RAII helper that
calls `save()` in its constructor and `restoreToCount()` in its destructor.

### Transformations

```
translate(dx, dy)             scale(sx, sy)             rotate(deg [, px, py])
skew(kx, ky)                  concat(SkMatrix or SkM44) setMatrix(SkM44)
resetMatrix()                 getLocalToDevice() -> SkM44
                              getLocalToDeviceAs3x3() -> SkMatrix
                              getTotalMatrix() -> SkMatrix  (legacy 3x3)
```

Matrix mutations are *premultiplied* with the existing matrix, so they
transform geometry *before* the existing transform. `setMatrix` replaces
the entire current transform; `concat` composes.

### Clipping

| Call | Effect |
|---|---|
| `clipRect(rect[, op, doAA])` | `op` defaults to `SkClipOp::kIntersect`; `kDifference` is the only other option. |
| `clipIRect(irect[, op])` | Promotes to scalar via `SkRect::Make`. |
| `clipRRect(rrect[, op, doAA])` | |
| `clipPath(path[, op, doAA])` | Honors path fill type. |
| `clipShader(sk_sp<SkShader>[, op])` | The shader's alpha defines the clip mask. |
| `clipRegion(deviceRgn[, op])` | `deviceRgn` is in device coordinates (matrix-independent). |
| `quickReject(rect)`, `quickReject(path)` | Conservative "outside the clip?" test. |
| `getLocalClipBounds()` / `getDeviceClipBounds()` | Conservative bounds. |

`isClipEmpty()` and `isClipRect()` are virtual probes that some backends
implement more efficiently than walking the clip stack.

### Pixel access

`SkCanvas` exposes pixel I/O for backends that allow it (raster surfaces, GPU
surfaces). The exact methods include `accessTopLayerPixels`,
`peekPixels(SkPixmap*)`, `readPixels(...)` (three overloads — `SkImageInfo`,
`SkPixmap`, `SkBitmap`), `writePixels(SkImageInfo, ...)`, and
`writePixels(SkBitmap, x, y)`. Document-based and recorder-backed canvases
return false from these. `accessTopRasterHandle()` returns the
`SkRasterHandleAllocator::Handle` for the topmost layer (see
[surface-and-output.md](surface-and-output.md)).

### Draw calls

The full set of draw entry points (each accepts at minimum a `SkPaint`):

```
drawColor / clear / drawPaint / discard
drawPoint / drawPoints (PointMode = kPoints | kLines | kPolygon)
drawLine
drawRect / drawIRect / drawRegion / drawOval / drawCircle / drawArc
drawRRect / drawDRRect / drawRoundRect
drawPath
drawImage / drawImageRect / drawImageNine / drawImageLattice
experimental_DrawEdgeAAQuad / experimental_DrawEdgeAAImageSet
drawSimpleText / drawString (overloads for SkString and const char*)
drawGlyphs (with positions, optional clusters+UTF-8) / drawGlyphsRSXform
drawTextBlob
drawPicture (with optional matrix and paint — paint forces an implicit layer)
drawVertices (SkBlendMode controls how vertex colors blend with the shader)
drawMesh (custom SkMeshSpecification — see runtime-effects.md)
drawPatch (12-point Coons cubic patch + 4 colors + 4 texCoords)
drawAtlas (SkRSXform-positioned sub-rects from an atlas image, optional per-sprite colors)
drawDrawable (replay an SkDrawable)
drawAnnotation (key/value metadata; PDF and SKP backends consume these)
private_draw_shadow_rec (for the SkShadowUtils path)
```

`SrcRectConstraint::kStrict_SrcRectConstraint` vs. `kFast_SrcRectConstraint`
on `drawImageRect` controls whether the sampler is allowed to read outside
the source rect (mipmaps and anisotropic filtering require strict-off).

`Lattice` (used by `drawImageLattice`) divides the source into a rectangular
grid; entries on even columns and rows are drawn at native size, the rest are
stretched to fit. Each cell can be `kDefault`, `kTransparent` (skipped), or
`kFixedColor` (filled from the `fColors` array).

### `SaveLayerRec` deep dive

Layers are the only state-stack operation that allocates pixel storage.
`SaveLayerRec` collects every layer parameter:

| Field | Default | Meaning |
|---|---|---|
| `fBounds` | `nullptr` | Hint at the layer extent. Drawing outside is clipped, but the layer is allocated only large enough to hold drawing inside the current clip. |
| `fPaint` | `nullptr` | Applied when the layer is composed back into the parent. Honors alpha, color filter, image filter, blend mode. |
| `fFilters` | empty | Up to `kMaxFiltersPerLayer = 16` `sk_sp<SkImageFilter>`. Mutually exclusive with `fPaint->getImageFilter()`. |
| `fBackdrop` | `nullptr` | If non-null, the *parent* layer is filtered through `fBackdrop` and used as the new layer's initial contents (instead of transparent black). |
| `fBackdropTileMode` | `SkTileMode::kClamp` | How the backdrop is sampled when the filter reads outside its bounds. |
| `fColorSpace` | `nullptr` | Promote the layer to a different color space; a conversion runs at restore time. |
| `fSaveLayerFlags` | `0` | Bitmask: `kPreserveLCDText_SaveLayerFlag`, `kInitWithPrevious_SaveLayerFlag` (initialize from the parent's pixels), `kF16ColorType` (use F16 instead of inheriting parent's color type). |

### Subclass extension contract

Most public draw entry points are non-virtual; they normalize arguments and
forward to a corresponding `onDraw*` virtual:

```
onDrawPaint / onDrawBehind
onDrawRect / onDrawRRect / onDrawDRRect / onDrawOval / onDrawArc
onDrawPath / onDrawRegion / onDrawPoints
onDrawImage2 / onDrawImageRect2 / onDrawImageLattice2 / onDrawAtlas2
onDrawVerticesObject / onDrawShadowRec
onDrawTextBlob / onDrawGlyphRunList / onDrawSlug / onDrawPatch
onDrawDrawable / onDrawPicture / onDrawAnnotation
onClipRect / onClipRRect / onClipPath / onClipShader / onClipRegion / onResetClip
onDoSaveBehind / willSave / willRestore / didRestore / didConcat44 / didSetM44
```

`SkCanvasVirtualEnforcer<Base>` (`include/core/SkCanvasVirtualEnforcer.h`) is
the recommended pattern for canvas subclasses — it forces overrides of all
new virtuals as Skia adds them, so that downstream wrappers fail to compile
rather than silently dropping draws when an SDK upgrade adds a new draw type.

`SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec&)` is the hook by
which a subclass can suppress layer allocation (`kNoLayer_SaveLayerStrategy`)
when it knows it doesn't need one — the wrapper canvases use this.

---

## Wrapper canvases

These all live under `include/utils/` (with `SkOverdrawCanvas` in
`include/core/`). Each derives from `SkCanvasVirtualEnforcer<...>` and works
by overriding the `onDraw*` family.

### SkNoDrawCanvas

Pixel-less canvas with conservative clipping (clip operations only see
rectangles). Every `onDraw*` is an empty body, so a `SkNoDrawCanvas` records
matrix and clip changes but performs no rasterization. Used as a base class
by analysis canvases, and exposed directly for clients that want to walk the
matrix/clip stack while replaying a picture.

`resetCanvas(w, h)` / `resetCanvas(SkIRect)` are an optimization to recycle
the same instance across many pictures without re-allocating.

### SkNullCanvas

`SkMakeNullCanvas()` returns a `unique_ptr<SkCanvas>` that drops every draw.
Used for performance baselines (how fast can the recording side go if the
draw side is free?).

### SkNWayCanvas

Forwards every draw to a list of inner canvases (`addCanvas`, `removeCanvas`,
`removeAll`). Useful for "draw to two surfaces at once" or "draw and capture
simultaneously". Built on top of `SkNoDrawCanvas`.

### SkOverdrawCanvas

Constructed around an `SkCanvas` that targets an `kAlpha_8_SkColorType`
bitmap. Each draw is rewritten so that, instead of painting the source color,
it adds 1 to every pixel it would have touched. The result is a per-pixel
overdraw count that an `SkOverdrawColorFilter` can later visualize.

### SkPaintFilterCanvas

Forwarding canvas that intercepts every draw and lets the subclass mutate the
`SkPaint` (or skip the draw entirely) before forwarding it to the wrapped
canvas. `onFilter(SkPaint&) -> bool` is the only virtual to implement.

Notes from the source:
- The base implementation only filters top-level paints. To filter paints
  *inside* recorded `SkPicture`s or `SkTextBlob`s, override `drawPicture` /
  `drawTextBlob` directly.
- It inherits from `SkNWayCanvas` and stores exactly one inner canvas in
  `fList[0]`; `proxy()` accesses it.
- `internal_private_asPaintFilterCanvas()` lets Skia internals detect the
  type without RTTI.

---

## Picture recording

### SkPicture

`SkPicture` is an immutable, refcounted record of one or more `SkCanvas`
draw calls. It's abstract — concrete subclasses are `SkBigPicture`
(non-trivial) and `SkEmptyPicture` — and hidden behind factories.

| API | Notes |
|---|---|
| `playback(SkCanvas* canvas, AbortCallback* = nullptr)` | Replay onto `canvas`. `AbortCallback::abort()` returning true stops playback (canvas state still restores). |
| `cullRect()` | Bounds hint passed at recording time; the recorder may discard ops fully outside this rect. |
| `uniqueID()` | Process-unique non-zero id. |
| `serialize([procs])` | Returns `sk_sp<SkData>`; a custom `SkSerialProcs::fImageProc` is required to keep `SkImage`s alive (the default encodes a nullptr). |
| `serialize(SkWStream*, [procs])` | Stream variant. |
| `MakeFromStream(stream, [procs])` / `MakeFromData(data[, procs])` / `MakeFromData(ptr, size, [procs])` | Parse from a serialized SKP. |
| `MakePlaceholder(cull)` | Empty picture used for slot-replacement during playback. |
| `approximateOpCount(nested = false)` | Heuristic op count. |
| `approximateBytesUsed()` | Heuristic memory footprint. |
| `makeShader(SkTileMode, SkTileMode, SkFilterMode[, localMatrix, tileRect])` | Wraps the picture in an `SkShader` (replays into a tile each time it's sampled). |

`AbortCallback` is a non-copyable abstract class with a single `abort()`
virtual.

### SkPictureRecorder

The standard builder.

```c++
SkPictureRecorder recorder;
SkCanvas* canvas = recorder.beginRecording(SkRect::MakeWH(640, 480));
canvas->drawRect(...);
sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
```

| Method | Notes |
|---|---|
| `beginRecording(bounds, sk_sp<SkBBoxHierarchy>)` | Provide the bounding-box hierarchy directly. |
| `beginRecording(bounds, SkBBHFactory* = nullptr)` | Use a factory; pass `SkRTreeFactory()` for a default R-tree, nullptr for none. |
| `beginRecording(width, height, factory)` | Convenience for `MakeWH`. |
| `getRecordingCanvas()` | Returns the active canvas (or null if not recording). |
| `finishRecordingAsPicture()` | Returns the recorded picture. Drawables added to the canvas are *snapshotted* into the picture. |
| `finishRecordingAsPictureWithCull(newCullRect)` | Same, but updates the cull rect. |
| `finishRecordingAsDrawable()` | Returns an `SkDrawable` that holds *live* references to nested drawables — useful when the scene contains animated drawables that need to be re-evaluated each frame. |

Internally, `SkPictureRecorder` owns a `SkRecord` (the op buffer) and a
`SkRecordCanvas` (the canvas that appends ops to it). The bounding-box
hierarchy (`SkBBHFactory` → `SkRTreeFactory` is the standard implementation)
indexes ops by bounds so playback can skip ops whose bounds fall outside the
playback canvas's clip — significant speedup for pictures with large empty
regions.

### SkDrawable

Whereas `SkPicture` is immutable, `SkDrawable` is "draw-on-demand": each
playback may produce different content (animation, lazy expansion, native
GPU draws). `SkDrawable` derives from `SkFlattenable` so it can be serialized.

The required virtuals for subclassers:

```c++
virtual SkRect onGetBounds() = 0;
virtual void   onDraw(SkCanvas*) = 0;
virtual size_t onApproximateBytesUsed();          // optional, default 0
virtual sk_sp<SkPicture> onMakePictureSnapshot(); // optional, default records onDraw into a picture
virtual std::unique_ptr<GpuDrawHandler>
   onSnapGpuDrawHandler(GrBackendApi, const SkMatrix&,
                        const SkIRect& clipBounds, const SkImageInfo&);
```

Public surface:

| Method | Notes |
|---|---|
| `draw(SkCanvas*[, const SkMatrix*])` / `draw(SkCanvas*, x, y)` | Forwards to `onDraw`. |
| `snapGpuDrawHandler(...)` | For Vulkan-backed Ganesh — returns a `GpuDrawHandler` whose `draw(GrBackendDrawableInfo)` is invoked when the GPU backend flushes; lets the drawable issue raw 3D-API calls intermixed with Skia draws. |
| `makePictureSnapshot()` | Captures the current state into an `SkPicture`. |
| `getGenerationID()` | Stable until the drawable changes. Subclasses call `notifyDrawingChanged()` to invalidate it. |
| `getBounds()` | Conservative bounds covering all possible states. |
| `Deserialize(...)` | Deserialize a flattened drawable. |

Drawables are how live, mutable objects participate in pictures: when a
recording canvas sees `drawDrawable(d)`, it captures a snapshot reference;
when a "real" canvas sees the same call, it just invokes `d->draw(canvas)`
immediately.

---

## SkRecorder — the recorder taxonomy

`include/core/SkRecorder.h` (added in 2025) is a tiny abstract type that
every backend's "recorder" satisfies. It exists so that `SkCanvas::baseRecorder()`
can return a single pointer that callers query for backend identity:

```c++
class SK_API SkRecorder {
public:
    enum class Type { kCPU, kGanesh, kGraphite };
    virtual Type type() const = 0;
    virtual skcpu::Recorder* cpuRecorder() = 0;
private:
    virtual SkCanvas* makeCaptureCanvas(SkCanvas*) = 0;
    virtual SkContentID createCaptureBreakpoint(SkSurface*) = 0;
    friend class SkSurface_Base;
};
```

Concrete recorders:

| Recorder | Source | Purpose |
|---|---|---|
| `skcpu::Recorder` | `src/core/SkCPURecorder*.{h,cpp}`, public surface in `include/core/SkCPURecorder.h` | CPU-side scratch arena, glyph cache strike refs, image-filter context. Every raster surface pulls one out of `skcpu::Recorder::TODO()` (the current "ambient" recorder). |
| `GrRecordingContext` (Ganesh) | `include/gpu/ganesh/GrRecordingContext.h` | Records Ganesh ops; subclass `GrDirectContext` actually flushes to the GPU. |
| `skgpu::graphite::Recorder` | `include/gpu/graphite/Recorder.h` | Records Graphite work into a `Recording` that a `Context` consumes. |

`SkCanvas` exposes three queries:

- `recordingContext()` — returns the Ganesh `GrRecordingContext*` if the
  canvas is GPU-backed in Ganesh, else nullptr.
- `recorder()` — returns the Graphite `skgpu::graphite::Recorder*` if
  Graphite-backed, else nullptr.
- `baseRecorder()` — returns the unified `SkRecorder*` (always non-null for
  drawable canvases).

`makeCaptureCanvas` and `createCaptureBreakpoint` on `SkRecorder` are
private hooks used by `SkSurface_Base` to integrate with Skia's capture
infrastructure (see [capture-and-debugging.md](capture-and-debugging.md)).

---

## How a draw flows

For a CPU draw of a rectangle:

1. Client calls `canvas->drawRect(rect, paint)`.
2. `SkCanvas::drawRect` checks for early reject (`paint.nothingToDraw()`,
   no overlap with the device clip via `quickReject`), then calls the
   virtual `onDrawRect`.
3. The default `onDrawRect` checks `paint.getMaskFilter()`/`getPathEffect()`;
   if either is set, it converts to a path and calls `onDrawPath`. Otherwise
   it forwards to the active `SkDevice`.
4. The raster device (`SkBitmapDevice`) chooses a blitter via
   `SkBlitter::Choose` based on color type, paint shader, alpha, blend mode,
   and color space, then walks the rect through the blitter row-by-row.
5. The blitter writes pixels into the device's `SkPixmap` (which points at
   the surface's underlying memory).

For a recording draw, step 4 is replaced by appending the op to the
`SkRecord` buffer; step 5 happens later when `SkPicture::playback` is run on
a real canvas.

For a GPU draw, step 4 hands the geometry to the recorder
(`GrRecordingContext` or `skgpu::graphite::Recorder`), which emits a
draw op into the appropriate render-pass and shader pipeline (see
[ganesh-backend.md](ganesh-backend.md), [graphite-backend.md](graphite-backend.md)).

---

## Cross-references

- [`SkPaint`](paint-color-and-blending.md) — every draw call accepts one.
- [`SkPath`, `SkMatrix`, `SkRect`, `SkRRect`, `SkVertices`](geometry-and-math.md)
  — geometry primitives.
- [`SkSurface`](surface-and-output.md) — provides the `SkCanvas` you
  ultimately draw into.
- [`SkColorSpace`](color-management.md) — the canvas, surface, and layers all
  carry one.
- [`SkImage`](bitmap-pixmap-image.md) — the source for `drawImage*`.
- [`SkTextBlob`, `SkFont`, `SkTypeface`](text-and-fonts.md) — for text drawing.
- [`SkImageFilter`, `SkMaskFilter`](image-filters-and-mask-filters.md) and
  [`SkPathEffect`](path-effects.md) — paint-attached effects that may turn a
  draw into a much larger pipeline.
