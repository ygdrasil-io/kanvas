---
id: KGPU-M5-002
title: "Add destination-read copy and intermediate strategy"
status: proposed
milestone: M5
priority: P0
owner_area: destination-read
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-001]
legacy_gate: "blend legacy"
---

# KGPU-M5-002 - Add destination-read copy and intermediate strategy

## PM Note

Ce ticket rend les lectures destination explicites et interdit l’échantillonnage
de l’attachement actif.

## Problem

Destination-dependent blends and filters need accepted copy/intermediate/layer
isolation strategies or stable refusals.

## Scope

- Add `GPUDestinationReadPlan` for copy and intermediate strategies.
- Add active-attachment sampling refusal evidence.

## Non-Goals

- Do not assume framebuffer fetch.
- Do not support all blend modes.

## Spec Sources

- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`

## Design Sketch

```kotlin
data class DestinationReadEvidence(val strategy: String, val bounds: String)
```

## Acceptance Criteria

- [ ] Destination-read bounds and resource binding are dumpable.
- [ ] Active-attachment sampling refuses.
- [ ] Strategy maps to one route kind.

## Required Evidence

- Copy/intermediate plan, resource, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported destination reads refuse rather than silently changing blend
semantics.

## Dashboard Impact

- Expected row: `gpu-renderer.destination-read.strategy`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Required before dst-dependent filters/blends.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:destination-read`
