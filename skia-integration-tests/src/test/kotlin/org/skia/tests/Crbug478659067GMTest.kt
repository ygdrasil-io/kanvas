package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "INTRACTABLE.GRAPHITE_ONLY: crbug_478659067 requires a Graphite (SK_GRAPHITE) " +
        "recorder — canvas->recorder() is null on all raster/CPU backends and the " +
        "upstream GM explicitly returns DrawResult::kSkip with errorMsg \"Graphite only\". " +
        "Unblocked by STUB.GRAPHITE_RECORDER, STUB.GRAPHITE_RENDER_TARGET, " +
        "STUB.GRAPHITE_CAPS_GLYPHS_AS_PATHS_FONT_SIZE, STUB.CANVAS_GET_BASE_LAYER_SIZE, " +
        "STUB.CANVAS_IMAGE_INFO, STUB.GRAPHITE_SURFACE_DRAW.",
)
class Crbug478659067GMTest {

    @Test
    fun `CrBug478659067GM is INTRACTABLE GRAPHITE_ONLY`() {
        val gm = CrBug478659067GM()
        TestUtils.runGmTest(gm)
    }
}
