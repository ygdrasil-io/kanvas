---
id: KGPU-M7-001
title: "Add registered runtime-effect descriptor route"
status: done
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
- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`

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

- [x] Descriptor ID/version and uniform schema are dumpable.
- [x] CPU oracle and WGSL evidence are linked.
- [x] Unregistered descriptors refuse.

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

- `done`: Independent review accepted the descriptor-route contract evidence
  on 2026-06-17 with no blocking findings. The gate is descriptor-backed only:
  it records a registered `runtime.simple.color` material descriptor with
  descriptor ID/version, uniform schema and packing, canonical 64-hex
  `sha256:` CPU oracle hash, parser-validated wgsl4k reflection linkage,
  material route dump, material-key boundary proof, and stable refusals for
  unregistered descriptors, descriptor collisions, dynamic SkSL source, wrong
  placement, missing explicit placement opt-in, WGSL
  reflection/schema/descriptor mismatch, and missing or non-canonical CPU
  oracle evidence. Adapter-backed execution, readback evidence, product
  activation, arbitrary SkSL/WGSL input, children, blenders, filters, and live
  editing remain unpromoted.
- Evidence: `RegisteredRuntimeEffectRouteTest` plus
  `reports/gpu-renderer/2026-06-17-m7-001-runtime-effect-descriptor-gate.md`.
- wgsl4k dependency evolution is tracked by `.upstream/specs/wgsl4k-evolution/`.
- 2026-06-15 re-evaluation: merged wgsl4k SHA
  `72a35b58758f241756d984a84768ae77308730da` produced Kanvas dependency
  fixtures under `reports/wgsl4k-evolution/generated/`, including
  `runtime-effect-wgsl-reflection.json` and
  `runtime-effect-wgsl-validation-report.json`. This branch consumes that
  evidence in the descriptor route gate while preserving non-promotion for
  adapter-backed execution/readback and product support.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:runtime-effects`
