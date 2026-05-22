package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PerlinNoiseLocalMatrixGM

/**
 * Cross-backend test : `PerlinNoiseLocalMatrixGM` on raster + GPU.
 * Exercises `SkShader::makeWithLocalMatrix` over perlin noise.
 */
class PerlinNoiseLocalMatrixCrossBackendTest {

    @Test
    fun `PerlinNoiseLocalMatrixGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PerlinNoiseLocalMatrixGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
