package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.LAZY_YUV_IMAGE + STUB.YUVA_PIXMAPS: yuv420_odd_dim_repeat is GPU-only — " +
        "requires SkGpuTestUtils.MakeYUVAPlanesAsA8 (split kernel + RGB→YUV matrices), " +
        "LazyYUVImage::Make(SkYUVAPixmaps, Mipmapped::kYes)->refImage(recorder, kFromPixmaps), " +
        "and canvas->recordingContext() / canvas->recorder() (Ganesh/Graphite GPU context). " +
        "Upstream returns DrawResult::kSkip on any non-GPU canvas. " +
        "Regression test for crbug.com/1210557 (subsampled planes not repeated at correct frequency)."
)
class Yuv420OddDimRepeatTest {

    @Test
    fun `Yuv420OddDimRepeatGM matches reference`() {
        TestUtils.runGmTest(Yuv420OddDimRepeatGM())
    }
}
