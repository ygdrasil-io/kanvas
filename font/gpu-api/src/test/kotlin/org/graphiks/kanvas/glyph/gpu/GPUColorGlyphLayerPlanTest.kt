package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUColorGlyphLayerPlanTest {

    private fun key(): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440010")),
        generation = GPUTextArtifactGeneration(1),
        contentFingerprint = "color-layer-plan-sha256",
    )

    @Test
    fun `plan exposes ordered layers and layer count`() {
        val plan = GPUColorGlyphLayerPlan(
            artifactKey = key(),
            baseGlyphID = 7u,
            layers = listOf(
                GPUColorGlyphLayer(
                    layerGlyphID = 11u,
                    paletteIndex = 0,
                    resolvedColorArgb = 0xFFFF0000.toInt(),
                    useForeground = false,
                ),
                GPUColorGlyphLayer(
                    layerGlyphID = 12u,
                    paletteIndex = 0xFFFF,
                    resolvedColorArgb = null,
                    useForeground = true,
                ),
            ),
        )

        assertEquals(2, plan.layerCount)
        assertEquals(7u, plan.baseGlyphID)
        assertEquals(11u, plan.layers[0].layerGlyphID)
        assertTrue(plan.layers[1].useForeground)
    }

    @Test
    fun `plan rejects an empty layer list`() {
        assertFailsWith<IllegalArgumentException> {
            GPUColorGlyphLayerPlan(artifactKey = key(), baseGlyphID = 7u, layers = emptyList())
        }
    }

    @Test
    fun `dump lists base glyph, colors, and foreground layers`() {
        val plan = GPUColorGlyphLayerPlan(
            artifactKey = key(),
            baseGlyphID = 7u,
            layers = listOf(
                GPUColorGlyphLayer(
                    layerGlyphID = 11u,
                    paletteIndex = 3,
                    resolvedColorArgb = 0xFFFF0000.toInt(),
                    useForeground = false,
                ),
                GPUColorGlyphLayer(
                    layerGlyphID = 12u,
                    paletteIndex = 0xFFFF,
                    resolvedColorArgb = null,
                    useForeground = true,
                ),
            ),
        )

        assertEquals(
            "GPUColorGlyphLayerPlan(baseGlyphID=7, contentFingerprint=color-layer-plan-sha256, " +
                "layerCount=2, layers=[{layerGlyphID=11, paletteIndex=3, color=#FFFF0000}, " +
                "{layerGlyphID=12, paletteIndex=65535, color=foreground}])",
            plan.toColorLayerDump(),
        )
    }

    @Test
    fun `foreground layer with a non-null color is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUColorGlyphLayer(
                layerGlyphID = 11u,
                paletteIndex = 0xFFFF,
                resolvedColorArgb = 0xFF0000FF.toInt(),
                useForeground = true,
            )
        }
    }

    @Test
    fun `non-foreground layer with a null color is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GPUColorGlyphLayer(
                layerGlyphID = 11u,
                paletteIndex = 0,
                resolvedColorArgb = null,
                useForeground = false,
            )
        }
    }
}
