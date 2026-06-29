package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayer
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.text.ColorGlyphRefusalKind
import org.graphiks.kanvas.gpu.renderer.text.GPUColorGlyphRouteDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUColorGlyphRoutePlannerTest {

    private fun key(): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440031")),
        generation = GPUTextArtifactGeneration(1),
        contentFingerprint = "color-plan-sha256",
    )

    private fun layer(glyph: UInt): GPUColorGlyphLayer = GPUColorGlyphLayer(
        layerGlyphID = glyph,
        paletteIndex = 0,
        resolvedColorArgb = 0xFFFF0000.toInt(),
        useForeground = false,
    )

    private fun plan(layerN: Int): GPUColorGlyphLayerPlan = GPUColorGlyphLayerPlan(
        artifactKey = key(),
        baseGlyphID = 7u,
        layers = (1..layerN).map { i -> layer(i.toUInt()) },
    )

    @Test
    fun `accepts COLRv0 within layer budget`() {
        val decision = GPUColorGlyphRoutePlanner()
            .planColorGlyphRoute(plan(GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS))
        assertTrue(decision is GPUColorGlyphRouteDecision.Accepted)
        assertEquals(7u, (decision as GPUColorGlyphRouteDecision.Accepted).route.plan.baseGlyphID)
    }

    @Test
    fun `accepts exactly the max layer budget`() {
        val decision = GPUColorGlyphRoutePlanner().planColorGlyphRoute(plan(16))
        assertTrue(decision is GPUColorGlyphRouteDecision.Accepted)
    }

    @Test
    fun `refuses layer count exceeding max`() {
        val decision = GPUColorGlyphRoutePlanner()
            .planColorGlyphRoute(plan(GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS + 1))
        val refused = assertIs<GPUColorGlyphRouteDecision.Refused>(decision)
        assertEquals(ColorGlyphRefusalKind.LAYER_COUNT_EXCEEDED, refused.refusalKind)
        assertEquals(
            "unsupported.text.color_font.layer_count_exceeded",
            refused.diagnostic.code,
        )
    }

    @Test
    fun `refuses unsupported color format`() {
        val refused = GPUColorGlyphRoutePlanner().refuseUnsupportedColorFormat("COLRv1")
        assertEquals(ColorGlyphRefusalKind.FORMAT_UNAVAILABLE, refused.refusalKind)
        assertEquals(
            "unsupported.text.color_font.format_unavailable",
            refused.diagnostic.code,
        )
    }
}
