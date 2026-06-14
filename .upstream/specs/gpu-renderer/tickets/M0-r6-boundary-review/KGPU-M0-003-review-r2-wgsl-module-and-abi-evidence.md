---
id: KGPU-M0-003
title: "Review R2 WGSL module and ABI evidence"
status: done
milestone: M0
priority: P0
owner_area: wgsl-materials
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-002]
legacy_gate: null
---

# KGPU-M0-003 - Review R2 WGSL module and ABI evidence

## PM Note

Ce ticket vérifie que le shader WGSL est validé comme module complet, pas comme
fragment isolé.

## Problem

R2 is reported as merged, but `WGSLModule`, reflection, binding layout, and
packing evidence need independent review before acceptance.

## Scope

- Review solid-source snippet metadata and module assembly.
- Review parser/reflection and Kotlin packing ABI evidence.
- Confirm unsupported parser, reflection, layout, and facade-limit cases refuse.

## Non-Goals

- Do not accept arbitrary WGSL strings.
- Do not claim route support from fragment-only validation.

## Spec Sources

- `.upstream/specs/gpu-renderer/03-material-key-wgsl.md`
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Use the static data, append data, uniform, texture, and shader-stage split as ABI vocabulary.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Reference how pipeline descriptions become validated pipeline handles before execution.
- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - source [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88); Compare WGSL key introspection against a mature shader-node key tree.
- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38); Use pipeline-key/cache behavior as a reference for module and ABI stability evidence.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class WGSLAbiReview(val moduleHash: String, val reflectionDump: String, val packingDump: String)
```

## Acceptance Criteria

- [x] Module hash and reflection dump are linked.
- [x] Packing layout and byte-size evidence are linked.
- [x] Rejected module fixtures are linked.

## Required Evidence

- `WGSLModuleAbiTest` output.
- WGSL reflection and packing dumps.
- R2 progress row.

## Fallback / Refusal Behavior

Parser ambiguity or surprising `wgsl4k` behavior must stop promotion and be
captured as evidence instead of hidden by workaround code.

## Dashboard Impact

- Expected row: `gpu-renderer.r2.wgsl-abi-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAbiTest
rtk git diff --check
```

## Status Notes

- `done`: Independent review found the R2 ABI dump needed explicit module-hash
  evidence. The dump now links `moduleHash`, reflection source, bindings,
  uniform byte sizes, packing plans, and rejected fixture coverage. This remains
  `ImplementationCandidate` evidence only and does not claim arbitrary
  parser-backed WGSL support.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:wgsl`
