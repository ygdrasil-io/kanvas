---
id: KGPU-M3-003
title: "Add simple stroke route candidate"
status: proposed
milestone: M3
priority: P0
owner_area: geometry-stroke
claim_impact: TargetPrepared
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "stroke legacy"
---

# KGPU-M3-003 - Add simple stroke route candidate

## PM Note

Ce ticket rend les strokes simples auditables avant toute promesse de parité.

## Problem

Stroke support requires cap, join, miter, dash, transform, and bounds evidence.

## Scope

- Add `GPUStrokeDescriptor`, `GPUStrokeExpansionPlan`, and route/refusal dumps.
- Cover one simple bounded stroke and nearby unsupported variants.

## Non-Goals

- No broad cap/join parity, hairline, dash, or path-effect support.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`

## Design Sketch

```kotlin
data class StrokeRouteEvidence(val strokePlan: String, val refusalDumps: List<String>)
```

## Acceptance Criteria

- [ ] Simple stroke route has deterministic descriptor and bounds dumps.
- [ ] Unsupported stroke styles refuse with stable diagnostics.
- [ ] Prepared/native route kind is explicit.

## Required Evidence

- Stroke descriptor and expansion dumps.
- CPU/GPU evidence or explicit refusal.

## Fallback / Refusal Behavior

Unsupported strokes refuse; they are not converted to CPU-rendered textures.

## Dashboard Impact

- Expected row: `gpu-renderer.stroke.simple`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Simple stroke only.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:stroke`
