# SVG Module

`modules/svg/` is Skia's SVG **renderer** — it parses an SVG document,
builds an in-memory DOM mirroring the SVG element tree, and walks that
DOM to issue calls into an `SkCanvas` (typically through the
[SkSG Scene Graph](sksg-scene-graph.md) primitives that the same
backend exposes). It is the inverse of the
[SVG Canvas](svg-canvas.md), which serialises canvas calls back out
to SVG markup.

The module is small and self-contained: an `SkXMLParser` feeds an
`SkSVGNode` tree, attribute parsing is centralised in
`SkSVGAttributeParser`, and rendering is a single recursive walk that
threads an `SkSVGRenderContext` (current viewport, presentation
attributes, ID map, font manager, resource provider).

## Layout

| Header | Role |
|--------|------|
| `SkSVGDOM.h` | Top-level document; build from an `SkStream`, render to `SkCanvas` |
| `SkSVGNode.h` | Base class for every element; `SkSVGTag` enum lists supported tags |
| `SkSVGSVG.h` | Root `<svg>` element — viewport, viewBox, intrinsic size |
| `SkSVGContainer.h` / `SkSVGG.h` / `SkSVGUse.h` / `SkSVGDefs.h` | Grouping & references |
| `SkSVGShape.h`, `SkSVGRect.h`, `SkSVGCircle.h`, `SkSVGEllipse.h`, `SkSVGLine.h`, `SkSVGPoly.h`, `SkSVGPath.h` | Geometric shapes |
| `SkSVGText.h` | `<text>` / `<tspan>` / `<textPath>` (uses SkShaper) |
| `SkSVGImage.h` | `<image>` (PNG/JPEG via decoders, or another SVG) |
| `SkSVGGradient.h`, `SkSVGLinearGradient.h`, `SkSVGRadialGradient.h`, `SkSVGStop.h`, `SkSVGPattern.h` | Paint servers |
| `SkSVGClipPath.h`, `SkSVGMask.h`, `SkSVGFilter.h`, `SkSVGFe*.h` | Clip / mask / filter primitives (`<feBlend>`, `<feGaussianBlur>`, `<feColorMatrix>`, `<feTurbulence>`, lighting, …) |
| `SkSVGAttribute.h`, `SkSVGAttributeParser.h`, `SkSVGTypes.h`, `SkSVGValue.h` | Presentation attributes & parsing |
| `SkSVGRenderContext.h` | Per-render mutable state (viewport stack, inherited paint, ID map, font / resource hooks) |
| `SkSVGOpenTypeSVGDecoder.h` | Implements `SkOpenTypeSVGDecoder` — colour-font glyph SVG bodies |

Implementations live in `modules/svg/src/` with one `.cpp` per header.

## SkSVGDOM — `modules/svg/include/SkSVGDOM.h`

`SkSVGDOM::Builder` is the construction entry point. It accepts:

- `setFontManager(sk_sp<SkFontMgr>)` — needed to resolve `<text>` font
  families. Without one, text is silently skipped.
- `setResourceProvider(sk_sp<skresources::ResourceProvider>)` — fetches
  external `<image>` references (`href="…"`); shared with
  [SkResources](skresources.md) and [Skottie](skottie.md).
- `setTextShapingFactory(sk_sp<SkShapers::Factory>)` — chooses
  HarfBuzz / CoreText / etc. for `<text>` shaping.

Then `Builder::make(SkStream&)` returns the parsed DOM. The shortcut
`SkSVGDOM::MakeFromStream` skips fonts and images. After
construction, `setContainerSize` resolves percentage-relative root
viewports, `getRoot()` returns the `SkSVGSVG`, and `render(SkCanvas*)`
walks the tree. `renderNode` re-roots the walk at a single subtree by
ID — useful for sprite-sheet style assets.

## DOM nodes — `SkSVGNode` and friends

Every parsed element is an `sk_sp<SkSVGNode>` tagged by an
`SkSVGTag` enum value (kRect, kPath, kFeBlend, …). `SkSVGNode`
holds the inherited presentation attributes (fill, stroke,
fill-opacity, stroke-width, fill-rule, opacity, visibility,
clip-rule, font-family, font-size, …) as `std::optional` fields so
the renderer can distinguish "unset, inherit" from "explicitly set".
`SkSVGTransformableNode` adds a `transform` attribute; shapes derive
from `SkSVGShape`; containers from `SkSVGContainer`.

Rendering proceeds by `SkSVGNode::render(SkSVGRenderContext&)`. The
node first updates the context with its own presentation attributes
(via the saveable `SkSVGPresentationContext` snapshot inside
`SkSVGRenderContext`), then dispatches to its `onRender` override.
Shapes call `onAsPath` to materialise their geometry and feed it
through `SkSVGRenderContext::fillPaint` / `strokePaint`, both of
which honour gradients, patterns, masks, and filters by recursively
asking the relevant `SkSVGNode` (looked up via
`SkSVGIDMapper`) to populate an `SkPaint` or set up a layer.

Filters compile each `<filter>` into a chain of
[Image Filters](image-filters-and-mask-filters.md):
`SkSVGFeGaussianBlur` becomes `SkImageFilters::Blur`,
`SkSVGFeColorMatrix` becomes a colour-filter image filter, and so on.
`SkSVGFilterContext` wires named results (`in="SourceGraphic"`,
`result="blur"`) into the input of the next primitive.

## SkSVGRenderContext — `modules/svg/include/SkSVGRenderContext.h`

The render context is the per-walk mutable state:

- `SkSVGLengthContext` — current viewport + DPI, resolves SVG length
  values (`12px`, `50%`, `1em`) into device units.
- `SkSVGPresentationContext` — the inherited paint stack (snapshotted
  on container entry, popped on exit).
- `SkSVGIDMapper` — `id → SkSVGNode` map for `<use>`, paint server
  references, `clip-path` / `mask` / `filter` lookups.
- `SkCanvas*` — the destination canvas. The walk pushes / pops
  matrices, layers, clips, and `saveLayer` filters as the SVG
  semantics demand.

## OpenType-SVG — `SkSVGOpenTypeSVGDecoder.h`

A small adapter implementing the abstract
`SkOpenTypeSVGDecoder` interface from
[Text & Fonts](text-and-fonts.md). When a colour font glyph is
backed by an SVG document (the OpenType `SVG ` table), the scaler
context calls into this decoder, which spins up an `SkSVGDOM` and
rasterises the glyph at the requested size.

## Source map

| File | Purpose |
|------|---------|
| `skia-main/modules/svg/src/SkSVGDOM.cpp` | Document construction, XML → node tree, render dispatch |
| `skia-main/modules/svg/src/SkSVGNode.cpp` | Base attribute application, render scaffolding |
| `skia-main/modules/svg/src/SkSVGRenderContext.cpp` | Length resolution, paint server lookup, layer setup |
| `skia-main/modules/svg/src/SkSVGAttributeParser.cpp` | Hand-rolled CSS / SVG value parser (lengths, transforms, paints, colours) |
| `skia-main/modules/svg/src/SkSVGFilter.cpp` and `SkSVGFe*.cpp` | Filter primitives → `SkImageFilter` chain |

## Cross-references

- [SVG Canvas](svg-canvas.md) — the inverse, an `SkCanvas` that
  emits SVG markup.
- [SkSG Scene Graph](sksg-scene-graph.md) — the retained-mode
  primitives used by Skottie; the SVG renderer shares the same
  attribute / paint vocabulary even though it walks its DOM directly.
- [Image Filters](image-filters-and-mask-filters.md) — back-end for
  `<filter>` and `<feGaussianBlur>` / `<feColorMatrix>` / lighting.
- [Image Decoders](image-decoders.md) — used by `<image>` via the
  `SkResources::ResourceProvider`.
- [Text & Fonts](text-and-fonts.md) — `<text>` shaping plus the
  OpenType-SVG glyph backend.
