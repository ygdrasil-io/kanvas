---
id: KGPU-M29-005
title: "KanvasShader — solid, linear, radial, sweep, bitmap, render target"
status: proposed
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M29-003]
legacy_gate: null
---

# KGPU-M29-005 - KanvasShader — solid, linear, radial, sweep, bitmap, render target

## PM Note

`KanvasShader` donne acces aux shaders GPU natifs (couleur unie, degradEs lineaires,
radiaux, balayage, bitmap, render target) sans passer par `SkShader`. Ce ticket
permet au PM de voir les effets de remplissage Kanvas en action.

## Problem

`KanvasPaint` references a `KanvasShader` for non-solid fills, but no shader types
exist in the native API. Gradients, bitmap fills, and render-target sampling are
impossible without shader definitions.

## Scope

- Define `KanvasShader` sealed class
- Implement `SolidShader(color)`
- Implement `LinearGradientShader(start, end, colors, positions, tileMode, matrix?)`
- Implement `RadialGradientShader(center, radius, colors, positions, tileMode, matrix?)`
- Implement `SweepGradientShader(center, startAngle, colors, positions, tileMode, matrix?)`
- Implement `BitmapShader(image, tileModeX, tileModeY, matrix?)`
- Implement `RenderTargetShader(renderTarget, tileModeX, tileModeY, matrix?)`
- Map each shader to GPU uniform/material slots

## Non-Goals

- No shader compilation or WGSL generation (handled by existing pipeline)
- No runtime effect shaders (M21, M7)
- No image decoding (KGPU-M29-006)
- No fractal or noise shaders (future milestone)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/03-material-key-wgsl.md`
- `.upstream/specs/gpu-renderer/16-material-dictionary-and-snippet-registry.md`
- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M14-gradients-radial-sweep/README.md`

## Design Sketch

```kotlin
sealed class KanvasShader {
    data class Solid(val color: Color) : KanvasShader()
    data class LinearGradient(
        val start: Point, val end: Point,
        val colors: List<Color>, val positions: List<Float>?,
        val tileMode: TileMode, val matrix: Matrix4? = null,
    ) : KanvasShader()
    data class RadialGradient(
        val center: Point, val radius: Float,
        val colors: List<Color>, val positions: List<Float>?,
        val tileMode: TileMode, val matrix: Matrix4? = null,
    ) : KanvasShader()
    data class SweepGradient(
        val center: Point, val startAngle: Float,
        val colors: List<Color>, val positions: List<Float>?,
        val tileMode: TileMode, val matrix: Matrix4? = null,
    ) : KanvasShader()
    data class Bitmap(
        val image: KanvasImage, val tileModeX: TileMode, val tileModeY: TileMode,
        val matrix: Matrix4? = null,
    ) : KanvasShader()
    data class RenderTarget(
        val renderTarget: KanvasSurface, val tileModeX: TileMode, val tileModeY: TileMode,
        val matrix: Matrix4? = null,
    ) : KanvasShader()
}
```

## Acceptance Criteria

- [ ] `KanvasShader` sealed class compiles with all six variants
- [ ] `TileMode` enum compiles (Clamp, Repeat, Mirror, Decal)
- [ ] Each shader variant produces correct GPU uniform/material key data
- [ ] Gradients with stops produce correct color/position arrays

## Required Evidence

- `KanvasShader.kt` committed with all six shader variants
- Shader-to-uniform dump transcript for: linear gradient, radial gradient, sweep gradient
- Bitmap shader material key dump
- Diagnostic output for unsupported shader configurations

## Fallback / Refusal Behavior

Shader variants with unsupported tile modes or color-stop counts emit
`unsupported-shader-config` diagnostics. No CPU gradient rasterization fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-shader`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-api:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas-api`
