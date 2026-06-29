# Module cpu-raster

CPU rasterization backend of the `kanvas-skia` port. Hosts `SkBitmapDevice` (and the broader scanline / AA / path rendering pipeline) ported from Skia's `src/core` + `src/effects` + `src/codec`. Sibling of `:gpu-raster` (WebGPU device) — both modules implement the device abstractions exposed by `:kanvas-skia`.

This module is the largest in the port by far : it carries the bulk of Skia's CPU rendering machinery — the scanline rasterizer, the path stroker (G3.4), the gradient shaders (G4), the image filters (C1), the codec dispatch (D3), the shadow tessellator, the path ops engine (D1), and the test/diagnostic harnesses (`org.skia.testing`).

# Package org.skia.testing

Cross-test infrastructure shared between `:cpu-raster` and `:gpu-raster`. Hosts `TestUtils` (reference-PNG loading, bitmap diff, debug image dumps), `BitmapComparison` (per-channel diff stats), `SimilarityTracker` (ratchet floor management), `TestReport` (machine-readable result aggregation), and `DiffImage` (rendered ｜ diff ｜ reference triptych).

# Package org.graphiks.kanvas.codec

Image codec dispatch (`Codec`, format-specific decoders : PNG, JPEG, WEBP, AVIF, RAW, ICO, JPEG-XL).

# Package org.skia.pathops

Boolean path operations (`SkPathOps.Op` : union, intersect, difference, xor, reverse-difference). Double-precision arithmetic ported in D1.

# Package org.skia.utils

Standalone utilities : `SkPathUtils`, `PixmapUtils`, `SkShadowUtils`, `SkParsePath`, `SkNoDrawCanvas`, ...

# Package org.skia.foundation

Concrete implementations of shader/effect types declared in `:kanvas-skia` (gradients, blends, color filters).

# Package org.skia.tools

Helpers shared by the test/GM ports (`ToolUtils`, `SkRandom`, `SkDiscretePathEffect`).

# Package org.skia.svg

`SkSVGCanvas` — output a draw stream as SVG markup.

# Package org.skia.dm

`Sink` / `RasterSinkF16` — the GM-runner sink abstraction that mirrors Skia's `gm.cpp` DM driver. The F16 sink renders into the `DM unified Rec.2020` reference colorspace used by `original-888/`.

# Package org.skia.tests

GM base class (`GM`) plus a small core of always-included GMs ; the bulk of the GM ports live in `:skia-integration-tests`.
