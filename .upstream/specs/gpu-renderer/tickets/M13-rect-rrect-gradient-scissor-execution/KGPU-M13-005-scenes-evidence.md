---
id: KGPU-M13-005
title: "Add gpu-renderer-scenes evidence: rrect-card, gradient-swatch, clipped-stack"
status: review
milestone: M13
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M13-001, KGPU-M13-002, KGPU-M13-003]
legacy_gate: null
---

# KGPU-M13-005 - Add gpu-renderer-scenes evidence: rrect-card, gradient-swatch, clipped-stack

## PM Note

Les scènes de démonstration sont la preuve visuelle pour le PM. Sans rrect-card, gradient-swatch et clipped-stack, l'activation M13 reste abstraite.

## Problem

M13 route activation needs scene evidence that exercises the three routes together in realistic compositions. Without scene dumps, PM cannot visually verify activation quality.

## Scope

- Add rrect-card scene: rounded card with gradient fill and scissor clip
- Add gradient-swatch scene: linear gradient swatches with all tile modes
- Add clipped-stack scene: overlapping rrects with scissor clips
- Produce scene fixture dumps for PM evidence bundle

## Non-Goals

- No interactive or animated scenes
- No performance benchmarking in scenes

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_GRADIENT_STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source gm/gradients.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class M13Scenes { fun rrectCard(): Scene; fun gradientSwatch(): Scene; fun clippedStack(): Scene }
```

## Acceptance Criteria

- [ ] rrect-card scene renders correctly with GPU routes active
- [ ] gradient-swatch scene shows all linear gradient tile modes
- [ ] clipped-stack scene demonstrates correct scissor clipping interaction

## Required Evidence

- rrect-card GPU rendering fixture dump
- gradient-swatch GPU rendering fixture dump
- clipped-stack GPU rendering fixture dump

## Fallback / Refusal Behavior

Scene rendering failures emit stable diagnostic; activation decision not blocked by scene issues.

## Dashboard Impact

- Expected row: `gpu-renderer.m13.scenes-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests '*M13Scene*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M13`
- `area:scenes-evidence`
