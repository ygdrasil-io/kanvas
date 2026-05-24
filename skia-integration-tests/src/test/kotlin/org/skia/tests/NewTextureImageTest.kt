package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GPU_TEXTURE_FROM_IMAGE: SkImages::TextureFromImage is GPU-only; no raster equivalent in kanvas-skia")
class NewTextureImageTest {

    @Test
    fun `NewTextureImageGM matches reference`() {
        val gm = NewTextureImageGM()
        TestUtils.runGmTest(gm)
    }
}
