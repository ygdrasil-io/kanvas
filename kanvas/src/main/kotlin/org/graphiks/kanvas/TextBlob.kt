package org.graphiks.kanvas

import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.handoff.GlyphDescriptor
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor

data class TextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
) {
    fun lower(): GlyphRunDescriptor {
        val glyphDescriptors = glyphRuns.flatMap { run ->
            run.glyphs.mapIndexed { index, glyphId ->
                GlyphDescriptor(
                    strikeKey = GlyphStrikeKey(
                        glyphId = glyphId.toInt(),
                        size = 12f,
                        subpixelX = 0,
                        subpixelY = 0,
                    ),
                    placement = GlyphAtlasPlacement(
                        strikeKey = GlyphStrikeKey(
                            glyphId = glyphId.toInt(),
                            size = 12f,
                            subpixelX = 0,
                            subpixelY = 0,
                        ),
                        region = AtlasRegion(x = 0, y = 0, width = 0, height = 0),
                    ),
                    drawX = run.positions.getOrElse(index) { KanvasPoint(0f, 0f) }.x,
                    drawY = run.positions.getOrElse(index) { KanvasPoint(0f, 0f) }.y,
                )
            }
        }
        return GlyphRunDescriptor(
            glyphs = glyphDescriptors,
            atlasPlan = GlyphAtlasUploadPlan.Accepted(
                atlasWidth = 0,
                atlasHeight = 0,
                atlasBytes = ByteArray(0),
                placements = emptyList(),
            ),
        )
    }
}

data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<KanvasPoint>,
)
