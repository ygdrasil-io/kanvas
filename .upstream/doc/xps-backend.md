# XPS Backend

The XPS backend is a Windows-only `SkDocument` implementation that
turns `SkCanvas` operations into an **OpenXPS / XPS** package — the
XML-and-ZIP page-description format Microsoft uses for spool files,
"Print to XPS" output, and the System.Printing pipeline. It is built
on top of Windows' XPS Object Model (`XpsObjectModel.h`,
`IXpsOMObjectFactory`) rather than emitting raw XML by hand: Skia
constructs `IXpsOMPage`, `IXpsOMGlyphs`, `IXpsOMPath`, `IXpsOMBrush`
COM objects and asks Windows to serialize them, which guarantees a
spec-conformant `.xps` package without Skia maintaining its own XML
writer.

```
   SkXPS::MakeDocument(stream, IXpsOMObjectFactory*, Options)
            │   (SK_BUILD_FOR_WIN only)
            ▼
   ┌──────────────────────────┐  beginPage   ┌───────────────────┐
   │  SkXPSDocument           │ ───────────► │ SkXPSDevice       │
   │  (SkDocument impl)       │              │  └─ SkCanvas      │
   │   IXpsOMObjectFactory    │ ◄── endPage  │                   │
   │   units/pixels-per-meter │              └─────────┬─────────┘
   └──────────┬───────────────┘                        │ draw ops
              │ close()                                ▼
              ▼                                COM objects:
   IXpsOMPackageWriter ─► .xps (ZIP/OPC)        IXpsOMPage / Glyphs /
                                                Path / Brush / Visual
```

Because XPS is Windows-only, the entire backend is gated on
`SK_BUILD_FOR_WIN`. On any other platform the public header expands to
nothing and the factory is unavailable. For non-Windows clients that
need a paginated vector format, use the [PDF Backend](pdf-backend.md)
instead.

## Public surface — `include/docs/SkXPSDocument.h`

| Symbol | Purpose |
| --- | --- |
| `SkXPS::MakeDocument(stream, IXpsOMObjectFactory*, Options)` | Factory; returns an `sk_sp<SkDocument>` writing the OPC package to `stream`. The caller supplies a CoCreated `IXpsOMObjectFactory` (CLSID `XpsOMObjectFactory`). |
| `SkXPS::Options::dpi` | Resolution (default `SK_ScalarDefaultRasterDPI`, 72) used when geometry must be rasterized — perspective text, complex blend modes, large composite shaders. |
| `SkXPS::Options::pngEncoder` | `EncodePngCallback` — Skia calls back into this to PNG-encode any pixmap that has to become an XPS image brush. |
| `SkXPS::Options::allowNoPngs` | Acknowledge that no PNG encoder was supplied; needed for documents with no embedded raster content. Otherwise an internal assert fires. |

XPS pages are addressed in points (1 pt = 1/72 inch), the same units
as `SkCanvas` and the [PDF Backend](pdf-backend.md). Internally the
device converts to the XPS Object Model's metric units (units-per-meter
= 360000/127, pixels-per-meter = `dpi * 5000/127`), see
`src/xps/SkXPSDocument.cpp`.

Typical usage from a Windows client:

```cpp
SkTScopedComPtr<IXpsOMObjectFactory> factory;
HRESULT hr = CoCreateInstance(CLSID_XpsOMObjectFactory, nullptr,
                              CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&factory));
SkXPS::Options opts; opts.pngEncoder = SkPngEncoder::Encode;
sk_sp<SkDocument> doc = SkXPS::MakeDocument(stream, factory.get(), opts);

SkCanvas* page = doc->beginPage(widthPt, heightPt);
// draw …
doc->endPage();
doc->close();
```

The COM factory must outlive the document; it owns the OM heap that
all page objects live on.

---

## SkXPSDocument — `src/xps/SkXPSDocument.cpp`

`SkXPSDocument` is a tiny `SkDocument` subclass that holds:

- the caller-supplied `IXpsOMObjectFactory` (a COM-pointer, ref-counted);
- a single `SkXPSDevice` configured with a generous virtual canvas
  (`SkISize{10000, 10000}`) — actual page bounds come from
  `beginSheet`;
- the precomputed `unitsPerMeter` / `pixelsPerMeter` vectors derived
  from `Options::dpi`.

Lifecycle:

1. **Construction** calls `fDevice.beginPortfolio(stream, factory)`,
   which opens an `IXpsOMPackageWriter` on the stream and starts an
   FDS (FixedDocumentSequence) → FixedDocument scaffold inside the
   OPC package.
2. **`onBeginPage(w, h)`** calls `beginSheet(unitsPerMeter,
   pixelsPerMeter, {w, h})` and constructs an `SkCanvas` over the
   `SkXPSDevice`. The XPS device is `SkClipStackDevice`-derived, so the
   canvas's clip stack maps directly onto XPS clip elements.
3. **`onEndPage`** drops the canvas and calls `endSheet`, which
   serializes the page's `IXpsOMPage` into the OPC package as a
   FixedPage part.
4. **`onClose`** calls `endPortfolio`, finalising the FixedDocument
   and FixedDocumentSequence and closing the package writer.
5. **`onAbort`** is a no-op — partial XPS packages are not legal, so
   aborting simply discards in-memory COM state without flushing.

---

## SkXPSDevice — `src/xps/SkXPSDevice.h`

`SkXPSDevice final : public SkClipStackDevice` is the meat of the
backend. It does not draw pixels; it **builds COM objects**.

Notable members from the header:

- `beginPortfolio` / `endPortfolio` — opens / closes the OPC package
  writer; called once per document.
- `beginSheet` / `endSheet` — adds a single page; takes the
  units/pixels per meter, a `trimSize`, and optional `mediaBox`,
  `bleedBox`, `artBox`, `cropBox` rectangles, mirroring XPS' page-box
  vocabulary. `trimSize` is the visible page; `mediaBox` is the
  physical sheet (with negative top-left for any bleed); the others
  are advisory boxes the consumer can use for trimming, layout, or
  recommended viewport.
- `SkAutoCoInitialize` — RAII wrapper that ensures `CoInitializeEx` /
  `CoUninitialize` are balanced for any thread driving the backend.
- `SkTScopedComPtr<T>` — Skia's smart pointer for COM types,
  intrusive-ref-counted via `IUnknown::AddRef` / `Release`.

### Mapping SkCanvas to XPS

| `SkCanvas` op | XPS Object Model construct |
| --- | --- |
| `drawRect` / `drawPath` / `drawOval` | `IXpsOMPath` with an `IXpsOMGeometry`; the path's `IXpsOMGeometryFigure`s are filled or stroked with a brush. |
| `drawColor` / solid paint | `IXpsOMSolidColorBrush`. |
| Linear / radial gradients | `IXpsOMLinearGradientBrush`, `IXpsOMRadialGradientBrush` (with `IXpsOMGradientStop` collections). |
| Image / bitmap shaders | `IXpsOMImageBrush` referencing an `IXpsOMImageResource`; the bitmap is PNG-encoded via `Options::pngEncoder` and added as an OPC part. |
| Tile / pattern shaders | `IXpsOMVisualBrush` over a recorded `IXpsOMVisualCollection`. |
| Text — `drawTextBlob` / glyph runs | `IXpsOMGlyphs` with the typeface embedded as an `IXpsOMFontResource` (subset OpenType, fed via the DirectWrite path-glue described in [Platform Ports](platform-ports.md)). Glyph indices, advances, and offsets come straight from the `sktext::GlyphRunList`; tracked in an `SkBitSet` so each font part embeds only the glyphs actually used on the page. |
| `clipPath` / `clipRect` / `clipRRect` | `IXpsOMGeometry` set as the page-tree node's `Clip`. The clip stack is replayed into nested `IXpsOMCanvas` elements when intersecting clips are involved. |
| `saveLayer` | A new `IXpsOMCanvas` group with an opacity / opacity-mask brush. |
| `drawImage` / `drawImageRect` | Image is PNG-encoded, registered as an `IXpsOMImageResource`, and a rectangular path with an `IXpsOMImageBrush` is emitted. |

Anything XPS cannot represent natively is rasterized at
`Options::dpi` and embedded as a PNG image brush — the same fallback
strategy as the PDF backend, but with PNG instead of JPEG /
`/FlateDecode`. Hence the requirement for a PNG encoder callback (or
explicit `allowNoPngs = true` for vector-only documents).

### Fonts

XPS fonts are embedded as **OpenType font resources** inside the OPC
package, referenced by the `IXpsOMGlyphs` element via a font URI. On
Windows these resources are produced from the typeface's underlying
DirectWrite font file: Skia's `SkTypeface_DirectWrite` exposes the
font-data stream (see [Platform Ports](platform-ports.md)) and the
XPS device subsets it down to the used glyph IDs before adding the
part to the package. Color glyphs (COLR/CPAL, CBDT/CBLC,
SVG-in-OpenType) are not directly representable in XPS and fall back
to per-glyph rasterized image brushes.

---

## Limitations

- **Windows-only** — the entire `src/xps/` tree compiles only when
  `SK_BUILD_FOR_WIN` is defined; downstream callers should `#ifdef`
  XPS-specific code or use the PDF backend on cross-platform builds.
- **Requires the Windows XPS Object Model** — the caller must supply
  an `IXpsOMObjectFactory`. There is no fallback that emits XPS XML
  directly; if `XpsObjectModel.dll` is unavailable (Windows Server
  Core without the desktop role), `SkXPS::MakeDocument` cannot be
  used.
- **PNG-only image brushes** — XPS' image-brush model accepts JPEG,
  PNG, TIFF, JPEG-XR; Skia's backend always re-encodes through the
  `EncodePngCallback`, so callers do not need a JPEG encoder but do
  need PNG.
- **No tagged / accessible XPS** — there is no equivalent of the PDF
  structure tree (`SkPDFTagTree`); XPS does have its own metadata and
  story-fragments mechanisms but those are not currently emitted.

---

## Cross references

- [PDF Backend](pdf-backend.md) — sister `SkDocument` backend; the
  preferred cross-platform target for paginated vector output.
- [Canvas & Recording API](canvas-and-recording.md) — the `SkCanvas`
  side of the contract; `SkDocument::beginPage` returns an
  `SkXPSDevice`-backed canvas.
- [Platform Ports](platform-ports.md) — DirectWrite typeface and
  font-stream plumbing that backs XPS' embedded font resources.

## Source map

| File | Role |
| --- | --- |
| `include/docs/SkXPSDocument.h` | Public `SkXPS::MakeDocument` factory and `Options`. |
| `src/xps/SkXPSDocument.cpp` | `SkDocument` driver: portfolio / sheet lifecycle. |
| `src/xps/SkXPSDevice.{h,cpp}` | `SkBaseDevice` that builds the XPS Object Model COM tree. |
| `src/utils/win/SkAutoCoInitialize.h` | RAII `CoInitializeEx` / `CoUninitialize` helper. |
| `src/utils/win/SkTScopedComPtr.h` | Smart pointer for COM `IUnknown`-derived types. |
| `src/utils/win/SkHRESULT.h` | `HRESULT` checking macros used throughout the backend. |
