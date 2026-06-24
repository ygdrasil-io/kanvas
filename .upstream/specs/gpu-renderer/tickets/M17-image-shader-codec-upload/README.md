# M17 - Image Shader + Codec Upload

## Goal

Deliver BitmapShader WGSL, BitmapRect execution, image upload materialization, and tile mode expansion with controlled product activation.

## Dependencies

Depends on M12 codec pipeline (KGPU-M12-006, KGPU-M12-007). Wave 1 milestone, parallel with M13 and M15.

## Exit Criteria

- [ ] BitmapShader samples textures with correct filter mode
- [ ] BitmapRect draws images with correct UV mapping
- [ ] Image uploads materialize decoded pixels to GPU textures
- [ ] All four tile modes (Clamp, Repeat, Mirror, Decal) render correctly
- [ ] BitmapShader and BitmapRect are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M17-001 - Add BitmapShader WGSL: texture sample + nearest/linear filter + clamp tile](KGPU-M17-001-bitmap-shader.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | [KGPU-M12-007] | null |
| [KGPU-M17-002 - Add BitmapRect execution: image rect draw with texture binding + sampler](KGPU-M17-002-bitmap-rect-execution.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M17-001] | null |
| [KGPU-M17-003 - Add GPU image upload materialization: decoded pixels -> staging buffer -> texture](KGPU-M17-003-image-upload-materialization.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `resources-execution` | [KGPU-M12-006, KGPU-M17-001] | null |
| [KGPU-M17-004 - Add tile mode expansion: Repeat + Mirror + Decal via WGSL math](KGPU-M17-004-tile-mode-expansion.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | [KGPU-M17-001] | null |
| [KGPU-M17-005 - Activate M17 routes: BitmapShader + BitmapRect default ON with rollback](KGPU-M17-005-route-activation.md) | `proposed` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M17-001, KGPU-M17-002, KGPU-M17-003, KGPU-M17-004] | legacy drawImage |
| [KGPU-M17-006 - Add gpu-renderer-scenes evidence: bitmap-sampler-matrix, tile-mode-strip](KGPU-M17-006-scenes-evidence.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M17-001, KGPU-M17-002, KGPU-M17-003, KGPU-M17-004] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*BitmapShader*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*BitmapRect*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImageUpload*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TileMode*'
```

## Non-Claims

- No mipmap, no anisotropic filter
- No color-managed decode
- No YUV formats
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
