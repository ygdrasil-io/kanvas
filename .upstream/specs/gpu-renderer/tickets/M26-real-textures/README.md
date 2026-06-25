# M26 - Real Textures

## Goal

Replace the procedural texture and glyph-atlas data that M24/M25 used with real
decoded image bytes and a real font glyph atlas. M25 wired the bitmap and text
families to real executors, but those paths still sample a procedural
`CHECKERBOARD` texture and procedural glyph coverage. This milestone uploads
real PNG/JPEG bytes and a real Liberation Sans A8 atlas so the scene evidence
proves decoded images and real glyphs render, not just that the sampling path
runs.

## Dependencies

Depends on M25 wiring (real executors in the offscreen renderer), M17's
`ImageUploadMaterializer`, and M12's `GlyphAtlasUploadPlanner` + font stack.

## Exit Criteria

- [x] PNG/JPEG bytes upload to a GPU texture via `ImageUploadMaterializer` (staging buffer -> texture)
- [x] BitmapShader offscreen render samples real decoded image bytes (no procedural `CHECKERBOARD`)
- [x] Text offscreen render samples a real A8 atlas built from Liberation Sans (no procedural coverage)
- [x] Bitmap/tile-mode scene PNGs show real images instead of checkerboard
- [x] All replaced scene PNGs are committed and show real decoded content

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M26-001 - Upload PNG/JPEG to GPU texture via ImageUploadMaterializer](KGPU-M26-001-image-upload-texture.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M25-001, KGPU-M17-003] | null |
| [KGPU-M26-002 - Wire real texture into BitmapShader offscreen renderer](KGPU-M26-002-bitmap-real-texture.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M26-001] | null |
| [KGPU-M26-003 - Wire real A8 glyph atlas into Text offscreen renderer](KGPU-M26-003-text-real-atlas.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M25-002, KGPU-M12-003] | null |
| [KGPU-M26-004 - Replace bitmap/tile-mode scene PNGs with real-image renders](KGPU-M26-004-scene-real-image-pngs.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M26-001, KGPU-M26-002] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=tile-mode-strip
```

## Non-Claims

- No product activation: these tickets render evidence, they do not flip routes ON
- No new codec support beyond what M17-003 delivers
- No font shaping beyond what M12/M20 deliver
- No performance readiness claims (M27 owns performance gates)
- No hidden CPU-rendered texture compatibility fallback

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
