# PDF Backend

The PDF backend turns an `SkCanvas` into a stream of paginated PDF
content. It is implemented as an `SkDocument` whose pages each return a
fresh `SkCanvas` backed by an `SkPDFDevice`; recorded drawing
operations are translated into PDF operators (graphics state, paths,
shaders, fonts, images), serialized into PDF objects, and written to a
caller-supplied `SkWStream`. The result is a real PDF — not a
rasterized PNG wrapped in a `/XObject` — with vector text, vector
gradients, embedded JPEGs, sRGB output intent, optional PDF/A-2b
conformance, and an optional structure tree for tagged / accessible
PDF.

```
   SkPDF::MakeDocument(stream, metadata)
            │
            ▼
   ┌─────────────────────┐    beginPage(w, h)     ┌──────────────────┐
   │  SkPDFDocument      │ ─────────────────────► │ SkPDFDevice      │
   │  (SkDocument impl)  │                        │  └─ SkCanvas     │
   │                     │ ◄─── endPage ───────── │                  │
   │  SkPDFTagTree       │                        └────────┬─────────┘
   │  SkPDFFont registry │                                 │ draw ops
   │  SkPDFShader cache  │                                 ▼
   │  SkPDFBitmap cache  │                        PDF content stream
   │  Object catalog     │                        (Tj, re, f*, sh, Do …)
   └────────┬────────────┘
            │ close()
            ▼
   PDF objects + xref + trailer  ──►  SkWStream
```

## Public surface — `include/docs/SkPDFDocument.h`

| Symbol | Purpose |
| --- | --- |
| `SkPDF::MakeDocument(stream, metadata)` | Factory; returns an `sk_sp<SkDocument>` writing PDF bytes to `stream`. |
| `SkPDF::Metadata` | Title / author / subject / keywords / creation+mod dates / language / DPI / PDF-A flag / JPEG callbacks / executor / compression level / subsetter. |
| `SkPDF::AttributeList` | Per-node Layout/List/Table attributes for tagged PDF (PDF 32000-1:2008 §14.8.5). |
| `SkPDF::StructureElementNode` | Recursive node carrying a type name (`"H1"`, `"P"`, `"Figure"`…), node ID, alt text, language, attributes, and children. |
| `SkPDF::SetNodeId(canvas, id)` | Tags every subsequent draw on `canvas` with `id`; must match an `fNodeId` somewhere in the structure tree. |
| `SkPDF::NodeID` | Sentinel IDs (`OtherArtifact`, `PaginationFooterArtifact`, …) that exclude content from the logical reading order. |
| `SkPDF::DateTime`, `toISO8601()` | UTC-offset date/time used in `Info` and `XMP`. |
| `SkPDF::DecodeJpegCallback`, `EncodeJpegCallback` | Hooks for clients to plug in a JPEG codec without forcing a Skia-side dependency. |

The document is driven by the `SkDocument` API:

```cpp
sk_sp<SkDocument> doc = SkPDF::MakeDocument(stream, metadata);
SkCanvas* page = doc->beginPage(widthPt, heightPt, /*content=*/nullptr);
// draw …
doc->endPage();
doc->close();   // writes xref + trailer; required for a valid PDF
```

PDF page sizes are in points (`1 pt = 1/72 inch`). The optional content
rect supplied to `beginPage` becomes the page's `/CropBox`.

---

## SkPDFDocument — `src/pdf/SkPDFDocument.cpp`

`SkPDFDocument` (declared in `src/pdf/SkPDFDocumentPriv.h`) is the
real implementation; it owns:

- the **object catalog** (`SkPDFObjNumMap`) that hands out indirect
  object numbers and writes the PDF cross-reference table;
- a **font registry** (a `THashMap` keyed by `SkDescriptor` mapping to
  `SkPDFStrike`) — see `SkPDFFont.h`;
- a **gradient/shader cache** (`SkPDFShader`) and **graphics-state
  cache** (`SkPDFGraphicState`) so repeated paints don't re-emit the
  same PDF resource;
- an **image cache** (`SkPDFBitmap`) keyed by `SkBitmapKey` (image
  unique-ID + sub-rect);
- the **structure tree** (`SkPDFTagTree`) when tagged PDF is enabled.

Each call to `beginPage` constructs an `SkPDFDevice` and wraps it in
an `SkCanvas`. When `endPage` runs the device's content stream is
deflated (`SkDeflate.cpp`) and added to the catalog as a `/Page`
object. `close()` then emits `/Catalog`, `/Info`, optional
`/Metadata` (XMP) and `/OutputIntents`, the `/StructTreeRoot`, the
`xref` table, and the trailer.

When `Metadata::fExecutor` is non-null the deflate of each content
stream is dispatched to a worker; the catalog still produces a valid
PDF but object numbers are no longer reproducible across runs.

---

## SkPDFDevice — `src/pdf/SkPDFDevice.h`

`SkPDFDevice` is the `SkBaseDevice` subclass that intercepts canvas
operations and emits PDF content-stream operators via
`SkPDFGraphicStackState`. It handles:

- **path fill/stroke** (`m`, `l`, `c`, `re`, `f`, `f*`, `S`) with
  CTM and clip tracked through `q` / `Q` save/restore;
- **text** — runs are mapped to a `SkPDFFont` (see below) and emitted
  as `Tj` / `TJ`, with positions adjusted by `Td` / `TD`;
- **images** — embedded as `/XObject Image` resources via
  `SkPDFBitmap`, then drawn with a CTM-scaled `Do`;
- **shaders** — gradients become PDF shading dictionaries, and
  bitmap/composite shaders fall back to tiled pattern dictionaries;
- **clipping** — non-rectangular clips emit `W` / `W*` then `n`;
- **layers and transparency** — `saveLayer` materializes an XObject
  Form (`SkPDFFormXObject`) with a soft-mask `/SMask` when needed;
- **annotations** — `SkAnnotation`s become PDF `/Annot` dictionaries
  (URLs, named destinations, link rectangles).

Anything the PDF imaging model cannot express natively (perspective on
text or images, certain blend modes, color filters, mask filters) is
rasterized at `Metadata::fRasterDPI` and embedded as a bitmap.

---

## Fonts and subsetting — `src/pdf/SkPDFFont.cpp`, `SkPDFSubsetFont.cpp`

PDF font handling has two layers:

1. **Strike** (`SkPDFStrike`): a per-`SkDescriptor` cache of the
   per-typeface state. Two strikes are kept side by side — `fPath` for
   vector text and `fImage` for bitmap-only or color-emoji fallbacks.
2. **Font resource** (`SkPDFFont`): one PDF font object per group of
   ≤256 glyphs (for single-byte encodings) or per typeface (for
   multi-byte CID fonts). `IsMultiByte()` returns true for
   `kType1CID_Font`, `kTrueType_Font`, and `kCFF_Font`; everything
   else (`kType1_Font`, bitmap, SVG, COLR fallback) goes through the
   single-byte path.

Each `SkPDFFont` tracks **glyph use** in an `SkPDFGlyphUse` bitset.
At document close, `SkPDFSubsetFont` invokes HarfBuzz's subsetter
(`Metadata::fSubsetter == kHarfbuzz_Subsetter`) to keep only the used
glyphs; the resulting `/FontFile2` (TrueType), `/FontFile3` (CFF), or
`/FontFile` (Type1, see `SkPDFType1Font.cpp`) stream is much smaller
than the original. CID widths come from
`SkPDFMakeCIDGlyphWidthsArray.cpp`, and a `/ToUnicode` CMap built by
`SkPDFMakeToUnicodeCmap.cpp` lets PDF readers extract / search the
text. Type 3 bitmap fonts are emitted for color-emoji and other
typefaces with no scalable outlines.

See also [Text & Fonts](text-and-fonts.md) for `SkAdvancedTypefaceMetrics`,
the platform-port entry point that supplies font metrics, name, and
font-file bytes to the PDF backend.

---

## Gradient shaders — `src/pdf/SkPDFGradientShader.cpp`

Linear, radial, sweep, and two-point conical gradients are emitted as
PDF **Shading dictionaries** (Type 2 / Type 3) — i.e. resolution-
independent vectors, not rasterized samples. Stops are converted into
PDF Function objects (Type 0 sampled functions for non-uniform stops,
Type 2 exponential interpolation for the simple linear case).

Sweep gradients have no native PDF equivalent and are tessellated
into a fan of small linear Type 2 shadings; very wide colour gamuts or
unusual interpolation modes also fall back to a sampled Type 0
function. Bitmap, image, and composed shaders go through
`SkPDFShader.cpp` and become PDF **Pattern** dictionaries that tile
an underlying `/XObject` over the page.

---

## Tagged PDF / accessibility — `src/pdf/SkPDFTag.cpp`

Tagged PDF requires two things:

- a `Metadata::fStructureElementTreeRoot` describing the document's
  semantic structure (paragraphs, headings, lists, tables, figures,
  alt text, language…);
- per-draw `SkPDF::SetNodeId(canvas, nodeID)` calls that mark each
  content stream span with the same node ID.

`SkPDFTagTree` walks the user-supplied tree at close time, builds the
PDF `/StructTreeRoot`, the parent-tree map, the role/class maps, and
emits per-page `/StructParents` references. Node IDs in
`SkPDF::NodeID` (negative sentinel values) flag content as **artifact**
— pagination, page numbers, watermarks — so it is excluded from the
reading order. `Metadata::Outline` controls whether structure
headings are also surfaced as a PDF outline (bookmark) tree.

When `Metadata::fPDFA` is true the document additionally emits XMP
metadata, a deterministic UUID (`SkUUID.h`), an sRGB output intent,
and refuses to embed colour profiles that would break PDF/A-2b
conformance.

---

## Images and JPEG passthrough — `src/pdf/SkPDFBitmap.cpp`, `include/docs/SkPDFJpegHelpers.h`

`SkPDFBitmap` is the PDF-side cache for `/XObject Image` resources,
keyed by `SkBitmapKey`. Two important fast paths exist:

- **JPEG passthrough** — if the source `SkImage` is already
  JPEG-encoded and the `Metadata::jpegDecoder` callback can decode its
  header, the original JPEG bytes are embedded *as-is* into a
  `/DCTDecode` stream, with no re-encoding. This is by far the
  smallest and fastest result for photographic content.
- **Lossy fallback** — when the source is opaque and
  `Metadata::fEncodingQuality ≤ 100`, `Metadata::jpegEncoder` is
  invoked to produce a fresh JPEG (must be RGB, not YUV, so Skia's own
  surrounding metadata applies). Otherwise the image is emitted as a
  zlib-deflated `/FlateDecode` stream.

If neither callback is provided the PDF still validates but is much
larger. Setting `Metadata::allowNoJpegs = true` silences the internal
assertion in that mode. `SkJpegDecoder::Decode` /
`SkJpegEncoder::Encode` from the regular Skia codec stack are the
intended values for these callbacks.

Soft-masked (alpha) images are split into an opaque `/XObject` plus a
separate single-channel `/SMask` `/XObject`.

---

## Color management

PDF objects are written in the source `SkColorSpace`'s primaries
where possible:

- **sRGB output intent** — when `fPDFA` is set, an sRGB ICC profile
  is embedded as the document's `/OutputIntent` so PDF/A-2b validators
  accept it.
- **Image color** — embedded JPEGs keep their original ICC profile if
  present; deflated images are tagged with an ICC-based
  `/ColorSpace`. Wide-gamut / linear sources are converted at embed
  time; HDR is tone-mapped to SDR before embedding.
- **Gradients and solid paints** — emitted in DeviceRGB; the
  conversion from the source `SkColorSpace` happens at the gradient
  stop level so the PDF reader does not need to perform a colour
  managed interpolation.

See [Color Management](color-management.md) for the underlying
`SkColorSpace` / `skcms` plumbing that the PDF backend consumes.

---

## Cross references

- [Canvas & Recording API](canvas-and-recording.md) — the `SkCanvas`
  side of the contract; `SkDocument::beginPage` returns an
  `SkPDFDevice`-backed canvas.
- [Text & Fonts](text-and-fonts.md) — `SkAdvancedTypefaceMetrics`
  and the `SkTypeface::openStream` family that feed the PDF font
  serializer.
- [Color Management](color-management.md) — colour conversion and
  ICC-profile handling shared with the rest of the pipeline.
- [XPS Backend](xps-backend.md) — sister `SkDocument` backend for
  Windows XPS/OPC.
- [SVG Canvas](svg-canvas.md) — single-page vector backend that
  emits SVG markup instead of PDF.

## Source map

| File | Role |
| --- | --- |
| `include/docs/SkPDFDocument.h` | Public API: `MakeDocument`, `Metadata`, tagged-PDF types. |
| `include/docs/SkPDFJpegHelpers.h` | Default JPEG decode/encode callbacks. |
| `src/pdf/SkPDFDocument.cpp`, `SkPDFDocumentPriv.h` | Document driver, object catalog, close/serialize. |
| `src/pdf/SkPDFDevice.{h,cpp}` | `SkBaseDevice` that emits PDF content streams. |
| `src/pdf/SkPDFGraphicStackState.cpp` | `q`/`Q`, CTM, clip, paint state tracking. |
| `src/pdf/SkPDFFont.{h,cpp}`, `SkPDFType1Font.cpp` | Font resource emission per encoding. |
| `src/pdf/SkPDFSubsetFont.cpp` | HarfBuzz subsetter integration. |
| `src/pdf/SkPDFMakeCIDGlyphWidthsArray.cpp`, `SkPDFMakeToUnicodeCmap.cpp` | CID widths and `/ToUnicode` CMap. |
| `src/pdf/SkPDFGradientShader.cpp`, `SkPDFShader.cpp` | Vector gradient and pattern shaders. |
| `src/pdf/SkPDFBitmap.cpp` | Image XObjects + JPEG passthrough. |
| `src/pdf/SkPDFTag.cpp` | Tagged PDF / structure tree emission. |
| `src/pdf/SkPDFMetadata.cpp` | XMP, `/Info`, output intent, UUID. |
| `src/pdf/SkDeflate.cpp` | zlib `/FlateDecode` filter (optionally threaded). |
| `src/pdf/SkPDFTypes.{h,cpp}`, `SkPDFUtils.{h,cpp}` | Low-level PDF object model and serialization. |
