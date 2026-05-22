package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug12866GM

/**
 * Cross-backend test : `Bug12866GM` on raster + GPU.
 *
 * Reproduces `skbug.com/40043963` -- `SkStroker` recursion-limit issue
 * triggered by a giant `resScale` (1200). 128 x 64 ; renders the same
 * tiny quad-only closed contour twice : left via `drawPath(path,
 * strokePaint)` at default `resScale = 1`, right via an explicit
 * `SkStroker.fromPaint(paint, resScale = 1200).stroke(path)` + fill (the
 * bug-trigger). G3.4.4 stroker stress at extreme subdivision depth.
 *
 * Tiny canvas so per-pixel drift is amplified by the small total pixel
 * count. Floors mirror the existing per-backend tests :
 *  - raster (`Round6Test`, tol=1) : 92.0 % ;
 *  - GPU (`Bug12866WebGpuTest`, tol=8) : 95.19 %.
 */
class Bug12866CrossBackendTest {

    @Test
    fun `Bug12866GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug12866GM(),
            rasterFloor = 92.0,
            gpuFloor = 95.19,
            rasterTolerance = 1,
        )
    }
}
