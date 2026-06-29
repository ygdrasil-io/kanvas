package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunID
import org.graphiks.kanvas.glyph.gpu.defaultGPUTextRouteRefusalReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ShapingIntegrationHandoffRouteTest {

    private fun shapedRun(
        runId: String,
        script: String,
        bidiLevel: Int,
        glyphIDs: List<Int>,
    ): GPUGlyphRunDescriptor = GPUGlyphRunDescriptor(
        runID = GPUGlyphRunID(Uuid.parse(runId)),
        glyphIDs = glyphIDs,
        script = script,
        bidiLevel = bidiLevel,
    )

    @Test
    fun `shaping facts flow into the gpu handoff descriptor`() {
        val rtl = shapedRun(
            runId = "550e8400-e29b-41d4-a716-446655440004",
            script = "Hebr",
            bidiLevel = 1,
            glyphIDs = listOf(40, 41),
        )

        assertEquals("Hebr", rtl.script)
        assertEquals(1, rtl.bidiLevel)
        assertEquals(listOf(40, 41), rtl.glyphIDs)
    }

    @Test
    fun `mixed bidi runs preserve distinct embedding levels`() {
        val ltr = shapedRun(
            runId = "550e8400-e29b-41d4-a716-446655440005",
            script = "Latn",
            bidiLevel = 0,
            glyphIDs = listOf(10),
        )
        val rtl = shapedRun(
            runId = "550e8400-e29b-41d4-a716-446655440006",
            script = "Hebr",
            bidiLevel = 1,
            glyphIDs = listOf(40),
        )

        assertEquals(0, ltr.bidiLevel)
        assertEquals(1, rtl.bidiLevel)
        assertTrue(ltr.bidiLevel != rtl.bidiLevel)
    }

    @Test
    fun `complex shaping integration never promotes a renderer claim`() {
        val report = defaultGPUTextRouteRefusalReport()
        assertTrue(report.refusals.none { refusal -> refusal.claimPromotionAllowed })

        val gates = GPUTextRepresentationGateMatrix.byRepresentation()
        assertFalse(gates.values.any { gate -> gate.promoted })
    }
}
