package org.graphiks.kanvas.surface.gpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUProductIntermediatePlannerScopeTest {
    @Test
    fun `product renderer destination-read path is explicitly outside phase five planner activation`() {
        val diagnostics = productIntermediatePlannerScopeDiagnostics()

        assertTrue(
            diagnostics.any {
                it.contains("phase5PlannerActivation=false") &&
                    it.contains("reason=product-display-list-route-not-yet-planner-backed")
            },
            diagnostics.toString(),
        )
    }
}
