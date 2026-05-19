package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SmallArcGM

/**
 * Cross-test : `SmallArcGM` on the GPU backend.
 *
 * One red AA stroked cubic Bezier approximating a quarter-arc, drawn at
 * `strokeWidth = 120` under `translate(-400,-400) ; scale(8, 8)`. The
 * upstream-style ¾-arc lands as a thick wedge across the 762 × 762 canvas.
 *
 * G3.4.1 stroke-style coverage : the stroker's `resScale` is exercised at
 * CTM scale 8×. The single open cubic becomes one closed outline that
 * routes recursively through `drawPath` as an AA single-contour concave
 * fill (G3.3b.3a.2).
 */
class SmallArcWebGpuTest {

    @Test
    fun `SmallArcGM renders close to reference PNG on the GPU backend`() {
        // First stroke-via-cubic GM under CTM 8× exercising
        // SkStroker.resScale. Score : 99.80 %.
        runGpuCrossTest(SmallArcGM(), floor = 99.75, logTag = "SmallArcWebGpu")
    }
}
