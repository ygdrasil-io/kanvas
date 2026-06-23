---
id: KGPU-M12-004
title: "Wire GPU renderer text handoff: GlyphRunDescriptor -> DrawTextRun accepted"
status: done
milestone: M12
priority: P0
owner_area: text-recording
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-001, KGPU-M12-002, KGPU-M12-003]
legacy_gate: null
---

# KGPU-M12-004 - Wire GPU renderer text handoff: GlyphRunDescriptor -> DrawTextRun accepted

## PM Note

Ce ticket ferme la boucle texte: du parseur SFNT au DrawTextRun accepté par le GPU renderer. Sans ce handoff, les tickets M12A sont des artefacts isolés.

## Problem

The text pipeline components (parser, scaler, rasterizer, atlas plan) must be wired into the GPU renderer's draw recording so that GlyphRunDescriptor payloads are accepted by the DrawTextRun path.

## Scope

- Wire GlyphRunDescriptor through GPU renderer recording pipeline
- Add DrawTextRun acceptance with atlas binding resolution
- Add text-specific diagnostics for unsupported glyph runs
- Produce recording trace for Latin text scene

## Non-Goals

- No actual GPU text rendering (gated by M20)
- No text shaper integration at this stage

## Spec Sources

- .upstream/specs/pure-kotlin-text/
- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawGlyphs; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GlyphRunDescriptor(val font: Font, val glyphs: List<GlyphRun>, val atlasPlan: GlyphAtlasUploadPlan)\nfun DrawList.recordDrawTextRun(descriptor: GlyphRunDescriptor): RecordingResult
```

## Acceptance Criteria

- [ ] GlyphRunDescriptor flows from text stack to GPU renderer recording
- [ ] DrawTextRun recording produces valid pipeline keys and atlas bindings
- [ ] Unsupported glyph runs emit stable refusal diagnostics

## Required Evidence

- GlyphRunDescriptor -> DrawTextRun recording trace
- Diagnostic output for unsupported glyph runs

## Fallback / Refusal Behavior

Text handoff failures emit stable diagnostics; GPU text route remains disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.text.handoff-wired`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextHandoff*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:text-recording`
