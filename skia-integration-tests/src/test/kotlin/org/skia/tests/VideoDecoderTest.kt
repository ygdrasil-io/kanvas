package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.FFMPEG: video decode is outside the portable pure-Kotlin codec matrix")
class VideoDecoderTest {

    @Test
    fun `VideoDecoderGM matches reference`() {
        val gm = VideoDecoderGM()
        TestUtils.runGmTest(gm)
    }
}
