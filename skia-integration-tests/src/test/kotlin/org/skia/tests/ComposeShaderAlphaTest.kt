package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COMPOSE_SHADER: needs SkShaders.Blend(mode, dst, src) compose-shader factory")
class ComposeShaderAlphaTest {

    @Test
    fun `ComposeShaderAlphaGM matches reference`() {
        val gm = ComposeShaderAlphaGM()
        TestUtils.runGmTest(gm)
    }
}
