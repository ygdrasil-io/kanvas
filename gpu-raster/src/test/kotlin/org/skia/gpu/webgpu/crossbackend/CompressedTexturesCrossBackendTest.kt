package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CompressedTexturesGM

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/ETC1/ASTC compressed texture decode + GPU upload")
class CompressedTexturesCrossBackendTest {
    @Test
    fun `CompressedTexturesGM placeholder`() {
        runCrossBackendTest(CompressedTexturesGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
