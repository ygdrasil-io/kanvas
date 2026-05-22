package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("ALIAS: upstream AnisotropicGM(Mode) — already ported as AnisotropicImageScale{Linear,Mip,Aniso}GM")
class AnisotropicTest {

    @Test
    fun `AnisotropicGM placeholder alias`() {
        val gm = AnisotropicGM()
        TestUtils.runGmTest(gm)
    }
}
