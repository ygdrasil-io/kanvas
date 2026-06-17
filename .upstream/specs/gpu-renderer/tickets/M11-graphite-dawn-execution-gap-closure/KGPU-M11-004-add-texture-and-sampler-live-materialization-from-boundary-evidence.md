---
id: KGPU-M11-004
title: "Add texture and sampler live materialization from boundary evidence"
status: proposed
milestone: M11
priority: P1
owner_area: textures-samplers
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M4-004, KGPU-M11-003]
legacy_gate: null
---

# KGPU-M11-004 - Add texture and sampler live materialization from boundary evidence

## PM Note

Ce ticket transforme les preuves de sampler en textures et samplers vivants,
sans élargir le support image.

## Problem

KGPU-M4-004 closes sampler boundary evidence, but explicitly does not claim
native sampler execution or WebGPU texture materialization. The execution gap is
to allocate, upload, import, view, sample, and refuse texture/sampler resources
from the accepted boundary facts.

## Scope

- Materialize `GPUTextureOwnershipPlan`, `GPUTextureDescriptor`,
  `GPUTextureViewDescriptor`, and `GPUSamplerDescriptor` through the provider.
- Cover already-decoded/uploaded texture artifacts and GPU-resident textures
  only where an accepted ownership plan exists.
- Bind sampled texture/sampler pairs through payload/resource binding blocks.
- Preserve M4 sampler refusals for unsupported tile, mip, cubic, anisotropic,
  perspective, swizzle, and usage cases.

## Non-Goals

- Do not add codec delivery, broad image format support, or color-managed decode.
- Do not silently downgrade tile/mipmap/sampling behavior.
- Do not turn sampler boundary evidence into product support without adapter
  readback evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-IMAGE-SAMPLER-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - Reference image shader and sampler behavior facts.
- [`GFX-SAMPLER-DESC`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-sampler-desc) - Reference compact sampler descriptor boundaries.
- [`GFX-DAWN-SAMPLER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dawn-sampler) - Study WebGPU/Dawn sampler translation as a reference only.
- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - Reference explicit upload scheduling.
- Boundary: do not port Graphite proxies or image fallback machinery.

## Design Sketch

```kotlin
data class GPUTextureSamplerMaterializationPlan(
    val ownershipPlan: String,
    val textureRef: String,
    val viewRef: String,
    val samplerRef: String,
    val bindingSlot: String,
)
```

## Acceptance Criteria

- [ ] Accepted texture ownership plans materialize into provider-owned texture,
      view, and sampler refs with generation and usage facts.
- [ ] Uploaded texture artifacts use explicit upload/copy paths and do not
      become CPU-rendered compatibility fallbacks.
- [ ] Sampled texture bindings enter `GPUResourceBindingBlock` and bind groups
      with layout-compatible view/sample types.
- [ ] Missing usage flags, unavailable mipmaps, unsupported sampling modes,
      swizzle requirements, stale generations, and upload failures refuse with
      stable diagnostics.
- [ ] Adapter-backed evidence includes at least one sampled texture readback or
      an explicit skipped-readback reason without support promotion.

## Required Evidence

- Texture/view/sampler materialization dump.
- Sampled binding and bind group dump.
- Upload artifact or GPU-resident provenance dump.
- Refusal fixtures for M4 boundary cases plus stale resource generation.

## Fallback / Refusal Behavior

Unsupported sampling or ownership refuses. The renderer must not change sampler
policy, sample the active attachment, or CPU-render an image into a hidden
fallback texture.

## Dashboard Impact

- Expected row: `gpu-renderer.texture-sampler.materialization`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until adapter-backed evidence is linked and
  reviewed.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Planning-only continuation of KGPU-M4-004 boundary evidence into
  live materialization.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:textures`
