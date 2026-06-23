---
id: KGPU-M20-002
title: "Add SDF glyph atlas: signed distance field generation + WGSL smoothstep"
status: proposed
milestone: M20
priority: P0
owner_area: text-rendering
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-003]
legacy_gate: null
---

# KGPU-M20-002 - Add SDF glyph atlas: signed distance field generation + WGSL smoothstep

## PM Note

Le SDF (Signed Distance Field) permet un rendu texte de haute qualité à toutes les échelles. Un seul atlas SDF remplace plusieurs tailles d'atlas A8.

## Problem

Signed Distance Field glyph atlases enable scale-independent high-quality text rendering. SDF generation and WGSL smoothstep sampling are needed for crisp text at arbitrary sizes.

## Scope

- Add SDF glyph generation from A8 masks
- Add SDF atlas texture upload with linear filtering
- Add WGSL smoothstep for SDF edge rendering
- Produce SDF text rendering fixture dumps

## Non-Goals

- Latin glyphs only
- No multi-channel SDF
- No color SDF glyphs

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_SDF_TEXT_STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-sdf-text-step) - source src/gpu/graphite/text/SDFTextRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
fn sdfEdge(dist: f32, threshold: f32, smoothing: f32) -> f32 {{\n  return smoothstep(threshold - smoothing, threshold + smoothing, dist);\n}}
```

## Acceptance Criteria

- [ ] SDF glyph generation produces valid distance fields from A8 masks
- [ ] WGSL smoothstep produces crisp glyph edges at multiple scales
- [ ] SDF text renders correctly at 0.5x to 4x atlas resolution

## Required Evidence

- SDF text GPU rendering fixture dumps at multiple scales
- SDF atlas texture visualization (distance field)
- SDF edge quality comparison vs A8 at same size

## Fallback / Refusal Behavior

SDF generation or sampling failure emits stable diagnostic; SDF text route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.text-sdf-atlas`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextSDF*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M20`
- `area:text-rendering`
