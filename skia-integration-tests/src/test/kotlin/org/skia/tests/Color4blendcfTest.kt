package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLOR4F_BLEND_CF: SkColorFilters::Blend(SkColor4f, SkColorSpace, SkBlendMode) not yet implemented")
class Color4blendcfTest {

    @Test
    fun `Color4blendcfGM matches color4blendcf_png within tolerance`() {
        val gm = Color4blendcfGM()
        TestUtils.runGmTest(gm)
    }
}
