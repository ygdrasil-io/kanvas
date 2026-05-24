package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.WORKING_COLOR_SPACE_IO: workingspace_input_output requires the four-argument" +
        " SkWorkingColorSpaceShader::Make(child, inputCS, outputCS, workInUnpremul)" +
        " — not yet exposed in the kanvas-skia public API.",
)
class WorkingspaceInputOutputTest {

    @Test
    fun `WorkingspaceInputOutputGM placeholder`() {
        TestUtils.runGmTest(WorkingspaceInputOutputGM())
    }
}
