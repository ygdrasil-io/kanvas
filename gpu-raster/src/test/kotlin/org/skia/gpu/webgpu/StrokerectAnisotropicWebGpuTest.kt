package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.StrokerectAnisotropicGM

/**
 * Cross-test : `StrokerectAnisotropicGM` on the GPU backend.
 *
 * 4 x 2 grid : `{miter, miter-half-pixel, bevel, bevel-half-pixel}` x
 * `{AA, non-AA}`, each cell drawing a `1000x20` rect routed through
 * `drawPath(SkPath.Rect(...))` under anisotropic `scale(0.03, 2)`.
 * Originally `crbug.com/935303` regression repro for anisotropic
 * stroke-rect bugs.
 *
 * G3.4.4 joins coverage : miter and bevel joins under heavy anisotropic
 * CTM (resScale stress) on stroked rects routed via path. Validates that
 * the stroker's join geometry is correct when source and device aspect
 * ratios diverge.
 */
class StrokerectAnisotropicWebGpuTest {

    @Test
    fun `StrokerectAnisotropicGM renders close to reference PNG on the GPU backend`() {
        // Score : 98.11 %. Drift on the anisotropic stroke edges
        // (heavy aspect-ratio CTM) and on the half-pixel-offset
        // columns where stroke edges land between integer rows.
        runGpuCrossTest(
            StrokerectAnisotropicGM(),
            floor = 98.06,
            logTag = "StrokerectAnisotropicWebGpu",
        )
    }
}
