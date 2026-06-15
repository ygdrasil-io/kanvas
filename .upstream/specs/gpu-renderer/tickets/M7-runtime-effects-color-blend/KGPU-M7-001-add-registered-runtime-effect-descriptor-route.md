---
id: KGPU-M7-001
title: "Add registered runtime-effect descriptor route"
status: proposed
milestone: M7
priority: P0
owner_area: runtime-effects
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-002]
legacy_gate: "runtime-effect legacy"
---

# KGPU-M7-001 - Add registered runtime-effect descriptor route

## PM Note

Ce ticket autorise seulement les effets enregistrés avec contrat Kotlin/WGSL.

## Problem

Runtime effects must resolve through descriptors, CPU oracle, WGSL plan, and
route contract, not arbitrary SkSL or source strings.

## Scope

- Add one descriptor-backed material route.
- Link uniform schema, CPU oracle, WGSL reflection, route, and refusal evidence.

## Non-Goals

- Do not compile SkSL or arbitrary WGSL.
- Do not support children or blenders yet.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`

## Graphite Algorithm References

- [`GFX-RUNTIME-EFFECT-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source [KeyHelpers.cpp:1387](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:1387); Study registered runtime-effect snippets and transformed uniform gathering.
- [`GFX-RUNTIME-EFFECT-PREAMBLE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-preamble) - source [ShaderCodeDictionary.cpp:638](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ShaderCodeDictionary.cpp:638); Reference callback-based runtime-effect lowering vocabulary without building SkSL support.
- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - source [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88); Use serializable key validation for descriptor evidence.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Compare registered descriptor lowering to paint-to-key metadata.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class RuntimeEffectRouteEvidence(val descriptorId: String, val wgslPlan: String)
```

## Acceptance Criteria

- [ ] Descriptor ID/version and uniform schema are dumpable.
- [ ] CPU oracle and WGSL evidence are linked.
- [ ] Unregistered descriptors refuse.

## Required Evidence

- Registry snapshot, oracle, WGSL, route, and refusal dumps.

## Fallback / Refusal Behavior

Unregistered or unsupported runtime effects refuse; no source-string support.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.registered`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Descriptor-backed only. Remaining gate is a registered descriptor
  with Kotlin/CPU oracle, complete parser-validated WGSL/reflection through
  `wgsl4k`, route integration, adapter-backed execution/readback evidence, and
  explicit unregistered-descriptor refusals.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:runtime-effects`
