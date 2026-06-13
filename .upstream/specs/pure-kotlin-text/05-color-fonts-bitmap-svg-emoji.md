# Color Fonts, Bitmap Glyphs, SVG Glyphs, And Emoji

Status: Draft
Date: 2026-06-13

## Purpose

Define complete Kanvas target behavior for color fonts and emoji:

- COLR/CPAL;
- COLRv1 paint graphs;
- PNG bitmap glyphs from CBDT/CBLC and sbix;
- SVG-in-OpenType glyph rendering through a pure Kotlin glyph-scoped renderer;
- emoji fallback and sequence shaping;
- color glyph diagnostics.

This spec is font-scoped. It does not define a general SVG document renderer or
general image codec system.

## COLR And CPAL

The final target supports:

- CPAL palettes;
- palette selection;
- palette overrides;
- COLRv0 base glyph and layer records;
- COLRv1 paint graphs;
- color glyph identity in `TypefaceID` and `GlyphStrikeKey`;
- palette data in evidence dumps.

Malformed optional color tables fail closed and preserve outline glyph support
when possible.

## COLRv1 Paint Graph

The complete COLRv1 target supports:

- `PaintSolid`;
- `PaintVarSolid`;
- `PaintLinearGradient`;
- `PaintRadialGradient`;
- `PaintSweepGradient`;
- variable gradient stops when variation data is available;
- `PaintGlyph`;
- `PaintColrGlyph`;
- `PaintTransform`;
- `PaintTranslate`;
- `PaintScale`;
- `PaintRotate`;
- `PaintSkew`;
- `PaintComposite`;
- `PaintClipBox`;
- bounded recursion;
- cycle detection;
- bounds computation;
- stable paint graph dumps.

Unsupported or malformed operations must diagnose. When a monochrome outline
fallback is used, the dump must say that the color glyph route was not used.

## COLR Rendering Policy

`ColorGlyphPlan` contains:

- glyph ID;
- paint graph root;
- palette identity;
- variation coordinates;
- resolved colors;
- paint operation list or immutable graph handle;
- required blend/composite facts;
- clip facts;
- bounds;
- diagnostics.

The GPU renderer decides final GPU route from this plan. The font stack owns
paint graph evaluation and route facts.

## PNG Bitmap Glyphs

The complete target supports PNG payloads only for embedded bitmap glyphs.

Supported table families:

- CBDT/CBLC PNG strikes;
- sbix PNG strikes.

Required behavior:

- strike selection by requested size and policy;
- glyph-to-image lookup;
- origin placement;
- PNG decode through pure Kotlin code;
- premul/alpha policy;
- scaling policy when exact strike size is absent;
- bitmap glyph plan dumps;
- CPU oracle hash;
- GPU handoff through uploaded texture or glyph artifact route.

Other bitmap payload formats refuse:

- non-PNG CBDT formats;
- non-PNG sbix formats;
- platform-specific bitmap payloads;
- malformed PNG payloads.

## SVG-In-OpenType

SVG glyph support is mandatory for the complete Kanvas target, but it is
glyph-scoped and pure Kotlin. It must not pull in a general native SVG engine.

The target renderer supports the static SVG subset needed for glyphs:

- document record lookup by glyph ID;
- `svg` root, `viewBox`, width/height;
- groups;
- paths;
- basic shapes when useful for fixtures;
- transforms;
- fill and stroke;
- fill rules;
- opacity;
- linear and radial gradients;
- clip paths;
- `use` references with bounded recursion;
- symbol/defs references needed by glyph documents;
- color inheritance relevant to glyph rendering;
- bounds computation;
- deterministic conversion to `SVGGlyphPlan`.

The SVG glyph renderer refuses:

- scripts;
- external resources;
- network references;
- animation;
- filters unless a later accepted glyph-scoped filter subset exists;
- `foreignObject`;
- embedded text requiring recursive font layout;
- unsupported CSS selectors or dynamic style behavior.

Refusals must be per glyph and must preserve monochrome outline fallback when a
valid outline exists and style permits it.

## Emoji

Emoji support includes:

- Unicode emoji property data for the pinned Unicode version;
- variation selectors;
- skin-tone modifiers;
- gender and role modifiers where represented by ZWJ sequences;
- ZWJ sequence shaping;
- emoji fallback family preference;
- color glyph route dispatch;
- fallback to monochrome only when valid and diagnosed.

Emoji shaping produces ordinary `ShapedGlyphRun` data plus emoji sequence facts
for glyph representation selection.

## Color Glyph Dispatch

The target dispatch order is explicit and style-dependent. A typical default
for emoji-capable color text is:

1. COLRv1 or COLRv0 when the selected typeface maps the sequence to COLR data;
2. PNG bitmap glyph when the selected typeface has a usable strike;
3. SVG glyph when the selected typeface maps the glyph to an SVG document;
4. monochrome outline fallback when valid and accepted by style;
5. refusal with stable diagnostics.

The actual selected route must appear in `ColorGlyphPlan` or
`TextRouteDiagnostics`.

## Security And Resource Limits

Color glyph rendering must be bounded:

- maximum COLR recursion depth;
- maximum SVG `use` recursion depth;
- maximum paint operations per glyph;
- maximum decoded PNG bytes;
- maximum SVG document bytes;
- maximum generated path commands;
- maximum gradient stops;
- maximum atlas area per strike.

Limits produce stable diagnostics, not process crashes or unbounded allocation.

## Diagnostics

Stable reason-code families:

- `text.color.CPAL-malformed`;
- `text.color.COLR-malformed`;
- `text.color.COLRv1-paint-unsupported`;
- `text.color.COLRv1-cycle-detected`;
- `text.color.COLRv1-budget-exceeded`;
- `text.bitmap.PNG-decode-failed`;
- `text.bitmap.strike-unavailable`;
- `text.bitmap.payload-format-unsupported`;
- `text.SVG.document-malformed`;
- `text.SVG.feature-unsupported`;
- `text.SVG.external-resource-refused`;
- `text.SVG.budget-exceeded`;
- `text.emoji.sequence-unsupported`;
- `text.emoji.fallback-unavailable`;
- `text.emoji.color-glyph-unavailable`.

## Acceptance Criteria

- COLRv0 and COLRv1 support is proven by paint-operation-specific fixtures.
- PNG bitmap glyph support is proven separately for CBDT/CBLC and sbix.
- SVG glyph support is pure Kotlin, glyph-scoped, bounded, and fixture-backed.
- Emoji sequence shaping and color glyph dispatch are visible in route dumps.
- Unsupported color glyph behavior diagnoses precisely and never uses native
  fallback engines.
