# OpenType Backend Contract

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`
Current implementation: `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/`

## Purpose

Define the internal pure Kotlin OpenType backend contract. This file owns font
file parsing, typeface construction, table access, glyph outlines, metrics,
style matching, variation/palette arguments, and fallback behavior.

It does not own full shaping, generic rendering, or native library integration.

## Current Supported Scope

The current backend supports a bounded internal OpenType/TrueType subset:

| Area | Current status |
|---|---|
| TTF loading | Implemented through `OpenTypeFontMgr` and `OpenTypeTypeface`. |
| TTC face index | Implemented for basic collection selection. |
| Bundled fonts | Implemented through Liberation resources. |
| System scanning | Implemented through `OpenTypeSystemFontMgr` and `SystemFontScanner`. |
| Core tables | `cmap`, `head`, `hhea`, `hmtx`, `loca`, `glyf`, `maxp`. |
| Metadata tables | `name`, `OS/2`, `post` where available. |
| Kerning | Legacy `kern` plus limited `GPOS` pair positioning fallback. |
| Glyph outlines | Simple and composite TrueType `glyf` to `SkPath`. |
| Raw tables | Defensive `copyTableData(tag)` copies. |
| Variations | `fvar` axis enumeration and initial simple-glyph `gvar` support. |
| COLRv0/CPAL | Metadata and layered outline rendering. |
| COLRv1 / bitmap / SVG | Metadata and selected internal slices; render support remains per-format gated unless tests prove it. |

## Contract Rules

### Parsing

- All table parsing must be bounded and defensive.
- Malformed optional tables must fail closed and leave ordinary outline text
  usable when possible.
- Required table failures should reject the font data.
- All table offsets and lengths must be bounds checked before reads.
- Generated fixtures are preferred for narrow table edge cases.

### Typeface Identity

Every binary-backed typeface should expose:

- family name;
- style;
- PostScript name when present;
- glyph count;
- raw table copy when table bytes are available;
- collection index where relevant;
- variation and palette arguments when cloned.

The empty typeface remains a no-op sentinel and must not pretend to have
portable font data.

### Cmap And Glyph Mapping

The current cmap policy is:

- prefer Unicode format 12 and format 4 subtables;
- accept format 6 and format 0 only as lower-priority legacy fallbacks;
- return glyph id `0` for missing code points;
- keep format 14 and broader rare cmaps gated until fixtures and product need
  justify them.

Glyph mapping must be deterministic for a given font file and variation clone.

### Metrics And Bounds

The backend owns:

- font metrics;
- glyph advance widths;
- glyph bounding boxes;
- text measurement;
- per-glyph path bounds;
- kerning adjustments where supported.

Skia/FreeType pixel-perfect metrics are not guaranteed. When a GM differs due
to OpenType-vs-FreeType scaler behavior, the evidence should say that directly.

### Outline Generation

TrueType outline conversion must:

- handle simple glyph contours;
- handle composite glyph transforms;
- preserve contour winding sufficiently for existing `SkPath` rendering;
- support scale, skew, and text-local positioning;
- return `null` or empty paths consistently for missing or non-outline glyphs.

Broader CFF, Type1/PFA/PFB, and native FreeType formats remain outside this
backend until an internal parser and fixtures exist.

### Kerning And Pair Positioning

Current support:

- legacy `kern` format 0;
- limited `GPOS` pair positioning lookup type 2 as a fallback when legacy
  `kern` data is absent.

Non-goals for this backend:

- full GSUB;
- full GPOS;
- mark positioning;
- cursive attachment;
- script shaping;
- HarfBuzz-equivalent feature application.

Those belong to the explicit shaping layer.

### Variations

Current support:

- `fvar` axis enumeration;
- simple-glyph `gvar` version 1.0 shared/embedded peak tuple deltas applied
  through `SkFontArguments.VariationPosition`.

Gated follow-ups:

- composite glyph variation deltas;
- `avar`;
- IUP edge cases;
- hinting interactions;
- phantom advance deltas.

Variation support must not mutate the original typeface. Use clone semantics.

### System Font Fallback

`OpenTypeSystemFontMgr` may scan host files, but the portable contract stays:

- no AWT or platform font API dependency;
- unsupported or malformed files are skipped with optional diagnostics;
- fallback planning is policy-driven and inspectable;
- host-dependent choices must not be used as deterministic reference evidence
  unless the font files are captured as fixtures.

Bundled Liberation fonts remain the deterministic baseline for reproducible
tests.

## Explicitly Unsupported

- HarfBuzz, FreeType, Fontations, CoreText, DirectWrite, fontconfig, or JNI as
  required dependencies.
- Full complex shaping inside `OpenTypeTypeface`.
- Broad CFF/Type1/PFA/PFB font loading.
- Full native color glyph dispatch.
- Platform font fallback through desktop APIs.
- Pixel-perfect FreeType raster parity.

## Fallback Diagnostics

Recommended stable font backend reason codes:

| Code | Use |
|---|---|
| `font.required-table-missing` | Reject malformed or incomplete required SFNT data. |
| `font.optional-table-malformed` | Ignore optional malformed table and keep outline path when possible. |
| `font.cmap-format-unsupported` | Cmap exists but no supported Unicode mapping is usable. |
| `font.outline-format-unsupported` | Glyph outline format is outside the internal parser. |
| `font.variation-axis-unsupported` | Requested variation needs unsupported variation machinery. |
| `font.native-engine-unavailable` | A test asks for native FreeType/Fontations parity outside this spec. |

These proposed `font.*` codes should appear in tests or reports before they are
used in dashboard scene rows.

## Acceptance Criteria

- Supported tables have focused parser tests and malformed-table tests.
- `makeFromData`, `makeFromStream`, and `makeFromFile` fail closed.
- Raw table copies are defensive and do not expose mutable backend storage.
- Bundled Liberation manager stays deterministic.
- System font manager diagnostics are optional and silent by default.
- No OpenType work introduces an external font dependency.
