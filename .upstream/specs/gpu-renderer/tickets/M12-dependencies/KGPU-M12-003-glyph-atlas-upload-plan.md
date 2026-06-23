---
id: KGPU-M12-003
title: "Add GPU glyph atlas upload plan with texture region packing"
status: done
milestone: M12
priority: P0
owner_area: text-resources
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-001, KGPU-M12-002]
legacy_gate: null
---

# KGPU-M12-003 - Add GPU glyph atlas upload plan with texture region packing

## PM Note

Le plan d'upload d'atlas glyphe est le contrat entre le rasterizer CPU et la texture GPU. Sans packing validé, l'atlas gaspillera la mémoire texture ou les glyphes seront tronqués.

## Problem

A8 glyph bitmaps must be packed into a GPU texture atlas with region management before text draw calls can bind and sample the atlas. Without a validated upload plan, glyph regions may collide or overflow.

## Scope

- Add atlas region allocator with rectangle packing algorithm
- Add GPU glyph atlas upload plan generation from A8 bitmaps
- Add atlas page management with multi-texture support
- Produce atlas region dump for validation

## Non-Goals

- No SDF atlas generation
- No dynamic atlas resizing mid-frame

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_DRAW_ATLAS_PLOTS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-atlas-plots) - source src/gpu/graphite/AtlasProvider.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class GlyphAtlasUploadPlan(val regions: Map<StrikeKey, AtlasRegion>, val uploadCommands: List<UploadCommand>)
```

## Acceptance Criteria

- [ ] Atlas packer fits Latin glyph set without overflow
- [ ] Upload plan specifies correct texture coordinates per glyph
- [ ] Region allocator rejects oversized glyphs with stable diagnostic

## Required Evidence

- Atlas region allocation dump for Latin glyph set
- Upload plan correctness transcript
- Atlas texture mockup visualization

## Fallback / Refusal Behavior

Atlas overflow emits stable diagnostic and refuses additional glyph uploads for current frame.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.text.glyph-atlas-upload`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GlyphAtlas*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:text-resources`
