package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokerectAnisotropicGM

/**
 * Cross-backend test : `StrokerectAnisotropicGM`
 * (`strokerect_anisotropic`) on raster + GPU.
 *
 * 4 x 2 grid : `{miter, miter-half-pixel, bevel, bevel-half-pixel}` x
 * `{AA, non-AA}`, each cell drawing a `1000 x 20` rect routed through
 * `drawPath(SkPath.Rect(...))` under anisotropic `scale(0.03, 2)`.
 * Originally a `crbug.com/935303` regression repro for anisotropic
 * stroke-rect bugs.
 *
 * G3.4.4 joins coverage : miter and bevel joins under heavy anisotropic
 * CTM (resScale stress) on stroked rects routed via path. Validates that
 * the stroker's join geometry is correct when source and device aspect
 * ratios diverge -- a different stress profile from the `Strokerect-
 * Anisotropic5408GM` cross-test already in place (50 x 200 single rect
 * at default scale).
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round11Test`, tol=1) : 80.0 % (lax to absorb anisotropic
 *    AA drift) ;
 *  - GPU (`StrokerectAnisotropicWebGpuTest`, tol=8) : 98.06 %.
 */
class StrokerectAnisotropicCrossBackendTest {

    @Test
    fun `StrokerectAnisotropicGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokerectAnisotropicGM(),
            rasterFloor = 80.0,
            gpuFloor = 98.06,
            rasterTolerance = 1,
        )
    }
}
