package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.CompressedTexturesGM

@Disabled("STUB.COMPRESSED_TEXTURES: requires BC1/ETC1/ASTC compressed texture decode + GPU upload")
class CompressedTexturesWebGpuTest {
    @Test
    fun `CompressedTexturesGM placeholder`() {
        runGpuCrossTest(CompressedTexturesGM(), floor = 0.0)
    }
}
