---
id: KGPU-M0-006
title: "Review R5 recording task graph evidence"
status: review
milestone: M0
priority: P0
owner_area: recording
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-005]
legacy_gate: null
---

# KGPU-M0-006 - Review R5 recording task graph evidence

## PM Note

Ce ticket vérifie que les recordings sont immuables et ne transforment pas une
preuve isolée en replay produit général.

## Problem

R5 recording/task graph evidence is reported as integrated, but replay policy
and task-list constraints need review before acceptance.

## Scope

- Review `GPURecorder`, immutable `GPURecording`, task-list, dependency token,
  and one-shot replay refusal evidence.
- Confirm surface leases and live handles do not enter durable recordings.
- Confirm task graph evidence stays first-route scoped.

## Non-Goals

- Do not claim broad display-list replay.
- Do not claim multi-family recording support.

## Spec Sources

- `.upstream/specs/gpu-renderer/02-gpu-recording-task-graph.md`
- `.upstream/specs/gpu-renderer/34-analysis-materialization-recording.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Design Sketch

```kotlin
data class RecordingReview(val recordingDump: String, val taskListDump: String)
```

## Acceptance Criteria

- [ ] Recording and task-list dumps are linked.
- [ ] One-shot replay refusal fixture is linked.
- [ ] Upload-before-use or render ordering evidence is linked.

## Required Evidence

- Recording tests.
- R5 progress row.
- Task-list and replay compatibility dumps.

## Fallback / Refusal Behavior

Illegal replay attempts must refuse deterministically and must not mutate
recorded state to repair execution.

## Dashboard Impact

- Expected row: `gpu-renderer.r5-recording-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.recording.*'
rtk git diff --check
```

## Status Notes

- `review`: R5 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:recording`
