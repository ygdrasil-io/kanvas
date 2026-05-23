package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GANESH_GPU: GrFragmentProcessor::ModulateRGBA requires Ganesh SurfaceDrawContext — no raster path; see MIGRATION_PLAN_GPU_WEBGPU.md")
class ModulateRgbaTest {

    @Test
    fun `ModulateRgbaGM matches reference`() {
        val gm = ModulateRgbaGM()
        TestUtils.runGmTest(gm)
    }
}
