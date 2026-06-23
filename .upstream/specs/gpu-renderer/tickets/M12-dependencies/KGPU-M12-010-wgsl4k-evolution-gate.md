---
id: KGPU-M12-010
title: "Add wgsl4k evolution gate: parser-backed reflection for all first-route WGSL"
status: done
milestone: M12
priority: P0
owner_area: wgsl4k-gate
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-008, KGPU-M12-009]
legacy_gate: null
---

# KGPU-M12-010 - Add wgsl4k evolution gate: parser-backed reflection for all first-route WGSL

## PM Note

La gate d'évolution wgsl4k verrouille la qualité: chaque nouveau shader WGSL doit passer la reflection avant d'être accepté. C'est la police d'assurance du pipeline.

## Problem

As new WGSL modules are added in later milestones, a gate must ensure every module passes parser-backed reflection before pipeline cache insertion.

## Scope

- Add evolution gate that requires parser-backed reflection for all WGSL modules
- Add gate check in pipeline cache insertion path
- Add gate bypass diagnostics for development/debug
- Produce evolution gate status report for all modules

## Non-Goals

- No automatic WGSL rewriting or fixing
- No gate for non-WGSL shader formats

## Spec Sources

- .upstream/specs/wgsl-pipeline/README.md
- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_PIPELINE_MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source src/gpu/graphite/PipelineCache.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class Wgsl4kEvolutionGate { fun check(module: WGSLModule): GateResult }
```

## Acceptance Criteria

- [ ] All first-route WGSL modules pass the evolution gate
- [ ] Pipeline cache rejects unreflected modules with stable diagnostic
- [ ] Gate status is reported in pipeline telemetry

## Required Evidence

- Evolution gate status for all first-route WGSL modules
- Gate refusal diagnostic transcript
- Pipeline cache telemetry showing gate enforcement

## Fallback / Refusal Behavior

Unreflected WGSL modules are rejected by pipeline cache with stable diagnostic; no silent fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.wgsl4k.evolution-gate`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Wgsl4kGate*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:wgsl4k-gate`
