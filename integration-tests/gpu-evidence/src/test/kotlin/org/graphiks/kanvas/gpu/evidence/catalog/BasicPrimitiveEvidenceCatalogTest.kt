package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BasicPrimitiveEvidenceCatalogTest {
    @Test
    fun `fractional AA rectangle overlap has a public CPU-backed evidence row`() {
        val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull {
            it.descriptor.id.value == "fractional-aa-rect-overlap"
        })

        assertEquals(EvidenceExecutionBoundary.PublicSurface, evidenceCase.executionBoundary)
        assertNotNull(evidenceCase.oracle)
    }

    @Test
    fun `basic primitive catalogue has public render evidence for color rect rrect drrect and points`() {
        val ids = GpuEvidenceCatalog.renderCases.map { it.descriptor.id.value }.toSet()

        assertTrue("basic-primitives-valid-alpha" in ids)
        assertTrue("basic-primitives-out-of-bounds" in ids)
        assertTrue("basic-primitives-points" in ids)

        listOf(
            "basic-primitives-valid-alpha",
            "basic-primitives-out-of-bounds",
            "basic-primitives-points",
        ).forEach { id ->
            val evidenceCase = assertNotNull(GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id })
            assertEquals(EvidenceExecutionBoundary.PublicSurface, evidenceCase.executionBoundary)
            assertNotNull(evidenceCase.oracle)
        }

        val emptyRect = assertNotNull(GpuEvidenceCatalog.refusalCases.firstOrNull {
            it.descriptor.id.value == "basic-primitives-empty-rect-refusal"
        })
        assertEquals(
            "unsupported.core_primitive.geometry.invalid",
            (emptyRect.descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode,
        )
    }
}
