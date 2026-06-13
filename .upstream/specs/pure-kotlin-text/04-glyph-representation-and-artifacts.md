# Glyph Representation And Artifacts

Status: Draft
Date: 2026-06-13

## Purpose

Define how shaped glyph runs become renderable glyph representations and typed
artifacts. This spec owns outline plans, A8 masks, SDF masks, atlas planning,
strike keys, cache identity, invalidation, and CPU-prepared GPU artifact
contracts.

Color font table behavior is detailed in
`05-color-fonts-bitmap-svg-emoji.md`. GPU renderer integration is detailed in
`06-gpu-renderer-handoff.md`.

## Representation Routes

Complete target routes:

| Route | Target behavior |
|---|---|
| `text.glyph.outline` | Glyph outline is rendered through path/shape renderer logic. |
| `text.glyph.color.COLR` | COLR/CPAL glyph plan with ordered paint graph. |
| `text.glyph.bitmap.PNG` | PNG bitmap glyph selected from CBDT/CBLC or sbix. |
| `text.glyph.SVG` | SVG-in-OpenType glyph rendered by a pure Kotlin glyph-scoped SVG renderer. |
| `text.glyph.mask.A8` | CPU-generated grayscale glyph mask placed in `GlyphAtlasArtifact`. |
| `text.glyph.mask.SDF` | CPU-generated SDF mask placed in `SDFGlyphAtlasArtifact`. |
| `text.glyph.unsupported` | Stable refusal with reason and fallback policy. |

LCD subpixel masks are outside the complete Kanvas target. They remain
`FutureResearch` until a separate accepted target defines pixel geometry,
gamma, color-fringe, transform, layer, and surface policies.

## Glyph Representation Selection

Selection inputs:

- glyph ID and source text cluster;
- typeface identity;
- requested text size;
- transform classification;
- paint/material needs;
- color glyph availability;
- emoji sequence facts;
- atlas budget;
- SDF eligibility;
- target GPU capabilities supplied by handoff planning;
- paragraph/style preferences.

Selection order is policy-driven and dumpable. A typical final order is:

1. explicit color glyph route when style and font request color glyphs;
2. SDF route when text size/transform/use case is SDF-eligible;
3. A8 atlas route for normal filled text;
4. outline route for path effects, precise vector needs, or small fixture
   evidence;
5. stable refusal when no route is valid.

The exact policy can vary by style, but the route dump must explain it.

## Glyph Strike Key

`GlyphStrikeKey` includes:

- `TypefaceID`;
- glyph ID;
- text size;
- variation coordinates;
- palette identity;
- representation route;
- mask format;
- transform bucket;
- subpixel bucket;
- edging/antialiasing mode;
- SDF spread and resolution when applicable;
- SVG/COLR/bitmap renderer version when applicable;
- Unicode data version when cluster or emoji shaping affects output.

Keys must be deterministic and stable across process runs for deterministic
font sources.

## A8 Masks

The A8 route produces grayscale coverage masks:

- one channel, 8-bit coverage;
- deterministic rasterization from Kanvas outlines or glyph-specific color
  decisions that reduce to monochrome;
- bounds and origin in glyph strike space;
- atlas placement with padding policy;
- upload bytes and row stride facts;
- CPU oracle hash.

A8 masks are `CPUPreparedGPU` artifacts when consumed by the GPU renderer.

## SDF Masks

The SDF route supports scalable text where eligible.

The target must define:

- source geometry: outline or accepted bitmap alpha source;
- SDF resolution;
- spread radius;
- padding;
- inside/outside distance convention;
- quantization and normalization;
- min/max text size policy;
- transform eligibility;
- CPU oracle generation;
- GPU sampling requirements;
- fallback to A8 or outline when SDF is unsafe.

SDF output must be deterministic for a fixed `GlyphStrikeKey`.

## Atlas Artifacts

`GlyphAtlasArtifact` and `SDFGlyphAtlasArtifact` include:

- artifact type;
- artifact key preimage and compact hash;
- atlas generation;
- texture format expectation;
- dimensions;
- row stride;
- entry list;
- glyph key to atlas rectangle mapping;
- source mask hashes;
- upload byte hash;
- memory budget class;
- lifetime class;
- invalidation token;
- diagnostics.

Atlas artifacts do not imply live GPU texture ownership. The GPU renderer owns
resource creation, binding, and submission after consuming upload plans.

## Cache And Invalidation

The glyph cache owns:

- glyph mask/SDF generation;
- atlas packing;
- eviction policy;
- stale generation detection;
- font source invalidation;
- variation and palette invalidation;
- SVG/COLR/PNG renderer version invalidation;
- budget accounting;
- upload scheduling facts.

The GPU renderer may cache GPU resources derived from artifacts, but it must
respect artifact generation and invalidation tokens.

## Outline Plans

`OutlineGlyphPlan` includes:

- glyph path or immutable path handle;
- transform from glyph space to text-local space;
- fill rule;
- bounds;
- source glyph key;
- diagnostics for missing or malformed outlines.

The GPU renderer may route outline plans through GPU-native path rendering or a
prepared coverage artifact according to its own route policy.

## Refusal And Fallback Policy

Fallback is explicit:

- missing glyph -> `.notdef` plus diagnostic;
- unsupported color glyph -> outline fallback only if a valid outline exists
  and style permits monochrome fallback;
- unsupported SDF transform -> A8 or outline fallback when valid;
- atlas capacity exceeded -> route refusal or split plan, never silent drop;
- stale atlas generation -> refusal until regenerated;
- unsupported LCD request -> future-research diagnostic.

## Diagnostics

Stable reason-code families:

- `text.glyph.missing`;
- `text.glyph.outline-unavailable`;
- `text.glyph.route-unsupported`;
- `text.glyph.A8-generation-failed`;
- `text.glyph.SDF-generation-failed`;
- `text.glyph.SDF-transform-unsupported`;
- `text.glyph.atlas-capacity-exceeded`;
- `text.glyph.atlas-generation-stale`;
- `text.glyph.cache-key-nondeterministic`;
- `text.glyph.LCD-future-research`;
- `text.glyph.artifact-budget-exceeded`.

## Acceptance Criteria

- Every glyph route has a value-object plan and stable diagnostics.
- A8 and SDF artifacts are valid `CPUPreparedGPU` artifact types.
- Glyph keys include all rendering-affecting facts.
- Atlas artifacts can be consumed by GPU renderer without font parsing or
  shaping.
- LCD is not accidentally claimed by A8 or SDF support.
