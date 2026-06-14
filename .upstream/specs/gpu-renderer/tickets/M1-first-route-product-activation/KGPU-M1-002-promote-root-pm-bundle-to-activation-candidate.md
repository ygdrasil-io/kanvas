---
id: KGPU-M1-002
title: "Promote root PM bundle to activation candidate"
status: proposed
milestone: M1
priority: P0
owner_area: validation-pm
claim_impact: PolicyGated
route_kind: mixed
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

## Design Sketch

```kotlin
data class PMActivationCandidate(val status: String, val activationDecisionRef: String?)
```

## Acceptance Criteria

- [ ] Manifest distinguishes refusal-first, executed diagnostic, and activation candidate states.
- [ ] Adapter-backed provenance is explicit.
- [ ] Existing validators reject product-support wording before approval.

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

- `proposed`: Depends on KGPU-M1-001.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:pm-evidence`
