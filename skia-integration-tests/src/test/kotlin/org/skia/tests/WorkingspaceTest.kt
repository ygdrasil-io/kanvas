package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.WORKING_SPACE_RT: workingspace GM blocked by (1) STUB.COLOR4F_BLEND_CF" +
        " (SkColorFilters.Blend with SkColor4f + kSrc), (2) STUB.CF_SHADER_CHILD" +
        " (SkRuntimeEffect.makeColorFilter with shader-typed child slots), and" +
        " (3) unregistered SkSL programs for raw/managed color-filter and shader effects.",
)
class WorkingspaceTest {

    @Test
    fun `WorkingspaceGM placeholder`() {
        TestUtils.runGmTest(WorkingspaceGM())
    }
}
