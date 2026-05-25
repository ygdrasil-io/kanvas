package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.WEBP_LOSSY: lossy WebP encode is outside the portable pure-Kotlin codec matrix")
class EncodeAlphaJpegLossyWebpTest {

    @Test
    fun `EncodeAlphaJpegLossyWebpGM matches reference`() {
        val gm = EncodeAlphaJpegLossyWebpGM()
        TestUtils.runGmTest(gm)
    }
}
