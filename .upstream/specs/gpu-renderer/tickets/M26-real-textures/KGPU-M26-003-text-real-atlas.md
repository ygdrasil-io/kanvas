---
id: KGPU-M26-003
title: "Wire real A8 glyph atlas into Text offscreen renderer"
status: done
milestone: M26
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-002, KGPU-M12-003]
legacy_gate: null
---

# KGPU-M26-003 - Wire real A8 glyph atlas into Text offscreen renderer

## PM Note

Le texte echantillonne encore un atlas procedural. Ce ticket construit un vrai
atlas A8 a partir de Liberation Sans pour que le PM voie de vrais glyphes
rasterises.

## Problem

M25-002 wired the text executors, but the offscreen renderer still samples a
procedural glyph atlas. M12's `GlyphAtlasUploadPlanner` and the Liberation Sans
font are not used to build the sampled atlas texture, so glyphs are procedural
shapes rather than real rasterized A8 coverage. Support cannot be promoted while
procedural glyph data is sampled.

## Scope

- Use M12's `GlyphAtlasUploadPlanner` with the real Liberation Sans font to build the atlas texture
- Rasterize real A8 glyph coverage into the atlas and upload it to the GPU
- Bind the real atlas texture for `TextA8AtlasExecutor` sampling (KGPU-M25-002)
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No text shaping beyond what M20-004 delivers
- No SDF data changes beyond the A8 atlas scope of this ticket
- No scene PNG catalog reshuffle (scene evidence stays in the text scenes)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M12-dependencies/README.md`
- `.upstream/specs/gpu-renderer/tickets/M20-text-a8-sdf-glyph-atlas/README.md`
- `gpu-renderer/src/main/kotlin/.../text/GlyphAtlasUploadPlanner.kt`
- `gpu-renderer/src/main/kotlin/.../execution/TextA8AtlasExecutor.kt`

## Design Sketch

```kotlin
val plan = GlyphAtlasUploadPlanner.plan(LiberationSans, glyphs) // real A8 coverage
val atlas = ImageUploadMaterializer.materialize(plan.atlasBytes)
bindGlyphAtlas(atlas) // sampled by TextA8AtlasExecutor
```

## Acceptance Criteria

- [ ] The procedural glyph atlas is removed from the text path
- [ ] `GlyphAtlasUploadPlanner` builds the atlas from real Liberation Sans glyphs
- [ ] The real A8 atlas texture binds for `TextA8AtlasExecutor`
- [ ] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Atlas plan + upload transcript (glyph count, atlas dimensions, font = Liberation Sans)
- Offscreen render output showing real A8 glyphs
- Coverage diff against the CPU reference for the glyph atlas scene

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. Missing font data emits a stable diagnostic; no
silent fallback to a procedural atlas.

## Dashboard Impact

- Expected row: `gpu-renderer.m26.text-real-atlas`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=glyph-atlas-strip
```

## Status Notes

- `done`: M26-003 implemented: TEXT_ATLAS_WRAPPER removed, real A8 glyph atlas built from Liberation Sans (GlyphAtlasTextureBuilder via M12 A8Rasterizer+GlyphAtlasUploadPlanner), uploaded as R8Unorm texture, sampled by TextAtlasA8Wgsl fragment shader.

## Linear Labels

- `gpu-renderer`
- `milestone:M26`
- `area:execution-renderer`
