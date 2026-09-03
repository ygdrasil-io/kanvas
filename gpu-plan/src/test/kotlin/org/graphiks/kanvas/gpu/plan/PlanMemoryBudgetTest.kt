package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlanMemoryBudgetTest {
    @Test
    fun `budget aligns readback and totals target plus staging`() {
        val result = PlanMemoryBudget.calculate(SizeI32(17, 3), bytesPerPixel = 4, copyBytesPerRowAlignment = 256, budget = PlanBudget(972))

        val within = assertIs<PlanMemoryBudgetResult.WithinBudget>(result)
        assertEquals(256, within.readbackBytesPerRow)
        assertEquals(972, within.peakBytes)
    }

    @Test
    fun `budget rejects a limit strictly below peak and arithmetic overflow`() {
        assertEquals(PlanMemoryBudgetResult.Exceeded(972, 971),
            PlanMemoryBudget.calculate(SizeI32(17, 3), 4, 256, PlanBudget(971)))
        assertEquals(PlanMemoryBudgetResult.Invalid("size-overflow"),
            PlanMemoryBudget.calculate(SizeI32(Int.MAX_VALUE, Int.MAX_VALUE), 4, 256, PlanBudget(Long.MAX_VALUE)))
    }
}
