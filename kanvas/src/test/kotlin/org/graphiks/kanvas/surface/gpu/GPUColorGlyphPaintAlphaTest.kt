package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GPUColorGlyphPaintAlphaTest {
    @Test
    fun `CPAL layer alpha is multiplied by partial paint alpha`() {
        val cpalLayer = Color.fromArgb(192, 32, 96, 224)
        val paint = Color.fromArgb(128, 255, 255, 255)

        val modulated = modulateCpalLayerAlpha(cpalLayer, paint)

        assertEquals(32, modulated.redByte)
        assertEquals(96, modulated.greenByte)
        assertEquals(224, modulated.blueByte)
        assertEquals(96, modulated.alphaByte)
    }

    @Test
    fun `color glyph geometry coverage is white and opaque`() {
        val cpalLayer = Color.fromArgb(96, 32, 96, 224)

        assertEquals(
            Color.WHITE,
            colorGlyphSourceColor(cpalLayer, geometryCoverage = true),
        )
        assertEquals(
            cpalLayer,
            colorGlyphSourceColor(cpalLayer, geometryCoverage = false),
        )
    }
}
