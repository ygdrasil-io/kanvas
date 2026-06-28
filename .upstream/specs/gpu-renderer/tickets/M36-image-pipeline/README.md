# M36 - Image Pipeline Extension

**Status:** active (2026-06-28) — Wave C Track 1

## Goal

Promote HEIF/AVIF gate criteria, YUV multi-plan texture routes, mipmap
auto-generation, and hardware codec descriptors from specs to accepted routes.

## Dependencies

Depends on M0 review completion and M1 product activation policy.

M36-001 and M36-004 depend on accepted KanvasImageCodec registry entries.
Without those entries the gates cannot be promoted from `DependencyGated` to
`ready`.

## Exit Criteria

- [ ] HEIF/AVIF gate criteria are accepted and linked to KanvasImageCodec registry.
- [ ] YUV multi-plan texture routes produce GPU-converted RGB within tolerance.
- [ ] Mipmap auto-generation renders correct minification without regressing nearest sampling.
- [ ] Hardware codec descriptors are registered with nondeterminism policy.
- [ ] All fallbacks emit stable diagnostics and no unsupported route is silently accepted.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M36-001 - HEIF/AVIF gate promotion](KGPU-M36-001-heif-avif-gate-promotion.md) | `blocked` | `P0` | `DependencyGated` | `GPUNative` | `false` | `false` | `images` | `KGPU-M1-001` | `null` |
| [KGPU-M36-002 - YUV multi-plan texture route](KGPU-M36-002-yuv-multi-plan-texture.md) | `review` | `P0` | `TargetNative` | `GPUNative` | `false` | `false` | `images` | `KGPU-M1-001` | `null` |
| [KGPU-M36-003 - Mipmap auto-generation](KGPU-M36-003-mipmap-auto-generation.md) | `review` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `images` | `KGPU-M1-001` | `null` |
| [KGPU-M36-004 - Hardware codec descriptor](KGPU-M36-004-hardware-codec-descriptor.md) | `blocked` | `P1` | `DependencyGated` | `GPUNative` | `false` | `false` | `images` | `KGPU-M1-001` | `null` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HEIF*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*YUV*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Mipmap*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HardwareCodec*'
```

## Non-Claims

- This milestone does not ship codec binaries or link against platform-specific
  hardware codec libraries.
- HEIF/AVIF gate promotion does not silently accept patent-encumbered profiles.
- YUV conversion does not claim pixel-exact parity with all platform decoder
  outputs.
- Mipmap generation does not cover arbitrary non-power-of-two 3D texture arrays.
- Hardware codec descriptors do not imply Android Bitmap/MediaCodec leakage
  into `:gpu-renderer`.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
