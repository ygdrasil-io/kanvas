---
id: KGPU-M10-003
title: "Retire legacy routes after promoted replacements"
status: proposed
milestone: M10
priority: P1
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M10-002]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M10-003 - Retire legacy routes after promoted replacements

## PM Note

Ce ticket encadre la suppression legacy uniquement après remplacement promu.

## Problem

Legacy code can only be retired when a replacement route has accepted evidence,
activation policy, rollback, and PM update.

## Scope

- Define retirement checklist for promoted replacement slices.
- Add guards against broad deletion.

## Non-Goals

- Do not retire multiple domains by implication.
- Do not remove archived evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`

## Graphite Algorithm References

- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Reference recording snapshot success/failure as the boundary before retirement.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Use pass/task extraction to prove replacement routes own execution.
- [`GFX-TASKLIST`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tasklist) - source [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19); Compare task success/discard/fail behavior before deleting legacy paths.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Use render-pass replay evidence for route-specific retirement gates.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class LegacyRetirementGate(val legacyRoute: String, val promotedReplacement: String)
```

## Acceptance Criteria

- [ ] Retirement requires accepted replacement ticket.
- [ ] Rollback and PM evidence are linked.
- [ ] Archived plans remain historical only.

## Required Evidence

- Retirement gate report, replacement evidence, and rollback validation.

## Fallback / Refusal Behavior

If any replacement evidence is missing, legacy route remains.

## Dashboard Impact

- Expected row: `gpu-renderer.legacy-retirement`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted replacement.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: No deletion by catalog creation.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:legacy-cleanup`
