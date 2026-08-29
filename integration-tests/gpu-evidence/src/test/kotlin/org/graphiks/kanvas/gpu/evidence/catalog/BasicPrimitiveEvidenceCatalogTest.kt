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
        val oracle = assertNotNull(evidenceCase.oracle)
        val pixels = oracle.render(64, 64)
        fun pixel(x: Int, y: Int): IntArray = IntArray(4) { channel ->
            pixels[(y * 64 + x) * 4 + channel].toInt() and 0xff
        }

        // The right edge of the hard scissor must remove the otherwise fully
        // covered blue rectangle pixel; this makes the clip a material part of
        // the proof rather than a non-constraining fixture decoration.
        assertEquals(intArrayOf(31, 115, 209, 255).toList(), pixel(45, 30).toList())
        assertEquals(intArrayOf(13, 20, 33, 255).toList(), pixel(46, 30).toList())
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
