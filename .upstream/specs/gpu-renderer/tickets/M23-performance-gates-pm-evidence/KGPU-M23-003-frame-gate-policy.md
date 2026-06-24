---
id: KGPU-M23-003
title: "Add frame gate policy: 60fps target, 30fps warning, quarantine on regression"
status: proposed
milestone: M23
priority: P0
owner_area: performance-validation
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M23-001]
legacy_gate: null
---

# KGPU-M23-003 - Add frame gate policy: 60fps target, 30fps warning, quarantine on regression

## PM Note

La politique de gate de frame garantit la qualité de performance avant activation. Une frame en dessous de 30fps déclenche une quarantaine.

## Problem

Frame rate gate policy needs 60fps target, 30fps warning threshold, and regression quarantine. Without this, performance regressions can ship undetected.

## Scope

- Add frame gate policy with 60fps target
- Add 30fps warning threshold
- Add regression quarantine mechanism (disable affected route)
- Produce frame gate policy report

## Non-Goals

- Not a release-blocking gate on non-Apple platforms
- Hardware baseline: Apple M-series only
- No adaptive quality scaling

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWCONTEXT_FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source src/gpu/graphite/Context.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class FrameGatePolicy(val targetFps: Int = 60, val warnFps: Int = 30) { fun evaluate(measured: Float): GateResult }
```

## Acceptance Criteria

- [ ] 60fps target enforced as performance goal
- [ ] 30fps warning triggers diagnostic
- [ ] Regression quarantine prevents shipping degraded routes

## Required Evidence

- Frame gate policy definition document
- Frame rate measurement for each active route
- Gate enforcement transcript (pass/warn/quarantine)

## Fallback / Refusal Behavior

Frame gate evaluation failure emits stable diagnostic; route remains disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m23.frame-gate-policy`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FrameGate*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M23`
- `area:performance-validation`
