package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.FillCircleGM

/**
 * G4.x cross-test : `FillCircleGM` -- 520 x 520 canvas, spiralling
 * stack of concentric AA ovals under scale(20, 20) + translate(13, 13)
 * CTM. Each oval is filled with a deterministic 565-quantised random
 * colour. Pure axis-aligned AA oval fill workout (no shader, no stroke,
 * no clip).
 */
class FillCircleWebGpuTest {

    @Test
    fun `FillCircleGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(FillCircleGM(), floor = 98.50)
    }
}
