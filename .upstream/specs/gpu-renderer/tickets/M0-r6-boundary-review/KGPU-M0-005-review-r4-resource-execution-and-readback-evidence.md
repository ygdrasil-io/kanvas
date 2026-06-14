---
id: KGPU-M0-005
title: "Review R4 resource execution and readback evidence"
status: review
milestone: M0
priority: P0
owner_area: resources-execution
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-004]
legacy_gate: null
---

# KGPU-M0-005 - Review R4 resource execution and readback evidence

## PM Note

Ce ticket vérifie que l’exécution refuse par défaut et ne simule pas un succès
GPU.

## Problem

R4 resource and execution work is reported as integrated, including opt-in
adapter-backed evidence, but it must be reviewed before acceptance.

## Scope

- Review target preparation, surface lease, materialization, command scope,
  submit handoff, and readback evidence.
- Confirm default execution contexts refuse instead of faking `Submitted`.
- Confirm adapter-backed evidence remains opt-in.

## Non-Goals

- Do not add release-blocking adapter requirements.
- Do not activate product route defaults.

## Spec Sources

- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`

## Graphite Algorithm References

- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source [ResourceProvider.cpp:113](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceProvider.cpp:113); Study texture/sampler/buffer keying and budget/shareability distinctions.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Use sampled-texture validation and pipeline resolution as resource-readiness evidence.
- [`GFX-UPLOAD-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-upload-task) - source [UploadTask.cpp:309](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/UploadTask.cpp:309); Reference upload target instantiation and replay clipping for execution/readback planning.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - source [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19); Compare success/discard/fail task traversal against Kanvas execution diagnostics.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class ExecutionReview(val materialization: String, val submission: String, val readback: String)
```

## Acceptance Criteria

- [ ] Resource materialization and target preparation dumps are linked.
- [ ] Completed readback evidence or stable skipped-readback reason is linked.
- [ ] Adapter-backed lane is not wired into root PM packaging.

## Required Evidence

- `GPUResourceProviderTest` and `GPUExecutionContextTest` output.
- Executed PM evidence validator output when adapter evidence is available.
- R4 progress row.

## Fallback / Refusal Behavior

Missing usage flags, stale device generation, active-attachment sampling, or
pipeline creation failures must refuse with stable diagnostics.

## Dashboard Impact

- Expected row: `gpu-renderer.r4-execution-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProviderTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContextTest
rtk git diff --check
```

## Status Notes

- `review`: R4 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:execution`
