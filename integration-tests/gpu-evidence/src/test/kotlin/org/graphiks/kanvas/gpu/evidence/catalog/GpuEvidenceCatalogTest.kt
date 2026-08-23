package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GpuEvidenceCatalogTest {
    @Test
    fun `catalog is the approved four case gate`() {
        val cases = GpuEvidenceCatalog.cases

        assertEquals(
            listOf(
                "solid-card-stack",
                "separable-blur-rect",
                "custom-runtime-effect-unregistered-refusal",
                "aggregate-memory-budget-refusal",
            ),
            cases.map { it.descriptor.id.value },
        )
        assertEquals(cases.size, cases.map { it.descriptor.id }.toSet().size)

        val solid = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "solid-card-stack" })
        assertEquals(64, solid.descriptor.width)
        assertEquals(64, solid.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(solid.descriptor.expectation)
        assertNotNull(solid.oracle)
        assertEquals(0, solid.descriptor.comparison?.perChannelTolerance)
        assertEquals(100.0, solid.descriptor.comparison?.minimumSimilarityPercent)

        val refusal = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "custom-runtime-effect-unregistered-refusal" })
        assertEquals(16, refusal.descriptor.width)
        assertEquals(16, refusal.descriptor.height)
        assertEquals(
            "unsupported.runtime_effect.custom_wgsl_not_registered",
            assertIs<EvidenceExpectation.ShouldRefuse>(refusal.descriptor.expectation).stableReasonCode,
        )
        assertEquals(null, refusal.oracle)

        val blur = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "separable-blur-rect" })
        assertEquals(64, blur.descriptor.width)
        assertEquals(64, blur.descriptor.height)
        assertIs<EvidenceExpectation.ShouldRender>(blur.descriptor.expectation)
        assertNotNull(blur.oracle)
        assertEquals(2, blur.descriptor.comparison?.perChannelTolerance)
        assertEquals(99.0, blur.descriptor.comparison?.minimumSimilarityPercent)

        val budget = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "aggregate-memory-budget-refusal" })
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", assertIs<EvidenceExpectation.ShouldRefuse>(budget.descriptor.expectation).stableReasonCode)
        assertEquals(null, budget.oracle)
    }
}
