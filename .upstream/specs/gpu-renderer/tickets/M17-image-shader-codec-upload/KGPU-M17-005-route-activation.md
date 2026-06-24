---
id: KGPU-M17-005
title: "Activate M17 routes: BitmapShader + BitmapRect default ON with rollback"
status: proposed
milestone: M17
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M17-001, KGPU-M17-002, KGPU-M17-003, KGPU-M17-004]
legacy_gate: legacy drawImage
---

# KGPU-M17-005 - Activate M17 routes: BitmapShader + BitmapRect default ON with rollback

## PM Note

L'activation des routes image est un jalon majeur: le GPU renderer peut maintenant afficher des images, pas seulement des formes vectorielles.

## Problem

Image shader and draw routes need controlled product activation with rollback and parity evidence. Image rendering is a fundamental 2D operation for any UI toolkit.

## Scope

- Add controlled product flags for BitmapShader and BitmapRect routes
- Implement rollback path (flag OFF -> legacy image rendering)
- Prove parity: GPU image output == legacy image output
- Set flag defaults to ON after parity review

## Non-Goals

- No activation for mipmapped or color-managed images
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawImage; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M17RouteFlags { var bitmapShader: Boolean = false; var bitmapRect: Boolean = false }\n// All tile modes supported; non-supported formats remain refused
```

## Acceptance Criteria

- [ ] BitmapShader and BitmapRect have controlled feature flags with rollback
- [ ] Parity evidence: GPU image output == legacy image output for all supported formats
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Image rendering GPU vs CPU pixel comparison for PNG/JPEG/WebP/GIF sources
- Per-format rollback validation transcript
- Tile mode correctness evidence under activated routes

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF; unsupported formats remain refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.route-activation`
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
- `milestone:M17`
- `area:product-validation`
