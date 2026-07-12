package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.paint.BlendMode

class GPUBlendPlanTest {
    @Test
    fun allPublicBlendModesHaveGpuPlans() {
        val plans = BlendMode.entries.map { it to it.toGpuBlendFacts() }

        assertEquals(29, plans.size)
        assertTrue(plans.none { (_, plan) -> plan.kind == GPUBlendKind.Unsupported })
        assertEquals(GPUBlendMode.CLEAR, BlendMode.CLEAR.toGpuBlendFacts().blendMode)
        assertEquals(GPUBlendMode.DST_OVER, BlendMode.DST_OVER.toGpuBlendFacts().blendMode)
    }
}
