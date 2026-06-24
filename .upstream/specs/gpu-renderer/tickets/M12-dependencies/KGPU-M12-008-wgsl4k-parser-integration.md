---
id: KGPU-M12-008
title: "Integrate wgsl4k parser into WGSLModuleAssembler for live reflection"
status: done
milestone: M12
priority: P0
owner_area: wgsl4k-parser
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M12-008 - Integrate wgsl4k parser into WGSLModuleAssembler for live reflection

## PM Note

L'intégration du parser wgsl4k est la condition préalable à toute validation ABI. Sans reflection live, les erreurs de layout uniforme ne seront détectées qu'au runtime GPU.

## Problem

The WGSL module assembler currently builds modules without parser-backed reflection, making uniform layout errors silent until WebGPU pipeline creation fails. wgsl4k integration provides live validation before GPU submission.

## Scope

- Integrate wgsl4k parser into WGSLModuleAssembler pipeline
- Add live WGSL reflection for uniform block layouts
- Add reflection error diagnostics before GPU submission
- Produce reflection dump for all first-route WGSL modules

## Non-Goals

- No wgsl4k code generation
- No WGSL optimization or minification

## Spec Sources

- .upstream/specs/wgsl-pipeline/README.md
- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RENDERSTEP_MODEL`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderstep-model) - source src/gpu/graphite/ShaderUtils.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class WGSLModuleAssembler { fun reflect(source: String): WGSLReflection; fun assemble(source: String, reflection: WGSLReflection): WGSLModule }
```

## Acceptance Criteria

- [ ] wgsl4k parser reflects uniform block layouts correctly
- [ ] Reflection errors prevent GPU submission with stable diagnostics
- [ ] All first-route WGSL modules pass reflection without errors

## Required Evidence

- WGSL reflection dump for each first-route shader module
- Reflection error diagnostic transcript for malformed WGSL
- Assembly success rate report

## Fallback / Refusal Behavior

Reflection failures emit stable diagnostic; module submission refused until WGSL is corrected.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.wgsl4k.parser-integration`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*WGSLReflection*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:wgsl4k-parser`
