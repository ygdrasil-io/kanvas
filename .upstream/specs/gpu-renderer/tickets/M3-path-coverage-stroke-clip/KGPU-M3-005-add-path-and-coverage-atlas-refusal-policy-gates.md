---
id: KGPU-M3-005
title: "Add path and coverage atlas refusal policy gates"
status: done
milestone: M3
priority: P1
owner_area: atlas-policy
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M3-001]
legacy_gate: null
---

# KGPU-M3-005 - Add path and coverage atlas refusal policy gates

## PM Note

Ce ticket garde les atlas non prêts comme refus explicites plutôt que promesses
de support.

## Problem

Atlas routes need memory, eviction, generation, mutation, and synchronization
policies before support can be claimed.

## Scope

- Add refusal gates for path and coverage atlas routes lacking policy evidence.
- Define required facts before future promotion.

## Non-Goals

- Do not implement atlas generation.
- Do not promote atlas-backed route support.

## Spec Sources

- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`

## Graphite Algorithm References

- [`GFX-PATH-ATLAS-PACK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-path-atlas-pack) - source [PathAtlas.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PathAtlas.cpp:38); Study cache lookup, padded atlas allocation, flush tokens, and plot eviction.
- [`GFX-DRAW-ATLAS-PLOTS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-atlas-plots) - source [DrawAtlas.cpp:149](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawAtlas.cpp:149); Reference plot allocation, LRU behavior, and retry-on-flush semantics.
- [`GFX-COMPUTE-PATH-ATLAS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-compute-path-atlas) - source [ComputePathAtlas.h:31](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ComputePathAtlas.h:31); Use compute atlas dispatch scheduling as a future dependency gate.
- [`GFX-RASTER-MASK-HELPER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-raster-mask-helper) - source [RasterPathUtils.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/RasterPathUtils.h:24); Keep CPU-prepared mask behavior typed and diagnostically visible.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class AtlasPolicyRefusal(val reason: String, val requiredFacts: List<String>)
```

## Acceptance Criteria

- [ ] Missing atlas policies refuse with stable diagnostics.
- [ ] Required facts for future promotion are listed.
- [ ] PM output cannot count selector-only atlas evidence as support.

## Required Evidence

- Refusal matrix and diagnostic dumps.
- Dashboard row showing `RefuseRequired`.

## Fallback / Refusal Behavior

Atlas routes refuse until policy, budgets, and synchronization evidence exist.

## Dashboard Impact

- Expected row: `gpu-renderer.atlas-policy-refusal`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUAtlasPolicyRefusalGate` refusal-only contract evidence
  for path and coverage atlas routes. The gate emits deterministic
  `GPUAtlasDiagnostic` reason codes, required/missing policy fact dumps,
  `RefuseRequired` dashboard classification, and non-claims that selector-only
  evidence, atlas generation, path atlas support, coverage atlas support, and
  hidden CPU texture fallback are not accepted. Independent review
  `019ec7d2-85bf-7d90-977d-8c7ee86f2710` accepted the evidence and confirmed
  that no atlas-backed route support or product activation is implied.
  Remaining gate: future path/coverage atlas promotion still requires real key,
  budget, generation, eviction, synchronization, upload-before-sample, GPU
  sampling, and visual evidence.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:atlas`
