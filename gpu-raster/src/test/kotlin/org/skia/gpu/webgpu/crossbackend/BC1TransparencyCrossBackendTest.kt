package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BC1TransparencyGM

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/DXT1 compressed texture decode + GPU upload")
class BC1TransparencyCrossBackendTest {
    @Test
    fun `BC1TransparencyGM placeholder`() {
        runCrossBackendTest(BC1TransparencyGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
