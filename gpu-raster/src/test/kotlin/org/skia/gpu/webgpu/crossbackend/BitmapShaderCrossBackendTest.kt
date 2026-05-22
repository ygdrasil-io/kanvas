package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BitmapShaderGM

@Disabled("ALIAS: upstream BitmapShaderGM — partial coverage in ClippedBitmapShaders/BitmapTiled/BitmapSubsetShader cross-tests")
class BitmapShaderCrossBackendTest {
    @Test
    fun `BitmapShaderGM placeholder`() {
        runCrossBackendTest(BitmapShaderGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
