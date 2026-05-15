package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.WEBP_LOSSY: requires libwebp via JNI — see API_FINALIZATION_PLAN.md")
class EncodeAlphaJpegLossyWebpTest {

    @Test
    fun `EncodeAlphaJpegLossyWebpGM matches reference`() {
        val gm = EncodeAlphaJpegLossyWebpGM()
        TestUtils.runGmTest(gm)
    }
}
