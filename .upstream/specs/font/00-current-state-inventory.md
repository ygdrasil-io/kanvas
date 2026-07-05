# Font Current State Inventory

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Record the font, text, glyph, and emoji behavior that already exists and the
remaining gaps. This inventory includes completed work only when it belongs to
the target font experience.

Generic paint lowering, non-text geometry, PM dashboard UX, and codec breadth
remain owned elsewhere.

## Current Public Surface

The portable font API surface lives mainly under:

```text
font/src/main/kotlin/org/graphiks/kanvas/font/
kanvas/src/main/kotlin/org/skia/foundation/
```

Current core types include:

| Type | Current role |
|---|---|
| `SkFont` | Mutable typeface plus size, scale, skew, edging, hinting flags, variation metadata, metrics, text-to-glyph helpers, glyph widths, and glyph path access. |
| `SkTypeface` | Abstract font resource, glyph lookup, table data, variation axes, clone arguments, family names, glyph metrics, and glyph paths. |
| `SkFontMgr` | Family/style discovery, data/stream/file typeface construction, style matching, and character fallback lookup. |
| `SkFontStyleSet` | Style-set enumeration and CSS-style matching. |
| `SkTextBlob` / `SkTextBlobBuilder` | Explicit glyph runs and positioned text blob storage. |
| `SkShaper` | Explicit portable shaping entry point for callers that need runs, clusters, directions, diagnostics, and optional fallback. |
| `LiberationFontMgr` | Deterministic bundled Liberation font manager factory. |

## Current OpenType Backend

The pure Kotlin OpenType backend lives under:

```text
font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/
```

It currently provides:

- pure Kotlin OpenType/TrueType loading with no AWT, JNI, FreeType, HarfBuzz,
  or Fontations dependency;
- bundled Liberation family manager with Sans, Serif, and Mono families across
  Regular, Bold, Italic, and Bold Italic;
- pure Kotlin system font scanning through `OpenTypeSystemFontMgr`;
- simple and composite TrueType `glyf` outline conversion to `SkPath`;
- Unicode cmap lookup for glyph ids;
- family names, localized family names, PostScript names, style matching,
  metrics, bounds, advance widths, and glyph counts;
- `kern` table pair adjustments plus a limited `GPOS` type 2 pair-positioning
  fallback;
- raw SFNT table copies through `copyTableData`;
- TTC face selection;
- `fvar` axis enumeration and initial simple-glyph `gvar` variation support;
- COLRv0/CPAL metadata and draw-path integration for layered outline glyphs;
- internal metadata parsing for selected COLRv1, CBDT/CBLC, sbix, and
  SVG-in-OpenType surfaces where present in current code and tests.

This is a real internal font backend, not a temporary substitute. It remains
narrower than Skia plus FreeType/HarfBuzz/Fontations and must say so in
support claims.

## Current Text Rendering

The current simple text path is:

```text
SkCanvas.drawString
  -> SkFont.makeTextPath
  -> SkTypeface glyph lookup and outline paths
  -> SkCanvas.drawPath
```

CPU and WebGPU can render simple outline text through the existing path
pipeline. Current WebGPU evidence includes:

- `TextSmokeWebGpuTest`, proving simple text reaches the GPU path-fill route;
- `TextMaskFilterWebGpuTest`, proving text drawn through `drawString` and
  `drawTextBlob` honors blur mask filters on the WebGPU path.

This is not a dedicated GPU text atlas pipeline and must not be described as
one. It is outline/path rendering for text.

## Current Shaping

`SkCanvas.drawString` intentionally does not run full complex shaping.

`SkShaper` provides an explicit portable entry point with:

- Unicode bidi run direction using JVM Unicode/Bidi primitives;
- script segmentation;
- stable original-text clusters;
- text-local glyph positions;
- missing-glyph diagnostics;
- optional multi-font fallback provider;
- conservative feature controls for standard ligatures, Arabic joining
  presentation forms, Devanagari pre-base vowel reordering, script/language
  gating, mark positioning, and cursive attachment provider hooks.

This is a deterministic internal shaping boundary. It is not HarfBuzz parity,
and it does not silently enable complex shaping for ordinary `drawString`.

## Current Glyph-Mask Boundary

Geometry/Coverage already defines the ownership split:

- text/glyph infrastructure owns glyph discovery;
- text/glyph infrastructure owns glyph rasterization;
- text/glyph infrastructure owns atlas lifetime;
- text/glyph infrastructure owns invalidation;
- geometry consumes an opaque glyph mask ref or emits a dependency diagnostic.

The existing stable diagnostics are:

| Diagnostic | Meaning |
|---|---|
| `coverage.glyph-mask-dependency-unavailable` | Text/glyph infrastructure did not provide a glyph mask. |
| `coverage.alpha-mask-unsupported` | Backend cannot consume/materialize standalone alpha-mask coverage. |

WebGPU currently refuses standalone alpha-mask coverage with
`coverage.alpha-mask-unsupported`. A future text atlas must change this with
adapter-backed evidence, not by weakening the diagnostic.

## Current GM Evidence

The latest current upstream rebaseline is:

```text
reports/upstream-rebaseline/2026-05-25-post-1087.md
reports/upstream-rebaseline/2026-05-25-post-1087.tsv
```

It records:

| Signal | Count |
|---|---:|
| Tracked GM rows | 437 |
| `PORTED` rows | 351 |
| `TEST_DISABLED` rows | 44 |
| `PARTIAL` rows | 36 |
| `HELPER` rows | 4 |
| `STUB` rows | 1 |
| `MISSING` rows | 1 |
| Mechanical GM progress | 80.3% |
| Actionable convergence | 87.3% |

Font-related rows include a mix of shipped coverage, fixture-gated rows, and
dependency-gated rows. The `PORTED` bucket does not automatically mean the full
font family is Skia-equivalent; support remains scene-contract specific.

## Current Font-Gated Rows

As of `2026-05-25-post-1087.tsv`, the active `font-gated` rows are:

| Row | Count | Status | Blocker |
|---|---:|---|---|
| `coloremoji_blendmodes` | 1 | `TEST_DISABLED` | `STUB.EMOJI_TABLES`, bitmap/SVG color glyph rendering |
| `dftext` | 1 | `TEST_DISABLED` | `STUB.DF_TEXT_FULL_GM` plus `STUB.EMOJI_TABLES` source dependency |
| `fontations` | 1 | `TEST_DISABLED` | `STUB.FONTATIONS` |
| `fontations_ft_compare` | 1 | `TEST_DISABLED` | `STUB.FONTATIONS` |
| `pdf_never_embed` | 3 | `PARTIAL` | `STUB.PDF_TABLE_SUBSET_FONTMGR` |
| `scaledemoji` | 3 | `PARTIAL` | `STUB.EMOJI_TABLES` |
| `scaledemoji_rendering` | 1 | `TEST_DISABLED` | `STUB.EMOJI_TABLES` |

These rows remain dependency-delivery work. They must not be cleared by adding
HarfBuzz, FreeType, Fontations, or platform renderer dependencies to the
portable path.

## Current Non-Font But Adjacent Rows

Some rows are related to font rendering but are not all font-gated:

| Row | Current classification | Font relevance |
|---|---|---|
| `typeface` | `fixture-gated` | Missing or unsupported fixture/font formats such as Type1 must stay separate from OpenType backend regressions. |
| `gammatext` | `partial-coverage` | Text rendering is active, but tolerance ratchets need measured OpenType-vs-FreeType drift evidence. |
| `gradtext` | `partial-coverage` | Text shader/gradient behavior remains a separate visual fidelity ratchet. |
| `dftext_blob_persp` | `PORTED` | Narrow SDF/perspective slice is active, but broad `dftext` remains gated. |
| `textblobmixedsizes` | `PORTED` | Active text blob coverage with known DF text raster caveats. |

## Gaps

The current font layer is useful but not final:

- no broad HarfBuzz-equivalent GSUB/GPOS shaping;
- no implicit complex shaping in `SkCanvas.drawString`;
- no release-ready multi-font fallback for all scripts and emoji sequences;
- no dedicated GPU glyph atlas or SDF/LCD text pipeline;
- WebGPU standalone glyph alpha masks are refused;
- no broad COLRv1 paint-graph support claim without generated evidence;
- no bitmap emoji render integration for CBDT/CBLC and sbix PNG strikes;
- no SVG-in-OpenType rendering path;
- no Fontations or FreeType parity layer in the portable target;
- Type1/PFA/PFB and PDF table-subset font manager work remain separate;
- no generated dashboard font scene pack with CPU/GPU/reference artifacts.

## Non-Goals

- Do not add native or external font dependencies to clear these gaps.
- Do not mark broad text/font readiness from simple outline text smoke tests.
- Do not replace OpenType parsing with platform font APIs.
- Do not conflate emoji table dispatch with ordinary monochrome glyph outlines.
- Do not move glyph atlas ownership into geometry coverage.
- Do not hide expected-unsupported rows when font scenes enter the dashboard.

## Acceptance Baseline

The current font baseline remains valid when:

- portable OpenType tests pass for the supported SFNT subset;
- `SkFont` and `SkTypeface` APIs preserve metrics, glyph id, path, bounds, and
  table-copy behavior;
- `SkShaper` remains explicit and deterministic;
- simple `drawString` and `drawTextBlob` continue to route through glyph paths;
- WebGPU text smoke tests keep proving non-empty text rendering where an adapter
  exists;
- font-gated rows stay classified as gated until internal implementations land;
- unsupported paths expose stable diagnostics instead of silent approximation.
