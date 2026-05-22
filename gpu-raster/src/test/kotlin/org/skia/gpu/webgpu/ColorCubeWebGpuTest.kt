package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ColorCubeGM

@Disabled("STUB.COLOR_CUBE_LUT: needs 3D LUT color-cube filter + GPU shader sampling")
class ColorCubeWebGpuTest {
    @Test
    fun `ColorCubeGM placeholder`() {
        runGpuCrossTest(ColorCubeGM(), floor = 0.0)
    }
}
