---
id: KGPU-M20-003
title: "Add DrawTextRun execution: subrun batch -> atlas bind -> draw fullscreen pass"
status: done
milestone: M20
priority: P0
owner_area: text-rendering
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M20-001, KGPU-M20-002]
legacy_gate: null
---

# KGPU-M20-003 - Add DrawTextRun execution: subrun batch -> atlas bind -> draw fullscreen pass

## PM Note

DrawTextRun est le draw call final pour le texte. Le batch de subruns optimise les changements d'état et réduit les draw calls.

## Problem

Text rendering needs a DrawTextRun execution path that batches glyph subruns, binds the appropriate atlas texture, and renders via a fullscreen pass with per-glyph instance data. Without this, individual glyph draws would be prohibitively expensive.

## Scope

- Add DrawTextRun execution with glyph subrun batching
- Add atlas texture binding per subrun batch
- Add fullscreen pass with per-glyph instance vertex data
- Produce DrawTextRun rendering fixture dumps

## Non-Goals

- Latin glyphs only
- No text transform beyond 2D translation

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawGlyphs; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class DrawTextRun(val subruns: List<GlyphSubrun>) {{\n  fun record(drawList: DrawList);\n}}\n// Each subrun: atlas page + glyph range -> instance batch
```

## Acceptance Criteria

- [ ] DrawTextRun batches glyphs by atlas page correctly
- [ ] Atlas texture binding changes only on page boundary
- [ ] Fullscreen pass renders all glyphs in batch with correct positions

## Required Evidence

- DrawTextRun GPU rendering fixture dump for Latin text scene
- Subrun batching trace showing atlas page groupings
- Draw call count telemetry for text scenes

## Fallback / Refusal Behavior

DrawTextRun execution failure emits stable diagnostic; text route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.draw-text-run`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DrawTextRun*'
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
