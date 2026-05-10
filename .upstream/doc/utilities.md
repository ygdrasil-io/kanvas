# Utilities

`include/utils/` and `src/utils/` collect helpers that build on top of
the core API but do not belong to any single subsystem. Most of them
are optional — a Skia build that does not call them simply does not
link them — but together they are used heavily by Android, Chromium,
the Skia tools (`dm`, `viewer`, `gms`), and any embedder that wants
common conveniences (text layout primitives, custom typefaces, debug
canvases, perspective transforms, …).

This page groups the headers by purpose. For the canvas surface they
extend see [Canvas & Recording API](canvas-and-recording.md); for the
text/font types they wrap see [Text & Fonts](text-and-fonts.md).

## Source map

| Group | Public header | Implementation |
| --- | --- | --- |
| String/path/colour parsing | `include/utils/SkParse.h`, `SkParsePath.h` | `src/utils/SkParse.cpp`, `SkParsePath.cpp`, `SkParseColor.cpp` |
| Drop shadows | `include/utils/SkShadowUtils.h` | `src/utils/SkShadowUtils.cpp`, `SkShadowTessellator.cpp` |
| User-defined fonts | `include/utils/SkCustomTypeface.h` | `src/utils/SkCustomTypeface.cpp` |
| 3D / perspective camera | `include/utils/SkCamera.h` | `src/utils/SkCamera.cpp` |
| Canvas wrappers | `SkPaintFilterCanvas.h`, `SkNWayCanvas.h`, `SkNoDrawCanvas.h`, `SkNullCanvas.h` | `SkPaintFilterCanvas.cpp`, `SkNWayCanvas.cpp`, `SkNullCanvas.cpp`, `SkCanvasStack.cpp` |
| Canvas state hand-off | `SkCanvasStateUtils.h` | `src/utils/SkCanvasStateUtils.cpp` |
| Multi-page documents | (`SkDocument` from core) | `src/utils/SkMultiPictureDocument.cpp` |
| Text drawing helper | `include/utils/SkTextUtils.h` | `src/utils/SkTextUtils.cpp` |
| Font manager utility | `include/utils/SkOrderedFontMgr.h` | `src/utils/SkOrderedFontMgr.cpp` |
| Tracing | `SkEventTracer.h`, `SkTraceEventPhase.h` | `src/utils/SkEventTracer.cpp` |
| JSON output | (private) `src/utils/SkJSONWriter.h` | `SkJSONWriter.cpp` |
| Path effect helpers (private) | — | `SkDashPath.cpp`, `SkPolyUtils.cpp`, `SkPatchUtils.cpp`, `SkClipStackUtils.cpp` |
| Platform `SkCFObject` | `include/utils/mac/SkCGUtils.h` | `src/utils/mac/` |
| `_get_executable_path` | `src/utils/SkGetExecutablePath.h` | `SkGetExecutablePath_{linux,mac,win}.cpp` |

The `mac/` and `win/` subdirectories contain platform-only utilities
that complement [Platform Ports](platform-ports.md) — for example
`SkCGUtils` lets callers convert `SkBitmap` ↔ `CGImageRef`, and
`SkAutoCoInitialize` brackets a Win32 COM lifecycle.

---

## Parsing — `SkParse`, `SkParsePath`, `SkParseColor`

`SkParse` (`include/utils/SkParse.h`) is a tiny scanner used to read
boolean / scalar / hex / fixed-point tokens out of an ASCII buffer.
It is the lowest-level helper behind `SkParsePath` and the SVG-style
attribute readers in the SVG module.

`SkParsePath` parses an SVG `d` attribute into an `SkPath`
(`SkParsePath::FromSVGString`) and serialises an `SkPath` back
(`SkParsePath::ToSVGString`). Both directions are loss-free for the
subset of commands the SVG path grammar supports
(`MmZzLlHhVvCcSsQqTtAa`); curves outside that subset are emitted as
cubic approximations.

`SkParseColor` parses CSS-style colour names ("red", "rebeccapurple",
…) into `SkColor`. It backs `SkSVGShape`'s colour attribute parsing
and is occasionally useful for custom DSLs.

These helpers do *no* allocation beyond the path/colour they return,
so they are safe to use from tight loops or signal handlers.

---

## Shadow drawing — `SkShadowUtils`

`SkShadowUtils::DrawShadow` is the high-level entry point used by
Android's `View.setOutlineProvider` shadows and by Compose. Given a
path, a height function (the `zPlaneParams` plane), a light position,
and ambient/spot colours, it draws an analytic ambient + spot shadow
onto the canvas in one call (`skia-main/include/utils/SkShadowUtils.h:61`).

The flag bits in `SkShadowFlags` control the trade-offs:

- `kTransparentOccluder_ShadowFlag` — allow Skia to draw shadow geometry
  *under* the occluder (otherwise the occluder is assumed opaque and
  shadow geometry behind it is culled).
- `kGeometricOnly_ShadowFlag` — disable the analytic fast path and
  force tessellation (used to debug the analytic path).
- `kDirectionalLight_ShadowFlag` — interpret `lightPos` as a direction
  rather than a point; `lightRadius` then becomes the blur amount at
  Z=1, growing linearly with elevation.
- `kConcaveBlurOnly_ShadowFlag` — skip tessellation for concave paths
  and use a blurred mask instead.

`GetLocalBounds` returns the conservative shadow extent so callers can
expand layer rectangles ahead of time. `ComputeTonalColors` matches
the Material-design "tonal alpha" formula used by Android's framework
shadows; consumers that want bit-identical output should call it
before passing colours to `DrawShadow`.

The implementation lives in `src/utils/SkShadowUtils.cpp` (CPU/Ganesh
glue, caching keyed on the path generation ID and the canvas matrix)
and `src/utils/SkShadowTessellator.cpp` (the geometric path that fills
in when analytic shadows do not apply — perspective matrices, volatile
paths, the `kGeometricOnly` flag).

---

## Custom typefaces — `SkCustomTypeface`

`SkCustomTypefaceBuilder` (`include/utils/SkCustomTypeface.h`) lets a
caller assemble a `SkTypeface` whose glyphs are *paths or
`SkDrawable`s*, rather than coming from a TTF/OTF file. It is the
mechanism behind icon fonts authored in Skia and Lottie's
"Lottie-as-a-font" trick.

Usage:

```cpp
SkCustomTypefaceBuilder b;
b.setMetrics(myMetrics, 1.0f);
b.setFontStyle(SkFontStyle::Normal());
b.setGlyph(/*glyphID*/ 'A', /*advance*/ 12, pathForA);
b.setGlyph(/*glyphID*/ 'B', /*advance*/ 12, drawableForB, drawableBounds);
sk_sp<SkTypeface> tf = b.detach();
```

(`skia-main/include/utils/SkCustomTypeface.h:32-42`).

Every glyph either carries an `SkPath` outline (the common case) or an
`SkDrawable` that is invoked at draw time (used for animated/colour
glyphs whose contents change frame-to-frame). The resulting `SkTypeface`
behaves like any other: it can be embedded in an `SkFont`, fed to
`SkTextBlob`, and serialised through the standard typeface flattening
mechanism (the deserialiser is registered under
`SkSetFourByteTag('u','s','e','r')`).

The serialised form is a Skia-private container; reading it back on
another system requires `SkCustomTypefaceBuilder::MakeFromStream`. See
[Text & Fonts](text-and-fonts.md) for how `SkTypeface` plugs into the
rest of the text stack.

---

## Perspective camera — `SkCamera`

`SkCamera3D` (`include/utils/SkCamera.h`) is a small helper that
bridges Skia's 2D matrix model with a perspective camera. Callers set
the eye position, the world translation/rotation/scale, and ask for
the resulting `SkMatrix` to apply to the canvas.

The header dates back to Android's pre-Hardware Layers days, where it
emulated the `Camera` class from android.graphics. Today it is mainly
used by the GMs that test perspective behaviour and by hwui's compat
shim for `Canvas.setMatrix(Matrix)` calls that originated in
`Camera.applyToCanvas`. New code that wants a perspective `SkMatrix`
typically constructs it directly via `SkMatrix::SetAll` rather than
going through `SkCamera3D`.

---

## Canvas wrappers

The canvas-wrapper headers all extend `SkCanvas` and intercept calls
either to redirect them, multiplex them, or filter them. They share a
common pattern: subclass `SkCanvasVirtualEnforcer<SkCanvas>` (or one
of its specialisations) so the compiler refuses to silently miss new
virtual methods when the core canvas grows them.

- **`SkNoDrawCanvas`** — accepts every call but draws nothing. Useful
  for measurement passes, clip computation, and dry-runs of recording
  pipelines.
- **`SkNullCanvas`** — even thinner: just discards everything. Used in
  tests as a sink.
- **`SkNWayCanvas`** — broadcasts every call to a list of child
  canvases. Adding a `dm` GPU canvas plus a `SkPicture` recorder is a
  common combination.
- **`SkPaintFilterCanvas`** — the most flexible wrapper. Subclass and
  override one virtual method, `bool onFilter(SkPaint&) const`, which
  is invoked with every paint about to be used. Returning `false`
  skips the draw; returning `true` after mutating the paint lets the
  draw proceed with the modified paint
  (`skia-main/include/utils/SkPaintFilterCanvas.h:81`). It already
  forwards to the wrapped canvas through `SkNWayCanvas`, so chains of
  filter canvases compose naturally. Android's `Canvas.drawColorFilter`
  for accessibility and Chromium's print-preview tinting are both
  built on this. `SkAndroidFrameworkUtils::getBaseWrappedCanvas`
  unrolls such chains
  (see [Android Integration](android-integration.md)).
- **`SkCanvasStack`** (private, `src/utils/SkCanvasStack.h`) — used
  internally by `SkNWayCanvas` to track per-child clip state.

---

## Other helpers

- **`SkCanvasStateUtils`** — captures the matrix, clip, and layer
  state of a canvas into an opaque blob (`SkCanvasState*`) that can be
  re-applied to a *different* canvas later. The Android framework
  uses it to hand a partially-set-up canvas across a JNI/IPC boundary.
- **`SkMultiPictureDocument`** (`src/utils/`) — implements an
  `SkDocument` whose pages are individual `SkPicture` blobs. Skia's
  `dm` tool serialises GMs to disk in this format for byte-comparable
  golden testing.
- **`SkTextUtils`** (`include/utils/SkTextUtils.h`) — convenience
  wrappers that combine `SkFont`, alignment, and a string into a
  single `Draw` call (`SkTextUtils::Draw(canvas, text, len, encoding,
  x, y, font, paint, align)`). Real layout still needs
  `SkShaper`/`SkParagraph`; this is for one-line labels.
- **`SkOrderedFontMgr`** — `SkFontMgr` adapter that takes an ordered
  list of underlying managers and forwards `matchFamily*`/
  `makeFromData` queries through them in order. Used to layer a
  custom-directory manager on top of the system manager (or vice
  versa) without writing a new manager class. See
  [Platform Ports](platform-ports.md) for the underlying managers.
- **`SkEventTracer`** — Skia's pluggable trace event sink. Embedders
  install one with `SkEventTracer::SetInstance`; Skia then routes its
  `TRACE_EVENT*` macros through it. Chromium installs a tracer that
  forwards to Perfetto; standalone builds get a no-op default.
- **`SkJSONWriter`** (`src/utils/SkJSONWriter.h`) — streaming JSON
  emitter used by the debugger (`tools/skiaserve`) and the GPU
  audit-trail dumps. Not part of the public API but pervasive enough
  in `src/` to be worth knowing about.
- **`SkGetExecutablePath`** — three small platform files (`_linux`,
  `_mac`, `_win`) that return the running binary's path. Used by the
  tools, not by the library.

The private helpers under `src/utils/` (`SkPolyUtils`, `SkPatchUtils`,
`SkDashPath`, `SkClipStackUtils`, `SkBitSet`, `SkFloatToDecimal`, …)
are implementation details that other Skia subsystems link against
but are not exposed to callers.

---

## Cross-references

- [Canvas & Recording API](canvas-and-recording.md) — what the
  wrapper canvases extend.
- [Text & Fonts](text-and-fonts.md) — `SkTypeface`, `SkFont`, and
  `SkTextBlob` underpin `SkCustomTypeface` and `SkTextUtils`.
- [Platform Ports](platform-ports.md) — base font managers wrapped by
  `SkOrderedFontMgr`.
- [Android Integration](android-integration.md) — `SkPaintFilterCanvas`
  is also the type underneath `SkAndroidFrameworkUtils::getBaseWrappedCanvas`.
