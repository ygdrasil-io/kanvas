---
id: KGPU-M2-002
title: "Add linear-gradient rect and rrect material route"
status: done
milestone: M2
priority: P0
owner_area: materials-wgsl
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-001]
legacy_gate: null
---

# KGPU-M2-002 - Add linear-gradient rect and rrect material route

## PM Note

Ce ticket ajoute le premier matériau non solide tout en gardant les payloads
hors des clés durables.

## Problem

Linear gradients are in the first-slice contract, but require material-source
lowering, stop normalization, payload packing, WGSL ABI, and tile-mode refusals.

## Scope

- Add linear-gradient material source plans for rect/rrect routes.
- Validate complete WGSL modules and payload packing.
- Refuse unsupported tile modes, stop storage, or color interpolation cases.

## Non-Goals

- Do not add radial, sweep, conical, image shader, or runtime-effect materials.
- Do not claim broad color management.

## Spec Sources

- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md`
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- `.upstream/specs/gpu-renderer/14-first-slice-contract.md`

## Graphite Algorithm References

- [`GFX-GRADIENT-STOPS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-gradient-stops) - source [KeyHelpers.cpp:166](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:166); Study bounded gradient stop packing and large-stop storage fallback decisions.
- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - source [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88); Use paint-key introspection for deterministic material payload evidence.
- [`GFX-ANALYTIC-RRECT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-analytic-rrect-step) - source [AnalyticRRectRenderStep.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/AnalyticRRectRenderStep.cpp:40); Reference rrect coverage interaction with gradient material routes.
- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38); Use pipeline-key/cache behavior for gradient shader variant promotion.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPULinearGradientRoute(val stops: Int, val tileMode: String, val wgslModuleHash: String)
```

## Acceptance Criteria

- [ ] Equivalent gradient descriptors produce deterministic material identity.
- [ ] Payload values do not alter durable keys.
- [ ] Unsupported gradient variants refuse with canonical diagnostics.

## Required Evidence

- MaterialKey and WGSL reflection dumps.
- Payload packing and slot dumps.
- Accepted and refused route fixtures.

## Fallback / Refusal Behavior

Unsupported gradients refuse; they must not be approximated silently or rendered
through CPU textures.

## Dashboard Impact

- Expected row: `gpu-renderer.linear-gradient.rect-rrect`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: `M2SimpleSceneEvidenceTest` adds a deterministic contract-fixture
  linear-gradient material plan with `material:linear-gradient.clamp.inline2`,
  `gradient.inline2` payload evidence, fixture-declared WGSL reflection, and
  stable `unsupported.gradient.tile_mode` refusal evidence. Product activation
  remains false. Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9`
  accepted the evidence as route-planning proof only and confirmed no
  adapter-backed GPU execution or broad gradient/color-management support is
  claimed.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:materials`
