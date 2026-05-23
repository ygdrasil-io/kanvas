package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.BACKDROP_FILTER: backdrop slot is copy-only, no image-filter execution yet")
class SaveLayerWithBackdropTest {

    @Test
    fun `SaveLayerWithBackdropGM placeholder`() {
        TestUtils.runGmTest(SaveLayerWithBackdropGM())
    }
}
