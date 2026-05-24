package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.LAZY_YUV_IMAGE: yuv420_odd_dim is GPU-only — requires " +
        "sk_gpu_test::LazyYUVImage::Make(SkData*)->refImage(GrRecordingContext*, kFromPixmaps) " +
        "and canvas->recordingContext() / canvas->recorder() (Ganesh/Graphite GPU context). " +
        "Upstream returns DrawResult::kSkip on any non-GPU canvas."
)
class Yuv420OddDimTest {

    @Test
    fun `Yuv420OddDimGM matches reference`() {
        TestUtils.runGmTest(Yuv420OddDimGM())
    }
}
