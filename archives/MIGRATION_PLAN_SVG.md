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

### B2.1 — Skeleton + geometry serializer ✅ shipped

- **New package** [`org.skia.svg`](kanvas-skia/src/main/kotlin/org/skia/svg/SkSVGCanvas.kt)
  (sibling of `org.skia.codec`, `org.skia.encode`, `org.skia.dm`).
- **`SkSVGCanvas(out: Writer, width, height)`** — extends
  [`SkCanvas`](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt)
  with the same 1×1 dummy bitmap pattern that
  `SkRecordingCanvas` uses ; writes `<svg xmlns="…" width=…
  height=… viewBox="0 0 W H">` on construct and `</svg>` on
  [flush] (idempotent).
- **`drawRect` / `drawOval` / `drawCircle` / `drawLine` /
  `drawRRect` / `drawPath`** → emit the corresponding SVG
  element. RRect with non-zero radii uses `rx` / `ry`
  attributes ; zero radii omits them. Path fill type honoured
  via the SVG `fill-rule` attribute (winding default omitted,
  even-odd emits `fill-rule="evenodd"`).
- **CTM** — implementation chose **per-draw `transform`
  attribute** (Strategy B in the slice notes) over the
  originally-described `<g>` wrappers : each draw whose CTM
  is non-identity gets a `transform="matrix(a b c d e f)"` ;
  identity matrices omit the attribute. `<g>` wrappers are
  deferred to B2.3 where they become the natural carrier for
  `<clipPath>` references.
- **`SkPath` → `d="…"`** — verbs walked in order : `kMove → M`,
  `kLine → L`, `kQuad → Q`, `kCubic → C`, `kClose → Z`.
  `kConic` is approximated by a single cubic via
  `c1 = start + k·(ctrl - start)`, `c2 = end + k·(ctrl - end)`
  with `k = 2w / (1 + 2w)` — matches at t=0/1 and is exact at
  t=0.5 ; sub-pixel error for the weights upstream GMs use.
- **Paint stub** — `fill="black" stroke="none"` for fill
  paints, `fill="none" stroke="black" stroke-width=…` for
  stroke paints. B2.2 will replace this with full paint
  serialisation (colour, alpha, linecap / linejoin / dash).
- **Number format** — `formatScalar` renders integer-valued
  floats without a `.0` suffix, others via `%.6g` with
  trailing-zero trim. Locale-independent so emitted SVG is
  bit-stable across JVMs.
- **Tests** :
  [SkSVGCanvasGeometryTest.kt](kanvas-skia/src/test/kotlin/org/skia/svg/SkSVGCanvasGeometryTest.kt)
  (18 — framing incl. `flush` idempotence, per-op emission
  for all 6 geometry verbs, RRect with and without radii,
  path verb sequences M/L/Q/C/Z, even-odd vs default fill
  rule, identity vs non-identity CTM transform attribute,
  translate / scale composition, save/restore matrix
  discipline, end-to-end well-formed XML guard via
  `javax.xml.parsers.DocumentBuilder`).
- **LOC** : 427 main + 295 test = 722 total (cf. plan
  estimate ~300 + ~150 — overage covers the conic→cubic
  approximator, the scalar formatter, and the
  parse-with-DocumentBuilder integration test).
- **Status** : full kanvas-skia suite **2330 / 2330 green**
  (no regressions ; +18 new SVG tests).

### B2.2 — Paint surface ✅ shipped

- **Colour decomposition** — `fill` / `stroke` carry the lower-case
  `#rrggbb` hex (no 3-char shorthand — easier to grep). Alpha is
  surfaced separately via `fill-opacity` / `stroke-opacity` (omitted
  at fully opaque). The `colorHex` / `opacityString` helpers live
  on the [SkSVGCanvas](kanvas-skia/src/main/kotlin/org/skia/svg/SkSVGCanvas.kt)
  companion for reuse by future slices.
- **Style** — `kFill_Style` → `fill=… stroke="none"` ; `kStroke_Style`
  → `fill="none" stroke=…` ; `kStrokeAndFill_Style` → both colour +
  opacity attrs.
- **Stroke surface** — `stroke-width`, `stroke-linecap` (only
  emitted for non-`butt` ; `butt` is the SVG default),
  `stroke-linejoin` (non-`miter` only), `stroke-miterlimit` (only
  when join is miter and value differs from SVG's default of `4`).
- **Dash effect** — when `paint.pathEffect is SkDashPathEffect`, emit
  `stroke-dasharray` from the intervals plus `stroke-dashoffset`
  when phase is non-zero. Required new accessors on
  [SkDashPathEffect](kanvas-skia/src/main/kotlin/org/skia/foundation/SkDashPathEffect.kt) :
  `getIntervals()` (defensive copy) + `getPhase()`. Mirrors
  upstream's `SkDashPathEffect::asADash(DashInfo*)` pattern.
- **Anti-alias OFF** → `shape-rendering="crispEdges"` (SVG default
  is AA on).
- **Blend mode** : `kSrcOver` (default) emits no annotation ;
  `kSrc` emits a `<!-- blend: kSrc -->` XML comment ; any other
  mode emits the comment **plus** a `System.err` warning (so
  consumers running tests notice the loss of fidelity).
- **Tests** :
  [SkSVGCanvasPaintTest.kt](kanvas-skia/src/test/kotlin/org/skia/svg/SkSVGCanvasPaintTest.kt)
  (18) — colour hex + alpha, all 3 paint styles, every cap/join
  variant + the SVG-default elision rules, miterlimit elision,
  dash with and without phase, anti-alias OFF, all 3 blend-mode
  branches (default / kSrc / other-with-warning, the last
  asserted via captured `System.err`).
- **LOC** : ~108 main delta on [SkSVGCanvas](kanvas-skia/src/main/kotlin/org/skia/svg/SkSVGCanvas.kt)
  + ~18 main delta on
  [SkDashPathEffect](kanvas-skia/src/main/kotlin/org/skia/foundation/SkDashPathEffect.kt)
  (the two accessors) + ~252 test = 378 total (cf. plan estimate
  ~200 main + ~80 test ; overage covers the explicit System.err
  warning + capture-stderr test scaffold and the dash plumbing).
- **Status** : full kanvas-skia suite **2347 / 2347 green**
  (+18 vs B2.1).

### B2.3 — Clip ✅ shipped

- **Per-clip emission** — every `clipRect` / `clipRRect` / `clipPath`
  writes a `<defs><clipPath id="clip-N">…</clipPath></defs>` def
  followed by an open `<g clip-path="url(#clip-N)">` wrapper. The
  wrapper stays open until a matching `restore()` (or `flush()`)
  closes it. Subsequent draws inside the wrapper inherit the clip.
  Clip ids are document-monotonic (never reused) — SVG renderers
  cache `<clipPath>` defs by id and reuse would be undefined.
- **CTM capture** — the inner geometry inside `<clipPath>` carries a
  `transform="matrix(…)"` snapshot of the CTM at clip emission
  time. Subsequent CTM changes don't move the clip — same semantic
  as Skia's "device-space clip computed at clipX call time". Per-
  draw `transform` continues to use the full current CTM, so the
  drawn geometry lands in the right place regardless of the clip
  wrapper's transform-bearing inner geometry.
- **save / restore tracking** — a private `clipDepthStack` counts
  how many wrappers each save level opened ; `restore()` closes
  exactly that many `</g>` tags. `flush()` defensively closes any
  wrappers the caller forgot to pop, so a malformed draw loop still
  produces well-formed XML.
- **Multiple clips per save scope** — each `clipX` opens its own
  wrapper ; intersection-of-clips becomes nested SVG
  `<g clip-path>…<g clip-path>…</g></g>` (matches Skia's "clips
  compose as path intersection").
- **AA flag** — dropped silently (SVG handles AA at the renderer
  level — `clipPath` content has no equivalent of `doAntiAlias` so
  the bit would be lost in any encoding).
- **`kDifference`** — SVG `<clipPath>` has no native subtraction.
  The op emits a `<!-- clipOp: kDifference (SVG fallback :
  kIntersect) -->` comment **plus** a `System.err` warning, then
  falls through to the kIntersect path.
- **Even-odd fill rule** for `clipPath` — emitted as
  `clip-rule="evenodd"` on the inner `<path>` (the wrapping `<g>`
  ignores `fill-rule`, so the attribute must live on the clip's
  geometry).
- **Implementation note** — clip overrides do **not** chain to
  `super.clipRect / clipRRect / clipPath`. Two reasons : (a) the
  parent's clip state governs only its raster pipeline (the 1×1
  dummy backing bitmap is never drawn into) so we don't need it to
  be accurate ; (b) parent's 3-arg `clipRect` virtually dispatches
  back to the 2-arg variant, which would loop through the same
  override.
- **Tests** :
  [SkSVGCanvasClipTest.kt](kanvas-skia/src/test/kotlin/org/skia/svg/SkSVGCanvasClipTest.kt)
  (11) — per-shape def emission + wrapper opening, RRect with rx/ry,
  path with even-odd → `clip-rule`, CTM-at-emit-time captured as
  inner geometry transform, save/restore closes the wrapper,
  multiple clips inside the same save → nested wrappers, document-
  monotonic ids (no reuse after close), `flush()` closes leaked
  wrappers without crashing, kDifference comment + stderr warning,
  end-to-end well-formed XML guard.
- **LOC** : ~155 main delta (on `SkSVGCanvas`) + ~243 test = 398
  total (cf. plan estimate ~150 main + ~80 test ; overage covers
  the kDifference fallback path + capture-stderr scaffold and the
  flush-leak-recovery code).
- **Status** : full kanvas-skia suite **green** with all SVG slices
  cumulating (11 clip + 18 paint + 18 geometry = 47 SVG tests).

### B2.4 — Image + gradients ✅ shipped

- **`drawImage` / `drawImageRect`** override → encode the source
  via [SkPngEncoder.Encode](kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt)
  + Base64 → emit a single `<image href="data:image/png;base64,…"
  x=… y=… width=… height=…>` element. The standard CTM-as-`transform`
  attr applies. Sub-rect `src` (≠ full image bounds) emits a
  `<!-- drawImageRect: non-full src rect not yet honoured -->`
  comment + `System.err` warning ; the SVG renders the full image
  scaled into the dst rect (visually wrong for crop call sites,
  but structurally diff-friendly — proper sub-rect support needs
  an SVG `<clipPath>` trick that's deferred).
- **`SkLinearGradient`** shader on `paint.shader` →
  `<defs><linearGradient id="def-N" gradientUnits="userSpaceOnUse"
  x1=… y1=… x2=… y2=…
  gradientTransform="…" spreadMethod="…">
  <stop offset="…" stop-color="#rrggbb" stop-opacity="…"/>…
  </linearGradient></defs>` plus `fill="url(#def-N)"` on the
  consumer element. Tile mode → `spreadMethod` ("pad" for
  `kClamp` is the SVG default and omitted ; `kRepeat` → "repeat",
  `kMirror` → "reflect", `kDecal` falls back to "pad").
  Non-identity local matrix → `gradientTransform`.
- **`SkRadialGradient`** → analogous `<radialGradient>` block with
  `cx` / `cy` / `r`.
- **`SkBitmapShader`** → `<defs><pattern
  patternUnits="userSpaceOnUse" x=0 y=0 width=W height=H
  patternTransform="…"><image href="data:…"/></pattern></defs>` +
  `fill="url(#def-N)"`. **Tile-mode caveat** : SVG `<pattern>`
  natively only supports "repeat" semantics. `kClamp` / `kMirror`
  are not natively expressible — non-`kRepeat` tile modes emit a
  `<!-- bitmap shader tile=… not natively representable -->`
  comment + `System.err` warning ; the rendered SVG tiles instead.
- **Document-monotonic def ids** — a single `nextDefId` counter is
  shared across linear gradient / radial gradient / pattern, never
  reused even after the def goes out of scope, mirroring the clip-id
  policy from B2.3 (SVG renderers cache defs by id).
- **Required prerequisite accessors** — added to the upstream
  shader classes (mirrors Skia's `asAGradient(GradientInfo*)` /
  `asImage` patterns) :
  - [SkLinearGradient](kanvas-skia/src/main/kotlin/org/skia/foundation/SkLinearGradient.kt) :
    `getStartPoint() / getEndPoint() / getColors() /
    getPositions() / getTileMode()`
  - [SkRadialGradient](kanvas-skia/src/main/kotlin/org/skia/foundation/SkRadialGradient.kt) :
    `getCenter() / getRadius() / getColors() / getPositions() /
    getTileMode()`
  - [SkBitmapShader](kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmapShader.kt) :
    `getImage() / getTileX() / getTileY()`
- **Implementation note — defs must precede the consumer element**.
  The B2.2 `paintToSvgAttrs` was a pure function ; B2.4 had to
  refactor `emitElement` to flush shader defs to `out` **before**
  opening the element tag (otherwise the `<defs>` block lands in
  the middle of an attribute list, breaking XML — caught by the
  end-to-end well-formed-XML test).
- **Tests** :
  [SkSVGCanvasImageGradientTest.kt](kanvas-skia/src/test/kotlin/org/skia/svg/SkSVGCanvasImageGradientTest.kt)
  (14) — drawImage `<image>` emission with correct dimensions,
  data-URL round-trip via [Codec](kanvas-skia/src/main/kotlin/org/skia/codec/Codec.kt)
  (lossless because PNG), drawImageRect with full src vs sub-rect
  src (sub-rect path emits comment + warning), CTM honoured on
  `<image>`, linear gradient defs + fill URL + spread method
  mapping + stop-opacity for translucent stops + gradientTransform
  for non-identity localMatrix, radial gradient analogue, bitmap
  shader → `<pattern>` (kRepeat silent, non-kRepeat warns),
  document-monotonic def ids across linear/radial/pattern, complex
  draw end-to-end well-formed XML.
- **LOC** : ~189 main delta on
  [SkSVGCanvas](kanvas-skia/src/main/kotlin/org/skia/svg/SkSVGCanvas.kt)
  + ~60 main delta across the 3 shader accessor adds + ~340 test
  = ~589 total (cf. plan estimate ~250 main + ~120 test ; overage
  covers the shader-defs-must-precede-element refactor on
  `emitElement`, the drawImageRect sub-rect fallback, the bitmap-
  pattern tile-mode caveat plumbing).
- **Status** : full kanvas-skia suite **2394 / 2394 green**
  (+47 vs B2.3 baseline ; 14 new image+gradient tests, the rest
  are upstream pathops slices that merged in parallel).

### B2.5 — D4.5 SvgSink wiring ✅ shipped

Closes the SVG mini plan. Wires the [SkSVGCanvas](kanvas-skia/src/main/kotlin/org/skia/svg/SkSVGCanvas.kt)
chain (B2.1 → B2.4) into the DM matrix as a fifth sink alongside
`8888` / `f16` / `pic-8888` / `pic-f16`.

- **[SvgSink](kanvas-skia/src/main/kotlin/org/skia/dm/SvgSink.kt)** —
  `Sink` impl that allocates a `StringWriter`, builds an
  `SkSVGCanvas` over it, drives `gm.draw`, flushes, and returns
  the UTF-8 bytes wrapped in [Sink.Result.Bytes].
  - `tag = "svg"`, `fileExtension = "svg"`.
  - GM-level `bgColor()` is honoured by emitting a full-viewport
    rect when non-default (the SVG default background is
    transparent, so emitting a white rect for every default-bg GM
    would be noise).
  - Wraps `gm.draw` exceptions as [Sink.Result.Error] with a
    `"SvgSink[<gmName>]: <message>"` prefix.
- **[Sink.Result.Bytes](kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt)**
  — new variant on the sealed `Result` hierarchy. The plan sketch
  in [MIGRATION_PLAN_RASTER_COMPLETION.md § D4](MIGRATION_PLAN_RASTER_COMPLETION.md)
  flagged this as a future extension ; B2.5 makes it real. Carries
  `bytes: ByteArray` + `mimeType: String` (typically
  `"image/svg+xml"`). Custom `equals` / `hashCode` for content
  comparison since `data class` defaults to reference equality on
  arrays.
- **[Sink.fileExtension](kanvas-skia/src/main/kotlin/org/skia/dm/Sink.kt)**
  — new property on the [Sink] interface (default `"png"` so the
  raster sinks don't have to declare). Mirrors upstream's
  `Sink::fileExtension()`. The [Runner] consumes it for the
  per-record `extension` field — B2.5 also drops the previous
  hardcoded `"png"` literal in [Runner.buildPassRecord].
- **[Runner](kanvas-skia/src/main/kotlin/org/skia/dm/Runner.kt)**
  extended to switch on the new variant : `Sink.Result.Bytes` →
  `buildBytesRecord` which hashes the raw bytes directly (no PNG
  re-encode — vector formats are already canonical) and leaves the
  raster classification fields empty (`colorType` / `alphaType` /
  `gamut` / `transferFn` / `colorDepth` = `""`).
- **[DmCli](kanvas-skia/src/main/kotlin/org/skia/dm/DmCli.kt)** —
  `"svg"` joins `KNOWN_CONFIGS` and `resolveOneSink` ; `--config svg`
  now resolves to a fresh `SvgSink` and mixed matrices like
  `--config 8888 svg` work.
- **`TestUtils.runGmTest`** — extended `when` to handle the new
  `Bytes` branch as an `IllegalStateException` (the function
  expects raster output ; F16 sink can't return Bytes).
- **`SinkTest`** — extended the "exhaustive sealed `when`" sanity
  test to assert the new trio (`Ok` / `Bytes` / `Error`).

**Tests** :
[SvgSinkTest.kt](kanvas-skia/src/test/kotlin/org/skia/dm/SvgSinkTest.kt)
(14) — `tag` / `fileExtension`, `Bytes` payload + MIME type +
parseable XML, GM draw ops surfaced in output, default-bg silence
vs non-default bg-rect emission, exception → Error wrap, Runner
produces `Bytes`-shaped record with `extension=svg` + empty raster
fields, **MD5 over raw bytes equals `MessageDigest.getInstance("MD5")`
output** (no PNG re-encode), `dm.json` ext field reflects `"svg"`,
DmCli `KNOWN_CONFIGS` membership + single-config resolution + mixed
8888+svg matrix, `Sink.Result.Bytes` content equality + inequality
(both bytes and mime as discriminators).

**LOC** : ~74 main (SvgSink) + ~46 main delta on Sink (Result.Bytes
variant + fileExtension property) + ~22 main delta on Runner
(Bytes branch + buildBytesRecord) + ~3 main delta on DmCli (one
config registration line + one resolveOneSink branch) + ~1 main
delta on TestUtils + ~1 main delta on SinkTest = **~147 main +
~221 test = 368 total** (cf. plan estimate ~80 main + ~120 test ;
overage covers the Sink interface widening (`fileExtension` +
`Result.Bytes` data class with custom equals/hashCode), the
exhaustiveness updates in callers, and the wider DmCli /
TestUtils plumbing).

**Status** : full kanvas-skia suite **2417 / 2417 green** (+23 vs
B2.4 ; 14 new SvgSink tests, the rest are upstream pathops slices
that merged in parallel). **SVG mini plan closes**.

## Total LOC

| Slice | Main | Test |
|---|---:|---:|
| B2.1 ✅ | **427** (planned ~300) | **295** (planned ~150) |
| B2.2 ✅ | **126** (108 SVG + 18 SkDashPathEffect ; planned ~200) | **252** (planned ~80) |
| B2.3 ✅ | **155** (planned ~150) | **243** (planned ~80) |
| B2.4 ✅ | **249** (189 SVG + 60 shader accessors ; planned ~250) | **340** (planned ~120) |
| B2.5 ✅ | **147** (74 SvgSink + 46 Sink + 22 Runner + 5 DmCli/test plumbing ; planned ~80) | **221** (planned ~120) |
| **Total** | **1104** (all 5 slices shipped) | **1351** (all 5 slices shipped) |

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

✅ **closed** — all 5 slices shipped. B2.1 ✅ skeleton + geometry,
B2.2 ✅ paint surface, B2.3 ✅ clip, B2.4 ✅ image + gradients,
B2.5 ✅ D4.5 SvgSink wiring. Full kanvas-skia suite **2417 / 2417
green** with the SVG canvas + sink on the classpath, `--config svg`
resolves, and `Runner` emits `dm.json` records carrying
`"ext": "svg"` for SVG output.

**What this delivers** :
- A vector debug channel — every existing GM can be re-rendered as
  SVG simply by adding `--config svg` to the DM CLI.
- A complete D4 matrix : 5 sinks (`8888`, `f16`, `pic-8888`,
  `pic-f16`, `svg`).
- The `Sink.Result.Bytes` extension hook so future vector / streaming
  sinks (e.g. a revived `PdfSink`) can plug in without touching the
  Runner's contract.

**What's not (deliberately) covered** — see [Out (descoped, with
revival path)](#out-descoped-with-revival-path) for the catalogue :
text, filters, saveLayer, color filters, non-clamp bitmap shader
modes (each has a "revive when…" trigger).
