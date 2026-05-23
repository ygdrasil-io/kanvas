package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ScaledEmojiPerspectiveGM

@Disabled("STUB.EMOJI_TABLES: colour-emoji typeface dispatch is stubbed")
class ScaledEmojiPerspectiveWebGpuTest {
    @Test
    fun `ScaledEmojiPerspectiveGM placeholder`() {
        runGpuCrossTest(ScaledEmojiPerspectiveGM(), floor = 0.0)
    }
}
