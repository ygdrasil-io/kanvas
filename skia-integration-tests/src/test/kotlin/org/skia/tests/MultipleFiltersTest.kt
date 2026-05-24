package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.IFX.MULTIPLE_FILTERS_SPAN: SkCanvas.saveLayerWithMultipleFilters not yet implemented")
class MultipleFiltersTest {

    @Test
    fun `MultipleFiltersGM matches reference`() {
        TestUtils.runGmTest(MultipleFiltersGM())
    }
}
