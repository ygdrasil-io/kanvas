package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BitmapShaderGM

@Disabled("ALIAS: upstream BitmapShaderGM — partial coverage in ClippedBitmapShaders/BitmapTiled/BitmapSubsetShader WebGpu tests")
class BitmapShaderWebGpuTest {
    @Test
    fun `BitmapShaderGM placeholder`() {
        runGpuCrossTest(BitmapShaderGM(), floor = 0.0)
    }
}
