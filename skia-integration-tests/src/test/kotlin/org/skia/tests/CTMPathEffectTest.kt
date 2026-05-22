package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.PATH_EFFECT_CTM: needs CTM-aware SkPath1DPathEffect / SkDiscretePathEffect")
class CTMPathEffectTest {

    @Test
    fun `CTMPathEffectGM matches reference`() {
        val gm = CTMPathEffectGM()
        TestUtils.runGmTest(gm)
    }
}
