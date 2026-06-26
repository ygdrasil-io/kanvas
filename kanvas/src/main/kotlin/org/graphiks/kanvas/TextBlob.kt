package org.graphiks.kanvas

import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlanner
import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.A8Rasterizer
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.handoff.GlyphDescriptor
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.font.scaler.GlyphScaleResult
import org.graphiks.kanvas.font.scaler.GlyphScaler

data class TextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
    val typeface: KanvasTypeface? = null,
    val fontSize: Float = 12f,
) {
    /**
     * Lowers this blob into a [GlyphRunDescriptor] for the GPU command stream.
     *
     * When a [typeface] is supplied, the real `:font` pipeline runs at lowering
     * time (the "text stack" role): [GlyphScaler] scales each distinct glyph,
     * [A8Rasterizer] produces A8 coverage, and [GlyphAtlasUploadPlanner] packs a
     * real atlas artifact. The resulting [GlyphRunDescriptor.atlasPlan] is the
     * typed artifact the renderer consumes — the typeface itself never enters the
     * normalized command (`21-text-glyph-pipeline.md`).
     *
     * Without a typeface the blob lowers to the legacy contract shape (glyph
     * descriptors with an empty atlas plan); the command is still refused
     * downstream until the typeface-backed route is wired.
     */
    fun lower(): GlyphRunDescriptor {
        val tf = typeface ?: return lowerWithoutTypeface()

        val fontBytes = TextBlob::class.java.getResourceAsStream(tf.resourcePath)?.readBytes()
            ?: return GlyphRunDescriptor(
                glyphs = buildGlyphDescriptors(emptyMap()),
                atlasPlan = GlyphAtlasUploadPlan.Refused("font-resource-missing:${tf.resourcePath}"),
            )

        val scaler = GlyphScaler.fromBytes(fontBytes)
        val rasterizer = A8Rasterizer()
        val entries = mutableListOf<Pair<GlyphStrikeKey, A8Bitmap>>()
        val distinctGlyphIds = glyphRuns.flatMap { it.glyphs }.map { it.toInt() }.distinct()
        for (glyphId in distinctGlyphIds) {
            val scaled = when (val result = scaler.scaleGlyphOrDiagnostic(glyphId, fontSize)) {
                is GlyphScaleResult.Success -> result.glyph
                is GlyphScaleResult.Unsupported -> continue
            }
            val bitmap = rasterizer.rasterize(scaled) ?: continue
            entries += GlyphStrikeKey(glyphId = glyphId, size = fontSize, subpixelX = 0, subpixelY = 0) to bitmap
        }

        val plan = GlyphAtlasUploadPlanner().plan(entries)
        val regionByGlyphId: Map<Int, AtlasRegion> = when (plan) {
            is GlyphAtlasUploadPlan.Accepted -> plan.placements.associate { it.strikeKey.glyphId to it.region }
            is GlyphAtlasUploadPlan.Refused -> emptyMap()
        }
        return GlyphRunDescriptor(glyphs = buildGlyphDescriptors(regionByGlyphId), atlasPlan = plan)
    }

    private fun lowerWithoutTypeface(): GlyphRunDescriptor = GlyphRunDescriptor(
        glyphs = buildGlyphDescriptors(emptyMap()),
        atlasPlan = GlyphAtlasUploadPlan.Accepted(
            atlasWidth = 0,
            atlasHeight = 0,
            atlasBytes = ByteArray(0),
            placements = emptyList(),
        ),
    )

    private fun buildGlyphDescriptors(regionByGlyphId: Map<Int, AtlasRegion>): List<GlyphDescriptor> =
        glyphRuns.flatMap { run ->
            run.glyphs.mapIndexed { index, glyphId ->
                val id = glyphId.toInt()
                val strikeKey = GlyphStrikeKey(glyphId = id, size = fontSize, subpixelX = 0, subpixelY = 0)
                val region = regionByGlyphId[id] ?: AtlasRegion(x = 0, y = 0, width = 0, height = 0)
                val position = run.positions.getOrElse(index) { KanvasPoint(0f, 0f) }
                GlyphDescriptor(
                    strikeKey = strikeKey,
                    placement = GlyphAtlasPlacement(strikeKey = strikeKey, region = region),
                    drawX = position.x,
                    drawY = position.y,
                )
            }
        }
}

data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<KanvasPoint>,
)
