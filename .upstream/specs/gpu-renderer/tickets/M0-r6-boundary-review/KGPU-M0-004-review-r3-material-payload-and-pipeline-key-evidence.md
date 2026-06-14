---
id: KGPU-M0-004
title: "Review R3 material payload and pipeline key evidence"
status: review
milestone: M0
priority: P0
owner_area: payloads-pipelines
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-003]
legacy_gate: null
---

# KGPU-M0-004 - Review R3 material payload and pipeline key evidence

## PM Note

Ce ticket vérifie que les valeurs de draw ne contaminent pas les clés durables.

## Problem

R3 is reported as integrated, but material identity, payload values, pipeline
keys, and cache facts need review before they can be treated as accepted.

## Scope

- Review `MaterialKey`, material dictionary, payload gatherer, and pipeline key
  evidence.
- Confirm RGBA values, bounds, concrete resources, handles, and surface leases
  stay out of durable keys.
- Review cache telemetry facts for material/module/pipeline hits and misses.

## Non-Goals

- Do not review real backend submission.
- Do not expand beyond solid `FillRect`.

## Spec Sources

- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Design Sketch

```kotlin
data class KeyBoundaryReview(val materialKeyDump: String, val pipelineKeyDump: String)
```

## Acceptance Criteria

- [ ] Material and pipeline key preimages are deterministic.
- [ ] Payload mutation tests prove values do not change durable keys.
- [ ] Cache ledger facts are linked and scoped.

## Required Evidence

- `SolidMaterialKeyTest`, payload gatherer, and pipeline key test output.
- Material/key/payload dumps.
- R3 progress row.

## Fallback / Refusal Behavior

If a resource handle or payload value enters a durable key, the route remains in
`review` or `blocked`.

## Dashboard Impact

- Expected row: `gpu-renderer.r3-key-payload-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.materials.SolidMaterialKeyTest --tests org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGathererTest --tests org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyDerivationTest
rtk git diff --check
```

## Status Notes

- `review`: R3 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:pipeline-key`
