---
id: KGPU-M20-005
title: "Activate M20 routes: A8 + SDF text default ON with rollback"
status: proposed
milestone: M20
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M20-001, KGPU-M20-002, KGPU-M20-003, KGPU-M20-004]
legacy_gate: legacy drawText
---

# KGPU-M20-005 - Activate M20 routes: A8 + SDF text default ON with rollback

## PM Note

L'activation du texte est le milestone le plus attendu par les utilisateurs. Le texte est partout dans les UI, et le rendre sur GPU change tout.

## Problem

A8 and SDF text routes need controlled product activation. Text rendering is the most requested and visible GPU renderer feature for UI applications.

## Scope

- Add controlled product flags for A8 text, SDF text, and DrawTextRun routes
- Implement rollback path (flag OFF -> legacy text rendering)
- Prove parity: GPU text output == legacy text output
- Set flag defaults to ON after parity review

## Non-Goals

- Latin text only; no bidi, complex scripts, color fonts
- No release-blocking status

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawText; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M20RouteFlags { var textA8: Boolean = false; var textSDF: Boolean = false; var drawTextRun: Boolean = false }\n// Latin only; complex scripts, bidi, color fonts remain refused
```

## Acceptance Criteria

- [ ] A8, SDF, and DrawTextRun have controlled feature flags with rollback
- [ ] Parity evidence: GPU text output == legacy text output for Latin glyphs
- [ ] Non-Latin and complex script text remain refused regardless of flag
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Text rendering GPU vs CPU pixel comparison for Latin text samples
- Rollback validation transcript
- Non-Latin text refusal diagnostic

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF; non-Latin refusals remain.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.route-activation`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check && rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M20`
- `area:product-validation`
