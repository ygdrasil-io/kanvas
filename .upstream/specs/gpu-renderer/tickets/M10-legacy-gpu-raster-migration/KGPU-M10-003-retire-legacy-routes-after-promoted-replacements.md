---
id: KGPU-M10-003
title: "Retire legacy routes after promoted replacements"
status: done
milestone: M10
priority: P1
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M10-002, KGPU-M1-004]
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
- Require each concrete retirement row to name the accepted replacement ticket,
  activation decision, rollback evidence, and PM evidence before any legacy
  code path can be removed.

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
data class GpuRendererLegacyRetirementEvidence(
    val family: GpuRendererLegacyRouteFamily,
    val acceptedReplacementTicket: String,
)
```

## Acceptance Criteria

- [x] Retirement requires accepted replacement ticket.
- [x] Rollback and PM evidence are linked.
- [x] Archived plans remain historical only.
- [x] Generic migration gates cannot retire a route unless the route-specific
      replacement ticket is accepted and linked.

## Required Evidence

- Retirement gate report, replacement evidence, and rollback validation.

## Fallback / Refusal Behavior

If any replacement evidence is missing, or if the replacement ticket is not
accepted and linked, the legacy route remains.

## Dashboard Impact

- Expected row: `gpu-renderer.legacy-retirement`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted replacement.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererLegacyRetirementGateTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*Gate*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GpuRendererLegacyRetirementGate`, a deterministic
  per-family retirement checklist for promoted replacement slices. Missing or
  unsafe evidence keeps the legacy route active. Accepted evidence must name
  the route family, accepted replacement ticket, activation decision, rollback
  evidence, rollback validation hash, old-path usage evidence, PM row, and
  M10-002 shadow parity result; it authorizes a future scoped removal but does
  not disable the production route in this ticket. Broad deletion, generic
  migration gates, archived-only evidence, product activation, release
  blocking, readiness movement, duplicate rows, and shared evidence across
  families are refused, including individually shared activation decisions,
  rollback evidence, or old-path usage evidence. Evidence report:
  `reports/gpu-renderer/2026-06-17-m10-003-legacy-retirement-gates.md`.
- Independent review `019ed5fb-8292-7931-b494-9034a88e15e0` found one P1
  partial shared-evidence gap and one P2 stale-report status issue. The follow
  up added per-artifact shared evidence refusal, a red/green regression test,
  and refreshed the M10 hygiene status matrix. Re-review found no remaining
  P0/P1/P2 blockers and accepted the ticket for `done`.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:legacy-cleanup`
