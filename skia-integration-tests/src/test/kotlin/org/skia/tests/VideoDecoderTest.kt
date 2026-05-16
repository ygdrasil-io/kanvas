package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.FFMPEG: requires FFmpeg libavformat/libavcodec via JNI — see API_FINALIZATION_PLAN.md")
class VideoDecoderTest {

    @Test
    fun `VideoDecoderGM matches reference`() {
        val gm = VideoDecoderGM()
        TestUtils.runGmTest(gm)
    }
}
