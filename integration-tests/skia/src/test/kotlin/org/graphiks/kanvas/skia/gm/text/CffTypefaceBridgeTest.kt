package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.text.Typefaces
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CffTypefaceBridgeTest {
    @Test
    fun `CFF OpenType fixture exposes a drawable glyph outline`() {
        val typeface = requireNotNull(Typefaces.fromResource("fonts/Stroking.otf"))
        val glyphId = typeface.glyphIdForCodepoint('◉'.code)

        assertTrue(glyphId != 0)
        assertNotNull(typeface.getGlyphPath(glyphId, 100f))
        assertTrue(typeface.getAdvance(glyphId, 100f) > 0f)
    }

}
