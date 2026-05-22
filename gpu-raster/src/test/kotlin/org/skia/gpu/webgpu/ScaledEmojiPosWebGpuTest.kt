package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ScaledEmojiPosGM

@Disabled("STUB.EMOJI_TABLES: colour-emoji typeface dispatch is stubbed")
class ScaledEmojiPosWebGpuTest {
    @Test
    fun `ScaledEmojiPosGM placeholder`() {
        runGpuCrossTest(ScaledEmojiPosGM(), floor = 0.0)
    }
}
