---
id: KGPU-M12-009
title: "Add WGSL ABI validation: reflected layout vs Kotlin packing byte-match"
status: done
milestone: M12
priority: P0
owner_area: wgsl4k-abi
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-008]
legacy_gate: null
---

# KGPU-M12-009 - Add WGSL ABI validation: reflected layout vs Kotlin packing byte-match

## PM Note

La validation ABI garantit que le layout mémoire Kotlin correspond exactement à ce que le GPU attend. Un décalage d'un octet casse tous les uniforms silencieusement.

## Problem

Kotlin uniform packing must match the WGSL reflected layout byte-for-byte. Without ABI validation, struct padding, alignment, or ordering mismatches silently corrupt GPU uniform data.

## Scope

- Add byte-level comparison between Kotlin packed uniforms and WGSL reflected layout
- Add ABI mismatch diagnostics with field-level detail
- Add ABI validation gate before pipeline creation
- Produce ABI validation report for all active uniform blocks

## Non-Goals

- No runtime ABI checking per draw call
- No automatic Kotlin code generation from WGSL

## Spec Sources

- .upstream/specs/wgsl-pipeline/README.md
- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_RESOURCE_KEYED_CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source src/gpu/graphite/ResourceProvider.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class WGSLAbiValidator { fun validate(kotlinLayout: UniformLayout, wgslReflection: WGSLReflection): AbiValidationResult }
```

## Acceptance Criteria

- [ ] All active uniform blocks pass ABI validation before pipeline creation
- [ ] ABI mismatch reports identify exact field, offset, and size discrepancy
- [ ] ABI failure prevents pipeline creation with stable diagnostic

## Required Evidence

- ABI validation report for all active uniform blocks
- ABI mismatch diagnostic transcript (intentionally misaligned test case)
- Pipeline creation gate transcript

## Fallback / Refusal Behavior

ABI mismatch prevents pipeline creation; GPU route disabled with field-level diagnostic.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.wgsl4k.abi-validation`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*WGSLAbi*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:wgsl4k-abi`
