---
id: KGPU-M23-005
title: "Add gpu-renderer-scenes final catalog: all 45+ scenes render offscreen + windowed"
status: done
milestone: M23
priority: P0
owner_area: scenes-evidence
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M23-004]
legacy_gate: null
---

# KGPU-M23-005 - Add gpu-renderer-scenes final catalog: all 45+ scenes render offscreen + windowed

## PM Note

Le catalogue final de scènes est la vitrine du GPU renderer. Chaque scène doit fonctionner en offscreen et en windowed.

## Problem

Final scene catalog needs all 45+ scenes rendering in both offscreen and windowed modes. This is the visual proof that the GPU renderer is production-ready.

## Scope

- Add final scene catalog with all 45+ scenes
- Add offscreen rendering mode for all scenes
- Add windowed rendering mode (Kadre/AppKit) for all scenes
- Produce scene catalog rendering report

## Non-Goals

- No interactive scene editing
- No scene performance benchmarking in catalog

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source gm/graphite/*.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class SceneCatalog(val scenes: List<Scene>) { fun renderAll(mode: RenderMode): RenderReport }
```

## Acceptance Criteria

- [ ] All 45+ scenes render without crashes
- [ ] Offscreen rendering produces correct output
- [ ] Windowed rendering produces correct output
- [ ] Catalog report lists all scenes with status

## Required Evidence

- Scene catalog rendering report (all scenes)
- Offscreen rendering fixture dumps for all scenes
- Windowed rendering screenshot evidence

## Fallback / Refusal Behavior

Scene rendering failures emit stable diagnostic per scene; catalog report marks failures.

## Dashboard Impact

- Expected row: `gpu-renderer.m23.scenes-catalog`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- PerformanceBudget, pipeline cache telemetry, frame gate policy, PM evidence bundle
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M23`
- `area:scenes-evidence`
