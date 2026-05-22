package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.ASYNC_RESCALE_AND_READ_YUV: requires SkSurface.asyncRescaleAndReadPixelsYUV420")
class AyncYUVNoScaleTest {

    @Test
    fun `AyncYUVNoScaleGM matches reference`() {
        val gm = AyncYUVNoScaleGM()
        TestUtils.runGmTest(gm)
    }
}
