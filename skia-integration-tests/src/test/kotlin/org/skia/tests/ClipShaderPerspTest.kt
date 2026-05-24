package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.POLY_TO_POLY: requires SkMatrix.setPolyToPoly (4-point perspective solve) — clip_shader_persp")
class ClipShaderPerspTest {

    @Test
    fun `ClipShaderPerspGM matches clip_shader_persp_png within tolerance`() {
        val gm = ClipShaderPerspGM()
        TestUtils.runGmTest(gm)
    }
}
