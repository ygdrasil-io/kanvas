package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ColorEmojiGM

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (lib)rsvg color-emoji table dispatch via JNI")
class ColorEmojiWebGpuTest {
    @Test
    fun `ColorEmojiGM placeholder`() {
        runGpuCrossTest(ColorEmojiGM(), floor = 0.0)
    }
}
