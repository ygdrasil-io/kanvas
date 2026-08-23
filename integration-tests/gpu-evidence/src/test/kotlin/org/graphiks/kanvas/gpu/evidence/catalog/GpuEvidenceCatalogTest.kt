package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator

class GpuEvidenceCatalogTest {
    @Test
    fun `catalog is the approved seven case gate`() {
        val cases = GpuEvidenceCatalog.cases

        assertEquals(
            listOf(
                "solid-card-stack",
                "separable-blur-rect",
                "translucent-card-overlap",
                "scissor-overlay",
                "stroke-rect-outline",
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

        listOf("translucent-card-overlap", "scissor-overlay", "stroke-rect-outline").forEach { id ->
            val evidenceCase = assertNotNull(cases.firstOrNull { it.descriptor.id.value == id })
            assertEquals(64, evidenceCase.descriptor.width)
            assertEquals(64, evidenceCase.descriptor.height)
            assertIs<EvidenceExpectation.ShouldRender>(evidenceCase.descriptor.expectation)
            assertNotNull(evidenceCase.oracle)
            assertNotNull(evidenceCase.descriptor.comparison)
        }
        assertEquals(1, cases.first { it.descriptor.id.value == "translucent-card-overlap" }.descriptor.comparison?.perChannelTolerance)
        assertEquals(0, cases.first { it.descriptor.id.value == "scissor-overlay" }.descriptor.comparison?.perChannelTolerance)
        assertEquals(0, cases.first { it.descriptor.id.value == "stroke-rect-outline" }.descriptor.comparison?.perChannelTolerance)

        val budget = assertNotNull(cases.firstOrNull { it.descriptor.id.value == "aggregate-memory-budget-refusal" })
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", assertIs<EvidenceExpectation.ShouldRefuse>(budget.descriptor.expectation).stableReasonCode)
        assertEquals(null, budget.oracle)
    }

    @Test
    fun `translucent policy accepts material rgba8 rounding delta one but rejects delta two`() {
        val evidenceCase = assertNotNull(
            GpuEvidenceCatalog.cases.firstOrNull { it.descriptor.id.value == "translucent-card-overlap" },
        )
        val policy = assertNotNull(evidenceCase.descriptor.comparison)
        assertEquals(1, policy.perChannelTolerance)
        assertEquals(100.0, policy.minimumSimilarityPercent)
        val oracle = assertNotNull(evidenceCase.oracle).render(64, 64)
        val comparator = EvidenceComparator()

        val deltaOne = oracle.copyOf().also { it[0] = (it[0].toInt() + 1).toByte() }
        val deltaTwo = oracle.copyOf().also { it[0] = (it[0].toInt() + 2).toByte() }

        val roundedGpu = comparator.compare(deltaOne, oracle, 64, 64, policy)
        assertTrue(roundedGpu.passed)
        assertEquals(100.0, roundedGpu.similarityPercent)

        val outOfBound = comparator.compare(deltaTwo, oracle, 64, 64, policy)
        assertFalse(outOfBound.passed)
    }
}
