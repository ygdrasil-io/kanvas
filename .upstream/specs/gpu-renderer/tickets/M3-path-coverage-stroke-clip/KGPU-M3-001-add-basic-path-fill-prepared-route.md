---
id: KGPU-M3-001
title: "Add basic path fill prepared route"
status: proposed
milestone: M3
priority: P0
owner_area: geometry-artifacts
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-003]
legacy_gate: "path fill legacy"
---

# KGPU-M3-001 - Add basic path fill prepared route

## PM Note

Ce ticket prépare les paths bornés comme artifacts consommés par le GPU, sans
fallback texture CPU.

## Problem

Path fill needs explicit descriptors, bounds, artifact keys, and route
diagnostics before it can be consumed by GPU work.

## Scope

- Add `GPUPathDescriptor`, `GPUGeometryPlan`, prepared artifact key, and route
  dumps for one bounded fill path.
- Add refusals for unsupported fill, transform, edge budget, and bounds cases.

## Non-Goals

- No arbitrary path boolean, perspective, stroke, or path effects.
- No product activation.

## Spec Sources

- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`

## Design Sketch

```kotlin
data class PreparedPathFillEvidence(val geometryPlan: String, val artifactKey: String)
```

## Acceptance Criteria

- [ ] Prepared artifact key excludes live handles.
- [ ] CPUPreparedGPU consumer is named.
- [ ] Unsupported path variants refuse with stable diagnostics.

## Required Evidence

- Geometry descriptor, bounds, artifact, and route dumps.
- CPU oracle or explicit refusal evidence.

## Fallback / Refusal Behavior

Unsupported paths emit `RefuseDiagnostic`; no full draw texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.path-fill.prepared`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: First path route remains prepared and evidence-gated.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:geometry`
