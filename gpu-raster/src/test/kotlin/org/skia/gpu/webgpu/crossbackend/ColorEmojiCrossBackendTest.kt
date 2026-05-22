package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ColorEmojiGM

@Disabled("STUB.EMOJI_TABLES: requires FreeType + (lib)rsvg color-emoji table dispatch via JNI")
class ColorEmojiCrossBackendTest {
    @Test
    fun `ColorEmojiGM placeholder`() {
        runCrossBackendTest(ColorEmojiGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
