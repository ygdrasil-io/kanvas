# SVG Canvas

`SkSVGCanvas` is an `SkCanvas` that **writes SVG 1.1 markup** instead
of rasterizing pixels. Every `drawRect`, `drawPath`, `drawTextBlob`,
`drawImage`, gradient, clip, and `saveLayer` becomes one or more XML
elements emitted to a caller-supplied `SkWStream`. The result is a
self-contained `<svg>` document that any browser, Inkscape, or
downstream Skia renderer can re-display.

> **Direction matters.** This document covers the canvas that
> **emits** SVG. The opposite direction — parsing SVG markup and
> drawing it into another canvas — is the responsibility of
> `modules/svg/` (`SkSVGDOM`, `SkSVGSVG`, `SkSVGRenderContext`) and is
> covered separately in [SVG Module](svg-module.md). The two stacks
> share no code beyond a common `SkXMLWriter`/`SkXMLParser` set; they
> are independent components that happen to live next to each other.

```
   SkSVGCanvas::Make(bounds, stream, opts)
            │
            ▼
   ┌─────────────────────┐  draw ops   ┌───────────────────┐
   │  SkCanvas (returned)│ ──────────► │ SkSVGDevice       │
   └─────────────────────┘             │  └─ SkXMLStream-  │
                                       │     Writer        │
                                       └─────────┬─────────┘
                                                 │ XML elements
                                                 ▼
                                          SkWStream  ►  <svg>…</svg>
```

## Public surface — `include/svg/SkSVGCanvas.h`

| Symbol | Purpose |
| --- | --- |
| `SkSVGCanvas::Make(bounds, stream, opts)` | Factory; returns a `std::unique_ptr<SkCanvas>` that emits SVG into `stream`. The canvas may buffer output, so the SVG is only guaranteed valid once the canvas is destroyed. |
| `SkSVGCanvas::Options::flags` | Bitmask of `Flags` controlling output style. |
| `SkSVGCanvas::Options::pngEncoder` | `EncodePngCallback` used to inline `drawImage` content as base64 `data:` URIs. |
| `SkSVGCanvas::Flags::kConvertTextToPaths_Flag` | Emit text as `<path>` outlines instead of `<text>` — guarantees identical rendering everywhere at the cost of selectability and file size. |
| `SkSVGCanvas::Flags::kNoPrettyXML_Flag` | Drop newlines and indentation from the XML output. |
| `SkSVGCanvas::Flags::kRelativePathEncoding_Flag` | Use relative path commands (`m`, `l`, `c` …) in `d=` attributes instead of absolute (`M`, `L`, `C` …). |

The `bounds` argument seeds the SVG root element's `viewBox` (and is
rounded out to the integer `width`/`height` attributes). It does **not**
clip subsequent draws — anything that escapes the bounds is still
written to the document; consumers can clip via `viewBox` or by
applying their own `clipRect` before drawing.

`Make` returns `nullptr` if the device cannot be created. The caller
must outlive the returned canvas with the stream. There is **no**
`SkDocument` wrapper here: `SkSVGCanvas` is single-page only — for a
multi-page paginated vector format, use the
[PDF Backend](pdf-backend.md).

### Typical use

```cpp
SkDynamicMemoryWStream stream;
SkSVGCanvas::Options opts;
opts.pngEncoder = SkPngEncoder::Encode;
{
    auto canvas = SkSVGCanvas::Make(SkRect::MakeWH(400, 300), &stream, opts);
    canvas->clear(SK_ColorWHITE);
    canvas->drawCircle(200, 150, 80, paint);
}   // unique_ptr destruction flushes the trailing </svg>
sk_sp<SkData> svgBytes = stream.detachAsData();
```

If `pngEncoder` is left null and `SK_DISABLE_LEGACY_SVG_FACTORIES` is
not defined, `SkSVGCanvas.cpp` installs a default that calls
`SkPngRustEncoder::Encode` (or `SkPngEncoder::Encode` if Rust PNG is
disabled at build time). With the legacy factories disabled and no
encoder supplied, construction aborts — every `drawImage` would have
no way to encode its pixmap.

---

## SkSVGCanvas factory — `src/svg/SkSVGCanvas.cpp`

`SkSVGCanvas::Make` is a thin wrapper:

1. Resolves the default PNG encoder if needed (legacy mode only).
2. Rounds `bounds` outward to obtain the `width`/`height` attributes
   used on the root `<svg>` element.
3. Constructs an `SkXMLStreamWriter` over the caller's
   `SkWStream`, with or without pretty-printing depending on
   `kNoPrettyXML_Flag`.
4. Calls `SkSVGDevice::Make(size, writer, opts)` to obtain the
   actual device, and wraps it in an `SkCanvas` (the public return
   value).

All of the SVG-writing intelligence lives in `SkSVGDevice`; the
`SkSVGCanvas` class is purely a typed factory.

---

## SkSVGDevice — `src/svg/SkSVGDevice.cpp`

`SkSVGDevice` extends `SkClipStackDevice` and translates draw calls
into SVG elements via the `SkXMLWriter`. Important behaviours:

### Element mapping

| `SkCanvas` op | SVG element |
| --- | --- |
| `drawRect`, `drawRRect` | `<rect>` (`rx`/`ry` for rounded). |
| `drawOval`, `drawCircle` | `<ellipse>` / `<circle>`. |
| `drawLine` | `<line>`. |
| `drawPath` | `<path d="…">`; the `d` string uses absolute commands by default, relative when `kRelativePathEncoding_Flag` is set, with channel hex compression in `svg_color`. |
| `drawTextBlob` (default) | `<text>` with `<tspan>` per glyph run, embedding the `font-family`, `font-size`, `font-style`, `font-weight`, and per-glyph `x`/`y` lists. |
| `drawTextBlob` (with `kConvertTextToPaths_Flag`) | One `<path>` per glyph run produced via `SkPath::getFillPath` / `SkFont::getPath`. |
| `drawImage`, `drawImageRect` | `<image xlink:href="data:image/png;base64,…">` — the pixmap is PNG-encoded via the `pngEncoder` callback and base64-wrapped (`SkBase64`). |
| `drawAnnotation("Url", rect, data)` | Wraps the next draw in `<a xlink:href="…">`. |
| `clipRect`, `clipPath` | `<clipPath id="clipN">…</clipPath>` in `<defs>`, then `clip-path="url(#clipN)"` on the wrapped `<g>`. |
| `saveLayer` | A `<g>` group with `opacity`, `filter`, or `mask` attributes derived from the layer paint. |
| Linear / radial / sweep gradients | `<linearGradient>` / `<radialGradient>` in `<defs>` with one `<stop>` per gradient stop, then referenced via `fill="url(#paintN)"` or `stroke="url(#paintN)"`. Sweep gradients fall back to a sampled raster if the SVG renderer is not expected to support them. |
| Image / pattern shaders | `<pattern>` definitions in `<defs>` with an embedded `<image>` tile. |
| Color filters / mask filters | `<filter>` in `<defs>` (limited subset — anything unrepresentable falls back to a rasterized image). |

### State tracking

The device piggy-backs on `SkClipStackDevice` for clip stacking and
keeps its own scratch state for resource deduplication:

- A `THashMap` of paint signatures → `<linearGradient>` / `<pattern>`
  IDs avoids re-emitting identical gradients across draws.
- A typeface → font-name table tracks which `font-family` strings
  have been used so subsequent `<text>` elements reference the same
  CSS font name. SVG `<text>` does **not** embed font files — text
  rendering at consumption time depends on the consumer having that
  font available, which is precisely why
  `kConvertTextToPaths_Flag` exists for hermetic output.
- The CTM is folded into a `transform="matrix(…)"` on the wrapping
  `<g>` for each save level.
- Non-trivial blend modes (anything other than `kSrcOver`) emit a
  `style="mix-blend-mode:…"` declaration; modes with no SVG analog
  cause that draw to be rasterized into an inline PNG.

### XML emission

`src/xml/SkXMLWriter.{h,cpp}` is the small XML printer the device
calls into. `SkXMLStreamWriter` writes directly to the stream and
optionally indents. The device opens the root `<svg>` element in its
constructor (with `xmlns`, `xmlns:xlink`, `width`, `height`,
`viewBox`) and closes it in its destructor; that is why the SVG is
not guaranteed valid until the canvas — and thus the device — is
destroyed.

---

## Limitations

- **Single page only** — there is no `SkDocument` wrapper around
  `SkSVGCanvas`. Multi-page output requires the caller to manage one
  `SkSVGCanvas` per file, or to switch to the
  [PDF Backend](pdf-backend.md).
- **Text portability** — by default `<text>` elements name the
  typeface but do **not** embed the font; consumers without the
  matching font will substitute. Set `kConvertTextToPaths_Flag` for
  pixel-faithful output (at the cost of file size and selectability).
- **Color management** — output is sRGB; non-sRGB sources are
  converted at draw time. No ICC profile is embedded.
- **Pixel fallback** — anything SVG cannot natively express
  (perspective, exotic blend modes, mask filters, mesh gradients) is
  rasterized into an inline base64 PNG via the `pngEncoder` callback.
  Documents heavy on those features can grow large.
- **Asymmetry with `modules/svg/`** — the writer's element vocabulary
  is intentionally conservative; round-tripping through
  `SkSVGCanvas` → `SkSVGDOM` is not guaranteed to be lossless.

---

## Cross references

- [Canvas & Recording API](canvas-and-recording.md) — base
  `SkCanvas` contract; `SkSVGCanvas` is just one more `SkCanvas`
  subclass.
- [PDF Backend](pdf-backend.md) — preferred backend for paginated
  vector output.
- [SVG Module](svg-module.md) — the **other** SVG stack: parsing
  SVG markup and rendering it into another canvas
  (`SkSVGDOM::render`). Lives under `modules/svg/`.
- [Image Encoders](image-encoders.md) — the PNG encoder that backs
  the inline base64 image fallback.

## Source map

| File | Role |
| --- | --- |
| `include/svg/SkSVGCanvas.h` | Public `SkSVGCanvas::Make` factory and `Options` / `Flags`. |
| `src/svg/SkSVGCanvas.cpp` | Resolves defaults and constructs an `SkSVGDevice`-backed canvas. |
| `src/svg/SkSVGDevice.{h,cpp}` | Translates `SkCanvas` operations into SVG markup. |
| `src/xml/SkXMLWriter.{h,cpp}` | Streaming XML writer used by the device. |
| `src/base/SkBase64.{h,cpp}` | Base64 wrapper for inline `data:` URIs. |
