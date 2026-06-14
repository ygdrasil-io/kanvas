# M4 - Image, Texture, Codec, And Upload

## Goal

Add image shader and texture upload routes with explicit ownership, codec,
sampler, color, and animation boundaries.

## Dependencies

Depends on M2 material/WGSL/resource foundations. Codec routes remain
dependency-gated until accepted codec descriptors and deterministic evidence
exist.

## Exit Criteria

- [ ] Image sources have dumpable texture ownership and upload plans.
- [ ] Codec provenance is visible and unsupported formats refuse stably.
- [ ] Sampler, tile, mip, color, and orientation facts are explicit.
- [ ] No arbitrary image/codec or CPU-rendered compatibility texture is hidden.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M4-001 - Add image shader route for already-decoded pixels](KGPU-M4-001-add-image-shader-route-for-already-decoded-pixels.md) | `proposed` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `images-textures` | `KGPU-M2-002` | `bitmap legacy` |
| [KGPU-M4-002 - Add uploaded texture artifact ownership gates](KGPU-M4-002-add-uploaded-texture-artifact-ownership-gates.md) | `proposed` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `resources-images` | `KGPU-M4-001` | - |
| [KGPU-M4-003 - Add codec provenance and dependency-gated refusals](KGPU-M4-003-add-codec-provenance-and-dependency-gated-refusals.md) | `proposed` | `P1` | `DependencyGated` | `CPUPreparedGPU` | `false` | `false` | `images-codecs` | `KGPU-M4-002` | `codec legacy` |
| [KGPU-M4-004 - Add sampler tile and mipmap boundary evidence](KGPU-M4-004-add-sampler-tile-and-mipmap-boundary-evidence.md) | `proposed` | `P1` | `TargetNative` | `mixed` | `false` | `true` | `textures-samplers` | `KGPU-M4-001` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Image*' --tests '*Bitmap*'
```

## Non-Claims

- No broad codec, animation, mipmap, perspective sampling, or color-managed
  decode support.
- No implicit first-frame animation behavior.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
