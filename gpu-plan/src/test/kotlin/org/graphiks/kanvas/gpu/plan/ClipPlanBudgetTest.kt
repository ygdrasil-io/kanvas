package org.graphiks.kanvas.gpu.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.math.geometry.SizeI32

class ClipPlanBudgetTest {
    @Test
    fun `AA clip pool charges two accumulators resolved scratch and msaa scratch`() {
        assertEquals(11_264L, ClipPlanBudget.aaPathClipBytes(SizeI32(16, 16)))
    }

    @Test
    fun `hard path clip pool charges its one-sample depth-stencil attachment`() {
        assertEquals(4_096L, ClipPlanBudget.hardClipBytes(SizeI32(16, 16)))
    }

    @Test
    fun `hard analytic rect pool omits depth-stencil capacity`() {
        assertEquals(3_072L, ClipPlanBudget.hardClipBytes(SizeI32(16, 16), requiresDepthStencil = false))
    }
}
