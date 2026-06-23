---
id: KGPU-M18-005
title: "Activate M18 routes: SaveLayer + destination read default ON with rollback"
status: proposed
milestone: M18
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M18-001, KGPU-M18-002, KGPU-M18-003, KGPU-M18-004]
legacy_gate: legacy saveLayer/restore
---

# KGPU-M18-005 - Activate M18 routes: SaveLayer + destination read default ON with rollback

## PM Note

L'activation SaveLayer est le dernier bloc manquant pour la composition complète. Avec M18, le GPU renderer peut gérer des arbres de calques arbitraires.

## Problem

SaveLayer and destination-read routes need controlled product activation. Layer compositing is the foundation for complex UI rendering (transparency groups, blend modes).

## Scope

- Add controlled product flags for SaveLayer and DestinationRead routes
- Implement rollback path (flag OFF -> legacy layer rendering)
- Prove parity: GPU layer output == legacy layer output
- Set flag defaults to ON after parity review

## Non-Goals

- No backdrop filter activation
- No layer elision optimization
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_SPECIAL_DRAW`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-draw) - source src/gpu/graphite/Device.cpp restoreLayer; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M18RouteFlags { var saveLayer: Boolean = false; var dstRead: Boolean = false }\n// Backdrop filters remain refused; no framebuffer-fetch
```

## Acceptance Criteria

- [ ] SaveLayer and DestinationRead have controlled feature flags with rollback
- [ ] Parity evidence: GPU layer output == legacy layer output
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Layer compositing GPU vs CPU pixel comparison
- Rollback validation transcript
- SaveLayer nesting test dumps (2-3 levels deep)

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF; unsupported operations remain refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.route-activation`
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
- `milestone:M18`
- `area:product-validation`
