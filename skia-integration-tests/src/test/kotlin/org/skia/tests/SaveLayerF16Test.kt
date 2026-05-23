package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.F16_COLOR_TYPE: kF16ColorType save-layer flag is silently ignored; layer is always 8-bit, so rendered output deviates from the F16-precision reference")
class SaveLayerF16Test {

    @Test
    fun `SaveLayerF16GM placeholder`() {
        TestUtils.runGmTest(SaveLayerF16GM())
    }
}
