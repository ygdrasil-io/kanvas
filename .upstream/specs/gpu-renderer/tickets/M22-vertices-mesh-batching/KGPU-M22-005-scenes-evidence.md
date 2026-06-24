---
id: KGPU-M22-005
title: "Add gpu-renderer-scenes evidence: vertices-color-mesh, mesh-ribbon-depth"
status: proposed
milestone: M22
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M22-001, KGPU-M22-002, KGPU-M22-003]
legacy_gate: null
---

# KGPU-M22-005 - Add gpu-renderer-scenes evidence: vertices-color-mesh, mesh-ribbon-depth

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le DrawVertices avec vertex colors et le mesh ribbon fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M22 active DrawVertices execution, vertex buffer materialization et mesh batching en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `vertices-color-mesh` (triangle mesh avec vertex colors varies), `mesh-ribbon-depth` (mesh en forme de ruban avec profondeur implicite)
- Etendre RectOnlyOffscreenRenderer pour supporter VerticesColorMesh et MeshRibbonDepth
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas d'index buffer ou custom vertex layouts
- Pas de GPU-driven draw generation

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// VerticesColorMesh command: list of triangles with per-vertex colors
data class VerticesColorMesh(val triangles: List<Triangle>, val vertexColors: List<Color>)
// MeshRibbonDepth command: ribbon mesh with depth-based vertex colors
data class MeshRibbonDepth(val controlPoints: List<Point>, val width: Float, val segments: Int)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour VerticesColorMesh et MeshRibbonDepth
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- vertices-color-mesh GPU rendering fixture dump (PNG)
- mesh-ribbon-depth GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M22 (index buffers, custom vertex layouts, GPU-driven draws) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m22.vertices-mesh-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=vertices-color-mesh
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=mesh-ribbon-depth
```

## Status Notes

- `proposed`: Initial ticket. Follows M14-004 pattern with offscreen render validation.

## Linear Labels

- `gpu-renderer`
- `milestone:M22`
- `area:scenes-evidence`
