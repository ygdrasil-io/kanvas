package org.graphiks.kanvas.glyph.color

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ColorGlyphGpuHandoffTest {

    private fun bounds(): ColorGlyphBounds =
        ColorGlyphBounds(xMin = 0, yMin = 0, xMax = 16, yMax = 16)

    private fun layer(
        index: Int,
        glyphId: Int,
        paletteIndex: Int,
        resolvedColor: Int?,
        foreground: Boolean,
    ): COLRV0LayerPlan = COLRV0LayerPlan(
        layerIndex = index,
        glyphId = glyphId,
        paletteIndex = paletteIndex,
        resolvedColor = resolvedColor,
        usesForegroundColor = foreground,
        outlineArtifactKey = ColorGlyphArtifactKey(
            glyphId = glyphId,
            route = "colrv0",
            strikeKeySha256 = "layer-$index-sha256",
        ),
        bounds = bounds(),
    )

    private fun richPlan(): ColorGlyphPlan = ColorGlyphPlan(
        glyphId = 7,
        typefaceId = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440020")),
        artifactKey = ColorGlyphArtifactKey(
            glyphId = 7,
            route = "colrv0",
            strikeKeySha256 = "base-sha256",
        ),
        palette = ColorGlyphPalette(
            identity = "cpal:0",
            selectionIndex = 0,
            resolvedIndex = 0,
            overrideCount = 0,
            colorCount = 2,
        ),
        layers = listOf(
            layer(
                index = 1,
                glyphId = 12,
                paletteIndex = COLR_FOREGROUND_PALETTE_INDEX,
                resolvedColor = null,
                foreground = true,
            ),
            layer(
                index = 0,
                glyphId = 11,
                paletteIndex = 0,
                resolvedColor = 0xFFFF0000.toInt(),
                foreground = false,
            ),
        ),
        bounds = bounds(),
        fallbackPolicy = "refuse",
    )

    @Test
    fun `bridge maps base glyph, layer order, colors, and foreground`() {
        val plan = richPlan()

        val gpu = plan.toGPUColorGlyphLayerPlan(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440021")),
            generation = GPUTextArtifactGeneration(1),
        )

        assertEquals(7u, gpu.baseGlyphID)
        assertEquals(2, gpu.layerCount)
        assertEquals(11u, gpu.layers[0].layerGlyphID)
        assertEquals(0xFFFF0000.toInt(), gpu.layers[0].resolvedColorArgb)
        assertEquals(0, gpu.layers[0].paletteIndex)
        assertEquals(0xFFFF, gpu.layers[1].paletteIndex)
        assertFalse(gpu.layers[0].useForeground)
        assertEquals(12u, gpu.layers[1].layerGlyphID)
        assertTrue(gpu.layers[1].useForeground)
    }

    @Test
    fun `bridge carries the plan content fingerprint`() {
        val plan = richPlan()

        val gpu = plan.toGPUColorGlyphLayerPlan(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440021")),
            generation = GPUTextArtifactGeneration(1),
        )

        assertEquals(plan.dumpSha256, gpu.artifactKey.contentFingerprint)
    }
}
