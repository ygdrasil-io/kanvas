---
id: KGPU-M4-002
title: "Add uploaded texture artifact ownership gates"
status: done
milestone: M4
priority: P0
owner_area: resources-images
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M4-001]
legacy_gate: null
---

# KGPU-M4-002 - Add uploaded texture artifact ownership gates

## PM Note

Ce ticket rend l’upload texture vérifiable avant qu’un draw puisse l’échantillonner.

## Problem

Uploaded textures need artifact keys, lifetime, generation, usage, and resource
ownership evidence.

## Scope

- Add `UploadedTextureArtifact` ownership and materialization gates.
- Add stale generation, missing usage, and active-attachment refusal fixtures.

## Non-Goals

- Do not add broad codec handling.
- Do not persist concrete texture handles in durable keys.

## Spec Sources

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`

## Graphite Algorithm References

- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251); Use texture proxy/upload-source creation as the ownership model for uploaded artifacts.
- [`GFX-UPLOAD-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-upload-task) - source [UploadTask.cpp:309](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/UploadTask.cpp:309); Study upload preparation and replay clipping for deterministic upload evidence.
- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source [ResourceProvider.cpp:113](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceProvider.cpp:113); Reference texture/buffer keying and budget/shareability boundaries.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - source [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19); Use ordered task preparation/replay to validate upload lifecycle.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class UploadedTextureOwnership(val artifactKey: String, val generation: String)
```

## Acceptance Criteria

- [ ] Artifact keys exclude live resource identity.
- [ ] Upload/resource usage facts are dumpable.
- [ ] Stale or incompatible resources refuse.

## Required Evidence

- Upload plan, artifact key, resource materialization, and refusal dumps.

## Fallback / Refusal Behavior

Missing or stale upload facts refuse before sampling.

## Dashboard Impact

- Expected row: `gpu-renderer.uploaded-texture-ownership`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUUploadedTextureArtifactOwnershipGate` resource contract
  evidence for typed `UploadedTextureArtifact` ownership before sampling. The
  gate validates artifact type, artifact generation, required usage labels,
  device generation, active-attachment sampling, and texture descriptor
  compatibility before returning an `UploadFromArtifact` allocation plan. Dumps
  include ownership scope, lifetime, release policy, usage/device facts,
  sampler/view facts, and upload-before-sample materialization intent while
  excluding live resource debug labels and handles. Stable refusals cover stale
  artifact generation, missing usage, stale device generation, active
  attachment sampling, descriptor mismatch, and missing uploaded-artifact
  provenance. Independent review
  `019ec815-a637-7e92-baa9-24bd28b69904` found that missing provenance had code
  coverage but no refusal fixture; remediation added RED/GREEN evidence for
  `artifactType != UploadedTextureArtifact`. Evidence is contract-only and does
  not allocate WebGPU textures, upload bytes, claim cache residency, activate
  product routing, or imply codec support. Post-remediation independent review
  `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95` accepted the evidence for `done` and
  confirmed no hidden activation, support-claim widening, package-cycle risk,
  material-key/resource-handle leak, or M4-004 promotion.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:resources`
