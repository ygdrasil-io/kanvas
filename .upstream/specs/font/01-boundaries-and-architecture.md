# Font Boundaries And Architecture

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Define what the font layer owns and how it connects to Kanvas rendering. This
spec keeps font architecture separate from generic rendering specs while still
making the font-rendering route reviewable.

## Audiences

| Audience | Needs |
|---|---|
| Font implementer | Know which internal modules own font parsing, shaping, glyph production, masks, and fallback diagnostics. |
| Rendering engineer | Know how glyph output enters geometry, paint, CPU raster, and WebGPU without duplicating font logic. |
| Reviewer | Verify that a font support claim has font source, shaping route, glyph representation, CPU/GPU result, and diagnostics. |
| PM / release owner | Understand which font rows are supported, fixture-gated, dependency-gated, or expected unsupported. |

## Ownership Boundary

The font layer owns:

- font file discovery and loading;
- SFNT/OpenType parsing;
- `SkFont`, `SkTypeface`, `SkFontMgr`, `SkFontStyleSet`, and related API
  fidelity;
- glyph id mapping;
- glyph metrics, bounds, advances, and text-to-glyph helpers;
- glyph outline production;
- font variation and palette arguments;
- explicit shaping through `SkShaper`;
- glyph-run metadata, clusters, directions, and shaping diagnostics;
- glyph representation selection: outline, layered color path, bitmap metadata,
  SVG metadata, A8/LCD/SDF mask;
- glyph mask and atlas ownership;
- font-specific fallback/refusal reason codes;
- font GM classification and evidence links.

The font layer does not own:

- generic path tessellation and coverage algorithms;
- generic paint/shader/color-filter/blend semantics;
- image/codec decoding outside embedded glyph image payloads;
- general SVG rendering outside SVG-in-OpenType glyph scope;
- PM dashboard UI;
- release scoring policy;
- platform-native font engines.

## Module Boundary

| Module / path | Font responsibility |
|---|---|
| `kanvas/src/main/kotlin/org/skia/foundation/` | Compatibility API surface kept in `:kanvas`. |
| `font/src/main/kotlin/org/graphiks/kanvas/font/` | Pure Kotlin font core and public Kanvas font contracts. |
| `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/` | Internal pure Kotlin OpenType/SFNT backend. |
| `reports/font/fixtures/fonts/` | Bundled deterministic font fixtures. |
| `integration-tests/skia/` | Upstream GM ports, fixtures, reference comparisons, and disabled/gated font rows. |
| `gpu-renderer/` | WebGPU text smoke, glyph outline rendering through paths, and future adapter-backed glyph-mask support/refusal. |

## Target Data Flow

```text
SkFontMgr / font data
  -> SkTypeface
  -> SkFont
  -> simple drawString path or explicit SkShaper
  -> GlyphRunDescriptor
  -> GlyphRepresentation
       font.glyph.outline-path
       font.glyph.colr-layer-paths
       font.glyph.bitmap-strike
       font.glyph.svg-document
       font.glyph.alpha-mask
       font.glyph.sdf-mask
       font.glyph.lcd-mask
       font.glyph.unsupported
  -> rendering route
       path pipeline
       glyph mask coverage handoff
       explicit refusal
  -> evidence
```

## Target Contracts

### FontSource

Font sources should be explicit:

```text
BundledLiberation
SystemOpenType
UserProvidedData
UserProvidedStream
GeneratedFixture
UnsupportedNativeBridge
```

Each source must expose:

- stable identifier;
- family/style metadata;
- raw table availability;
- deterministic or host-dependent classification;
- fixture provenance when generated;
- diagnostics for skipped files or unsupported tables.

### TextRunInput

A text run entering the font layer should carry:

- text or glyph ids;
- `SkFont`;
- paint-affecting text flags only when they affect glyph representation;
- requested shaping mode: simple, portable shaper, or unsupported;
- optional language/script/fallback hints;
- transform facts relevant to SDF or mask strategy;
- expected evidence scope.

### GlyphRunDescriptor

The target glyph run descriptor should carry:

- run id;
- font source id;
- typeface identity;
- glyph ids;
- clusters;
- text-local positions;
- direction;
- script/language metadata when known;
- shaping route;
- fallback diagnostics;
- representation route.

### GlyphRepresentation

Glyph representation must be explicit and use the canonical route ids:

| Route | Meaning |
|---|---|
| `font.glyph.outline-path` | Glyphs render as ordinary paths through the existing paint/coverage path. |
| `font.glyph.colr-layer-paths` | Color glyph renders as ordered path layers with palette-resolved colors. |
| `font.glyph.bitmap-strike` | Bitmap strike payload selected and decoded internally. |
| `font.glyph.svg-document` | SVG table metadata exists but rendering is refused unless an internal renderer lands. |
| `font.glyph.alpha-mask` | Text infrastructure produced an A8 mask reference for coverage consumption. |
| `font.glyph.sdf-mask` | Text infrastructure produced an SDF mask reference for coverage consumption. |
| `font.glyph.lcd-mask` | Text infrastructure produced an LCD mask reference for coverage consumption. |
| `font.glyph.unsupported` | Stable reason explains why the representation cannot render. |

These routes are not fallback reason codes. `coverage.*` diagnostics already
exist for geometry/coverage handoff failures. `font.*` reason codes proposed by
this pack remain proposed until implementation evidence proves they are emitted.

## Fallback Policy

Fallbacks must be stable and visible:

- missing glyph -> `.notdef` and diagnostic;
- missing fallback family -> diagnostic, no hidden system call;
- unsupported shaping feature -> diagnostic, not silent approximation;
- unsupported glyph representation -> monochrome outline fallback only when the
  font has a valid outline and the fallback is documented;
- unsupported WebGPU glyph mask -> `coverage.alpha-mask-unsupported`;
- missing glyph mask dependency -> `coverage.glyph-mask-dependency-unavailable`.

## Internal Implementation Principle

The target is internal-first:

- implement parsers and fixtures in Kotlin when the scope is bounded;
- keep generated fixtures small and deterministic;
- fail closed on malformed optional tables;
- preserve usable monochrome outline rendering when optional color tables fail;
- isolate host-dependent behavior behind explicit diagnostics;
- keep native/external engine parity as non-goals for this pack.

## Evidence Requirements

A font support claim needs:

- font source and fixture provenance;
- CPU artifact or CPU oracle;
- GPU artifact or explicit GPU refusal when GPU is in scope;
- diff/stat artifact when compared visually;
- glyph route diagnostic;
- shaping route diagnostic for non-simple text;
- fallback reason for any expected unsupported behavior;
- test command and owning test file.

## Acceptance Criteria

- Font work can be planned without reopening generic rendering specs.
- Font support labels distinguish simple outline text, shaped text, color font,
  emoji, SDF, and glyph-mask routes.
- WebGPU support claims identify whether text used path rendering or a dedicated
  glyph/mask route.
- Dependency-gated rows remain visible until an internal delivery closes them.
- No font spec requires external libraries to make progress.
