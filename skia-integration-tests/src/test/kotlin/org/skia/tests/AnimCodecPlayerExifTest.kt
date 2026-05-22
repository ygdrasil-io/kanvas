package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.ANIM_CODEC_PLAYER: requires SkAnimCodecPlayer + SkCodec frame-by-frame seek")
class AnimCodecPlayerExifTest {

    @Test
    fun `AnimCodecPlayerExifGM matches reference`() {
        val gm = AnimCodecPlayerExifGM()
        TestUtils.runGmTest(gm)
    }
}
