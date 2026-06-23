---
id: KGPU-M12-007
title: "Wire codec provenance into GPUImagePipelinePlan (accept PNG/JPEG/WebP/GIF)"
status: done
milestone: M12
priority: P0
owner_area: codec-pipeline
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-005, KGPU-M12-006]
legacy_gate: null
---

# KGPU-M12-007 - Wire codec provenance into GPUImagePipelinePlan (accept PNG/JPEG/WebP/GIF)

## PM Note

La traçabilité codec dans le pipeline plan est essentielle pour le diagnostic. Sans provenance, impossible de savoir si un refus vient du codec ou du shader.

## Problem

The decode-and-upload pipeline must be wired into GPUImagePipelinePlan with codec provenance tracking so that image source failures are distinguishable from shader or texture failures.

## Scope

- Add GPUImagePipelinePlan with codec provenance metadata
- Wire decode->upload->plan flow for PNG/JPEG/WebP/GIF
- Add provenance diagnostics in pipeline plan dumps
- Produce end-to-end image pipeline trace

## Non-Goals

- No image filtering or transformation in pipeline plan
- No lazy or deferred decode

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_IMAGE_COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-copy) - source src/gpu/graphite/ImageProvider.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUImagePipelinePlan(val source: ImageSource, val codecProvenance: CodecProvenance, val uploadPlan: TextureUploadPlan)
```

## Acceptance Criteria

- [ ] PNG/JPEG/WebP/GIF images flow through complete pipeline plan
- [ ] Codec provenance is recorded and visible in diagnostics
- [ ] Pipeline plan dumps distinguish codec failure from upload failure

## Required Evidence

- GPUImagePipelinePlan dump for each supported format
- Codec provenance trace for successful and failed decodes
- End-to-end pipeline transcript

## Fallback / Refusal Behavior

Codec pipeline failures emit stable diagnostic with provenance information; image route remains disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.codec.pipeline-plan`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImagePipeline*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:codec-pipeline`
