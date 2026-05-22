package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BC1TransparencyGM

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/DXT1 compressed texture decode + GPU upload")
class BC1TransparencyWebGpuTest {
    @Test
    fun `BC1TransparencyGM placeholder`() {
        runGpuCrossTest(BC1TransparencyGM(), floor = 0.0)
    }
}
