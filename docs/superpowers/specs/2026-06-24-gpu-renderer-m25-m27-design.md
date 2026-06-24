# GPU Renderer M25-M27 — Câblage, Textures, Performance

Date: 2026-06-24
Status: Accepted
Scope: `:gpu-renderer`, `:gpu-renderer-scenes`, `:font`

## Goal

Complete the GPU renderer pipeline by wiring remaining executors into the
offscreen renderer (M25), replacing procedural textures with real image
uploads (M26), and adding measured performance benchmarks (M27).

## Starting Point

After M24: 129 tickets done. 6 families have real GPU rendering (rect, rrect,
linear/radial/sweep gradients, blur, colorMatrix, stroke). 8 families have
contracts/executors/stubs but are not wired into the offscreen rendering
pipeline.

## M25 — Câblage manquant (6 tickets)

Replace all procedural shaders and executor stubs with real GPU pipeline
calls in `RectOnlyOffscreenRenderer`.

| ID | Title | Depends On |
|----|-------|-----------|
| KGPU-M25-001 | Wire BitmapShader with real GPU sampling | M17 |
| KGPU-M25-002 | Wire Text A8 + SDF atlas rendering | M20, M12 |
| KGPU-M25-003 | Wire RuntimeEffect execution | M21 |
| KGPU-M25-004 | Wire SaveLayer offscreen target + composite | M18 |
| KGPU-M25-005 | Wire PathFill (tessellation + stencil-cover + convex fan) | M15 |
| KGPU-M25-006 | Wire Vertices mesh rendering | M22 |

Each ticket: replace procedural WGSL constant with real executor call,
update scene PNGs, commit evidence.

## M26 — Textures réelles (4 tickets)

Replace generated textures with real image uploads.

| ID | Title | Depends On |
|----|-------|-----------|
| KGPU-M26-001 | Upload PNG/JPEG → GPU texture via ImageUploadMaterializer | M25 |
| KGPU-M26-002 | Wire real texture into BitmapShader offscreen renderer | M26-001 |
| KGPU-M26-003 | Wire real A8 glyph atlas into Text offscreen renderer | M25, M12 |
| KGPU-M26-004 | Replace bitmap/tile-mode scene PNGs with real-image renders | M26-001, M26-002 |

## M27 — Performance (3 tickets)

Measured benchmarks for each draw family.

| ID | Title | Depends On |
|----|-------|-----------|
| KGPU-M27-001 | Per-family benchmark (FillRect, gradient, path, bitmap, text, blur, vertices) | M25, M26 |
| KGPU-M27-002 | Pipeline cache telemetry (hit rate, eviction, module count) | M25 |
| KGPU-M27-003 | Frame gate policy (60fps target, 30fps warning, Apple M-series) | M27-001 |

## Total

13 tickets across 3 milestones.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk git diff --check
```
