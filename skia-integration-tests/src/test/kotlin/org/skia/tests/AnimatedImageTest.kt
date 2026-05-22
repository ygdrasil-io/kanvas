package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.ANIMATED_IMAGE: requires SkAnimatedImage + SkCodec frame decode pipeline")
class AnimatedImageTest {

    @Test
    fun `AnimatedImageGM matches reference`() {
        val gm = AnimatedImageGM()
        TestUtils.runGmTest(gm)
    }
}
