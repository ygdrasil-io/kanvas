# Color Fonts Emoji And Fixtures

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Define the target policy for color fonts, emoji, and generated font fixtures.
This spec owns COLR/CPAL, bitmap color glyphs, SVG-in-OpenType glyph metadata,
emoji table dispatch, palette behavior, fixture strategy, and refusal policy.

It does not own general image codec breadth or general SVG rendering outside
font glyph payloads.

## Current State

The OpenType backend already covers useful color-font pieces:

- CPAL palette metadata;
- COLRv0 base glyph and layer records;
- COLRv0 layered outline rendering through `SkCanvas.drawString`;
- palette selection and palette overrides through `SkFontArguments.Palette`;
- selected COLRv1 paint graph metadata / internal slices;
- CBDT/CBLC bitmap strike metadata;
- sbix bitmap strike metadata;
- SVG-in-OpenType document metadata;
- generated fixture strategy for small table-specific cases.

The active GM classification still keeps broad emoji and several color font
rows gated. A parser or metadata slice is not the same as full rendering support.

All `font.*` reason codes in this file are proposed until tests or reports
prove they are emitted by implementation evidence.

## COLRv0

COLRv0 support may be claimed only for:

- palette-resolved layered outline glyphs;
- deterministic generated or bundled fixtures;
- CPU/reference comparisons;
- GPU evidence if the support claim includes WebGPU.

Palette overrides must be part of the typeface clone identity and evidence
route.

## COLRv1

COLRv1 must remain split by paint operation. Do not claim "COLRv1 support" from
metadata parsing alone.

Target operation slices:

| Slice | Required evidence |
|---|---|
| solid paint | glyph path, palette, CPU image, route dump |
| transform / translate | transform stack, bounds, CPU image, route dump |
| gradients | gradient payload, color space policy, CPU/GPU artifacts |
| composites | blend/composite policy, layer ordering, artifacts |
| clip boxes | clipping behavior, fallback policy, artifacts |
| reusable paint graph | cycle detection, recursion limit, artifacts |

Unsupported operations should fall back to monochrome outline only when a valid
outline exists and the fallback is documented. Otherwise they should refuse with
a stable reason.

Recommended reason codes:

| Code | Use |
|---|---|
| `font.colrv1-paint-unsupported` | Paint operation is outside the internal subset. |
| `font.colrv1-cycle-detected` | Paint graph recursion/cycle is unsafe. |
| `font.colrv1-gradient-unsupported` | Gradient paint cannot be rendered by the current font path. |
| `font.colrv1-composite-unsupported` | Composite mode is outside the accepted subset. |

## Bitmap Color Glyphs

Bitmap color glyph support is table-specific:

| Format | Current target rule |
|---|---|
| CBDT/CBLC PNG | Parse metadata first; render only after internal PNG decode, strike selection, placement, and artifacts. |
| sbix PNG | Keep separate from CBDT/CBLC because origin and strike layout differ. |
| other bitmap payloads | Refuse or fallback until an internal decoder and fixtures exist. |

Rendering a bitmap color glyph requires:

- selected strike policy;
- requested size matching or scaling policy;
- origin placement;
- premul/alpha policy;
- internal decode path;
- CPU and GPU evidence if GPU support is claimed;
- fallback reason for missing decode or missing strike.

Recommended reason codes:

| Code | Use |
|---|---|
| `font.bitmap-strike-unavailable` | No usable strike for requested size/glyph. |
| `font.bitmap-glyph-decode-unavailable` | Embedded bitmap payload cannot be decoded internally. |
| `font.bitmap-glyph-format-unsupported` | Payload format is outside the accepted internal codec subset. |

## SVG-In-OpenType

SVG-in-OpenType currently stays metadata-only unless an internal SVG glyph
renderer is accepted.

Rules:

- parse document records defensively;
- expose metadata for diagnostics and tests;
- preserve monochrome outline fallback when available;
- do not add a general SVG library or renderer as a hidden dependency;
- refuse unsupported SVG glyph rendering with stable diagnostics.

Recommended reason code:

```text
font.svg-glyph-renderer-unavailable
```

## Emoji

Emoji support must be explicit. It can involve:

- color glyph table dispatch;
- emoji fallback family selection;
- emoji ZWJ sequence shaping;
- skin tone and variation selector handling;
- bitmap, SVG, or COLR color glyph representation;
- fallback to monochrome outline when valid and documented.

Current `STUB.EMOJI_TABLES` rows must remain gated until internal table dispatch
and rendering evidence land. Do not clear them by routing through native emoji
renderers or platform APIs.

Recommended reason codes:

| Code | Use |
|---|---|
| `font.emoji-table-dispatch-unavailable` | Emoji table selection/rendering is not implemented. |
| `font.emoji-sequence-shaping-unsupported` | ZWJ or modifier sequence requires unsupported shaping. |
| `font.emoji-fallback-unavailable` | No internal fallback typeface covers the emoji code point/sequence. |

## Fixture Strategy

Font fixtures should be:

- small;
- generated when practical;
- table-specific;
- committed with provenance;
- deterministic across platforms;
- free of native toolchain requirements at test runtime.

Preferred fixture approach:

1. Start from a bundled Liberation or tiny test font when license-compatible.
2. Replace or append the exact optional table under test.
3. Keep malformed-table fixtures focused and minimal.
4. Keep color glyph and emoji fixtures split by table family.
5. Do not add large external binary fonts just to clear a GM row.

## GM Rows

Rows currently gated by emoji or color font work include:

- `coloremoji_blendmodes`;
- `scaledemoji`;
- `scaledemoji_rendering`.

Rows with related but distinct font blockers include:

- `fontations`;
- `fontations_ft_compare`;
- `pdf_never_embed`;
- `typeface` fixture/formats.

Each row should keep its blocker wording precise. Use:

- `emoji table dispatch missing`;
- `COLRv1 renderer subset missing`;
- `bitmap strike decode missing`;
- `SVG glyph renderer unavailable`;
- `fixture / format blocker`;
- `native Fontations/FreeType parity out of scope`.

## Acceptance Criteria

- Color glyph support is split by representation and table family.
- Emoji support is not implied by ordinary text support.
- Bitmap/SVG glyph formats do not pull in external libraries.
- Generated fixtures prove table parsing and rendering slices independently.
- Gated GM rows keep stable, actionable blocker names.
