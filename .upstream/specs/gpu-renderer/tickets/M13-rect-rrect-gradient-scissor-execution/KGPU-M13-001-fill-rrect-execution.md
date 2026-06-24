---
id: KGPU-M13-001
title: "Add FillRRect execution: analytic rrect coverage WGSL + GPU command stream"
status: review
milestone: M13
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M13-001 - Add FillRRect execution: analytic rrect coverage WGSL + GPU command stream

## PM Note

FillRRect est la première forme non-rectangulaire native sur GPU. La couverture analytique via WGSL doit être parfaite avant d'activer la route produit.

## Problem

Rounded rectangle fills need analytic coverage computation in WGSL to produce correct anti-aliased edges on GPU. Without this, rrects would render with aliased or incorrect corners.

## Scope

- Add analytic rrect coverage WGSL shader with distance-field math
- Add FillRRect GPU command stream generation with uniform payload
- Add rrect bounds validation and clipping
- Produce rrect rendering fixture dumps

## Non-Goals

- No stroked rrects at this stage
- No elliptical or complex rrect variants beyond simple corner radii

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_ANALYTIC_RRECT_STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-analytic-rrect-step) - source src/gpu/graphite/geom/Shape.cpp:42; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct FillRRectUniforms { rect: vec4f, radii: vec4f }\n@vertex fn vs() -> @builtin(position) vec4f;\n@fragment fn fs() -> @location(0) vec4f { /* analytic AA coverage */ }
```

## Acceptance Criteria

- [ ] FillRRect renders with correct analytic anti-aliasing on GPU
- [ ] Corner radii produce smooth coverage transitions
- [ ] Rrect bounds outside viewport are correctly clipped

## Required Evidence

- FillRRect GPU rendering fixture dump
- Analytic coverage comparison against reference (CPU)
- Edge case dumps: zero radii, max radii, negative radii

## Fallback / Refusal Behavior

RRect rendering artifacts emit stable diagnostic. Route remains disabled until parity evidence is accepted.

## Dashboard Impact

- Expected row: `gpu-renderer.m13.fill-rrect-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FillRRect*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M13`
- `area:geometry-passes`
