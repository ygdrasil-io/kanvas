# MIGRATION_PLAN_SVG.md — mini plan for B2 (SkSVGCanvas) + D4.5 SvgSink

This is a **slimmed-down replacement** for the original B2 entry in
[MIGRATION_PLAN_RASTER_COMPLETION.md § B2](MIGRATION_PLAN_RASTER_COMPLETION.md).
That entry estimated ~3000 LOC across 7 slices ; the audit below
shows we can ship a useful subset for ~25-30 % of that budget by
descoping text, filters, and the more exotic shader / saveLayer
paths until a workflow demands them.

## Audit (2026-05-08)

`gm/*.cpp` upstream search for `SVG` / `SkSVG` references :

| Upstream GM | Role | Ported in kanvas-skia ? |
|---|---|---|
| `arcto.cpp` | Comment-only — illustrative SVG fragment in a kdoc to document the same `arcTo` semantics ; the test itself uses `SkPath`. | **Yes** (`ArcToGM`) |

That's the only hit. **No GM is SVG-specific** ; no port currently
in `kanvas-skia/src/main/kotlin/org/skia/tests/` requires SVG output
to pass its similarity ratchet. The GMs all run through the raster
sink path (`RasterSink8888` / `RasterSinkF16`) and SVG would be an
*additional* output channel, not a prerequisite for any GM.

This puts SVG in the same bucket as PDF (descoped per the [B1
audit](MIGRATION_PLAN_RASTER_COMPLETION.md#b1--skpdf--descoped)).
Two differences justify a "mini" plan instead of an outright descope :

- **Vector debug output** — an SVG file can be inspected directly in
  any browser / editor, which makes it a useful diagnostic for
  visual regressions in the raster pipeline (compare what we *say*
  we drew against the rendered raster).
- **D4.5 SvgSink completes the D4 matrix** — once B2 lands, D4.5
  becomes a ~80-LOC wrapper rather than its own chantier, and the
  DM harness gains a canonical alternative output channel that
  exercises the same op surface as the raster sinks.

## Goal

A `SkSVGCanvas` that can serialize the ops emitted by the **already-
ported raster GMs**, sufficient to route them through D4.5 SvgSink
and produce a viewable, well-formed SVG. **Iso-fidelity** with
upstream Skia's `SkSVGCanvas` is not the goal ; producing a
structurally correct SVG that matches the raster output **visually**
when rendered in a stock SVG engine (browser, Batik) is.

## Scope

### In

- **Geometry** — `SkRect`, `SkRRect`, `SkPath`, `SkOval`, `SkCircle`,
  `SkLine`. Path verbs `M / L / Q / C / Z` translate 1:1 to SVG path
  `d="..."` syntax ; `kConic` cubics get split via the
  `SkPathBuilder` Conic→cubic helper (already used by the rasterizer's
  flattener).
- **Paint** — solid fill + solid stroke, alpha, stroke style
  (`linecap` / `linejoin` / `miterlimit` / `dasharray`).
- **Blend mode** — `kSrcOver` (default), `kSrc` (annotated as
  comment) ; everything else logs a warning and falls through to the
  default, since SVG only natively supports `mix-blend-mode` for a
  small subset.
- **CTM** — translate / scale / skew / rotate via
  `<g transform="matrix(a, b, c, d, e, f)">`.
- **Clip** — rect / rrect / path → `<clipPath id="...">` defs +
  `clip-path="url(#...)"` on the wrapping `<g>`. AA flag is a no-op
  (SVG engines handle AA themselves).
- **Image embedding** — `SkImage` → encode via [`SkPngEncoder`](kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt)
  (D3.5) + base64 → `<image href="data:image/png;base64,...">`.
- **Gradients** — linear and radial → `<linearGradient>` /
  `<radialGradient>` defs ; one `<stop>` per stop entry.

### Out (descoped, with revival path)

| Feature | Reason | Revive when |
|---|---|---|
| **Text** (`SkTextBlob` → `<text>`) | needs glyph projection or font embedding ; SkTextBlob's per-glyph positioning doesn't map cleanly to SVG `<text>` | a text-only SVG workflow shows up |
| **Filters** (`SkImageFilter` → `<feGaussianBlur>` etc.) | own ~500 LOC chunk ; SVG filter graph is its own DSL | filter-heavy GMs land in scope |
| **Save layers** (`saveLayer` → `<g filter="...">`) | same blocker as filters — saveLayer's transparency layer compositing requires `feColorMatrix` / `feMerge` machinery | same trigger as filters |
| **Color filters** (`SkColorFilter` → `<feColorMatrix>`) | same family as filters | same trigger as filters |
| **Non-clamp bitmap shaders** (`kRepeat` / `kMirror`) | SVG `<pattern>` natively only supports clamp ; repeat/mirror need pre-tiling at write time | a GM exercises a non-clamp tile mode through SvgSink |
| **SVG → SVG round-trip / parser** (read SVG and replay into SkCanvas) | upstream's `SkSVGCanvas` only writes ; reading is a separate library (`modules/svg`) | never (out of `:kanvas-skia` scope) |

## Slices

### B2.1 — Skeleton + geometry serializer ✏️ ~300 LOC

- **New package** `org.skia.svg` (sibling of `org.skia.codec`,
  `org.skia.encode`, `org.skia.dm`).
- **`SkSVGCanvas(out: java.io.Writer)`** — extends [`SkCanvas`](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt)
  ; writes `<svg width=… height=…>` on construct, `</svg>` on flush.
- **`onDrawRect` / `Oval` / `Circle` / `Path` / `Line` / `RRect`** →
  emit the corresponding SVG element (`<rect>` / `<ellipse>` /
  `<circle>` / `<path d="…">` / `<line>` / `<rect rx=… ry=…>`).
- **CTM** — every `save()` / `restore()` writes a `<g transform=…>`
  open / close, mirroring the existing CTM stack in `SkCanvas`.
- **`SkPath` → `d="…"`** — walk verbs ; `kMove` → `M x y`, `kLine` →
  `L x y`, `kQuad` → `Q x1 y1 x2 y2`, `kCubic` → `C x1 y1 x2 y2 x3 y3`,
  `kClose` → `Z`. `kConic` is converted to cubics first via the
  rasterizer's existing helper.
- **Tests** — `SkSVGCanvasGeometryTest` (rect / circle / path with
  every verb / nested CTM stack ; assert against expected SVG strings
  + parse with `javax.xml.parsers.DocumentBuilder` to confirm
  well-formedness).

### B2.2 — Paint surface ✏️ ~200 LOC

- **`SkPaint`** → `fill="#rrggbb" fill-opacity="…"` (or `none` for
  stroke-only), `stroke="…" stroke-width="…"
  stroke-linecap="…" stroke-linejoin="…" stroke-miterlimit="…"
  stroke-dasharray="…"`.
- **Blend mode** — `kSrcOver` is implicit (default), `kSrc` is
  annotated as an XML comment, every other mode logs a warning to
  stderr and falls through to default.
- **Tests** — round-trip a paint with each style/cap/join combo and
  assert the emitted attributes.

### B2.3 — Clip ✏️ ~150 LOC

- Each `clipRect` / `clipRRect` / `clipPath` writes a `<defs><clipPath
  id="clip-N">…</clipPath></defs>` (auto-allocated `N` from the
  existing save/restore stack depth) plus `clip-path="url(#clip-N)"`
  on the wrapping `<g>`.
- AA flag is dropped silently (SVG handles AA in the renderer).
- **Tests** — assert per-clip-shape output ; assert nested clips
  produce nested wrappers.

### B2.4 — Image + gradients ✏️ ~250 LOC

- **`SkImage`** → `SkPngEncoder.Encode(bitmap)` + Base64 → `<image
  href="data:image/png;base64,…" width=… height=…>`. The bitmap
  passes the codec's `kRGBA_8888 / kUnpremul / sRGB` projection — a
  Rec.2020 working-space bitmap is encoded as 8-bit sRGB, matching
  what the raster sinks emit through the same encoder.
- **`SkLinearGradient`** → `<defs><linearGradient id="g-N" x1=… y1=…
  x2=… y2=…><stop offset="…" stop-color="…" stop-opacity="…"/>…
  </linearGradient></defs>` + `fill="url(#g-N)"` on the consumer.
- **`SkRadialGradient`** → `<defs><radialGradient id="g-N" cx=… cy=…
  r=…>…</radialGradient></defs>`.
- **Bitmap shader (clamp only)** → `<pattern>` wrapping the encoded
  image. Non-clamp tile modes log a warning.
- **Tests** — round-trip a 4×4 bitmap through the data-URL path ;
  parse the resulting SVG and decode the data URL ; assert the
  bytes are exactly `SkPngEncoder.Encode(bitmap)`.

### B2.5 — D4.5 SvgSink wiring ✏️ ~80 LOC

Once B2.1–B2.4 land, D4.5 SvgSink is a thin shell :

- **`SvgSink`** in `org.skia.dm` — `Sink` impl that allocates a
  `StringWriter`, builds an `SkSVGCanvas` over it, runs `gm.draw`,
  flushes, returns the bytes (`ByteArray.fromUtf8`).
- **`tag = "svg"`**, **`fileExtension = "svg"`**.
- Register with the [DmCli registry](kanvas-skia/src/main/kotlin/org/skia/dm/DmCli.kt)'s
  `KNOWN_CONFIGS` so `--config svg` resolves.
- **Result type** — `Sink.Result` currently carries an `SkBitmap`
  ; SVG output is bytes, not pixels. Two options :
    1. Add `Sink.Result.Bytes(bytes, mimeType)` (the plan-sketch
       in the original D4 section flagged this as a future
       extension).
    2. Render the SVG to a raster bitmap via Batik / a stock SVG
       engine and return the rasterised bitmap.
  **Pick (1)** — it's the cleaner extension and keeps SvgSink
  closer to upstream's "vector sinks emit bytes, not pixels"
  convention. The D4.3 [Runner](kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt)
  needs a small extension for the new variant : MD5 hashes the
  bytes directly (no PNG re-encode).
- **Tests** — run a representative subset of GMs (rect / path /
  clip / gradient / image, ~5 GMs) through `SvgSink` ; parse the
  output with `DocumentBuilder` to confirm well-formedness ; assert
  the SVG contains the expected structural anchors (one
  `<rect>` / `<path>` per draw call, etc.).

## Total LOC

| Slice | Main | Test |
|---|---:|---:|
| B2.1 | ~300 | ~150 |
| B2.2 | ~200 | ~80 |
| B2.3 | ~150 | ~80 |
| B2.4 | ~250 | ~120 |
| B2.5 | ~80 | ~120 |
| **Total** | **~980** | **~550** |

vs. the original B2 estimate of ~3000 main + ~700 test (text +
filters + saveLayer + non-clamp shaders + color filters all
contribute the missing ~2000 LOC).

## Validation

- **Per-slice** : unit tests of the serializer (input op → expected
  SVG fragment, parsed and structurally checked).
- **End-to-end** (B2.5) : `SvgSink` runs on 5-10 representative GMs ;
  output is parsed with `javax.xml.parsers.DocumentBuilder` and
  structurally checked. We do **not** compare pixel-for-pixel with
  the raster reference at this stage — an SVG render goes through a
  separate engine (Batik, browser) and that comparison is a follow-up
  if/when a workflow needs exact-render parity.
- **No similarity ratchet entry** for SvgSink results — the current
  ratchet compares against `original-888/*.png` references, which
  exist for raster only. SVG fidelity is asserted structurally for
  now.

## Sequencing notes

- **Independent of D1.2 / pathops finalization** — the SVG slices
  only consume `SkPath` (already complete) and don't touch the
  pathops algorithm.
- **Depends on D3.5 `SkPngEncoder`** ✅ (already shipped) for image
  embedding.
- **D4.5 SvgSink** trails B2.4 ; once SvgSink ships, the D4 matrix
  has 5 sinks (`8888`, `f16`, `pic-8888`, `pic-f16`, `svg`).

## Status

📋 **planned** — slices defined, scope agreed, no code yet.
Pickup-ready when the schedule allows ; not blocking any open chantier.
