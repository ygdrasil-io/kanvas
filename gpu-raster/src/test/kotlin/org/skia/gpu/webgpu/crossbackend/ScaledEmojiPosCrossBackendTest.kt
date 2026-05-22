package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ScaledEmojiPosGM

@Disabled("STUB.EMOJI_TABLES: colour-emoji typeface dispatch is stubbed")
class ScaledEmojiPosCrossBackendTest {
    @Test
    fun `ScaledEmojiPosGM placeholder`() {
        runCrossBackendTest(ScaledEmojiPosGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
