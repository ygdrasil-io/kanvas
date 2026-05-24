package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.VULKAN_YCBCR_SAMPLER: ycbcrimage requires VkYcbcrSamplerHelper + Vulkan-backed SkImage — no CPU-raster equivalent in kanvas-skia")
class YCbCrImageTest {

    @Test
    fun `YCbCrImageGM matches reference`() {
        val gm = YCbCrImageGM()
        TestUtils.runGmTest(gm)
    }
}
