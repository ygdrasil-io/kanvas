---
id: KGPU-M7-003
title: "Add blend mode allowlist and destination-read refusals"
status: proposed
milestone: M7
priority: P0
owner_area: blend-destination-read
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-002]
legacy_gate: "blend legacy"
---

# KGPU-M7-003 - Add blend mode allowlist and destination-read refusals

## PM Note

Ce ticket borne les modes de blend au lieu de promettre toute la matrice Skia.

## Problem

Blend modes need fixed-function, shader, or destination-read strategies with
stable refusals for unsupported modes.

## Scope

- Add blend allowlist and key/state evidence.
- Add destination-read refusal evidence for modes without accepted strategy.

## Non-Goals

- Do not support all blend modes.
- Do not assume framebuffer fetch.

## Spec Sources

- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`

## Design Sketch

```kotlin
data class BlendRouteEvidence(val mode: String, val routeKind: String)
```

## Acceptance Criteria

- [ ] Supported modes have deterministic state/key dumps.
- [ ] Unsupported modes refuse stably.
- [ ] Destination-read modes cite accepted strategy or refuse.

## Required Evidence

- Blend plan, pipeline key, destination-read, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported blend modes refuse or remain legacy-policy gated before activation.

## Dashboard Impact

- Expected row: `gpu-renderer.blend-allowlist`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Selected modes only.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:blend`
