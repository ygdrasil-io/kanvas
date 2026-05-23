package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/beziereffects.cpp` — two `GpuGM`s:
 *
 *  - [BezierConicEffectsGM] — GM name `bezier_conic_effects`, 128 × 1280
 *  - [BezierQuadEffectsGM]  — GM name `bezier_quad_effects`,  128 × 640
 *
 * Both GMs are classified **INTRACTABLE.GANESH_EFFECT**: they exercise
 * internal Ganesh GPU geometry-processor primitives (`GrConicEffect` /
 * `GrQuadEffect`) through `GrMeshDrawOp` and `SurfaceDrawContext::addDrawOp`.
 * The draw path bypasses the public `SkCanvas` API entirely — commands go
 * through `BezierConicTestOp` / `BezierQuadTestOp` → `GrBezierEffect.h` →
 * GPU shader programs compiled by `GrGLSLProgramBuilder`. None of these
 * facilities exist in `:kanvas-skia` (WebGPU / CPU-raster pipeline).
 *
 * The upstream `onDraw` signature is
 * ```cpp
 * DrawResult onDraw(GrRecordingContext* rContext, SkCanvas* canvas, SkString* errorMsg)
 * ```
 * — a `GpuGM`, not a plain `GM`. Without `rContext` / `SurfaceDrawContext`
 * there is nothing to render; the bodies call `TODO("STUB.GANESH_BEZIER_EFFECT")`
 * so the compile contract holds and both test classes are `@Disabled`.
 *
 * Upstream file: `gm/beziereffects.cpp`
 * Relevant includes: `src/gpu/ganesh/effects/GrBezierEffect.h`,
 * `src/gpu/ganesh/SurfaceDrawContext.h`, `src/gpu/ganesh/ops/GrMeshDrawOp.h`.
 */

/**
 * Stub GM for `bezier_conic_effects` (upstream `BezierConicEffects : GpuGM`).
 *
 * Renders 10 rows of 128 × 128 conic-effect cells via `GrConicEffect` +
 * `BezierConicTestOp`. **INTRACTABLE.GANESH_EFFECT** — requires Ganesh
 * `GrRecordingContext` and internal GPU draw-op infrastructure.
 *
 * C++ size: `SkISize::Make(128, 10 * 128)` = 128 × 1280.
 */
public class BezierConicEffectsGM : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "bezier_conic_effects"
    override fun getISize(): SkISize = SkISize.Make(kCellWidth, kNumConics * kCellHeight)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GANESH_BEZIER_EFFECT")
    }

    private companion object {
        const val kNumConics: Int  = 10
        const val kCellWidth: Int  = 128
        const val kCellHeight: Int = 128
    }
}

/**
 * Stub GM for `bezier_quad_effects` (upstream `BezierQuadEffects : GpuGM`).
 *
 * Renders 5 rows of 128 × 128 quad-effect cells via `GrQuadEffect` +
 * `BezierQuadTestOp`. **INTRACTABLE.GANESH_EFFECT** — requires Ganesh
 * `GrRecordingContext` and internal GPU draw-op infrastructure.
 *
 * C++ size: `SkISize::Make(128, 5 * 128)` = 128 × 640.
 */
public class BezierQuadEffectsGM : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "bezier_quad_effects"
    override fun getISize(): SkISize = SkISize.Make(kCellWidth, kNumQuads * kCellHeight)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GANESH_BEZIER_EFFECT")
    }

    private companion object {
        const val kNumQuads: Int   = 5
        const val kCellWidth: Int  = 128
        const val kCellHeight: Int = 128
    }
}
