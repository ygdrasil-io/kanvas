---
id: KGPU-M27-003
title: "Frame gate policy"
status: proposed
milestone: M27
priority: P0
owner_area: performance
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M27-001]
legacy_gate: null
---

# KGPU-M27-003 - Frame gate policy

## PM Note

La politique de gate de frame garantit la qualite avant activation. Sur la
baseline Apple M-series, une frame sous 30fps declenche une quarantaine de la
famille en regression.

## Problem

The per-family benchmarks (M27-001) need a frame gate policy with a 60fps
target, a 30fps warning threshold, and regression quarantine. Without this,
performance regressions in the wired families can ship undetected.

## Scope

- Add a frame gate policy with a 60fps target
- Add a 30fps warning threshold
- Add a regression quarantine mechanism (disable the affected family route)
- Anchor measurements to the Apple M-series baseline from M27-001
- Produce a frame gate policy report

## Non-Goals

- Not a release-blocking gate on non-Apple platforms
- Hardware baseline: Apple M-series only
- No adaptive quality scaling
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`

## Graphite Algorithm References

- [`GFX-GFX_DRAWCONTEXT_FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source src/gpu/graphite/Context.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class FrameGatePolicy(val targetFps: Int = 60, val warnFps: Int = 30) {
    fun evaluate(measured: Float): GateResult // pass / warn / quarantine
}
```

## Acceptance Criteria

- [ ] 60fps target enforced as the performance goal
- [ ] 30fps warning triggers a diagnostic
- [ ] Regression quarantine disables the affected family route
- [ ] Gate decisions are measured against the Apple M-series baseline

## Required Evidence

- Frame gate policy definition document
- Frame rate measurement for each active family route
- Gate enforcement transcript (pass / warn / quarantine)

## Fallback / Refusal Behavior

Frame gate evaluation failure emits a stable diagnostic; the affected route
remains disabled and no performance claim is promoted.

## Dashboard Impact

- Expected row: `gpu-renderer.m27.frame-gate-policy`
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
- `milestone:M27`
- `area:performance`
