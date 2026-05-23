package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.FIXTURE: requires upstream `images/stoplight_h.webp` / `images/flightAnim.gif` " +
        "fixtures, which are not shipped with `:cpu-raster` (codec-fixtures dir holds " +
        "`stoplight.webp`, a different file). Body is fully ported against the live " +
        "`org.skia.codec.{SkCodec, SkAndroidCodec, SkAnimatedImage}` surface — drop " +
        "this `@Disabled` once the fixtures land in `kanvas-legacy/src/test/resources/images/`.",
)
class AnimatedImageTest {

    @Test
    fun `AnimatedImageGM matches reference`() {
        val gm = AnimatedImageGM()
        TestUtils.runGmTest(gm)
    }
}
