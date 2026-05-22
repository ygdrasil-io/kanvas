package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ColorCubeGM

@Disabled("STUB.COLOR_CUBE_LUT: needs 3D LUT color-cube filter + GPU shader sampling")
class ColorCubeCrossBackendTest {
    @Test
    fun `ColorCubeGM placeholder`() {
        runCrossBackendTest(ColorCubeGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
