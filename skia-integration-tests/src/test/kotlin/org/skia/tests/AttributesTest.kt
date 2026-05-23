package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * `@Disabled` — [AttributesGM] is a Ganesh-internal GM with no
 * canvas-level equivalent in `:kanvas-skia`.
 *
 * The upstream `attributes` GM exercises explicit and implicit
 * vertex-attribute offsets and strides via `GrGeometryProcessor`,
 * `GrDrawOp`, `GrGpuBuffer`, `GrRecordingContext`, and Ganesh GLSL
 * emitters — all private Ganesh backend types that have no counterpart
 * in the raster-CPU `:kanvas-skia` implementation.
 *
 * See [AttributesGM] KDoc for the full analysis.
 */
class AttributesTest {

    @Test
    @Disabled(
        "STUB.GANESH_INTERNAL: AttributesGM requires GrGeometryProcessor, GrDrawOp, " +
            "GrGpuBuffer, GrRecordingContext and Ganesh GLSL emitters — none available " +
            "in the raster-CPU :kanvas-skia backend. Enable when a GPU backend lands.",
    )
    fun `AttributesGM placeholder`() {
        TestUtils.runGmTest(AttributesGM())
    }
}
