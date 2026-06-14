---
id: KGPU-M0-002
title: "Review R1 first native draw route evidence"
status: review
milestone: M0
priority: P0
owner_area: analysis-routing-passes
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M0-001]
legacy_gate: null
---

# KGPU-M0-002 - Review R1 first native draw route evidence

## PM Note

Ce ticket suit la première route `FillRect` comme preuve isolée, pas comme
activation produit.

## Problem

The first native route is reported as integrated, but support must remain gated
until accepted-route and refusal evidence are reviewed.

## Scope

- Review `NormalizedDrawCommand.FillRect` through analysis, routing, and pass
  construction.
- Review refusals for transform, target format, blend, clip, layer/filter,
  capability, and WGSL/ABI mismatch.
- Confirm no hidden CPU-rendered texture route.

## Non-Goals

- Do not activate default `gpu-raster` behavior.
- Do not claim rrect, gradient, path, image, text, filter, or runtime-effect support.

## Spec Sources

- `.upstream/specs/gpu-renderer/14-first-slice-contract.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `reports/gpu-renderer/2026-06-14-implementation-roadmap-progress.md`

## Design Sketch

```kotlin
data class FirstRouteReview(val acceptedRouteDump: String, val refusalDumps: List<String>)
```

## Acceptance Criteria

- [ ] Accepted solid `FillRect` route dump is linked.
- [ ] Canonical unsupported branch refusals are linked.
- [ ] Product activation remains `false`.

## Required Evidence

- R1 progress row and review notes.
- First-route command and planner tests.
- Route/refusal dumps.

## Fallback / Refusal Behavior

Unsupported nearby variants must emit stable `RefuseDiagnostic` entries and
must not be repaired through CPU-rendered compatibility textures.

## Dashboard Impact

- Expected row: `gpu-renderer.r1.first-route-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest
rtk git diff --check
```

## Status Notes

- `review`: R1 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:routing`
