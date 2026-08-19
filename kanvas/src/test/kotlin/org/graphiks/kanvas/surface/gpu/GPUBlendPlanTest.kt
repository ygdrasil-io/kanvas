package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.paint.BlendMode

class GPUBlendPlanTest {
    @Test
    fun allPublicBlendModesHaveGpuPlans() {
        val plans = BlendMode.entries.map { mode -> mode to mode.toGpuBlendFacts().canonicalBlendPlan() }

        assertEquals(29, plans.size)
        assertTrue(plans.none { (_, plan) -> plan is GPUBlendPlan.UnsupportedBlend })
        assertEquals(GPUBlendMode.CLEAR, BlendMode.CLEAR.toGpuBlendFacts().mode)
        assertEquals(GPUBlendMode.DST_OVER, BlendMode.DST_OVER.toGpuBlendFacts().mode)
    }
}
