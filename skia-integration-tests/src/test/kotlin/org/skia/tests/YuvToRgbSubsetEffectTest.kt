package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.LAZY_YUV_IMAGE: yuv_to_rgb_subset_effect requires " +
        "sk_gpu_test::LazyYUVImage + SkImage::makeSubset(SkRecorder*) — " +
        "no CPU-raster equivalent in kanvas-skia"
)
class YuvToRgbSubsetEffectTest {

    @Test
    fun `YuvToRgbSubsetEffectGM placeholder`() {
        TestUtils.runGmTest(YuvToRgbSubsetEffectGM())
    }
}
