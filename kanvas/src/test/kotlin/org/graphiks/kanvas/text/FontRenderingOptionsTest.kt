package org.graphiks.kanvas.text

import org.graphiks.kanvas.geometry.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class FontRenderingOptionsTest {
    @Test
    fun `font retains Skia rendering selection options`() {
        val typeface = CustomTypeface.Builder().setGlyph(1, 1f, Path { }).build()
        val font = Font(
            typeface = typeface,
            size = 16f,
            antiAlias = true,
            subpixel = true,
            edging = FontEdging.SUBPIXEL_ANTI_ALIAS,
            hinting = FontHinting.FULL,
            embeddedBitmaps = true,
            variationCoordinates = mapOf("wght" to 721f),
        )

        assertEquals(FontEdging.SUBPIXEL_ANTI_ALIAS, font.edging)
        assertEquals(FontHinting.FULL, font.hinting)
        assertEquals(true, font.embeddedBitmaps)
        assertEquals(721f, font.variationCoordinates.getValue("wght"))
    }
}
