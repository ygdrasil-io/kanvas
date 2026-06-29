---
id: KGPU-M38-003
title: "Dynamic shader graph assembly"
status: review
milestone: M38
priority: P1
owner_area: runtimeeffects
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M38-003 - Dynamic shader graph assembly

## PM Note

Les effets avec enfants forment un DAG de shaders. Le renderer assemble le
module WGSL complet à partir du graphe.

## Problem

Runtime effects with child effects form a directed acyclic graph of shader
descriptors. The renderer must assemble a single complete WGSL module that
inlines all graph nodes with deterministic naming and uniform layout, rather
than dispatching each node in isolation.

## Scope

- `GPURuntimeEffectShaderGraph`: DAG of `GPURuntimeEffectDescriptor` nodes
  with parent-child slots, edge validation.
- `GPURuntimeEffectShaderGraphAssemblyPlan`: topological sort, generate
  `evaluateChild_<slot>()` wrapper functions for each edge, inline uniforms
  from all nodes, emit combined WGSL module.
- `GPURuntimeEffectShaderGraphBudget`: max depth, max children per node,
  max WGSL instructions, max uniform buffer size.
- Assembly algorithm:
  1. Walk graph and validate DAG (no cycles).
  2. Topological sort nodes.
  3. Inline each node's WGSL with unique prefix (avoid symbol collision).
  4. Parent calls `evaluateChild_<slot>(coord)` for each child slot.
  5. Combined uniform block merges all node uniforms deterministically.
  6. Combined WGSL validated via wgsl4k parser.
- Cycle detection: before assembly, detect and refuse cycles in the
  parent-child edge set.

## Non-Goals

- Do not provide a general-purpose WGSL linker/combiner.
- Do not support runtime graph mutation after assembly.
- Do not support graph nodes from different effect registries.
- Do not support conditional or dynamic child selection.

## Spec Sources

- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`

## Dependencies

- Assembly (merge, cycle detection, topological sort, prefixing) is done in
  Kanvas; wgsl4k validates the single combined WGSL module.

## Graphite Algorithm References

- [`GFX-RUNTIME-EFFECT-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-key) - source [KeyHelpers.cpp:1387](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:1387); Register or find a runtime-effect snippet, stash user-defined effects in a transient dictionary, gather transformed uniforms, and fall back to no-op when registration fails.
- [`GFX-RUNTIME-EFFECT-PREAMBLE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-runtime-effect-preamble) - source [ShaderCodeDictionary.cpp:638](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ShaderCodeDictionary.cpp:638); Resolve known or transient runtime effects, convert their program through pipeline callbacks, and inject child sampling/color transform callbacks.
- [`GFX-PAINT-KEY-TREE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paint-key-tree) - source [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88); Decode snippet IDs into a shader node tree, carry embedded sampler data blocks, and validate serializable keys against known runtime-effect IDs.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPURuntimeEffectShaderGraph(
    val nodes: List<GPURuntimeEffectShaderGraphNode>,
    val edges: List<GPURuntimeEffectShaderGraphEdge>,
)

data class GPURuntimeEffectShaderGraphNode(
    val descriptor: GPURuntimeEffectDescriptor,
    val childSlots: Map<String, GPURuntimeEffectDescriptor>,
)

data class GPURuntimeEffectShaderGraphAssemblyPlan(
    val sortedNodes: List<GPURuntimeEffectDescriptor>,
    val combinedWgsl: String,
    val combinedUniformBlock: GPURuntimeEffectUniformBlock,
)

data class GPURuntimeEffectShaderGraphBudget(
    val maxDepth: Int,
    val maxChildrenPerNode: Int,
    val maxWgslInstructions: Int,
    val maxUniformBufferBytes: Int,
)
```

## Acceptance Criteria

- [ ] 2-level shader graph (parent + children) produces correct GPU output
      matching CPU oracle.
- [ ] Cycle detection before WGSL assembly → refusal with stable diagnostic.
- [ ] Budget exceeded (depth, children, instructions, uniform buffer) →
      refusal with stable diagnostic.
- [ ] Deterministic assembly: same descriptors always produce identical WGSL
      output.

## Required Evidence

- GPU dump for 2-level graph with CPU oracle parity.
- Cycle detection fixture with diagnostic output.
- Budget-exceeded fixture with diagnostic output.
- Determinism proof: two assembly runs with same input producing byte-identical
  WGSL output.

## Fallback / Refusal Behavior

- Cycle detected → `unsupported.runtime_effect.shader_graph_cycle`.
- Depth exceeded → `unsupported.runtime_effect.shader_graph_depth_exceeded`.
- Budget exceeded (children/instructions/uniform buffer) → stable diagnostic
  with specific budget counter.

## Dashboard Impact

- Expected row: `gpu-renderer.runtime-effect.shader-graph`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ShaderGraph*'
```

## Status Notes

- `proposed`: Initial ticket.
- `blocked` (2026-06-28): Blocked on wgsl4k multi-fragment module assembly support
- blocked → ready (2026-06-28): unblocked — approach is Kanvas assembles WGSL fragments into single module, wgsl4k validates final module (not multi-fragment).
- ready → review (2026-06-28): shader graph assembly implemented (Kanvas merge, cycle detection, topo sort, prefixing, budget enforcement).

## Linear Labels

- `gpu-renderer`
- `milestone:M38`
- `area:runtimeeffects`
