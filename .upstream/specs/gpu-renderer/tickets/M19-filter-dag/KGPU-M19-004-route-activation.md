---
id: KGPU-M19-004
title: "Activate M19 routes: Blur + ColorMatrix + bounded filter DAG default ON"
status: proposed
milestone: M19
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M19-001, KGPU-M19-002, KGPU-M19-003]
legacy_gate: legacy drawFilter
---

# KGPU-M19-004 - Activate M19 routes: Blur + ColorMatrix + bounded filter DAG default ON

## PM Note

L'activation des filtres est la cerise sur le gâteau pour la vague 2. Avec M19, le GPU renderer supporte le flou et les matrices de couleur, couvrant 90% des besoins de filtres UI.

## Problem

Blur, ColorMatrix, and bounded filter DAG routes need controlled product activation. Filter support enables soft shadows, image effects, and UI polish on GPU.

## Scope

- Add controlled product flags for Blur, ColorMatrix, and FilterDAG routes
- Implement rollback path (flag OFF -> legacy filter rendering)
- Prove parity: GPU filter output == legacy filter output
- Set flag defaults to ON after parity review

## Non-Goals

- No Picture, RuntimeShader, or arbitrary SkSL filter activation
- No DAG depth beyond 2 nodes
- No release-blocking status

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source src/gpu/graphite/Device.cpp drawFilter; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
object M19RouteFlags { var blur: Boolean = false; var colorMatrix: Boolean = false; var filterDAG: Boolean = false }\n// DAG depth limited to 2; deeper graphs refused
```

## Acceptance Criteria

- [ ] Blur, ColorMatrix, and FilterDAG have controlled feature flags with rollback
- [ ] Parity evidence: GPU filter output == legacy filter output
- [ ] DAG depth limit enforced regardless of flag state
- [ ] Flags default to ON after parity review acceptance

## Required Evidence

- Filter GPU vs CPU pixel comparison for blur and color matrix
- Rollback validation transcript
- DAG depth limit refusal diagnostic

## Fallback / Refusal Behavior

Any parity failure keeps the affected route flag OFF; DAG depth refusals remain.

## Dashboard Impact

- Expected row: `gpu-renderer.m19.route-activation`
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
- `milestone:M19`
- `area:product-validation`
