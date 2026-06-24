---
id: KGPU-M15-005
title: "Add gpu-renderer-scenes evidence: path-fill-stencil, convex-fan-mesh"
status: done
milestone: M15
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M15-001, KGPU-M15-002, KGPU-M15-003]
legacy_gate: null
---

# KGPU-M15-005 - Add gpu-renderer-scenes evidence: path-fill-stencil, convex-fan-mesh

## PM Note

Les scenes de rendu GPU sont la preuve visuelle que le path fill via stencil-cover et le convex fan fonctionnent. Sans PNG dans le depot, l'activation reste theorie.

## Problem

M15 active le path fill avec stencil-cover et convex fan en production, mais sans rendu offscreen committe dans le depot, la preuve est manquante.

## Scope

- Ajouter 2 scenes dans le catalogue gpu-renderer-scenes : `path-fill-stencil` (remplissage non-convexe via stencil-cover), `convex-fan-mesh` (remplissage convexe en single-pass triangulation)
- Etendre RectOnlyOffscreenRenderer pour supporter PathFillStencil et ConvexFanMesh
- Produire les PNGs de rendu et les commiter dans reports/gpu-renderer-scenes/offscreen/

## Non-Goals

- Pas de scenes interactives Kadre
- Pas de combinaison path fill + gradient ou path fill + bitmap
- Pas de paths depassant le budget de 256 edges

## Spec Sources

- .upstream/specs/gpu-renderer/README.md
- gpu-renderer-scenes/src/main/kotlin/.../catalog/GPURendererSceneRegistry.kt
- .upstream/specs/gpu-renderer/tickets/templates/milestone-template.md

## Graphite Algorithm References

- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
// PathFillStencil command: non-convex path -> tessellate -> stencil write + cover resolve
data class PathFillStencil(val path: Path, val color: Color)
// ConvexFanMesh command: convex path -> triangulate -> single-pass analytic AA
data class ConvexFanMesh(val vertices: List<Point>, val color: Color)
```

## Acceptance Criteria

- [ ] 2 nouvelles scenes definies dans GPURendererSceneRegistry
- [ ] RectOnlyOffscreenRenderer etendu pour PathFillStencil et ConvexFanMesh
- [ ] Les 2 scenes produisent render.png avec pixel count > 0
- [ ] Rapports commites dans reports/gpu-renderer-scenes/offscreen/

## Required Evidence

- path-fill-stencil GPU rendering fixture dump (PNG)
- convex-fan-mesh GPU rendering fixture dump (PNG)

## Fallback / Refusal Behavior

Scenes hors du scope M15 (path atlas, compute tessellation) restent not-yet-rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m15.path-fill-stencil-scene-evidence`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-stencil
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=convex-fan-mesh
```

## Status Notes

- `proposed`: Initial ticket. Follows M14-004 pattern with offscreen render validation.
- `done`: Added PathFillStencil and ConvexFanMesh SceneCommand variants. Created M15CandidatePromotionScenes.kt (path-fill-stencil star scene + convex-fan-mesh octagon scene). Extended RectOnlyOffscreenRenderer to handle path fill families. Added human documentation. Registered scenes in GPURendererSceneRegistry.

## Linear Labels

- `gpu-renderer`
- `milestone:M15`
- `area:scenes-evidence`
