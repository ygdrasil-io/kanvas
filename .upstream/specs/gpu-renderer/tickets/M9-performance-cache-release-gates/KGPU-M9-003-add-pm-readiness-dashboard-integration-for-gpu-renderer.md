---
id: KGPU-M9-003
title: "Add PM readiness dashboard integration for GPU renderer"
status: proposed
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

- `proposed`: PM integration after gate policy.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:pm-evidence`
