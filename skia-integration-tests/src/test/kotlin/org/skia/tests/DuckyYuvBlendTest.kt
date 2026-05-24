package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.LAZY_YUV_IMAGE: LazyYUVImage::Make (GPU-backed mipmapped YUV image) " +
        "not implemented in the kanvas-skia raster backend — second foreground pass " +
        "uses plain JPEG instead of YUV texture (GPU-only feature, Ganesh/SK_GANESH)",
)
class DuckyYuvBlendTest {

    @Test
    fun `DuckyYuvBlendGM matches reference`() {
        val gm = DuckyYuvBlendGM()
        TestUtils.runGmTest(gm)
    }
}
