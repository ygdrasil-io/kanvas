---
id: KGPU-M18-004
title: "Add destination-read intermediate strategy: bind existing intermediate texture"
status: proposed
milestone: M18
priority: P0
owner_area: layers-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M18-003]
legacy_gate: null
---

# KGPU-M18-004 - Add destination-read intermediate strategy: bind existing intermediate texture

## PM Note

La stratégie intermediate évite une copie coûteuse quand une texture intermédiaire existe déjà. C'est une optimisation importante pour les scènes avec plusieurs couches.

## Problem

When a previous render pass already produced an intermediate texture (e.g., from SaveLayer), the destination-read should bind that existing texture instead of copying again. Without this optimization, every blended layer incurs an unnecessary copy.

## Scope

- Add intermediate texture binding strategy for destination-read
- Add texture readiness check before intermediate bind
- Add fallback to copy strategy when intermediate is unavailable
- Produce intermediate strategy vs copy strategy comparison

## Non-Goals

- No automatic intermediate texture lifetime management
- No intermediate texture sharing across non-adjacent passes

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TEXTURE_UPLOAD_ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source src/gpu/graphite/TextureProxy.cpp intermediate; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class DstReadIntermediateStrategy(val existingTexture: GpuTexture?, val fallback: DstReadCopyStrategy)
```

## Acceptance Criteria

- [ ] Intermediate strategy correctly binds existing texture for destination-read
- [ ] Fallback to copy strategy triggers when intermediate is unavailable
- [ ] Intermediate strategy avoids unnecessary GPU copies

## Required Evidence

- Intermediate strategy GPU rendering trace showing texture reuse
- Fallback to copy strategy transcript
- Performance comparison: intermediate vs copy strategy

## Fallback / Refusal Behavior

Intermediate unavailable triggers fallback to copy strategy with diagnostic; no data loss.

## Dashboard Impact

- Expected row: `gpu-renderer.m18.dst-read-intermediate`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DstReadInter*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M18`
- `area:layers-passes`
