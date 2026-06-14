---
id: KGPU-M1-002
title: "Promote root PM bundle to activation candidate"
status: done
milestone: M1
priority: P0
owner_area: validation-pm
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: pipelinePmBundle
---

# KGPU-M1-002 - Promote root PM bundle to activation candidate

## PM Note

Ce ticket prépare le bundle PM à montrer une activation candidate sans la
confondre avec une activation réelle.

## Problem

The root bundle is currently refusal-first and incomplete. If activation is
approved, PM packaging must change explicitly and verifiably.

## Scope

- Define the root PM manifest changes needed for an activation candidate.
- Preserve adapter-backed evidence provenance and hash validation.
- Keep product activation false until the activation ticket lands.

## Non-Goals

- Do not make adapter-backed evidence a silent root dependency.
- Do not mark release blocking.

## Spec Sources

- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`

## Graphite Algorithm References

- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Use root upload and recording preparation boundaries to structure the PM bundle.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Reference the assembled pass/task evidence required before activation candidate status.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Include pipeline/texture preparation evidence as an activation candidate gate.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class PMActivationCandidate(val status: String, val activationDecisionRef: String?)
```

## Acceptance Criteria

- [x] Manifest distinguishes refusal-first, executed diagnostic, and activation candidate states.
- [x] Adapter-backed provenance is explicit.
- [x] Existing validators reject product-support wording before approval.

## Required Evidence

- PM manifest diff.
- Validator output for root and executed bundles.
- Claim-scan output.

## Fallback / Refusal Behavior

If activation policy is absent, root PM remains refusal-first and incomplete.

## Dashboard Impact

- Expected row: `gpu-renderer.r6-first-route-pm-evidence`
- Expected classification: `PolicyGated`
- Claim promotion allowed: only after activation decision.

## Validation

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py .
rtk git diff --check
```

## Status Notes

- `done`: Root PM bundle packaging now reports `ActivationCandidate` with
  `packagingState=activation-candidate`, while the underlying validation report
  remains `Incomplete`, `productRouteActivated=false`, `releaseBlocking=false`,
  and `readinessDelta=0.0`.
- Fresh evidence:
  `reports/gpu-renderer/2026-06-14-m1-002-activation-candidate-pm-bundle.md`
  and `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`.
- Independent review `019ec714-40ab-73b1-a242-9dc36c3b2694` approved moving
  the ticket to `done`: no hidden product activation, no release blocking, no
  readiness movement, adapter-backed evidence remains opt-in, and no widened
  GPU/WebGPU support claim was found.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:pm-evidence`
