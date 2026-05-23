package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.COMPRESSED_TEXTURES: SkImages.RasterFromCompressedTextureData + " +
        "SkCompressedDataUtils.SkCompressedDataSize are flag-planted as " +
        "TODO() in :kanvas-skia — the BC1 / DXT1 block-decompression " +
        "routine has not landed. Body is fully ported against the live " +
        "SkTextureCompressionType / SkImages surface ; drop this " +
        "@Disabled once the decode lands.",
)
class BC1TransparencyTest {

    @Test
    fun `BC1TransparencyGM matches reference`() {
        val gm = BC1TransparencyGM()
        TestUtils.runGmTest(gm)
    }
}
