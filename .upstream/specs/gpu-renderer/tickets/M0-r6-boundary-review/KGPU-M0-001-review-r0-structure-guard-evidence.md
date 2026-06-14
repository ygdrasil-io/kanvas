---
id: KGPU-M0-001
title: "Review R0 structure guard evidence"
status: done
milestone: M0
priority: P0
owner_area: validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M0-001 - Review R0 structure guard evidence

## PM Note

Ce ticket vérifie que la structure du nouveau renderer GPU est protégée avant
de considérer les preuves comme acceptées.

## Problem

R0 is reported as integrated, but a ticket catalog must not convert that report
into `done` without independent review.

## Scope

- Review layout, package-boundary, forbidden-import, placeholder, and `Nothing`
  return-type guards.
- Review deterministic first-slice ownership and alias dumps.
- Confirm no Skia-like, Ganesh, Graphite, browser-only, or validation leakage.

## Non-Goals

- Do not review route execution.
- Do not claim product support.

## Spec Sources

- `.upstream/specs/gpu-renderer/35-package-class-layout.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `reports/gpu-renderer/2026-06-14-implementation-roadmap-progress.md`

## Graphite Algorithm References

- [`GFX-RENDERSTEP-MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83); Compare package boundaries against a working renderer decomposition without importing Graphite APIs.
- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Review where recording state is finalized and reset so structure guards protect the same lifecycle seams.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Use the flush/extract boundary as a reference for validating package ownership around tasks and passes.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class StructureReviewEvidence(val checks: List<String>, val violations: List<String>)
```

## Acceptance Criteria

- [x] `:gpu-renderer:check` evidence is linked.
- [x] Boundary checks reject forbidden imports and package cycles.
- [x] R0 remained `review` until independent review accepted the linked evidence.

## Required Evidence

- R0 progress row.
- Layout and package-boundary test output.
- First-slice ownership dump.

## Fallback / Refusal Behavior

Any missing structure guard keeps the ticket in `review` or `blocked`; it must
not be hidden by product route evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.r0.structure-guard-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Independent review accepted the R0 structure guard evidence as
  `ImplementationCandidate` only. This status does not activate product
  support or a renderer route.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:validation`
