package org.graphiks.kanvas.font.handoff

import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey

data class GlyphDescriptor(
    val strikeKey: GlyphStrikeKey,
    val placement: GlyphAtlasPlacement,
    val drawX: Float,
    val drawY: Float,
)

data class GlyphRunDescriptor(
    val glyphs: List<GlyphDescriptor>,
    val atlasPlan: GlyphAtlasUploadPlan,
)
