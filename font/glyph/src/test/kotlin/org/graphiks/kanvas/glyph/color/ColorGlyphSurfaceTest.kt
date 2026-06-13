package org.graphiks.kanvas.glyph.color

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the pure Kotlin color glyph package exposes the planned public surface.
 */
class ColorGlyphSurfaceTest {
    /**
     * References every color glyph planning type so the module fails to compile until the
     * surface exists.
     */
    @Test
    fun exposesColorGlyphPipelineSurface() {
        val names = listOf(
            ColorGlyphPlanner::class.simpleName,
            COLRGlyphPlanner::class.simpleName,
            COLRPaintGraph::class.simpleName,
            COLRPaintNode::class.simpleName,
            CPALPalette::class.simpleName,
            BitmapStrikeSelector::class.simpleName,
            PNGGlyphDecoder::class.simpleName,
            SVGGlyphRenderer::class.simpleName,
            SVGGlyphParser::class.simpleName,
            EmojiGlyphDispatcher::class.simpleName,
            ColorGlyphDiagnostic::class.simpleName,
        )

        assertEquals(
            listOf(
                "ColorGlyphPlanner",
                "COLRGlyphPlanner",
                "COLRPaintGraph",
                "COLRPaintNode",
                "CPALPalette",
                "BitmapStrikeSelector",
                "PNGGlyphDecoder",
                "SVGGlyphRenderer",
                "SVGGlyphParser",
                "EmojiGlyphDispatcher",
                "ColorGlyphDiagnostic",
            ),
            names,
        )
    }
}
