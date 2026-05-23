package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.GANESH_FWIDTH: FwidthSquircleGM requires Ganesh-private GPU pipeline " +
        "types (GrGeometryProcessor, GrDrawOp, GrGLSLFragmentShaderBuilder, " +
        "SurfaceDrawContext::addDrawOp, and the GLSL fwidth() screen-space " +
        "derivative built-in) which have no raster equivalent in kanvas-skia. " +
        "The upstream GM explicitly skips on non-Ganesh contexts.",
)
class FwidthSquircleTest {

    @Test
    fun `FwidthSquircleGM matches reference`() {
        val gm = FwidthSquircleGM()
        TestUtils.runGmTest(gm)
    }
}
