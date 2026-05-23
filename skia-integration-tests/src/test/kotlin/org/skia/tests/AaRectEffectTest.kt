package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "INTRACTABLE.GPU_ONLY: aa_rect_effect is a GpuGM that exercises " +
        "GrFragmentProcessor::Rect with all GrClipEdgeType variants via " +
        "SurfaceDrawContext::addDrawOp — the entire onDraw body requires a " +
        "Ganesh GPU context and returns kSkip on any raster canvas. " +
        "No reference PNG exists for this GM in the repo.",
)
class AaRectEffectTest {

    @Test
    fun `AaRectEffectGM is GPU-only INTRACTABLE`() {
        val gm = AaRectEffectGM()
        TestUtils.runGmTest(gm)
    }
}
