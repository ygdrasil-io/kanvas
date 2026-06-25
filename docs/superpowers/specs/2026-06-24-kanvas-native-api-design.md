# Kanvas Native API — M29 M30 M31

Date: 2026-06-24
Status: Accepted
Scope: `:kanvas-api` (new), `:gpu-renderer`, `:kanvas-skia`

## Goal

Create a native Kanvas public API (`:kanvas-api`) backed by `:gpu-renderer`, with
Skia compatibility as a legacy wrapper. Replace `gpu-raster` as the default
renderer path.

## Starting Point

148 tickets done. All 8 draw families have real GPU rendering. The pipeline
(planners, WGSL, materials, product flags) is complete. Missing: a public API
that uses it.

## M29 — `:kanvas-api` (8 tickets)

New module, zero Skia dependency. Package `org.graphiks.kanvas.api`.

| ID | Title |
|----|-------|
| KGPU-M29-001 | `:kanvas-api` module skeleton + `KanvasSurface` |
| KGPU-M29-002 | `KanvasCanvas` — drawRect/drawRRect/drawPath/drawImage/drawText |
| KGPU-M29-003 | `KanvasPaint` — color, shader, blendMode, colorFilter, stroke |
| KGPU-M29-004 | `KanvasPath` — moveTo/lineTo/quadTo/cubicTo/close + fillType |
| KGPU-M29-005 | `KanvasShader` — solid, linearGradient, radialGradient, sweep, bitmap, runtimeEffect |
| KGPU-M29-006 | `KanvasImage` — decode PNG/JPEG/WebP → texture |
| KGPU-M29-007 | `KanvasTextBlob` — glyphRun + positionnement |
| KGPU-M29-008 | `KanvasSurface.flush()` → GPU submit |

## M30 — Skia wrapper + gpu-raster retirement (4 tickets)

| ID | Title |
|----|-------|
| KGPU-M30-001 | `KanvasSkiaBridge` — SkCanvas → KanvasCanvas translation |
| KGPU-M30-002 | Route SkSurface → KanvasSurface (replace gpu-raster path) |
| KGPU-M30-003 | Regression tests: Skia GM parity via Kanvas bridge |
| KGPU-M30-004 | gpu-raster deprecation + legacy route freeze |

## M31 — Production activation (4 tickets)

| ID | Title |
|----|-------|
| KGPU-M31-001 | Default renderer = Kanvas (productActivation=true) |
| KGPU-M31-002 | Rollback flag: disable Kanvas → restore legacy gpu-raster |
| KGPU-M31-003 | Final PM evidence bundle (all families, gate green) |
| KGPU-M31-004 | Release notes + API stability freeze |

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-api:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk git diff --check
```
