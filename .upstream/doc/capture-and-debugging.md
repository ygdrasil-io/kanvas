# Capture & Debugging

This document covers Skia's runtime capture and debugging surface — the
machinery that lets a host application snapshot what its `SkCanvas`
draws, attach human-readable trace events to GPU work, and replay or
inspect those captures offline. The capture stack is the foundation of
the standalone debugger (`tools/debugger`) and the in-browser
[CanvasKit](canvaskit.md) debugger UI.

| Surface | Source root | Purpose |
| --- | --- | --- |
| Live capture API | `src/capture/` | wrap an `SkCanvas`, snap `SkPicture`s on demand |
| `SkCaptureCanvas` | `src/capture/SkCaptureCanvas.h` | SkNWayCanvas that mirrors draws into a recorder |
| `SkCaptureManager` | `src/capture/SkCaptureManager.h` | tracks active capture canvases, holds the latest `SkCapture` |
| Trace events | `include/utils/SkEventTracer.h`, `src/core/SkTraceEvent.h` | `TRACE_EVENT*` macros that route to Chrome / Perfetto |
| Phase taxonomy | `include/utils/SkTraceEventPhase.h` | the `B`/`E`/`X`/… single-byte phase codes |
| Ganesh audit trail | `src/gpu/ganesh/GrAuditTrail.{h,cpp}` | per-op JSON log of GPU work |
| ATrace bridge | `src/utils/SkAndroidTracingUtils.cpp`, Perfetto build flags | re-emits `TRACE_EVENT*` to Android `atrace` / Perfetto |
| Debugger bindings | `modules/canvaskit/debugger_bindings.cpp` | WASM surface for the web debugger |
| Standalone debugger | `tools/debugger/` | C++ command-step UI, see [Developer Tools](developer-tools.md) |

The capture API is intentionally orthogonal to the canvas — clients opt
in by wrapping their canvas — so it composes with every backend
([Ganesh](ganesh-backend.md), [Graphite](graphite-backend.md), the
[CPU pipeline](cpu-rendering-pipeline.md), and [PDF](pdf-backend.md)).

---

## SkCaptureCanvas — `src/capture/SkCaptureCanvas.h`

`SkCaptureCanvas` is an `SkNWayCanvas` (see
[Canvas & Recording](canvas-and-recording.md)) that the host wraps
around its real drawing canvas. Every draw is forwarded to both the
underlying canvas and an internal `SkPictureRecorder`, so the capture
runs at full fidelity without affecting on-screen output.

```cpp
SkCaptureCanvas(SkCanvas* base, SkCaptureManager*);
sk_sp<SkPicture> snapPicture();   // closes and returns the current recording
SkSurface* getBaseCanvasSurface() const;
```

It overrides every `onDraw*` virtual on `SkCanvas` (paint, points,
rect, region, oval, arc, rrect, drrect, path, image, image-rect,
image-lattice, atlas, text-blob, glyph-run, slug, patch, picture,
drawable, shadow, edge-AA quad, vertices, annotation) and the matrix /
save-stack hooks (`willSave`, `getSaveLayerStrategy`, `onDoSaveBehind`,
`willRestore`, `didConcat44`, `didSetM44`, `didScale`, `didTranslate`)
so that the recorded `SkPicture` is byte-equivalent to what hit the
GPU.

## SkCaptureManager — `src/capture/SkCaptureManager.h`

The manager owns the global "are we capturing right now?" toggle and
the list of live `SkCaptureCanvas` instances:

```cpp
sk_sp<SkCanvas>  makeCaptureCanvas(SkCanvas*);   // wrap and track
void             toggleCapture(bool);            // arm / disarm
void             snapPictures();                 // snap all tracked canvases
SkContentID      snapPicture(SkSurface*);        // snap one and tag it
sk_sp<SkCapture> getLastCapture() const;
bool             isCurrentlyCapturing() const;
```

`SkContentID` is a monotonic per-surface tag (`fSurfaceContentCounters`
maps unique surface id → next `SkContentID`) so a multi-surface app
(e.g. game UI + main scene) can be reassembled in the right z-order
when the capture is replayed.

`SkCapture` itself is a small ref-counted bundle of pictures + per-snap
metadata. It is consumed by `tools/debugger` and CanvasKit to drive
step-by-step playback.

## Audit trail — `src/gpu/ganesh/GrAuditTrail.h`

Inside the [Ganesh](ganesh-backend.md) op pipeline, `GrAuditTrail`
optionally records every `GrOp` that gets enqueued, batched, and
flushed. It is gated behind `AutoEnable` because keeping the log alive
is expensive — the comment at the top of the header is unusually
emphatic about disabling it promptly. The captured ops are dumped via
`SkJSONWriter`, which is what feeds the "GPU ops" panel of the
debugger.

The Graphite backend has analogous diagnostics (`Recorder` priv
inspectors, `GraphicsPipelineCache` stats) — see
[Graphite Backend](graphite-backend.md).

## Trace events — `SkEventTracer` / `SkTraceEvent.h`

Skia is instrumented with `TRACE_EVENT0("skia", "name")`,
`TRACE_EVENT_BEGIN0`, `TRACE_EVENT_INSTANT*`, etc. Those macros expand
to calls into `src/core/SkTraceEvent.h`, which in turn route through
`SkEventTracer::GetInstance()`.

`SkEventTracer` is a thin abstract interface (header at
`include/utils/SkEventTracer.h`):

```cpp
class SK_API SkEventTracer {
    typedef uint64_t Handle;
    static bool SetInstance(SkEventTracer*);   // first writer wins
    static SkEventTracer* GetInstance();       // installs default if unset

    virtual const uint8_t* getCategoryGroupEnabled(const char* name) = 0;
    virtual Handle addTraceEvent(char phase, …, int32_t numArgs, …) = 0;
    virtual void   updateTraceEventDuration(…, Handle) = 0;
    // optional: newTracingSection(name)
    virtual void   onExit() {}
};
```

The intent is documented in the header itself: "the interface between
Skia's internal tracing macros and an external entity (e.g., Chrome)".
A host like Chrome installs a subclass that forwards to `base::trace_event`;
on Android, an SkEventTracer subclass forwards to `ATrace_*` / Perfetto;
the Skia-internal default is `SkDebugfTracer`. Phase codes
(`'B'` begin, `'E'` end, `'X'` complete, `'I'` instant, `'S'`/`'F'`
async start/finish, `'M'` metadata, …) are catalogued in
`include/utils/SkTraceEventPhase.h` and follow the Chrome
trace-event JSON spec verbatim, which is why a Skia trace can be loaded
straight into `chrome://tracing` or `ui.perfetto.dev`.

## Debugger bindings — `modules/canvaskit/debugger_bindings.cpp`

The CanvasKit debugger ships as a separate WASM module whose Embind
surface is `modules/canvaskit/debugger_bindings.cpp`. It exposes
`DebugTrace` (a deserialised `.skp`-with-commands), per-command stepping,
GPU op breakdown, and matrix / clip / paint introspection so the
JavaScript UI in `modules/canvaskit/debugger.js` can render a Chrome
DevTools-style timeline. See [CanvasKit](canvaskit.md) for the broader
WASM surface.

## See also

- [Canvas & Recording API](canvas-and-recording.md) — `SkCanvas`, `SkPictureRecorder`, `SkNWayCanvas` (the base of `SkCaptureCanvas`).
- [Developer Tools](developer-tools.md) — `tools/debugger`, `tools/skiaserve`, the Viewer slide system and the `.skp` round-trippers.
- [Ganesh Backend](ganesh-backend.md) — context for `GrAuditTrail` and the GPU op log.
- [CanvasKit](canvaskit.md) — debugger WASM bindings shipped to the browser.
