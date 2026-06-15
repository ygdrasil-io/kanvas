---
id: KGPU-M9-003
title: "Add PM readiness dashboard integration for GPU renderer"
status: blocked
milestone: M9
priority: P1
owner_area: pm-evidence
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M9-001, KGPU-M9-002]
legacy_gate: pipelinePmBundle
---

# KGPU-M9-003 - Add PM readiness dashboard integration for GPU renderer

## PM Note

Ce ticket rend la readiness GPU renderer lisible sans sur-vendre les preuves.

## Problem

PM dashboards must distinguish correctness, activation, performance, cache, and
release readiness.

## Scope

- Add PM rows for GPU renderer activation/perf/cache state.
- Preserve explicit non-claims and readiness deltas.

## Non-Goals

- Do not move readiness by writing dashboard rows.
- Do not hide reporting-only gates.

## Spec Sources

- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`

## Graphite Algorithm References

- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Use pipeline/texture validation and per-pipeline draw areas as PM dashboard rows.
- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38); Reference cache-hit and task-creation metrics for readiness summaries.
- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - source [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163); Expose resource reuse and purge telemetry in dashboard evidence.
- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Use recording snapshot success/failure boundaries for PM readiness.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPURendererReadinessRow(val area: String, val readinessDelta: Double)
```

## Acceptance Criteria

- [ ] Dashboard rows separate activation and performance readiness.
- [ ] Release-blocking state is explicit.
- [ ] Non-claims are preserved.

## Required Evidence

- PM manifest/dashboard diff and validator output.

## Fallback / Refusal Behavior

Missing gates keep readiness unchanged and reporting-only.

## Dashboard Impact

- Expected row: `gpu-renderer.readiness`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted evidence.

## Validation

```bash
rtk ./gradlew --no-daemon pipelinePmBundle --dry-run
rtk git diff --check
```

## Status Notes

- `blocked`: PM integration depends on accepted KGPU-M9-002 gate policy plus a
  PM manifest/dashboard diff that keeps correctness support, activation,
  performance, cache, and release readiness separate. Missing gates must keep
  `readinessDelta=0.0`, `releaseBlocking=false`, and reporting-only status.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:pm-evidence`
