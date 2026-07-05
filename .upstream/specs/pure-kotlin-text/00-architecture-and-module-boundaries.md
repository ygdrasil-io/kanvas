# Architecture And Module Boundaries

Status: Draft
Date: 2026-06-13

## Purpose

Define the architecture of the complete pure Kotlin text stack and its
boundaries with `:kanvas` compatibility facade and `:gpu-renderer`.

The target separates font/text intelligence from GPU execution:

- font/text modules resolve fonts, shape text, lay out paragraphs, choose glyph
  representations, and prepare glyph artifacts;
- `:gpu-renderer` records and executes GPU work using typed text artifacts;
- `:kanvas` compatibility facade adapts Skia-like APIs onto the pure Kotlin core.

## Target Modules

The exact Gradle module names are chosen during implementation planning, but
the target responsibilities are fixed:

| Target area | Package root | Responsibility |
|---|---|---|
| Font core | `org.graphiks.kanvas.font` | Font sources, font identity, SFNT/OpenType table access, collections, fallback catalog, diagnostics. |
| Font scaler | `org.graphiks.kanvas.font.scaler` | TrueType `glyf`, CFF, CFF2, metrics, bounds, variations, outlines, scaler diagnostics. |
| Shaping | `org.graphiks.kanvas.text.shaping` | Unicode segmentation, bidi runs, script runs, GSUB/GPOS/GDEF, clusters, fallback runs. |
| Paragraph | `org.graphiks.kanvas.text.paragraph` | Paragraph builder, rich text runs, line breaking, alignment, truncation, metrics, selection, hit testing. |
| Glyph artifacts | `org.graphiks.kanvas.glyph` | Glyph representation selection, A8 masks, SDF masks, color glyph plans, atlas planning, cache keys. |
| GPU text handoff | `org.graphiks.kanvas.glyph.gpu` or equivalent | Typed artifacts consumed by `:gpu-renderer`, upload plans, generation tokens, budget facts. |
| Skia facade | `org.skia.foundation` in `:kanvas` compatibility facade | Compatibility surface for `SkFontMgr`, `SkTypeface`, `SkFont`, `SkShaper`, `SkTextBlob`, and paragraph-compatible APIs. |

The implementation may group several target areas into one Gradle module at
first, but package boundaries and dependency direction must still match this
table.

## Dependency Direction

Allowed dependencies:

```text
:kanvas compatibility facade
  -> pure Kotlin font/text/glyph modules

pure Kotlin glyph artifact modules
  -> pure Kotlin font/text/scaler modules

:gpu-renderer
  -> glyph GPU handoff value objects

validation/report modules
  -> pure Kotlin font/text/glyph modules
  -> optional external comparison tools only for non-normative drift reports
```

Forbidden dependencies:

- pure Kotlin font/text modules must not depend on `:gpu-renderer`;
- `:gpu-renderer` must not depend on `SkFont`, `SkTypeface`, `SkPaint`,
  `SkTextBlob`, `SkPath`, or other Skia-like mutable API types;
- product code must not require HarfBuzz, FreeType, Fontations, AWT, JNI,
  CoreText, DirectWrite, fontconfig, or platform shapers;
- glyph cache and atlas lifetime must not be hidden inside generic geometry or
  coverage code.

## Core Data Contracts

The target uses value objects at subsystem boundaries. These names are target
concepts; implementation may refine exact class names while preserving the
contracts.

| Contract | Owner | Meaning |
|---|---|---|
| `FontSourceID` | Font core | Stable `kotlin.uuid.Uuid` identity for bundled, user, stream, file, generated, or system-scanned font data. |
| `TypefaceID` | Font core | Stable `kotlin.uuid.Uuid` identity for one face, collection index, variation position, palette, and source hash. |
| `ResolvedFontRun` | Font resolver | A text range mapped to a typeface, style, locale, script hint, and fallback reason. |
| `ShapedGlyphRun` | Shaping | Glyph IDs, clusters, positions, direction, script, language, features, and shaping diagnostics. |
| `TextLayoutResult` | Paragraph | Paragraph-level lines, runs, visual boxes, metrics, hit-test data, and glyph-run references. |
| `GlyphRunDescriptor` | Glyph artifacts | Renderer-neutral glyph run with font identity, glyphs, positions, representation needs, and route diagnostics. |
| `GlyphStrikeKey` | Glyph artifacts | Cache key for glyph size, transform bucket, representation, variation, palette, edging, and subpixel facts. |
| `GlyphArtifactPlan` | Glyph artifacts | Chosen representation plans for outlines, color glyphs, bitmap glyphs, SVG glyphs, A8 masks, or SDF masks. |
| `GlyphAtlasArtifact` | Glyph artifacts | CPU-prepared A8 atlas artifact consumed by GPU rendering. |
| `SDFGlyphAtlasArtifact` | Glyph artifacts | CPU-prepared SDF atlas artifact consumed by GPU rendering. |
| `TextRouteDiagnostics` | Shared | Serializable support/refusal facts for shaping, layout, glyph representation, cache, and GPU handoff. |

Every contract that can affect rendering must be serializable for tests and PM
evidence. Dumps must avoid nondeterministic object identity, unordered maps, and
host-specific paths unless paths are explicitly normalized.

Opaque subsystem identities use Kotlin 2.4 `kotlin.uuid.Uuid` values wrapped in
domain-specific value classes. Human-readable labels, fingerprints, table tags,
and diagnostic codes remain strings; they are not identity handles.

## Naming Rules

Public concept names use uppercase acronyms:

- `CPU`, `GPU`, `WGSL`;
- `SDF`, `SVG`, `PNG`;
- `CFF`, `CFF2`;
- `COLR`, `CPAL`, `GSUB`, `GPOS`, `GDEF`;
- `A8`, `RGBA`, `BGRA` where applicable.

Kotlin implementation details may use shorter local variable names, but public
types, spec terms, diagnostics, and serialized route fields should keep
acronym spelling stable.

## Ownership Boundary

The pure Kotlin text stack owns:

- font discovery and data loading;
- SFNT/OpenType parsing and table validation;
- font collections and face selection;
- typeface identity, variations, palettes, and metrics;
- glyph mapping, outlines, and scaler behavior;
- shaping, fallback runs, clusters, and text positions;
- paragraph layout, line metrics, hit testing, selection geometry, ellipsis,
  placeholders, and bidi paragraph layout;
- glyph representation selection;
- A8 and SDF glyph mask generation;
- glyph atlas keys, budget, lifetime, invalidation, and upload facts;
- color font, bitmap glyph, SVG glyph, and emoji dispatch;
- font/text-specific diagnostics and evidence.

The pure Kotlin text stack does not own:

- generic GPU recording, sorting, pass planning, pipeline keys, or submission;
- generic blend, layer, filter, image, path, or coverage semantics outside text
  artifact production;
- general SVG document rendering outside SVG-in-OpenType glyph payloads;
- general image codec breadth outside embedded PNG glyph payloads;
- native engine parity;
- GPU shader compilation or WGSL validation.

## Facade Policy

`SkCanvas.drawString` stays a simple text path:

```text
text -> code points -> cmap glyphs -> advances -> glyph paths or simple glyph artifact route
```

It must not silently enable complex shaping, multi-font fallback, paragraph
layout, emoji ZWJ shaping, or SVG glyph rendering unless a future accepted
compatibility spec explicitly changes that API behavior.

Complex text uses explicit APIs:

- `SkShaper` for shaped runs;
- `SkTextBlob` or glyph-run APIs for explicit glyph IDs and positions;
- paragraph API for line layout, rich text, and hit testing.

The Skia-like facade may preserve familiar names, but it must delegate to the
pure Kotlin core instead of duplicating font logic.

## Current Reusable Evidence

The current codebase already provides reusable prototypes:

- `OpenTypeTypeface` and `OpenTypeFontMgr` for bounded SFNT/TrueType parsing;
- `OpenTypeSystemFontMgr` for pure Kotlin system scanning and fallback policy;
- `SkShaper` as a deterministic explicit shaping boundary;
- `SkCpuGlyphCache` as an initial A8 glyph mask inventory and dump format;
- `SkWebGpuGlyphAtlas` as an initial upload-plan prototype;
- legacy gate, blocker, and baseline records carried forward by
  `09-migration-from-current-font-pack.md`.

These are not final APIs by themselves. The new target may reuse implementation
ideas, tests, fixtures, or diagnostics after their contracts are promoted.

## Acceptance Criteria

- Each subsystem has one owner and one boundary contract.
- `:gpu-renderer` can consume text without parsing fonts or shaping text.
- The `:kanvas` compatibility facade can expose compatibility APIs without becoming the core text
  implementation.
- Native/external font engines remain outside normative product behavior.
- Serialized diagnostics can explain every text route, fallback, and refusal.
