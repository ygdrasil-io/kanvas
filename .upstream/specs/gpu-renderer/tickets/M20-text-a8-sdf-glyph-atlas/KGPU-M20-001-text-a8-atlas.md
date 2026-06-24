---
id: KGPU-M20-001
title: "Add text A8 atlas execution: glyph mask upload -> atlas texture -> WGSL sample"
status: done
milestone: M20
priority: P0
owner_area: text-rendering
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-003, KGPU-M12-004]
legacy_gate: null
---

# KGPU-M20-001 - Add text A8 atlas execution: glyph mask upload -> atlas texture -> WGSL sample

## PM Note

L'exécution de l'atlas A8 est le coeur du rendu texte GPU. Chaque glyphe est échantillonné depuis l'atlas et le masque A8 contrôle la couverture.

## Problem

A8 glyph atlas textures must be uploaded to GPU and sampled in WGSL to render glyph masks. Without this, the text handoff from M12 remains a dead letter with no visual output.

## Scope

- Add A8 glyph atlas texture upload and binding
- Add WGSL glyph mask sampling from atlas texture
- Add per-glyph instance data (atlas UV rect, screen position)
- Produce A8 text rendering fixture dumps

## Non-Goals

- Latin glyphs only
- No color glyphs or emoji
- No subpixel LCD rendering

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_TEXT_ATLAS_CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source src/gpu/graphite/text/TextAtlasManager.cpp:42; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct GlyphInstance { atlasRect: vec4f, screenPos: vec2f, glyphSize: vec2f }\n@group(X) @binding(Y) var glyphAtlas: texture_2d<f32>;
```

## Acceptance Criteria

- [ ] A8 glyph atlas texture is correctly uploaded and bound to GPU
- [ ] WGSL samples glyph mask from atlas and produces correct coverage
- [ ] Multiple glyphs from same atlas page render correctly

## Required Evidence

- A8 text GPU rendering fixture dump for Latin glyph set
- Glyph atlas texture visualization
- Per-glyph instance data validation

## Fallback / Refusal Behavior

Atlas texture upload or sampling failure emits stable diagnostic; text route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.text-a8-atlas`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextA8*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- TextA8AtlasExecutor, SDFGenerator, GPUDrawTextRunExecutor, WGSL snippets
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M20`
- `area:text-rendering`
