# Font Source, SFNT, And Scalers

Status: Draft
Date: 2026-06-13

## Purpose

Define the complete Kanvas target for font sources, SFNT/OpenType parsing,
collections, typeface identity, fallback catalogs, metrics, and outline
scalers.

This spec owns font data before shaping. It does not own paragraph layout,
GSUB/GPOS feature application, glyph atlas lifetime, or GPU execution.

## Font Sources

Supported final sources:

| Source | Target behavior |
|---|---|
| `BundledFontSource` | Deterministic fonts committed with provenance and licenses. |
| `GeneratedFixtureFontSource` | Small generated fonts for parser, scaler, shaping, and color-font tests. |
| `UserDataFontSource` | Caller-provided bytes with stable content hash. |
| `UserStreamFontSource` | Caller-provided stream copied into bounded immutable font data before parsing. |
| `UserFileFontSource` | Caller-provided file path with explicit path, mtime, length, and content hash policy. |
| `SystemScannedFontSource` | Pure Kotlin directory scan, no platform font API, diagnostics for skipped files. |

Every source must expose:

- `FontSourceID` backed by `kotlin.uuid.Uuid`;
- provenance classification;
- content hash when bytes are available;
- host-dependent marker when the source comes from system scanning;
- face count;
- supported table tags;
- diagnostics for skipped, malformed, unsupported, or duplicate faces.

Host system font choices cannot be normative reference evidence unless the font
bytes are captured as fixtures.

## Font Collections

The final target supports:

- single-face SFNT fonts;
- TTC collections with indexed faces;
- OTC collections when the contained outlines are supported by the scaler;
- stable face selection by index and by family/style match;
- collection-level diagnostics for malformed or unsupported faces.

Collection identity includes:

- source hash;
- collection index;
- PostScript name when present;
- family name and style metadata;
- variation position;
- palette position;
- table availability facts.

## Required SFNT/OpenType Tables

The parser must be defensive and bounded. Required table sets depend on outline
format.

Common required or high-value tables:

- `cmap`;
- `head`;
- `hhea`;
- `hmtx`;
- `maxp` for TrueType outlines;
- `name`;
- `OS/2`;
- `post`;
- `loca` and `glyf` for TrueType outlines;
- `CFF ` for CFF outlines;
- `CFF2` for CFF2 variable outlines;
- `vhea` and `vmtx` for vertical layout when present;
- `GDEF`, `GSUB`, `GPOS` for shaping data;
- `BASE` for baseline data when present;
- `fvar`, `avar`, `gvar`, `HVAR`, `VVAR`, `MVAR`, and CFF2 variation data
  for variable fonts;
- `COLR`, `CPAL`, `CBDT`, `CBLC`, `sbix`, and `SVG ` for glyph
  representation dispatch.

Malformed required tables reject the face. Malformed optional tables fail
closed and preserve ordinary outline text when possible.

## `cmap` Contract

The final target supports Unicode mapping sufficient for Kanvas text:

- format 12 and format 4 as primary Unicode mappings;
- format 14 for variation selectors;
- format 6 and format 0 as legacy lower-priority fallbacks;
- format 13 for many-to-one mappings when fixtures prove product need;
- stable glyph id `0` for missing code points;
- diagnostics when no usable Unicode `cmap` exists.

Rare legacy encodings may remain refused when they do not affect the Kanvas
required script matrix. Refusal must be precise, not generic.

## Typeface Identity

`TypefaceID` is an opaque domain-specific value class backed by
`kotlin.uuid.Uuid`. The UUID generation policy must include every fact that can
alter glyph output:

- `FontSourceID`;
- collection index;
- family and style;
- PostScript name;
- supported outline format;
- variation coordinates;
- palette index and overrides;
- selected `cmap` subtable;
- scaler mode;
- fallback catalog generation when the typeface came from fallback;
- content hash or fixture identity.

Two typefaces with different variation coordinates, palettes, collection
indices, or source bytes are different identities even if their family names
match.

## Scaler Target

The complete target supports three outline families:

| Outline | Target |
|---|---|
| TrueType `glyf` | Required, including simple and composite glyphs. |
| CFF | Required for OpenType/CFF fonts. |
| CFF2 | Required for variable CFF2 fonts. |

Out of scope for the complete Kanvas target unless later accepted:

- legacy Type1/PFA/PFB as standalone font formats;
- bitmap-only fonts without a supported color bitmap glyph route;
- platform-native synthetic outlines;
- native FreeType raster behavior.

Durable refusal examples:

- `font.outline-format.legacy-type1`;
- `font.outline-format.bitmap-only`;
- `font.outline-format.unsupported-wrapper`.

## TrueType `glyf`

The final `glyf` scaler must support:

- simple contours;
- composite glyph recursion with bounds and depth limits;
- component transforms;
- contour winding preservation;
- phantom points for advance interactions where variation support requires
  them;
- `loca` short and long offsets;
- malformed glyph isolation so one bad optional glyph does not poison the face
  when safe to skip.

## CFF And CFF2

The CFF/CFF2 scaler must support:

- CFF INDEX structures with bounds checks;
- top dict, private dict, charstrings, subrs, and global subrs;
- Type 2 charstring drawing operators listed below;
- width extraction and metrics integration;
- CFF2 variation store application;
- deterministic conversion to the Kanvas path representation;
- explicit diagnostics for unsupported or malformed operators.

CFF stem hints may be parsed or skipped as metadata, but the target does not
require pixel-perfect hinted raster parity.

### CFF Type 2 Operator Contract

The required Type 2 interpreter supports these operator classes:

| Class | Required operators |
|---|---|
| Path movement | `rmoveto`, `hmoveto`, `vmoveto`. |
| Lines | `rlineto`, `hlineto`, `vlineto`. |
| Curves | `rrcurveto`, `vhcurveto`, `hvcurveto`, `rcurveline`, `rlinecurve`, `vvcurveto`, `hhcurveto`. |
| Flex curves | `flex`, `hflex`, `hflex1`, `flex1`. |
| Subroutines | `callsubr`, `callgsubr`, `return`, with bounded local/global subr index resolution. |
| End glyph | `endchar`, including width handling; deprecated accented endchar behavior may refuse unless fixture evidence promotes it. |
| Hints parsed as metadata | `hstem`, `vstem`, `hstemhm`, `vstemhm`, `hintmask`, `cntrmask`. |
| CFF2 variation | `vsindex`, `blend`, variation-store lookup, and tuple interpolation. |

Interpreter limits are part of conformance:

- maximum operand stack depth is fixed and diagnosed on overflow;
- maximum subroutine recursion depth is fixed and diagnosed on overflow;
- all INDEX, dict, and charstring offsets are bounds checked before reads;
- unsupported escaped operators refuse the glyph with
  `font.cff-operator-unsupported`;
- malformed stack effects refuse the glyph with `font.cff-stack-malformed`;
- hint operators do not affect normative raster output unless a later spec
  accepts hinted CFF raster behavior.

CFF2 support must not be marked complete until `blend`, `vsindex`, variation
store lookup, metrics interaction, and at least one variable CFF2 fixture are
covered by tests.

## Variation Support

The complete target supports variable font behavior needed by Kanvas:

- `fvar` axis enumeration and named instances;
- `avar` normalized coordinate mapping;
- TrueType `gvar` deltas for simple and composite glyphs;
- IUP interpolation;
- phantom point and advance deltas where required;
- `HVAR`, `VVAR`, and `MVAR` metrics deltas;
- CFF2 variation data;
- variation coordinates in all relevant cache keys.

Unsupported axes or malformed variation data must diagnose and either:

- fall back to default coordinates when that is semantically valid and visible;
  or
- refuse the face or glyph route when default fallback would misrepresent the
  requested instance.

## Metrics

The font core owns:

- glyph advances;
- glyph bounds;
- font metrics;
- vertical metrics;
- underline and strikeout metrics;
- baseline data from `BASE` when present;
- variation-adjusted metrics;
- synthetic style metrics when the facade requests a supported synthetic style.

Metrics must be stable for a given font source and variation position.

## Hinting Policy

The complete target does not require:

- a full TrueType instruction VM;
- CFF stem hint raster parity;
- pixel-perfect FreeType output;
- platform font raster parity.

The scaler may parse hinting metadata when useful, but Kanvas normative output
is deterministic outline, A8, and SDF behavior. Evidence that differs from
FreeType because of hinting must be labeled as Kanvas scaler drift, not hidden
by broad tolerances.

## Font Fallback Catalog

The pure Kotlin fallback catalog must support:

- family/style matching;
- generic family matching;
- script-aware fallback ordering;
- locale-aware fallback hints;
- emoji preference;
- variable-axis-aware fallback requests;
- deterministic bundled fallback mode;
- host-dependent system-scanned fallback mode with diagnostics.

Fallback selection must be visible in `ResolvedFontRun` and
`TextRouteDiagnostics`.

## Diagnostics

Stable reason-code families:

- `font.source.unreadable`;
- `font.source.host-dependent`;
- `font.sfnt.required-table-missing`;
- `font.sfnt.optional-table-malformed`;
- `font.collection-index-invalid`;
- `font.cmap-format-unsupported`;
- `font.outline-format-unsupported`;
- `font.cff-operator-unsupported`;
- `font.variation-data-malformed`;
- `font.variation-axis-unsupported`;
- `font.metrics-variation-unavailable`;
- `font.fallback-family-unavailable`;
- `font.fallback-glyph-unavailable`.

## Acceptance Criteria

- Font source identity is stable and dumpable.
- SFNT parsing is bounded and defensive.
- TrueType `glyf`, CFF, and CFF2 outlines can produce deterministic paths.
- Variable font coordinates affect identity, metrics, and glyph output.
- Fallback decisions are inspectable.
- No native or external font engine is required for normative behavior.
