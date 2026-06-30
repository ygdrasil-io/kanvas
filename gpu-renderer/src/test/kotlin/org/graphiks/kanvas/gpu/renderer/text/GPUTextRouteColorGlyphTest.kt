package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayer
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextRouteColorGlyphTest {

    private fun richPlan(): GPUColorGlyphLayerPlan = GPUColorGlyphLayerPlan(
        artifactKey = GPUTextArtifactKey(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440030")),
            generation = GPUTextArtifactGeneration(1),
            contentFingerprint = "fp",
        ),
        baseGlyphID = 7u,
        layers = listOf(
            GPUColorGlyphLayer(
                layerGlyphID = 11u,
                paletteIndex = 0,
                resolvedColorArgb = 0xFFFF0000.toInt(),
                useForeground = false,
            ),
        ),
    )

    @Test
    fun `color font format unavailable and layer count exceeded codes exist`() {
        assertTrue(GPUTextDiagnosticCodes.COLOR_FONT_FORMAT_UNAVAILABLE in GPUTextDiagnosticCodes.all)
        assertTrue(GPUTextDiagnosticCodes.COLOR_FONT_LAYER_COUNT_EXCEEDED in GPUTextDiagnosticCodes.all)
        assertEquals(
            "unsupported.text.color_font.format_unavailable",
            GPUTextDiagnosticCodes.COLOR_FONT_FORMAT_UNAVAILABLE,
        )
        assertEquals(
            "unsupported.text.color_font.layer_count_exceeded",
            GPUTextDiagnosticCodes.COLOR_FONT_LAYER_COUNT_EXCEEDED,
        )
    }

    @Test
    fun `color glyph refusal kind enum exists`() {
        assertEquals(
            listOf(
                ColorGlyphRefusalKind.FORMAT_UNAVAILABLE,
                ColorGlyphRefusalKind.LAYER_COUNT_EXCEEDED,
            ),
            ColorGlyphRefusalKind.entries.toList(),
        )
    }

    @Test
    fun `color glyph route carries the rich GPUColorGlyphLayerPlan`() {
        val route = GPUTextRoute.ColorGlyph(plan = richPlan())
        assertEquals(7u, route.plan.baseGlyphID)
        assertEquals(1, route.plan.layerCount)
    }

    @Test
    fun `COLRColorGlyph gate is promoted with render evidence`() {
        val gates = GPUTextRepresentationGateMatrix.byRepresentation()
        val colorGate = gates.getValue("COLRColorGlyph")
        assertEquals(GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED, colorGate.diagnosticCode)
        assertTrue(colorGate.promoted)
    }
}
