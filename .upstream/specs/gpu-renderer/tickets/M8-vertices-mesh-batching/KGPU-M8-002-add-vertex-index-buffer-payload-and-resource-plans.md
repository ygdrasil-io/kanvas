---
id: KGPU-M8-002
title: "Add vertex index buffer payload and resource plans"
status: proposed
milestone: M8
priority: P1
owner_area: vertices-resources
claim_impact: TargetPrepared
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M8-001]
legacy_gate: null
---

# KGPU-M8-002 - Add vertex index buffer payload and resource plans

## PM Note

Ce ticket rend les buffers vertices/index vérifiables avant soumission GPU.

## Problem

Buffer packing and upload must be deterministic, resource-owned, and excluded
from material identity where appropriate.

## Scope

- Add vertex/index buffer payload, upload, and resource plan evidence.
- Add stale/missing/budget refusals.

## Non-Goals

- Do not optimize batching.
- Do not add broad mesh formats.

## Spec Sources

- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`

## Design Sketch

```kotlin
data class VertexBufferEvidence(val layoutHash: String, val uploadPlan: String)
```

## Acceptance Criteria

- [ ] Buffer layout and upload plan dumps are deterministic.
- [ ] Resource facts stay out of material keys.
- [ ] Invalid buffers refuse.

## Required Evidence

- Buffer layout, payload, upload, resource, and refusal dumps.

## Fallback / Refusal Behavior

Invalid or stale buffer facts refuse before submission.

## Dashboard Impact

- Expected row: `gpu-renderer.vertices.buffers`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Resource plan for vertices.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:vertices`
