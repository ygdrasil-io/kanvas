package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GANESH_GPU: GrFragmentProcessor::MakeColor requires Ganesh SurfaceDrawContext — no raster path; see MIGRATION_PLAN_GPU_WEBGPU.md")
class ConstColorProcessorTest {

    @Test
    fun `ConstColorProcessorGM matches reference`() {
        val gm = ConstColorProcessorGM()
        TestUtils.runGmTest(gm)
    }
}
