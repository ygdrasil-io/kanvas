---
id: KGPU-M10-004
title: "Add archived evidence hygiene for migrated routes"
status: proposed
milestone: M10
priority: P1
owner_area: docs-evidence
claim_impact: RefuseRequired
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M10-001]
legacy_gate: archives
---

# KGPU-M10-004 - Add archived evidence hygiene for migrated routes

## PM Note

Ce ticket évite de réactiver des plans archivés comme backlog actif.

## Problem

Archived migration plans and old snapshots are historical evidence only and
must not become active acceptance criteria during migration.

## Scope

- Add migration docs rule for archived evidence.
- Add checks or review criteria for active vs historical references.

## Non-Goals

- Do not rewrite archives.
- Do not weaken current target docs.

## Spec Sources

- `AGENTS.md`
- `.upstream/specs/gpu-renderer/32-target-authority-taxonomy-diagnostics.md`

## Graphite Algorithm References

- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Use current recording boundaries to distinguish active evidence from archived snapshots.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Require current pipeline/texture preparation evidence before migration claims.
- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - source [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163); Keep current cache/resource telemetry separate from archived evidence.
- [`GFX-DRAWGEOMETRY-ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512); Map archived draw-family evidence to current route ownership before reuse.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class EvidenceReferencePolicy(val path: String, val role: String)
```

## Acceptance Criteria

- [ ] Active tickets cite active specs for acceptance criteria.
- [ ] Archive references are labeled historical.
- [ ] No archived checkbox is used as active backlog.

## Required Evidence

- Reference hygiene report or review checklist.

## Fallback / Refusal Behavior

Tickets relying on archived acceptance criteria remain blocked.

## Dashboard Impact

- Expected row: `gpu-renderer.archive-hygiene`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Documentation hygiene ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:evidence`
